plugins {
    alias(libs.plugins.kotlin.jvm)
    `maven-publish`
}

group = "io.github.menjoo.cucumberkmp"
version = providers.gradleProperty("cucumberkmp.version").get()

kotlin {
    explicitApi()
    jvmToolchain(21)
}

dependencies {
    implementation(libs.ksp.api)
    implementation(libs.kotlinpoet)
    implementation(libs.kotlinpoet.ksp)

    // The processor validates expressions with the very parser it generates calls to, so a
    // malformed Cucumber Expression or tag expression is a compile error rather than a surprise at
    // run time. This is the JVM artifact of a multiplatform module — the same trick the Gradle
    // plugin will use for reading .feature files.
    implementation(project(":cucumber-kmp-core"))
}

publishing {
    publications.create<MavenPublication>("maven") {
        from(components["java"])
    }
}
