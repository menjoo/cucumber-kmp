package io.github.menjoo.cucumberkmp.core.expression

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Runs upstream cucumber-expressions' own test data against our implementation.
 *
 * Five families cover the whole pipeline: tokenizer, parser, regex generation, expression matching
 * and raw-regex matching. Running them on every target — not only the JVM — is deliberate: regex
 * behaviour differs between the four engines behind `kotlin.text.Regex`, and that is the top
 * correctness risk for this project (ARCHITECTURE.md §13.2).
 */
class ExpressionCorpusTest {

    @Test
    fun corpusIsPresent() {
        assertTrue(EXPRESSION_TOKENIZER_CORPUS.size >= 15, "tokenizer corpus missing")
        assertTrue(EXPRESSION_PARSER_CORPUS.size >= 25, "parser corpus missing")
        assertTrue(EXPRESSION_TRANSFORMATION_CORPUS.size >= 8, "transformation corpus missing")
        assertTrue(EXPRESSION_MATCHING_CORPUS.size >= 60, "matching corpus missing")
        assertTrue(EXPRESSION_REGEX_CORPUS.size >= 3, "regex corpus missing")
    }

    @Test
    fun tokenizerMatchesUpstream() = runCases(EXPRESSION_TOKENIZER_CORPUS) { case ->
        ExpressionTokenizer.tokenize(case.expression)
            .joinToString("\n") { "${it.type} ${it.start} ${it.end} ${it.text.escapeTrace()}" }
    }

    @Test
    fun parserMatchesUpstream() = runCases(EXPRESSION_PARSER_CORPUS) { case ->
        CucumberExpressionParser.parse(case.expression).toTrace()
    }

    @Test
    fun regexGenerationMatchesUpstream() = runCases(EXPRESSION_TRANSFORMATION_CORPUS) { case ->
        CucumberExpression(case.expression, ParameterTypeRegistry()).regexp.pattern
    }

    @Test
    fun expressionMatchingMatchesUpstream() {
        runArgumentCases(EXPRESSION_MATCHING_CORPUS) { case ->
            CucumberExpression(case.expression, ParameterTypeRegistry()).match(requireNotNull(case.text))
        }
    }

    @Test
    fun regularExpressionMatchingMatchesUpstream() {
        runArgumentCases(EXPRESSION_REGEX_CORPUS) { case ->
            RegularExpression(Regex(case.expression), ParameterTypeRegistry())
                .match(requireNotNull(case.text))
        }
    }

    /** Runs cases whose expectation is a single string, or an exception message. */
    private fun runCases(cases: List<ExpressionCase>, produce: (ExpressionCase) -> String) {
        val failures = mutableListOf<String>()
        for (case in cases) {
            val actual = runCatching { produce(case) }
            when {
                case.exception != null -> checkException(case, actual, failures)
                actual.isFailure ->
                    failures += "${case.name}: expected success, threw ${actual.describeFailure()}"
                actual.getOrThrow() != case.expected ->
                    failures += buildString {
                        appendLine("${case.name}  (expression: ${case.expression})")
                        appendLine("  expected: ${case.expected}")
                        appendLine("    actual: ${actual.getOrThrow()}")
                    }
            }
        }
        reportFailures(cases.size, failures)
    }

    /** Runs cases whose expectation is a list of argument values, or an exception message. */
    private fun runArgumentCases(
        cases: List<ExpressionCase>,
        produce: (ExpressionCase) -> List<Argument>?,
    ) {
        val failures = mutableListOf<String>()
        for (case in cases) {
            val actual = runCatching { produce(case) }
            if (case.exception != null) {
                checkException(case, actual, failures)
                continue
            }
            if (actual.isFailure) {
                failures += "${case.name}: expected a match, threw ${actual.describeFailure()}"
                continue
            }
            val arguments = actual.getOrThrow()
            if (case.expectNoMatch) {
                if (arguments != null) {
                    failures += "${case.name}: '${case.expression}' should not match '${case.text}'," +
                        " but matched with ${arguments.map { it.value }}"
                }
                continue
            }
            if (arguments == null) {
                failures += "${case.name}: '${case.expression}' did not match '${case.text}'"
                continue
            }
            val rendered = arguments.map { it.renderForCorpus() }
            if (rendered != case.expectedArgs) {
                failures += buildString {
                    appendLine("${case.name}  (expression: ${case.expression}, text: ${case.text})")
                    appendLine("  expected: ${case.expectedArgs}")
                    appendLine("    actual: $rendered")
                }
            }
        }
        reportFailures(cases.size, failures)
    }

    private fun checkException(
        case: ExpressionCase,
        actual: Result<*>,
        failures: MutableList<String>,
    ) {
        val thrown = actual.exceptionOrNull()
        if (thrown == null) {
            failures += buildString {
                appendLine("${case.name}: expected an exception, got ${actual.getOrNull()}")
                appendLine("  expected: ${case.exception}")
            }
            return
        }
        if (thrown.message != case.exception) {
            failures += buildString {
                appendLine("${case.name}  (expression: ${case.expression})")
                appendLine("  expected message:")
                appendLine(case.exception?.prependIndent("    "))
                appendLine("  actual message:")
                appendLine(thrown.message?.prependIndent("    ") ?: "    (none)")
            }
        }
    }

    private fun reportFailures(total: Int, failures: List<String>) {
        if (failures.isNotEmpty()) {
            fail("${failures.size}/$total cases differ:\n\n" + failures.joinToString("\n"))
        }
    }

    private fun Result<*>.describeFailure(): String =
        exceptionOrNull()?.let { "${it::class.simpleName}: ${it.message}" } ?: "nothing"
}

/** Canonical AST trace, matching the format `tools/update-expression-corpus.py` emits. */
internal fun ExpressionNode.toTrace(depth: Int = 0): String {
    val indent = "  ".repeat(depth)
    val head = "$indent$type $start $end"
    if (token != null) return "$head token=${token.escapeTrace()}"
    return (listOf(head) + (nodes ?: emptyList()).map { it.toTrace(depth + 1) }).joinToString("\n")
}

internal fun String.escapeTrace(): String =
    replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")

/**
 * Renders an argument value in a way that is identical on every target.
 *
 * `Float.toString()` and `Double.toString()` are **not portable**: JS has a single number type and
 * prints a whole value as `1500`, where the JVM and Native print `1500.0`. Normalising here keeps
 * the comparison strict — the alternative, comparing numerically, would let an `Int` pass where a
 * `Double` was expected and so stop checking the conversion's type.
 */
/**
 * Renders an argument value identically on every target.
 *
 * Two portability traps make this necessary, both specific to Kotlin/JS, where every number is a
 * single JS `number`:
 *
 * 1. `Double.toString()` drops the fraction, printing `1500` where the JVM and Native print
 *    `1500.0`.
 * 2. Runtime type checks cannot tell `Int` from a whole `Double` — `1500.0 is Int` is true — so
 *    branching on the *value's* type renders integers as decimals.
 *
 * Both are avoided by taking the format from the **declared parameter type** and normalising the
 * rendered string rather than inspecting the runtime value. That also checks more than a numeric
 * comparison would: `{int}` must produce something integral, not merely something equal to 3.
 */
internal fun Argument.renderForCorpus(): String {
    val value = value ?: return "null"
    val text = value.toString()
    return if (parameterTypeName in DECIMAL_PARAMETER_TYPES && text.looksIntegral()) {
        "$text.0"
    } else {
        text
    }
}

private val DECIMAL_PARAMETER_TYPES = setOf("float", "double", "bigdecimal")

private fun String.looksIntegral(): Boolean =
    toDoubleOrNull() != null && none { it == '.' || it == 'e' || it == 'E' }
