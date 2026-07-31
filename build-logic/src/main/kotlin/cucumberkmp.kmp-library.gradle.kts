import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven
import org.gradle.plugins.signing.Sign

/**
 * Publishing for the modules that ship to consumers.
 *
 * Split from `cucumberkmp.kmp-targets` so verification-only modules can take the target matrix
 * without also acquiring publications.
 */

plugins {
    id("cucumberkmp.kmp-targets")
    `maven-publish`
    signing
}

val PROJECT_URL = "https://github.com/menjoo/cucumber-kmp"

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
