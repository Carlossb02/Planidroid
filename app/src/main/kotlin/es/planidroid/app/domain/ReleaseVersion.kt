package es.planidroid.app.domain

/** Four-part stable release versions; legacy three-part versions imply a final zero. */
object ReleaseVersion {
    private val stableTag = Regex("^v?(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)\\.(0|[1-9][0-9]*)(?:\\.(0|[1-9][0-9]*))?$")
    fun parse(tag: String): List<Int>? {
        val match = stableTag.matchEntire(tag) ?: return null
        return match.groupValues.drop(1).mapIndexed { index, value ->
            if (index == 3 && value.isEmpty()) 0 else value.toIntOrNull() ?: return null
        }
    }
    fun newer(candidate: String, installed: String): Boolean {
        val a = parse(candidate) ?: return false
        val b = parse(installed) ?: return false
        for (i in a.indices) if (a[i] != b[i]) return a[i] > b[i]
        return false
    }
    /** APK metadata must agree with the release; never bypass Android's upgrade checks. */
    fun validationError(tag:String,packageName:String,expectedPackage:String,apkVersion:String?,apkCode:Long,installedVersion:String?,installedCode:Long):String? {
        if(packageName!=expectedPackage)return "Paquete incorrecto: el APK contiene $packageName; se esperaba $expectedPackage."
        if(parse(tag)==null||apkVersion!=tag.removePrefix("v"))return "Versión del APK (${apkVersion ?: "desconocida"}) distinta de la release (${tag.removePrefix("v")})."
        if(apkCode<=installedCode)return "versionCode del APK ($apkCode) no supera el instalado ($installedCode; versión ${installedVersion ?: "desconocida"}). Publica una compilación con versionCode superior."
        return null
    }
    fun apkNames(tag: String): List<String> = if (parse(tag) == null) emptyList()
        else listOf("Planidroid_v${tag.removePrefix("v")}.apk", "Planidroid.apk")
}
