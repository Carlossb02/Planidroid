package es.planidroid.app.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import es.planidroid.app.domain.ReleaseVersion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.URL
import java.net.HttpURLConnection
import java.security.MessageDigest

private const val REPO = "Carlossb02/Planidroid"
private const val MAX_APK = 200L * 1024 * 1024
private const val MAX_JSON = 2 * 1024 * 1024

data class Release(val tag: String, val notes: String, val url: String, val size: Long, val digest: String)

class GithubUpdater(private val context: Context) {
    private val pm = context.packageManager
    @Suppress("DEPRECATION")
    private val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
    @Suppress("DEPRECATION")
    private fun installed() = pm.getPackageInfo(context.packageName, flags)
    @Suppress("DEPRECATION")
    private fun code(info: PackageInfo): Long = if (Build.VERSION.SDK_INT >= 28) info.longVersionCode else info.versionCode.toLong()
    @Suppress("DEPRECATION")
    private fun signers(info: PackageInfo): Set<String> = (if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures).orEmpty().map { it.toCharsString() }.toSet()
    private fun connection(url: String): HttpURLConnection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10000; readTimeout = 20000
        setRequestProperty("User-Agent", "Planidroid-Android")
        setRequestProperty("Accept", "application/vnd.github+json")
    }
    suspend fun check(): Release? = withContext(Dispatchers.IO) {
        val c = connection("https://api.github.com/repos/$REPO/releases/latest")
        try {
            if (c.responseCode == 404) return@withContext null
            check(c.responseCode == 200) { "GitHub no disponible (${c.responseCode})" }
            val bytes = c.inputStream.use { input ->
                val output = java.io.ByteArrayOutputStream(); val buf = ByteArray(8192)
                while (true) { val n = input.read(buf); if (n < 0) break; require(output.size() + n <= MAX_JSON); output.write(buf, 0, n) }
                output.toByteArray()
            }
            val json = JSONObject(bytes.toString(Charsets.UTF_8)); val tag = json.getString("tag_name")
            if (json.optBoolean("draft") || json.optBoolean("prerelease") || !ReleaseVersion.newer(tag, installed().versionName.orEmpty())) return@withContext null
            val assets = json.getJSONArray("assets")
            // Prefer the versioned APK; retain support for the previous asset convention.
            for (name in ReleaseVersion.apkNames(tag)) for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name") != name) continue
                val url = a.getString("browser_download_url"); val size = a.getLong("size"); val digest = a.optString("digest")
                if (!url.startsWith("https://github.com/$REPO/releases/download/") || size !in 1L..MAX_APK || !Regex("sha256:[a-fA-F0-9]{64}").matches(digest)) continue
                return@withContext Release(tag, json.optString("body").take(4000), url, size, digest.substringAfter(':').lowercase())
            }
            null
        } finally { c.disconnect() }
    }
    suspend fun download(release: Release): File = withContext(Dispatchers.IO) {
        require(release.url.startsWith("https://github.com/$REPO/releases/download/"))
        require(release.size in 1L..MAX_APK)
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }; val tmp = File.createTempFile("planidroid-", ".apk", dir)
        var success = false
        try {
            val c = connection(release.url)
            val digest = MessageDigest.getInstance("SHA-256")
            try {
                c.setRequestProperty("Accept", "application/octet-stream")
                check(c.responseCode == 200) { "Descarga no disponible" }
                c.inputStream.use { input -> tmp.outputStream().use { out ->
                    var total = 0L; val buffer = ByteArray(65536)
                    while (true) { val n = input.read(buffer); if (n < 0) break; total += n; require(total <= release.size); digest.update(buffer, 0, n); out.write(buffer, 0, n) }
                    require(total == release.size) { "Descarga incompleta" }
                } }
            } finally { c.disconnect() }
            require(digest.digest().joinToString("") { "%02x".format(it.toInt() and 255) } == release.digest) { "Checksum incorrecto" }
            @Suppress("DEPRECATION") val apk = pm.getPackageArchiveInfo(tmp.absolutePath, flags) ?: error("APK inválido")
            val current = installed()
            require(apk.packageName == context.packageName && code(apk) > code(current) && apk.versionName == release.tag.removePrefix("v")) { "Identidad o versión incorrecta" }
            val expected = signers(current)
            require(expected.isNotEmpty() && signers(apk) == expected) { "La firma del APK no coincide" }
            success = true; tmp
        } finally { if (!success) tmp.delete() }
    }
    fun install(file: File) {
        if (!pm.canRequestPackageInstalls()) {
            context.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
    }
}
