import org.gradle.api.artifacts.VersionCatalogsExtension
import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

/**
 * Convention plugin for every published `cucumber-kmp` module.
 *
 * Declares the target matrix from ARCHITECTURE.md §7 — Tier A (runnable on the dev machine
 * without a device) plus `iosArm64` from Tier B, which is compiled and link-checked here and
 * test-run on a physical device by the harness scheduled in Phase 5.
 *
 * Android uses `com.android.kotlin.multiplatform.library` rather than `com.android.library`:
 * since AGP 9.0 the latter is incompatible with the Kotlin Multiplatform plugin. The KMP variant
 * moves configuration from a top-level `android { }` block to `kotlin { android { } }`.
 */

plugins {
    id("org.jetbrains.kotlin.multiplatform")
    id("com.android.kotlin.multiplatform.library")
    `maven-publish`
    signing
}

group = "io.github.menjoo.cucumberkmp"

// Releases pass -Pcucumberkmp.version=<tag without the leading v>; everything else is a snapshot.
version = providers.gradleProperty("cucumberkmp.version").getOrElse("0.1.0-SNAPSHOT")

// Browser tests need Chrome resolvable by Karma. Off by default to keep `check` fast locally;
// CI opts in with -Pcucumberkmp.browserTests.
//
// Skipped via onlyIf rather than `enabled = false` on purpose: whether these tasks are *enabled*
// changes which npm packages Kotlin resolves, so toggling the flag would rewrite
// kotlin-js-store/yarn.lock and make every build fail on a lock mismatch in one mode or the other.
// Keeping them enabled but unexecuted holds the dependency set — and the lock — constant.
val browserTestsEnabled: Boolean = providers.gradleProperty("cucumberkmp.browserTests").isPresent
val BROWSER_TESTS_REASON = "browser tests run only with -Pcucumberkmp.browserTests"

val PROJECT_URL = "https://github.com/menjoo/cucumber-kmp"

// Type-safe `libs.*` accessors are not generated for precompiled script plugins, so the catalog
// is read through its API instead. Versions still live only in gradle/libs.versions.toml.
val catalog = the<VersionCatalogsExtension>().named("libs")
val androidCompileSdkVersion: Int = catalog.findVersion("androidCompileSdk").get().requiredVersion.toInt()
val androidMinSdkVersion: Int = catalog.findVersion("androidMinSdk").get().requiredVersion.toInt()

kotlin {
    explicitApi()
    jvmToolchain(21)

    // --- Tier A: tests execute locally, no device needed ---
    jvm()

    android {
        namespace = "io.github.menjoo.cucumberkmp." +
            project.name.removePrefix("cucumber-kmp-").replace('-', '.')
        compileSdk = androidCompileSdkVersion
        minSdk = androidMinSdkVersion

        // Host-JVM unit tests. Cheap, but see ARCHITECTURE.md §7.1 — these prove little that
        // jvm() does not. The device tests added in Phase 5 are the real Android signal.
        withHostTest {}
    }

    macosArm64() // the workhorse: cheapest proof of the no-reflection design on Native
    iosSimulatorArm64()

    js {
        nodejs()
        browser {
            testTask { onlyIf(BROWSER_TESTS_REASON) { browserTestsEnabled } }
        }
    }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs {
        nodejs()
        browser {
            testTask { onlyIf(BROWSER_TESTS_REASON) { browserTestsEnabled } }
        }
    }

    // --- Tier B: compiled and link-checked here, test-run on a real device ---
    iosArm64()

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}

// --- publishing ----------------------------------------------------------------------------

/**
 * Maven Central requires a `-javadoc` artifact. Kotlin Multiplatform has no javadoc tool, so an
 * empty jar is the conventional stand-in; the real documentation is the KDoc in the sources jar.
 */
val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        artifact(javadocJar)
        pom {
            name.set(project.name)
            description.set(
                "A Kotlin Multiplatform port of Cucumber: Gherkin feature files as tests on " +
                    "JVM, Android, iOS, macOS, JS and Wasm.",
            )
            url.set(PROJECT_URL)
            licenses {
                license {
                    // MIT, matching the upstream Cucumber projects this is ported from.
                    name.set("MIT License")
                    url.set("https://opensource.org/license/mit")
                }
            }
            developers {
                developer {
                    id.set("menjoo")
                    name.set("Menno Morsink")
                    url.set("https://github.com/menjoo")
                }
            }
            scm {
                url.set(PROJECT_URL)
                connection.set("scm:git:$PROJECT_URL.git")
                developerConnection.set("scm:git:ssh://git@github.com/menjoo/cucumber-kmp.git")
            }
        }
    }

    repositories {
        // Works with nothing but GITHUB_TOKEN, so releases can flow before the Sonatype
        // namespace for io.github.menjoo is verified. See ARCHITECTURE.md §15.
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/menjoo/cucumber-kmp")
            credentials {
                username = providers.gradleProperty("gpr.user")
                    .orElse(providers.environmentVariable("GITHUB_ACTOR"))
                    .orNull
                password = providers.gradleProperty("gpr.token")
                    .orElse(providers.environmentVariable("GITHUB_TOKEN"))
                    .orNull
            }
        }
    }
}

// Signing is opt-in: absent key material, local builds and CI still publish unsigned snapshots.
// Maven Central will require it; GitHub Packages does not.
val signingKey = providers.environmentVariable("SIGNING_KEY")
if (signingKey.isPresent) {
    signing {
        useInMemoryPgpKeys(signingKey.get(), providers.environmentVariable("SIGNING_PASSWORD").orNull)
        sign(publishing.publications)
    }

    // Kotlin Multiplatform registers publications lazily, which leaves Gradle unable to infer that
    // every publish task needs the signing tasks. Without this, publishing fails on a missing
    // signature for some targets and not others.
    tasks.withType<AbstractPublishToMaven>().configureEach {
        dependsOn(tasks.withType<Sign>())
    }
}
