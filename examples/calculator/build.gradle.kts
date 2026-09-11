import org.jetbrains.kotlin.gradle.ExperimentalWasmDsl

plugins {
    kotlin("multiplatform")
    id("com.android.kotlin.multiplatform.library")
    id("com.google.devtools.ksp")
    id("io.github.menjoo.cucumberkmp")
}

// Same source of truth as the library itself, so this example cannot drift out of date.
val cucumberKmpVersion: String = rootDir
    .resolve("../../gradle.properties")
    .readLines()
    .first { it.startsWith("cucumberkmp.version=") }
    .substringAfter('=')
    .trim()

kotlin {
    jvmToolchain(21)

    jvm()

    android {
        namespace = "com.example.calculator"
        compileSdk = 36
        minSdk = 24
        withHostTest {}
    }

    macosArm64()
    iosSimulatorArm64()
    iosArm64()
    js { nodejs() }

    @OptIn(ExperimentalWasmDsl::class)
    wasmJs { nodejs() }

    sourceSets {
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation("io.github.menjoo.cucumberkmp:cucumber-kmp-core:$cucumberKmpVersion")
            implementation("io.github.menjoo.cucumberkmp:cucumber-kmp-annotations:$cucumberKmpVersion")
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")
        }
    }
}

cucumberKmp {
    // Where the PO writes feature files. This is the default, spelled out for the example.
    featureDirectory.set(layout.projectDirectory.dir("src/commonTest/resources/features"))

    // The plugin hands this to KSP as well, so the generated tests and the generated step
    // registry land in one package and `stepRegistry` needs no value of its own.
    generatedPackage.set("com.example.calculator.cucumber")
}
