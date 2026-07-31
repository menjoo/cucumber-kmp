package io.github.menjoo.cucumberkmp.core.runner

import io.github.menjoo.cucumberkmp.core.gherkin.GherkinParser
import io.github.menjoo.cucumberkmp.core.gherkin.Pickle
import io.github.menjoo.cucumberkmp.core.gherkin.PickleCompiler
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * End-to-end tests: `.feature` text in, executed steps out.
 *
 * This is the walking skeleton of the whole framework — parse, compile to pickles, match against
 * step definitions, run. Everything after Phase 1 (KSP, the Gradle plugin) only changes how the
 * step definitions and pickles are *supplied*, not how they run.
 */
class CucumberRunnerTest {

    private fun picklesOf(vararg lines: String): List<Pickle> =
        PickleCompiler.compile(GherkinParser.parse("test.feature", lines.joinToString("\n")))

    @Test
    fun runsAScenarioEndToEnd() = runTest {
        val recorded = mutableListOf<String>()
        val registry = steps {
            given("I have {int} cucumbers") { count: Int -> recorded += "given:$count" }
            whenever("I eat {int}") { count: Int -> recorded += "eat:$count" }
            then("I have {int} left") { count: Int -> recorded += "left:$count" }
        }

        val pickle = picklesOf(
            "Feature: Eating",
            "  Scenario: eat some",
            "    Given I have 12 cucumbers",
            "    When I eat 5",
            "    Then I have 7 left",
        ).single()

        val result = CucumberRunner(registry).run(pickle)

        assertTrue(result.isPassed, "expected pass, got ${result.steps.map { it.status }}")
        assertEquals(listOf("given:12", "eat:5", "left:7"), recorded)
        assertTrue(result.steps.all { it.status == StepStatus.PASSED })
    }

    @Test
    fun isolatesStateBetweenScenarios() = runTest {
        // The `var` lives inside the steps block, so each scenario gets its own.
        val registry = steps {
            var total = 0
            given("I add {int}") { value: Int -> total += value }
            then("the total is {int}") { expected: Int -> assertEquals(expected, total) }
        }

        val pickles = picklesOf(
            "Feature: Adding",
            "  Scenario: first",
            "    Given I add 5",
            "    Then the total is 5",
            "  Scenario: second",
            "    Given I add 3",
            // Would be 8 if state leaked between scenarios.
            "    Then the total is 3",
        )

        val runner = CucumberRunner(registry)
        assertEquals(2, pickles.size)
        for (pickle in pickles) {
            assertTrue(runner.run(pickle).isPassed, "scenario '${pickle.name}' failed")
        }
    }

    @Test
    fun inlinesBackgroundSteps() = runTest {
        val recorded = mutableListOf<String>()
        val registry = steps {
            given("setup") { recorded += "setup" }
            given("the body") { recorded += "body" }
        }

        val pickle = picklesOf(
            "Feature: F",
            "  Background:",
            "    Given setup",
            "  Scenario: S",
            "    Given the body",
        ).single()

        assertTrue(CucumberRunner(registry).run(pickle).isPassed)
        assertEquals(listOf("setup", "body"), recorded)
    }

    @Test
    fun expandsScenarioOutlineIntoOneCasePerRow() = runTest {
        val seen = mutableListOf<Pair<Int, Int>>()
        val registry = steps {
            given("I start with {int}") { start: Int -> seen += start to -1 }
            then("I end with {int}") { end: Int -> seen[seen.lastIndex] = seen.last().first to end }
        }

        val pickles = picklesOf(
            "Feature: F",
            "  Scenario Outline: counting",
            "    Given I start with <start>",
            "    Then I end with <end>",
            "    Examples:",
            "      | start | end |",
            "      | 1     | 2   |",
            "      | 10    | 20  |",
        )

        assertEquals(2, pickles.size)
        val runner = CucumberRunner(registry)
        for (pickle in pickles) assertTrue(runner.run(pickle).isPassed)
        assertEquals(listOf(1 to 2, 10 to 20), seen)
    }

    @Test
    fun skipsRemainingStepsAfterAFailure() = runTest {
        var thirdRan = false
        val registry = steps {
            given("ok") { }
            given("boom") { throw IllegalStateException("boom") }
            given("never") { thirdRan = true }
        }

        val result = CucumberRunner(registry).run(
            picklesOf("Feature: F", "  Scenario: S", "    Given ok", "    Given boom", "    Given never").single(),
        )

        assertEquals(
            listOf(StepStatus.PASSED, StepStatus.FAILED, StepStatus.SKIPPED),
            result.steps.map { it.status },
        )
        assertTrue(!thirdRan, "a step after a failure must not run")
        assertEquals("boom", result.failure?.message)
    }

    @Test
    fun reportsUndefinedStepWithASnippet() = runTest {
        val result = CucumberRunner(steps { }).run(
            picklesOf("Feature: F", "  Scenario: S", "    Given I have 3 \"red\" cukes").single(),
        )

        val step = result.steps.single()
        assertEquals(StepStatus.UNDEFINED, step.status)

        val snippet = assertNotNull(step.snippet)
        // Numbers and quoted strings become placeholders.
        assertTrue(snippet.contains("{int}"), snippet)
        assertTrue(snippet.contains("{string}"), snippet)
        assertTrue(snippet.contains("TODO"), snippet)
    }

    @Test
    fun reportsAmbiguousStep() = runTest {
        val registry = steps {
            given("I have {int} cukes") { _: Int -> }
            given("I have {word} cukes") { _: String -> }
        }

        val result = CucumberRunner(registry).run(
            picklesOf("Feature: F", "  Scenario: S", "    Given I have 3 cukes").single(),
        )

        assertEquals(StepStatus.AMBIGUOUS, result.steps.single().status)
        val failure = assertNotNull(result.failure)
        assertTrue(assertNotNull(failure.message).contains("matches 2 step definitions"))
    }

    @Test
    fun runsHooksInOrderAndAfterHooksInReverse() = runTest {
        val order = mutableListOf<String>()
        val registry = steps {
            before { order += "before1" }
            before { order += "before2" }
            after { order += "after1" }
            after { order += "after2" }
            given("a step") { order += "step" }
        }

        assertTrue(
            CucumberRunner(registry)
                .run(picklesOf("Feature: F", "  Scenario: S", "    Given a step").single())
                .isPassed,
        )
        assertEquals(listOf("before1", "before2", "step", "after2", "after1"), order)
    }

    @Test
    fun runsAfterHooksEvenWhenAStepFailed() = runTest {
        var tornDown = false
        val registry = steps {
            after { tornDown = true }
            given("boom") { throw IllegalStateException("boom") }
        }

        val result = CucumberRunner(registry).run(
            picklesOf("Feature: F", "  Scenario: S", "    Given boom").single(),
        )

        assertTrue(!result.isPassed)
        assertTrue(tornDown, "teardown must run even after a failure")
    }

    @Test
    fun appliesTaggedHooksOnlyToMatchingScenarios() = runTest {
        val ran = mutableListOf<String>()
        val registry = steps {
            before("@smoke") { ran += "smoke-hook" }
            given("a step") { }
        }

        val runner = CucumberRunner(registry)
        val pickles = picklesOf(
            "Feature: F",
            "  @smoke",
            "  Scenario: tagged",
            "    Given a step",
            "  Scenario: untagged",
            "    Given a step",
        )

        for (pickle in pickles) runner.run(pickle)
        assertEquals(listOf("smoke-hook"), ran)
    }

    @Test
    fun tagFilterSkipsNonMatchingScenarios() = runTest {
        var ran = 0
        val registry = steps { given("a step") { ran++ } }
        val runner = CucumberRunner(registry, tagFilter = "@smoke and not @wip")

        val pickles = picklesOf(
            "Feature: F",
            "  @smoke",
            "  Scenario: included",
            "    Given a step",
            "  @smoke @wip",
            "  Scenario: excluded",
            "    Given a step",
        )

        val results = pickles.map { runner.run(it) }
        assertEquals(1, ran)
        assertTrue(!results[0].skipped)
        assertTrue(results[1].skipped)
        // A skipped scenario is not a pass — see DEVIATIONS.md on runtime filtering.
        assertTrue(!results[1].isPassed)
    }

    @Test
    fun runOrThrowFormatsTheFailure() = runTest {
        val registry = steps {
            given("ok") { }
            given("boom") { throw IllegalStateException("kaboom") }
            given("never") { }
        }

        val failure = assertFailsWith<ScenarioFailure> {
            CucumberRunner(registry).runOrThrow(
                picklesOf(
                    "Feature: F",
                    "  Scenario: exploding",
                    "    Given ok",
                    "    Given boom",
                    "    Given never",
                ).single(),
            )
        }

        val message = assertNotNull(failure.message)
        // The message reproduces the scenario so the reader sees where it stopped.
        assertTrue(message.contains("exploding"), message)
        assertTrue(message.contains("✓ ok"), message)
        assertTrue(message.contains("✗ boom"), message)
        assertTrue(message.contains("- never"), message)
        assertTrue(message.contains("kaboom"), message)
        assertEquals("kaboom", failure.cause?.message)
    }

    @Test
    fun supportsRegularExpressionStepDefinitions() = runTest {
        var captured: Any? = null
        val registry = steps {
            step("^I have (\\d+) cukes$") { count: Int -> captured = count }
        }

        assertTrue(
            CucumberRunner(registry)
                .run(picklesOf("Feature: F", "  Scenario: S", "    Given I have 9 cukes").single())
                .isPassed,
        )
        assertEquals(9, captured)
    }

    @Test
    fun reportsAnUnbindableArgumentType() = runTest {
        // {int} captures an Int; binding it to a String cannot work. KSP will reject this at
        // compile time; the DSL can only fail at run time.
        val registry = steps {
            given("I have {int} cukes") { _: String -> }
        }

        val result = CucumberRunner(registry).run(
            picklesOf("Feature: F", "  Scenario: S", "    Given I have 3 cukes").single(),
        )

        assertEquals(StepStatus.FAILED, result.steps.single().status)
        assertTrue(result.failure is StepArgumentTypeException, "got ${result.failure}")
    }
}
