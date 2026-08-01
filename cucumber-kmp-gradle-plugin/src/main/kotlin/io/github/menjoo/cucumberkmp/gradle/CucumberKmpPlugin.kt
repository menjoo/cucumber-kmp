package io.github.menjoo.cucumberkmp.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.file.DirectoryProperty
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension

/**
 * Configuration for the cucumber-kmp Gradle plugin.
 *
 * ```kotlin
 * cucumberKmp {
 *     stepRegistry.set("com.example.steps.generatedStepRegistry")
 *     tagFilter.set("not @wip")
 * }
 * ```
 */
public abstract class CucumberKmpExtension {

    /**
     * Where `.feature` files live. Defaults to `src/commonTest/resources/features`.
     *
     * A resources directory by convention, because that is where a non-developer expects to find
     * them and where they sit outside the Kotlin source tree.
     */
    public abstract val featureDirectory: DirectoryProperty

    /** Package for the generated test classes. Defaults to `cucumber.generated`. */
    public abstract val generatedPackage: Property<String>

    /**
     * A fully-qualified Kotlin expression evaluating to a `StepRegistryFactory`.
     *
     * With KSP that is the generated property, for example
     * `com.example.generated.generatedStepRegistry`. With the `steps { }` DSL it is whatever
     * property holds the factory.
     */
    public abstract val stepRegistry: Property<String>

    /**
     * An optional tag expression. Scenarios that do not match are generated with `@Ignore` rather
     * than omitted, so a filtered run still reports what exists.
     */
    public abstract val tagFilter: Property<String>
}

/**
 * Generates one Kotlin test class per `.feature` file, with one test function per scenario.
 *
 * The generated sources are added to **every target's test source set** rather than to
 * `commonTest`. That is deliberate: a KSP-generated step registry only exists per target
 * compilation, and `commonTest` compiles to metadata that cannot see it. Generating per target
 * sidesteps the `expect`/`actual` shim entirely. See ARCHITECTURE.md §4a and §13.1.
 */
public class CucumberKmpPlugin : Plugin<Project> {

    override fun apply(target: Project) {
        val extension = target.extensions.create("cucumberKmp", CucumberKmpExtension::class.java)
        extension.featureDirectory.convention(
            target.layout.projectDirectory.dir("src/commonTest/resources/features"),
        )
        extension.generatedPackage.convention("cucumber.generated")

        val generate = target.tasks.register(
            TASK_NAME,
            GenerateCucumberTestsTask::class.java,
        ) { task ->
            task.group = "verification"
            task.description = "Generates Kotlin tests from Gherkin .feature files."
            task.featureDirectory.set(extension.featureDirectory)
            task.generatedPackage.set(extension.generatedPackage)
            task.stepRegistry.set(extension.stepRegistry)
            task.tagFilter.set(extension.tagFilter)
            task.outputDirectory.set(target.layout.buildDirectory.dir("generated/cucumber/kotlin"))
        }

        target.plugins.withId("org.jetbrains.kotlin.multiplatform") {
            val kotlin = target.extensions.getByType(KotlinMultiplatformExtension::class.java)
            kotlin.targets.configureEach { kotlinTarget ->
                kotlinTarget.compilations.configureEach { compilation ->
                    if (compilation.name in TEST_COMPILATION_NAMES) {
                        // Passing the task provider carries the dependency, so the generator runs
                        // before compilation without an explicit dependsOn.
                        compilation.defaultSourceSet.kotlin.srcDir(generate)
                    }
                }
            }

            // Without this, generated tests only appear after a build, so a fresh IDE sync shows
            // an empty test tree and unresolved references. `matching` keeps it a no-op if the
            // Kotlin plugin ever stops registering the task.
            target.tasks.matching { it.name == "prepareKotlinIdeaImport" }
                .configureEach { it.dependsOn(generate) }
        }
    }

    private companion object {
        const val TASK_NAME = "generateCucumberTests"

        /**
         * Kotlin names the test compilation `test`; the Android KMP plugin uses `hostTest` and
         * `deviceTest` instead.
         */
        val TEST_COMPILATION_NAMES = setOf("test", "hostTest", "deviceTest")
    }
}
