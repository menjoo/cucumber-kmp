plugins {
    id("cucumberkmp.kmp-targets")
    id("cucumberkmp.ksp")
}

kotlin {
    sourceSets {
        commonTest.dependencies {
            implementation(project(":cucumber-kmp-core"))
            implementation(project(":cucumber-kmp-annotations"))
            implementation(libs.kotlinx.coroutines.test)
        }
    }
}

/**
 * KSP runs once per target test compilation, not over common metadata.
 *
 * There is no `kspCommonTestMetadata` — KSP's only metadata entry point is `kspCommonMainMetadata`
 * — so a processor cannot generate into `commonTest` at all. Per-target processing also suits the
 * generated code, which contains typed casts that only mean anything in a platform compilation.
 *
 * The consequence is visible in the sources: `commonTest` declares `expect val generatedRegistry`
 * and each target test source set supplies a one-line `actual` pointing at its own generated
 * property. Phase 3's Gradle plugin should emit that shim so users never write it.
 */
val kspTestConfigurations = listOf(
    "kspJvmTest",
    "kspAndroidHostTest",
    "kspMacosArm64Test",
    "kspIosSimulatorArm64Test",
    // No test *run* task for a device target, but the source set still has to compile and link.
    "kspIosArm64Test",
    "kspJsTest",
    "kspWasmJsTest",
)

dependencies {
    kspTestConfigurations.forEach { configuration ->
        add(configuration, project(":cucumber-kmp-ksp"))
    }
}

ksp {
    arg("cucumberkmp.generatedPackage", "io.github.menjoo.cucumberkmp.verification.generated")
}
