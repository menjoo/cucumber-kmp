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

// The step registry is generated per target test compilation — KSP cannot generate into
// commonTest. See ARCHITECTURE.md §13.1.
listOf(
    "kspJvmTest",
    "kspAndroidHostTest",
    "kspMacosArm64Test",
    "kspIosSimulatorArm64Test",
    "kspIosArm64Test",
    "kspJsTest",
    "kspWasmJsTest",
).forEach { configuration ->
    dependencies.add(configuration, "io.github.menjoo.cucumberkmp:cucumber-kmp-ksp:$cucumberKmpVersion")
}

ksp {
    arg("cucumberkmp.generatedPackage", "com.example.calculator.generated")
}

cucumberKmp {
    // Where the PO writes feature files. This is the default, spelled out for the example.
    featureDirectory.set(layout.projectDirectory.dir("src/commonTest/resources/features"))
    generatedPackage.set("com.example.calculator.cucumber")
    stepRegistry.set("com.example.calculator.generated.generatedStepRegistry")
}
