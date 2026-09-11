package com.example.calculator

import io.github.menjoo.cucumberkmp.annotations.Given
import io.github.menjoo.cucumberkmp.annotations.Steps
import io.github.menjoo.cucumberkmp.annotations.Then
import io.github.menjoo.cucumberkmp.annotations.When
import io.github.menjoo.cucumberkmp.core.gherkin.DataTable
import kotlin.test.assertEquals

/**
 * Step definitions for `calculator.feature`.
 *
 * A fresh instance is created for every scenario, so the mutable state below cannot leak between
 * them — no reset needed and no dependency-injection container involved.
 */
@Steps
class CalculatorSteps {

    private val calculator = Calculator()

    @Given("a fresh calculator")
    fun aFreshCalculator() {
        // Nothing to do: a new instance of this class already means a new Calculator.
    }

    @Given("I have entered {double}")
    fun iHaveEntered(value: Double) {
        calculator.enter(value)
    }

    // One annotation is enough even though the feature says both "When I press add" and
    // "And I press add": Gherkin does not match on the keyword.
    @When("I press add")
    fun iPressAdd() {
        calculator.add()
    }

    @When("I press multiply")
    fun iPressMultiply() {
        calculator.multiply()
    }

    @When("I apply a discount of {int} percent")
    fun iApplyADiscount(percentage: Int) {
        calculator.applyDiscount(percentage)
    }

    /** A step taking the feature's data table as its last parameter. */
    @Given("the basket contains")
    fun theBasketContains(items: DataTable) {
        // asMaps() keys each row by the header row, so the step reads a column by name rather
        // than by position — and stays correct if the feature's columns are reordered.
        for (item in items.asMaps()) {
            calculator.enter(item.getValue("price").toDouble())
        }
    }

    @Then("the result should be {double}")
    fun theResultShouldBe(expected: Double) {
        assertEquals(expected, calculator.result)
    }
}
