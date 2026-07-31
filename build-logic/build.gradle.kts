plugins {
    `kotlin-dsl`
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.plugin.kotlin)
    implementation(libs.plugin.android)

    // KSP lives here rather than being applied with `alias(libs.plugins.ksp)` in a module: the
    // plugin marker drags in its own Kotlin Gradle plugin, and two Kotlin plugins on different
    // classloaders both try to register the root `kotlinNodeJs` extension, which fails the build.
    // Sharing build-logic's classpath keeps exactly one.
    implementation(libs.plugin.ksp)
}
