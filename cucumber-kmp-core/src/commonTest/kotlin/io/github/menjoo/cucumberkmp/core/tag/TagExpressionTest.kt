package io.github.menjoo.cucumberkmp.core.tag

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Runs upstream cucumber/tag-expressions' own test data against our parser.
 *
 * Three families: canonical rendering (which round-trips the AST), evaluation, and exact error
 * messages. See ARCHITECTURE.md §2.
 */
class TagExpressionCorpusTest {

    @Test
    fun corpusIsPresent() {
        assertTrue(TAG_PARSING_CORPUS.size >= 20, "parsing corpus missing")
        assertTrue(TAG_EVALUATION_CORPUS.size >= 20, "evaluation corpus missing")
        assertTrue(TAG_ERROR_CORPUS.size >= 15, "error corpus missing")
    }

    @Test
    fun renderingMatchesUpstream() {
        val failures = TAG_PARSING_CORPUS.mapNotNull { case ->
            val actual = runCatching { TagExpressionParser.parse(case.expression).toString() }
            when {
                actual.isFailure -> "'${case.expression}' threw ${actual.exceptionOrNull()?.message}"
                actual.getOrThrow() != case.formatted ->
                    "'${case.expression}'\n    expected: ${case.formatted}\n      actual: ${actual.getOrThrow()}"
                else -> null
            }
        }
        report(TAG_PARSING_CORPUS.size, failures)
    }

    @Test
    fun evaluationMatchesUpstream() {
        val failures = TAG_EVALUATION_CORPUS.mapNotNull { case ->
            val actual = runCatching { TagExpressionParser.parse(case.expression).evaluate(case.variables) }
            when {
                actual.isFailure -> "'${case.expression}' threw ${actual.exceptionOrNull()?.message}"
                actual.getOrThrow() != case.result ->
                    "'${case.expression}' with ${case.variables}: expected ${case.result}, got ${actual.getOrThrow()}"
                else -> null
            }
        }
        report(TAG_EVALUATION_CORPUS.size, failures)
    }

    @Test
    fun errorMessagesMatchUpstream() {
        val failures = TAG_ERROR_CORPUS.mapNotNull { case ->
            val thrown = runCatching { TagExpressionParser.parse(case.expression) }.exceptionOrNull()
            when {
                thrown == null -> "'${case.expression}' should have failed but parsed"
                thrown.message != case.error ->
                    "'${case.expression}'\n    expected: ${case.error}\n      actual: ${thrown.message}"
                else -> null
            }
        }
        report(TAG_ERROR_CORPUS.size, failures)
    }

    /**
     * Rendering must round-trip: parsing a rendered expression yields the same rendering again.
     *
     * Not an upstream fixture, but it is the property that makes `toString` usable for reporting
     * which selection was applied, and it exercises the literal re-escaping.
     */
    @Test
    fun renderingRoundTrips() {
        val failures = TAG_PARSING_CORPUS.mapNotNull { case ->
            val once = TagExpressionParser.parse(case.expression).toString()
            val twice = TagExpressionParser.parse(once).toString()
            if (once != twice) "'${case.expression}': '$once' then '$twice'" else null
        }
        report(TAG_PARSING_CORPUS.size, failures)
    }

    private fun report(total: Int, failures: List<String>) {
        if (failures.isNotEmpty()) {
            fail("${failures.size}/$total cases differ:\n" + failures.joinToString("\n"))
        }
    }
}

/** Behaviour the upstream fixtures leave uncovered. */
class TagExpressionTest {

    @Test
    fun emptyExpressionSelectsEverything() {
        val expression = TagExpressionParser.parse("")
        assertTrue(expression.evaluate(emptyList()))
        assertTrue(expression.evaluate(listOf("@anything")))
        assertEquals("", expression.toString())
    }

    @Test
    fun evaluatesRealisticSelection() {
        val expression = TagExpressionParser.parse("@smoke and not @wip")

        assertTrue(expression.evaluate(listOf("@smoke")))
        assertTrue(expression.evaluate(listOf("@smoke", "@fast")))
        assertFalse(expression.evaluate(listOf("@smoke", "@wip")))
        assertFalse(expression.evaluate(listOf("@wip")))
        assertFalse(expression.evaluate(emptyList()))
    }

    @Test
    fun notBindsTighterThanAnd() {
        assertEquals("( not ( a ) and b )", TagExpressionParser.parse("not a and b").toString())
    }

    @Test
    fun andBindsTighterThanOr() {
        assertEquals("( a or ( b and c ) )", TagExpressionParser.parse("a or b and c").toString())
    }

    @Test
    fun acceptsRepeatedNot() {
        // `not` is right-associative, so it may stack.
        val expression = TagExpressionParser.parse("not not a")
        assertTrue(expression.evaluate(listOf("a")))
        assertFalse(expression.evaluate(emptyList()))
    }

    @Test
    fun matchesTagsWithEscapedParentheses() {
        val expression = TagExpressionParser.parse("@x\\(1\\)")
        assertTrue(expression.evaluate(listOf("@x(1)")))
        assertFalse(expression.evaluate(listOf("@x1")))
    }
}
