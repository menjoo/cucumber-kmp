plugins {
    `kotlin-dsl`
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.plugin.kotlin)
    implementation(libs.plugin.android)
}
