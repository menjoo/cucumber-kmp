// A standalone build, deliberately not part of the main one.
//
// It runs in two modes:
//
//   composite (default)  includeBuild("../..") substitutes the cucumber-kmp artifacts with the
//                        live projects. Nothing has to be published, edits to the library are
//                        picked up immediately, and an IDE opening this directory can navigate
//                        and debug straight into the library sources.
//
//   published            -Pcucumberkmp.composite=false resolves real artifacts from mavenLocal
//                        instead. This is what CI runs, because substituting projects would hide
//                        exactly the packaging and source-set wiring the example exists to check.
//
//     ./gradlew -p examples/calculator build
//     ./gradlew publishToMavenLocal && ./gradlew -p examples/calculator build -Pcucumberkmp.composite=false

pluginManagement {
    repositories {
        mavenLocal()
        google()
        gradlePluginPortal()
        mavenCentral()
    }

    // `providers` resolves to the enclosing Settings object; a `val` cannot be declared before
    // pluginManagement, which Gradle requires to be the first block in the file.
    if (providers.gradleProperty("cucumberkmp.composite").getOrElse("true").toBoolean()) {
        // Contributes both the Gradle plugin and the library artifacts, so no publishing is needed.
        includeBuild("../..")
    }

    plugins {
        kotlin("multiplatform") version "2.4.10"
        id("com.android.kotlin.multiplatform.library") version "9.3.1"
        id("com.google.devtools.ksp") version "2.3.10"
        id("io.github.menjoo.cucumberkmp") version "0.1.0-SNAPSHOT"
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal()
        google()
        mavenCentral()
    }
}

rootProject.name = "calculator"
