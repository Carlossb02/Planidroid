plugins {
    kotlin("jvm") version "2.0.21"
    application
}
repositories { mavenCentral() }
dependencies {
    implementation("com.google.firebase:firebase-admin:9.4.3")
    implementation("com.google.code.gson:gson:2.11.0")
    testImplementation(kotlin("test-junit"))
}
kotlin { jvmToolchain(17) }
application { mainClass.set("es.planidroid.backend.ServerKt") }
