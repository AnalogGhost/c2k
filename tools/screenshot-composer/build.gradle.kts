plugins {
    alias(libs.plugins.kotlin.jvm)
    application
}

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("com.hackerapps.c2k.tools.screenshots.MainKt")
}

// Screenshot paths are relative to the repo root (fastlane/metadata/...), not this module's
// directory, so the composer needs to run with the root project as its working directory.
tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
}
