package io.github.menjoo.cucumberkmp.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.file.DirectoryProperty
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.KotlinSourceSet
import java.util.concurrent.Callable

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
 *
 * "Every target's test source set" means every one that can actually reach [SHARED_TEST_SOURCE_SET],
 * where the step definitions and the cucumber dependencies live. A test source set that cannot is
 * skipped, because generated scenarios there could not compile and — once the dependencies were
 * added by hand — would run against an empty step registry. See [canSeeStepDefinitions].
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
                    if (compilation.name !in TEST_COMPILATION_NAMES) return@configureEach

                    val sourceSet = compilation.defaultSourceSet

                    // Resolved lazily, and that is the whole point. A compilation is realised as
                    // its target is declared, but the Kotlin plugin finalises `dependsOn` edges in
                    // a later lifecycle stage — after `afterEvaluate`, and with no public hook in
                    // between. Asking at configuration time sees every test source set as an
                    // island and silently generates nothing anywhere: a green build with no
                    // scenarios in it, which is worse than the failure this filter prevents.
                    // Wrapping the decision in a Callable defers it to whenever Gradle resolves
                    // the source directories, by which point the hierarchy is wired.
                    sourceSet.kotlin.srcDir(
                        target.files(
                            Callable {
                                if (sourceSet.canSeeStepDefinitions()) {
                                    listOf(generate)
                                } else {
                                    target.logger.info(
                                        "cucumber-kmp: not generating scenarios for " +
                                            "${kotlinTarget.name}/${compilation.name}. Its source " +
                                            "set '${sourceSet.name}' does not depend on " +
                                            "'$SHARED_TEST_SOURCE_SET', so it can see neither the " +
                                            "step definitions nor the cucumber dependencies.",
                                    )
                                    emptyList<Any>()
                                }
                            },
                        ),
                    )
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

/**
 * The source set holding the step definitions, matching [CucumberKmpExtension.featureDirectory]'s
 * default of `src/commonTest/resources/features`.
 */
internal const val SHARED_TEST_SOURCE_SET: String = "commonTest"

/**
 * Whether a generated scenario placed in this source set could find its step definitions.
 *
 * A compilation sees only its own source set and that source set's transitive `dependsOn` parents,
 * so this is also the whole set of places its cucumber dependencies can come from.
 *
 * The name of a compilation says nothing useful here. An Android device-test source set is the
 * motivating case: whether it reaches `commonTest` depends on the source-set tree the consumer
 * attached it to, so `deviceTest` is sometimes a perfectly good home for scenarios and sometimes an
 * island. Ask the graph rather than the name.
 *
 * Top level rather than a member of [CucumberKmpPlugin]: [KotlinSourceSet] comes from a
 * `compileOnly` dependency, and a member signature naming it would have to resolve when Gradle
 * decorates the plugin class — which fails in a project that has not applied the Kotlin plugin.
 */
internal fun KotlinSourceSet.canSeeStepDefinitions(): Boolean =
    SHARED_TEST_SOURCE_SET in closureOf(this) { it.dependsOn }.map { it.name }

/**
 * Every node reachable from [start] through [next], [start] itself included.
 *
 * Visited-guarded rather than naively recursive: a `dependsOn` cycle is someone else's broken
 * build, and it should surface as their error rather than as a stack overflow in this plugin.
 */
internal fun <T> closureOf(start: T, next: (T) -> Set<T>): Set<T> {
    val seen = mutableSetOf(start)
    val queue = ArrayDeque(listOf(start))
    while (queue.isNotEmpty()) {
        for (node in next(queue.removeFirst())) {
            if (seen.add(node)) queue.addLast(node)
        }
    }
    return seen
}
