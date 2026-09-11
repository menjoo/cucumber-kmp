package io.github.menjoo.cucumberkmp.gradle

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Exercises the Gradle wiring: task registration, up-to-date behaviour, and the error paths.
 *
 * Applying the full Kotlin Multiplatform plugin here would mean resolving and running a KMP build
 * inside a test for very little extra signal, so source-set registration is not covered — the
 * `examples/` project verifies that against published artifacts instead.
 */
class CucumberKmpPluginFunctionalTest {

    private val projectDir: File = createTempDirectory("cucumber-kmp-plugin-test").toFile()

    @AfterTest
    fun cleanUp() {
        projectDir.deleteRecursively()
    }

    private fun writeProject(buildScript: String) {
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"fixture\"\n")
        projectDir.resolve("build.gradle.kts").writeText(buildScript)
    }

    private fun writeFeature(name: String, content: String) {
        val dir = projectDir.resolve("src/commonTest/resources/features")
        dir.mkdirs()
        dir.resolve(name).writeText(content)
    }

    private fun runner(vararg arguments: String): GradleRunner =
        GradleRunner.create()
            .withProjectDir(projectDir)
            .withPluginClasspath()
            .withArguments(*arguments, "--stacktrace")

    private val defaultBuildScript = """
        plugins { id("io.github.menjoo.cucumberkmp") }
        cucumberKmp {
            stepRegistry.set("com.example.steps")
        }
    """.trimIndent()

    private val generatedFile: File
        get() = projectDir.resolve("build/generated/cucumber/kotlin/cucumber/generated/CalculatorFeatureTest.kt")

    @Test
    fun `generates a test class from a feature file`() {
        writeProject(defaultBuildScript)
        writeFeature(
            "calculator.feature",
            """
            Feature: Calculator
              Scenario: adds two numbers
                Given I have entered 5
            """.trimIndent(),
        )

        val result = runner("generateCucumberTests").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateCucumberTests")?.outcome)
        assertTrue(generatedFile.exists(), "expected ${generatedFile.path}")
        val code = generatedFile.readText()
        assertTrue(code.contains("class CalculatorFeatureTest"), code)
        assertTrue(code.contains("addsTwoNumbers"), code)
        assertTrue(result.output.contains("covering 1 scenario"), result.output)
    }

    @Test
    fun `is up to date when nothing changed`() {
        writeProject(defaultBuildScript)
        writeFeature("calculator.feature", "Feature: F\n  Scenario: S\n    Given x")

        runner("generateCucumberTests").build()
        val second = runner("generateCucumberTests").build()

        assertEquals(TaskOutcome.UP_TO_DATE, second.task(":generateCucumberTests")?.outcome)
    }

    @Test
    fun `regenerates when a feature changes`() {
        writeProject(defaultBuildScript)
        writeFeature("calculator.feature", "Feature: F\n  Scenario: first\n    Given x")
        runner("generateCucumberTests").build()

        writeFeature("calculator.feature", "Feature: F\n  Scenario: second\n    Given x")
        val result = runner("generateCucumberTests").build()

        assertEquals(TaskOutcome.SUCCESS, result.task(":generateCucumberTests")?.outcome)
        assertTrue(generatedFile.readText().contains("second"), generatedFile.readText())
    }

    @Test
    fun `removes the test class for a deleted feature`() {
        writeProject(defaultBuildScript)
        writeFeature("calculator.feature", "Feature: F\n  Scenario: S\n    Given x")
        writeFeature("other.feature", "Feature: G\n  Scenario: T\n    Given y")
        runner("generateCucumberTests").build()

        projectDir.resolve("src/commonTest/resources/features/other.feature").delete()
        runner("generateCucumberTests").build()

        // A stale generated class would keep compiling against steps nobody defines any more.
        assertTrue(generatedFile.exists())
        assertTrue(
            !projectDir.resolve("build/generated/cucumber/kotlin/cucumber/generated/OtherFeatureTest.kt").exists(),
            "stale generated class was not removed",
        )
    }

    @Test
    fun `reports every malformed feature, not just the first`() {
        writeProject(defaultBuildScript)
        writeFeature("bad-one.feature", "not gherkin at all")
        writeFeature("bad-two.feature", "also not gherkin")

        val result = runner("generateCucumberTests").buildAndFail()

        assertTrue(result.output.contains("bad-one.feature"), result.output)
        assertTrue(result.output.contains("bad-two.feature"), result.output)
    }

    @Test
    fun `defaults the step registry to the one KSP generates`() {
        // Nothing configured at all: the package the plugin hands KSP and the registry the
        // generated tests call are the same value, so a consumer using annotations sets neither.
        writeProject("plugins { id(\"io.github.menjoo.cucumberkmp\") }")
        writeFeature("calculator.feature", "Feature: F\n  Scenario: S\n    Given x")

        runner("generateCucumberTests").build()

        val code = projectDir
            .resolve("build/generated/cucumber/kotlin/cucumber/generated/CalculatorFeatureTest.kt")
            .readText()
        assertTrue(code.contains("generatedStepRegistry"), code)
    }

    @Test
    fun `follows generatedPackage when the registry is left to default`() {
        writeProject(
            """
            plugins { id("io.github.menjoo.cucumberkmp") }
            cucumberKmp { generatedPackage.set("com.example.app.cucumber") }
            """.trimIndent(),
        )
        writeFeature("calculator.feature", "Feature: F\n  Scenario: S\n    Given x")

        runner("generateCucumberTests").build()

        val code = projectDir
            .resolve("build/generated/cucumber/kotlin/com/example/app/cucumber/CalculatorFeatureTest.kt")
            .readText()
        assertTrue(code.contains("com.example.app.cucumber.generatedStepRegistry"), code)
    }

    @Test
    fun `skips quietly when there are no feature files`() {
        writeProject(defaultBuildScript)

        val result = runner("generateCucumberTests").build()

        // @SkipWhenEmpty: an absent feature directory is not an error, it just means no work.
        assertEquals(TaskOutcome.NO_SOURCE, result.task(":generateCucumberTests")?.outcome)
    }
}
