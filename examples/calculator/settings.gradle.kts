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
//                        instead. This is what CI runs for the full matrix, because substituting
//                        projects would hide exactly the packaging and source-set wiring the
//                        example exists to check.
//
//     ./gradlew -p examples/calculator build
//     ./gradlew publishToMavenLocal && ./gradlew -p examples/calculator build -Pcucumberkmp.composite=false
//
// Note that mavenLocal is only a repository in published mode. That is deliberate: with it always
// present, composite mode would silently fall back to previously published artifacts and appear to
// work even when substitution was broken — which is exactly what happened once.

pluginManagement {
    repositories {
        if (!providers.gradleProperty("cucumberkmp.composite").getOrElse("true").toBoolean()) {
            mavenLocal()
        }
        google()
        gradlePluginPortal()
        mavenCentral()
    }

    // `providers` resolves to the enclosing Settings object; a `val` cannot be declared before
    // pluginManagement, which Gradle requires to be the first block in the file.
    if (providers.gradleProperty("cucumberkmp.composite").getOrElse("true").toBoolean()) {
        // Contributes the Gradle plugin. Note this does NOT substitute ordinary dependencies —
        // that needs the top-level includeBuild below.
        includeBuild("../..")
    }

    plugins {
        kotlin("multiplatform") version "2.4.20"
        id("com.android.kotlin.multiplatform.library") version "9.3.1"
        id("com.google.devtools.ksp") version "2.3.12"
        // Read from the repository's gradle.properties rather than hardcoded, so a release does
        // not silently leave the example pinned to a version that no longer exists.
        //
        // Parsed by hand rather than with java.util.Properties: this block is extracted and
        // compiled in a restricted scope where the script's imports do not apply.
        id("io.github.menjoo.cucumberkmp") version settingsDir
            .resolve("../../gradle.properties")
            .readLines()
            .first { it.startsWith("cucumberkmp.version=") }
            .substringAfter('=')
            .trim()
    }
}

// Substitutes cucumber-kmp-core, -annotations and -ksp with the live projects. A build included
// under pluginManagement contributes plugins only, so this second inclusion is required and is not
// a duplicate — Gradle resolves them to the same included build.
if (providers.gradleProperty("cucumberkmp.composite").getOrElse("true").toBoolean()) {
    includeBuild("../..")
}

dependencyResolutionManagement {
    repositories {
        if (!providers.gradleProperty("cucumberkmp.composite").getOrElse("true").toBoolean()) {
            mavenLocal()
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "calculator"
