pluginManagement {
    includeBuild("build-logic")
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

// Sonatype publishes no official Gradle plugin for the Central Portal and points at community
// ones instead. nmcp is the smallest fit: it uploads the publications `maven-publish` already
// produces rather than defining its own, so the POM, signing and KMP variants set up in
// build-logic keep working untouched.
plugins {
    id("com.gradleup.nmcp.settings") version "1.6.1"
}

nmcpSettings {
    centralPortal {
        // A Central Portal *user token*, not the account password — generated at
        // central.sonatype.com under Account. Absent locally, which is fine: the upload task
        // simply has nothing to authenticate with and is never run.
        username = providers.environmentVariable("CENTRAL_PORTAL_USERNAME").orNull
        password = providers.environmentVariable("CENTRAL_PORTAL_PASSWORD").orNull

        // USER_MANAGED leaves the deployment staged in the Portal for a human to inspect and
        // release. Worth keeping until a few releases have gone through cleanly, because a
        // Central release is permanent — switch to AUTOMATIC with
        // -Pcucumberkmp.centralPublishingType=AUTOMATIC once it is routine.
        publishingType = providers.gradleProperty("cucumberkmp.centralPublishingType")
            .getOrElse("USER_MANAGED")
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
