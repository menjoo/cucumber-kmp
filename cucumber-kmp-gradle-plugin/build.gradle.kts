plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-gradle-plugin`
    id("cucumberkmp.publishing")
}

kotlin {
    explicitApi()
    jvmToolchain(21)
}

// Central requires a sources jar; java-gradle-plugin does not add one. It attaches to the
// `pluginMaven` publication, not to the plugin marker, which is correct — a marker is POM-only.
java {
    withSourcesJar()
}

dependencies {
    implementation(libs.kotlinpoet)

    // The plugin parses .feature files during the build with the very same parser the runtime
    // uses, so there is exactly one Gherkin implementation in the project. This is the JVM
    // artifact of a multiplatform module. See ARCHITECTURE.md §12.4.
    implementation(project(":cucumber-kmp-core"))

    // Needed to register the generated directory with each target's test source set.
    compileOnly(libs.plugin.kotlin)

    testImplementation(kotlin("test"))
    testImplementation(gradleTestKit())
}

gradlePlugin {
    website = "https://github.com/menjoo/cucumber-kmp"
    vcsUrl = "https://github.com/menjoo/cucumber-kmp.git"
    plugins {
        create("cucumberKmp") {
            id = "io.github.menjoo.cucumberkmp"
            implementationClass = "io.github.menjoo.cucumberkmp.gradle.CucumberKmpPlugin"
            displayName = "cucumber-kmp"
            description = "Generates Kotlin Multiplatform tests from Gherkin .feature files."
            tags = listOf("cucumber", "gherkin", "bdd", "kotlin-multiplatform", "testing")
        }
    }
}

tasks.test {
    useJUnitPlatform()
}
