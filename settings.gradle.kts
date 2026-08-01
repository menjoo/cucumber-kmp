pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "cucumber-kmp"

include(":cucumber-kmp-annotations")
include(":cucumber-kmp-core")
include(":cucumber-kmp-ksp")
include(":cucumber-kmp-gradle-plugin")

// Verification only, never published: exercises the KSP processor end to end on every target.
include(":cucumber-kmp-ksp-verification")
