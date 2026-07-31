package io.github.menjoo.cucumberkmp.verification

import io.github.menjoo.cucumberkmp.core.gherkin.GherkinParser
import io.github.menjoo.cucumberkmp.core.gherkin.Pickle
import io.github.menjoo.cucumberkmp.core.gherkin.PickleCompiler
import io.github.menjoo.cucumberkmp.core.runner.CucumberRunner
import io.github.menjoo.cucumberkmp.core.runner.StepStatus
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Proves the KSP-generated registry works end to end, on every target.
 *
 * The point of Phase 2 is that `@Given`/`@When`/`@Then` behave exactly like the `steps { }` DSL
 * while requiring no reflection — so these tests are deliberately the same shape as
 * `CucumberRunnerTest`, just driven by generated code instead of closures.
 */
class GeneratedStepRegistryTest {

    @BeforeTest
    fun resetCounters() {
        CalculatorSteps.resetCounters()
        topLevelStepRan = false
        SharedSteps.objectStepRan = false
    }

    private fun picklesOf(vararg lines: String): List<Pickle> =
        PickleCompiler.compile(GherkinParser.parse("verification.feature", lines.joinToString("\n")))

    private fun runner() = CucumberRunner(generatedRegistry)

    @Test
    fun registryWasGenerated() {
        val registry = generatedRegistry.create()
        // Six step definitions and three hooks are declared in CalculatorSteps, plus the
        // top-level and object-hosted steps.
        assertEquals(8, registry.definitions.size, registry.definitions.joinToString("\n"))
        assertEquals(3, registry.hooks.size)
    }

    @Test
    fun runsAnnotatedStepsEndToEnd() = runTest {
        val result = runner().run(
            picklesOf(
                "Feature: Calculator",
                "  Scenario: adding",
                "    Given I have entered 5",
                "    And I have entered 7",
                "    When I press add",
                "    Then the result should be 12",
            ).single(),
        )

        assertTrue(result.isPassed, result.steps.joinToString("\n") { "${it.status} ${it.step.text}" })
        assertTrue(result.steps.all { it.status == StepStatus.PASSED })
    }

    @Test
    fun createsAFreshGlueInstancePerScenario() = runTest {
        // Both scenarios enter 5 and expect 5. If the glue instance were shared, the second would
        // see 10 — this is the contract ARCHITECTURE.md §8 promises without a DI container.
        val pickles = picklesOf(
            "Feature: Isolation",
            "  Scenario: first",
            "    Given I have entered 5",
            "    When I press add",
            "    Then the result should be 5",
            "  Scenario: second",
            "    Given I have entered 5",
            "    When I press add",
            "    Then the result should be 5",
        )

        assertEquals(2, pickles.size)
        val runner = runner()
        for (pickle in pickles) {
            assertTrue(runner.run(pickle).isPassed, "scenario '${pickle.name}' failed")
        }
    }

    @Test
    fun bindsSeveralArgumentTypesInOneStep() = runTest {
        val result = runner().run(
            picklesOf(
                "Feature: F",
                "  Scenario: S",
                "    Given the label \"ab\" repeated 3 times",
                "    Then the label should be \"ababab\"",
            ).single(),
        )
        assertTrue(result.isPassed, result.failure?.message)
    }

    @Test
    fun callsSuspendSteps() = runTest {
        val result = runner().run(
            picklesOf(
                "Feature: F",
                "  Scenario: S",
                "    Given I have entered 4",
                "    When I press add slowly",
                "    Then the result should be 4",
            ).single(),
        )
        assertTrue(result.isPassed, result.failure?.message)
    }

    @Test
    fun callsTopLevelAndObjectSteps() = runTest {
        val result = runner().run(
            picklesOf(
                "Feature: F",
                "  Scenario: S",
                "    Given a top-level step",
                "    And a step on an object",
            ).single(),
        )

        assertTrue(result.isPassed, result.failure?.message)
        assertTrue(topLevelStepRan, "top-level step did not run")
        assertTrue(SharedSteps.objectStepRan, "object step did not run")
    }

    @Test
    fun runsGeneratedHooks() = runTest {
        val pickles = picklesOf(
            "Feature: F",
            "  @tagged",
            "  Scenario: tagged",
            "    Given I have entered 1",
            "  Scenario: untagged",
            "    Given I have entered 1",
        )

        val runner = runner()
        for (pickle in pickles) assertTrue(runner.run(pickle).isPassed)

        assertEquals(2, CalculatorSteps.setupCount, "untagged @Before should run for both")
        assertEquals(1, CalculatorSteps.taggedSetupCount, "tagged @Before should run once")
        assertEquals(2, CalculatorSteps.teardownCount, "@After should run for both")
    }

    @Test
    fun reportsAnUndefinedStepAgainstGeneratedDefinitions() = runTest {
        val result = runner().run(
            picklesOf("Feature: F", "  Scenario: S", "    Given something nobody defined").single(),
        )
        assertEquals(StepStatus.UNDEFINED, result.steps.single().status)
    }
}
