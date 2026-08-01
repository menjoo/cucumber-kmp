plugins {
    alias(libs.plugins.kotlin.jvm)
    `java-gradle-plugin`
    `maven-publish`
}

group = "io.github.menjoo.cucumberkmp"
version = providers.gradleProperty("cucumberkmp.version").getOrElse("0.1.0-SNAPSHOT")

kotlin {
    explicitApi()
    jvmToolchain(21)
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
