/**
 * Applies KSP from build-logic's classpath.
 *
 * A module cannot apply `com.google.devtools.ksp` directly: `includeBuild("build-logic")` exposes
 * build-logic's own precompiled script plugins, not the third-party plugins on its implementation
 * classpath. Applying it from inside a precompiled script plugin works, and keeps a single Kotlin
 * Gradle plugin on the classpath — see build-logic/build.gradle.kts for why that matters.
 */

plugins {
    id("com.google.devtools.ksp")
}
