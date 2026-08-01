package com.example.calculator

/**
 * Ordinary shared business logic — nothing here knows about Cucumber.
 *
 * That separation is the point: the same `commonMain` code that ships in the app is what the
 * feature files exercise, on every platform it compiles to.
 */
class Calculator {

    private val entries = mutableListOf<Double>()

    var result: Double = 0.0
        private set

    fun enter(value: Double) {
        entries += value
    }

    fun add() {
        result = entries.sum()
    }

    fun multiply() {
        result = entries.fold(1.0) { running, value -> running * value }
    }

    /** Applies a percentage discount, rounded to whole cents. */
    fun applyDiscount(percentage: Int) {
        val discounted = result * (100 - percentage) / 100
        result = kotlin.math.round(discounted * 100) / 100
    }
}
