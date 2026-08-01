package io.github.menjoo.cucumberkmp.gradle

import io.github.menjoo.cucumberkmp.core.gherkin.GherkinParseException
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction

/**
 * Reads `.feature` files and writes one Kotlin test class per file.
 *
 * Cacheable and relocatable: inputs are tracked with relative path sensitivity and no absolute
 * path reaches the output, so the generated sources can be shared through the build cache.
 */
@CacheableTask
public abstract class GenerateCucumberTestsTask : DefaultTask() {

    @get:InputDirectory
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    public abstract val featureDirectory: DirectoryProperty

    @get:Input
    public abstract val generatedPackage: Property<String>

    /**
     * Optional to Gradle, required in practice.
     *
     * Declaring it mandatory would make Gradle fail first with a generic "property doesn't have a
     * configured value", hiding the message below that actually says what to set it to.
     */
    @get:Input
    @get:Optional
    public abstract val stepRegistry: Property<String>

    @get:Input
    @get:Optional
    public abstract val tagFilter: Property<String>

    @get:OutputDirectory
    public abstract val outputDirectory: DirectoryProperty

    @TaskAction
    public fun generate() {
        val registry = stepRegistry.orNull
            ?: throw GradleException(
                "cucumberKmp.stepRegistry is not set. Point it at a StepRegistryFactory, for " +
                    "example \"com.example.generated.generatedStepRegistry\" when using KSP, or " +
                    "the property holding your steps { } factory.",
            )

        val root = featureDirectory.get().asFile
        val output = outputDirectory.get().asFile
        // Stale classes for deleted features would otherwise keep compiling.
        output.deleteRecursively()
        output.mkdirs()

        val features = root.walkTopDown()
            .filter { it.isFile && it.extension == "feature" }
            .sortedBy { it.invariantSeparatorsPath }
            .toList()

        if (features.isEmpty()) {
            logger.lifecycle("No .feature files found in ${root.path}")
            return
        }

        var scenarios = 0
        val failures = mutableListOf<String>()

        for (feature in features) {
            // A path relative to the feature root keeps failure messages stable and the output
            // free of machine-specific paths.
            val relativePath = feature.relativeTo(root).invariantSeparatorsPath
            try {
                val generated = FeatureTestGenerator.generate(
                    path = relativePath,
                    source = feature.readText(),
                    packageName = generatedPackage.get(),
                    stepRegistry = registry,
                    tagFilter = tagFilter.orNull,
                )
                val destination = output.resolve(generated.relativePath)
                destination.parentFile.mkdirs()
                destination.writeText(generated.code)
                scenarios += generated.scenarioCount
            } catch (failure: GherkinParseException) {
                // Report every malformed feature, not just the first: fixing them one build at a
                // time is a poor experience, and the parser already collects all errors per file.
                failures += failure.message ?: "Failed to parse $relativePath"
            }
        }

        if (failures.isNotEmpty()) {
            throw GradleException(failures.joinToString("\n\n"))
        }

        logger.lifecycle(
            "Generated ${features.size} test class(es) covering $scenarios scenario(s) from ${root.path}",
        )
    }
}
