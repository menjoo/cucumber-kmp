plugins {
    alias(libs.plugins.kotlin.jvm)
    id("cucumberkmp.publishing")
}

kotlin {
    explicitApi()
    jvmToolchain(21)
}

// Central requires a sources jar. Kotlin Multiplatform produces one per target automatically;
// a plain JVM module has to ask.
java {
    withSourcesJar()
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

    testImplementation(kotlin("test"))
    testImplementation(libs.kctfork.ksp)
    // The processor reads these annotations, so the compiled snippets need them on the classpath.
    testImplementation(project(":cucumber-kmp-annotations"))
}

tasks.test {
    useJUnitPlatform()
}

publishing {
    publications.create<MavenPublication>("maven") {
        from(components["java"])
    }
}
// The POM, javadoc jar, signing and repositories come from cucumberkmp.publishing.
