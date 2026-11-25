import java.io.File
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.StandardOpenOption
import java.util.Properties

pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS); repositories { google(); mavenCentral() } }
rootProject.name = "Planidroid"

// Initialize the untracked SDK configuration before Android projects are evaluated.
// Never overwrite an existing local.properties or put credentials into this file.
val localSdkProperties = File(settingsDir, "local.properties")
if (!localSdkProperties.exists()) {
    val home = System.getProperty("user.home")?.let { File(it) }
    val candidates = listOfNotNull(
        System.getenv("ANDROID_HOME")?.takeIf { it.isNotBlank() }?.let { File(it) },
        System.getenv("ANDROID_SDK_ROOT")?.takeIf { it.isNotBlank() }?.let { File(it) },
        System.getenv("LOCALAPPDATA")?.let { File(it, "Android/Sdk") },
        home?.let { File(it, "AppData/Local/Android/Sdk") },
        home?.let { File(it, "Library/Android/sdk") },
        home?.let { File(it, "Android/Sdk") }
    )
    val sdk = candidates.firstOrNull {
        it.isDirectory && (File(it, "platforms").isDirectory ||
            File(it, "platform-tools").isDirectory ||
            File(it, "cmdline-tools").isDirectory)
    }
    if (sdk != null) {
        try {
            Files.newOutputStream(
                localSdkProperties.toPath(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE
            ).use { output ->
                Properties().apply {
                    setProperty("sdk.dir", sdk.absolutePath.replace('\\', '/'))
                }.store(output, "Generated locally by Planidroid; never commit this file.")
            }
            println("Planidroid: generated local.properties for the installed Android SDK.")
        } catch (_: FileAlreadyExistsException) {
            // Another Gradle process or Android Studio created the local file.
        }
    } else {
        println("Planidroid: Android SDK not detected. Install SDK 35 or configure sdk.dir locally.")
    }
}
include(":app")
