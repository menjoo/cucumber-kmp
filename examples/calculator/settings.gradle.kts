// A standalone build, deliberately not part of the main one.
//
// The point of this example is to consume cucumber-kmp exactly as a real project would —
// resolving published artifacts rather than project dependencies — which is the only way to prove
// the Gradle plugin registers its generated sources with each target's test source set.
//
// Run it after `./gradlew publishToMavenLocal` in the repository root:
//
//     ./gradlew -p examples/calculator build

pluginManagement {
    repositories {
        mavenLocal()
        google()
        gradlePluginPortal()
        mavenCentral()
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
