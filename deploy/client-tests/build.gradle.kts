plugins {
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.serialization") version "2.4.20"
}
kotlin {
    jvmToolchain(25)
    sourceSets.main {
        kotlin.srcDir("../../web/src/wasmJsMain/kotlin")
        kotlin.include("ru/finney/pet/web/CloudSync.kt")
    }
}
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    testImplementation(kotlin("test-junit"))
}
tasks.test { inputs.property("liveApi", providers.environmentVariable("FINNY_LIVE_TEST").orElse("0")); testLogging { events("passed", "failed"); showStandardStreams = true } }
