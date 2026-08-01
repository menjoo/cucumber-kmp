import org.gradle.api.publish.maven.tasks.AbstractPublishToMaven
import org.gradle.plugins.signing.Sign

/**
 * Everything Maven Central demands of a published artifact, for any module — multiplatform or
 * plain JVM.
 *
 * This exists because it did not: the POM, the javadoc jar and signing were originally inside the
 * Kotlin Multiplatform convention plugin, so `cucumber-kmp-ksp` and `cucumber-kmp-gradle-plugin`
 * hand-rolled bare `maven-publish` blocks and shipped without any of it. GitHub Packages accepted
 * them anyway; Central rejected the whole deployment with eleven validation errors apiece.
 */

plugins {
    `maven-publish`
    signing
}

group = "io.github.menjoo.cucumberkmp"

// Read from gradle.properties, which is changed by pull request. See RELEASING.md.
version = providers.gradleProperty("cucumberkmp.version").get()

val PROJECT_URL = "https://github.com/menjoo/cucumber-kmp"

/**
 * Central requires a `-javadoc` artifact. Kotlin has no javadoc tool worth pointing at it, so an
 * empty jar is the conventional stand-in; the real documentation is the KDoc in the sources jar.
 */
val javadocJar by tasks.registering(Jar::class) {
    archiveClassifier.set("javadoc")
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        // A Gradle plugin marker is a POM with no artifacts; attaching a jar to it would stop it
        // being a marker. Central does not ask markers for sources or javadoc, only for metadata.
        if (!name.endsWith("PluginMarkerMaven")) {
            artifact(javadocJar)
        }

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
        // Works with nothing but GITHUB_TOKEN. Note the Packages Maven registry requires
        // authentication even for public repositories, so Central is the one consumers can reach.
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

// Signing is opt-in: absent key material, local builds still publish unsigned. Central requires
// it; GitHub Packages ignores it.
val signingKey = providers.environmentVariable("SIGNING_KEY")
if (signingKey.isPresent) {
    signing {
        // Empty rather than null when unset: a GPG key generated without a passphrase is
        // perfectly valid, and the underlying API takes a non-null string.
        useInMemoryPgpKeys(
            signingKey.get(),
            providers.environmentVariable("SIGNING_PASSWORD").getOrElse(""),
        )
        sign(publishing.publications)
    }

    // Publications are registered lazily — by the Kotlin Multiplatform plugin per target, and by
    // java-gradle-plugin for the marker — which leaves Gradle unable to infer that every publish
    // task needs the signing tasks. Without this, some artifacts ship unsigned and others do not.
    tasks.withType<AbstractPublishToMaven>().configureEach {
        dependsOn(tasks.withType<Sign>())
    }
}
