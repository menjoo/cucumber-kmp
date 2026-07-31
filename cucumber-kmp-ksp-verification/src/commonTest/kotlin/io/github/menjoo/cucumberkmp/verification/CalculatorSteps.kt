package io.github.menjoo.cucumberkmp.verification

import io.github.menjoo.cucumberkmp.annotations.After
import io.github.menjoo.cucumberkmp.annotations.Before
import io.github.menjoo.cucumberkmp.annotations.Given
import io.github.menjoo.cucumberkmp.annotations.Step
import io.github.menjoo.cucumberkmp.annotations.Steps
import io.github.menjoo.cucumberkmp.annotations.Then
import io.github.menjoo.cucumberkmp.annotations.When
import kotlin.test.assertEquals

/**
 * Glue for the KSP verification suite, written the way a user would write it.
 *
 * Deliberately exercises the shapes the processor has to handle: a class needing a fresh instance
 * per scenario, several parameter types, a suspend step, a keyword-agnostic `@Step`, and hooks with
 * and without a tag expression.
 */
@Steps
class CalculatorSteps {

    private var entered = mutableListOf<Int>()
    private var result: Int = 0
    private var label: String = ""

    @Before
    fun reset() {
        entered = mutableListOf()
        result = 0
        setupCount++
    }

    @Before("@tagged")
    fun taggedSetup() {
        taggedSetupCount++
    }

    @After
    fun teardown() {
        teardownCount++
    }

    @Given("I have entered {int}")
    fun iHaveEntered(value: Int) {
        entered += value
    }

    @When("I press add")
    fun iPressAdd() {
        result = entered.sum()
    }

    /** A suspend step, to prove the generated call site is inside a coroutine. */
    @When("I press add slowly")
    suspend fun iPressAddSlowly() {
        result = entered.sum()
    }

    @Then("the result should be {int}")
    fun theResultShouldBe(expected: Int) {
        assertEquals(expected, result)
    }

    /** Keyword-agnostic, and takes two arguments of different types. */
    @Step("the label {string} repeated {int} times")
    fun theLabel(text: String, times: Int) {
        label = text.repeat(times)
    }

    @Then("the label should be {string}")
    fun theLabelShouldBe(expected: String) {
        assertEquals(expected, label)
    }

    companion object {
        // Static counters, so a test can observe how often hooks ran across scenarios.
        var setupCount: Int = 0
        var taggedSetupCount: Int = 0
        var teardownCount: Int = 0

        fun resetCounters() {
            setupCount = 0
            taggedSetupCount = 0
            teardownCount = 0
        }
    }
}

/** A top-level step function, which needs no receiver at all. */
@Given("a top-level step")
fun aTopLevelStep() {
    topLevelStepRan = true
}

var topLevelStepRan: Boolean = false

/** Steps on an `object`, which the processor should reference directly rather than instantiate. */
object SharedSteps {

    @Given("a step on an object")
    fun aStepOnAnObject() {
        objectStepRan = true
    }

    var objectStepRan: Boolean = false
}
