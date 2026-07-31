package io.github.menjoo.cucumberkmp.core.gherkin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Runs upstream cucumber/gherkin's own parser test data against our parser.
 *
 * This is the suite that decides whether the port is faithful. Our hand-written tests can only
 * confirm the parser does what we *think* Gherkin does; these fixtures confirm it does what
 * Gherkin *does*. See ARCHITECTURE.md §2.
 *
 * The fixtures are embedded as generated Kotlin (`GherkinCorpus.kt`) rather than read from test
 * resources, so the suite runs on every target including Native and Wasm.
 */
class GherkinCorpusTest {

    @Test
    fun corpusIsPresent() {
        // Guards against a generation failure silently producing a suite that asserts nothing.
        assertTrue(
            GHERKIN_GOOD_CORPUS.size >= 40,
            "expected the good corpus to be populated, got ${GHERKIN_GOOD_CORPUS.size}",
        )
        assertTrue(
            GHERKIN_BAD_CORPUS.size >= 10,
            "expected the bad corpus to be populated, got ${GHERKIN_BAD_CORPUS.size}",
        )
    }

    @Test
    fun parseableFeaturesMatchUpstreamAst() {
        val failures = mutableListOf<String>()

        for (entry in GHERKIN_GOOD_CORPUS) {
            val outcome = GherkinParser.tryParse(entry.name, entry.source)
            if (outcome.errors.isNotEmpty()) {
                failures += "${entry.name}: expected no errors, got ${outcome.errors}"
                continue
            }
            val actual = outcome.document.toTrace()
            if (actual != entry.expectedTrace) {
                failures += "${entry.name}:\n${diff(entry.expectedTrace, actual)}"
            }
        }

        if (failures.isNotEmpty()) {
            fail("${failures.size}/${GHERKIN_GOOD_CORPUS.size} features differ:\n\n" + failures.joinToString("\n\n"))
        }
    }

    @Test
    fun failingFeaturesMatchUpstreamErrors() {
        val failures = mutableListOf<String>()

        for (entry in GHERKIN_BAD_CORPUS) {
            val actual = GherkinParser.tryParse(entry.name, entry.source).errors.map { it.toString() }
            if (actual != entry.expectedErrors) {
                failures += buildString {
                    appendLine(entry.name)
                    appendLine("  expected:")
                    entry.expectedErrors.forEach { appendLine("    $it") }
                    appendLine("  actual:")
                    if (actual.isEmpty()) appendLine("    (none)")
                    actual.forEach { appendLine("    $it") }
                }
            }
        }

        if (failures.isNotEmpty()) {
            fail("${failures.size}/${GHERKIN_BAD_CORPUS.size} failing features differ:\n\n" + failures.joinToString("\n"))
        }
    }

    @Test
    fun traceRoundTripsForASimpleFeature() {
        // Sanity-checks the trace renderer itself, so a corpus failure implicates the parser
        // rather than the comparison format.
        val document = GherkinParser.parse(
            "t.feature",
            "Feature: F\n  Scenario: S\n    Given x\n",
        )

        assertEquals(
            """
            document
              feature 1:1 language=en keyword=Feature name=F
                description ${""}
                scenario 2:3 keyword=Scenario name=S
                  description ${""}
                  step 3:5 keyword=Given  type=CONTEXT text=x
            """.trimIndent(),
            document.toTrace(),
        )
    }

    /** First differing line, with a little context — full traces are too long to eyeball. */
    private fun diff(expected: String, actual: String): String {
        val expectedLines = expected.lines()
        val actualLines = actual.lines()
        val firstDifference = expectedLines.indices.firstOrNull { index ->
            actualLines.getOrNull(index) != expectedLines[index]
        } ?: expectedLines.size

        return buildString {
            appendLine("  first difference at trace line ${firstDifference + 1}")
            for (index in (firstDifference - 2).coerceAtLeast(0) until firstDifference) {
                appendLine("     context: ${expectedLines[index]}")
            }
            appendLine("    expected: ${expectedLines.getOrNull(firstDifference) ?: "(end)"}")
            appendLine("      actual: ${actualLines.getOrNull(firstDifference) ?: "(end)"}")
            if (expectedLines.size != actualLines.size) {
                appendLine("    (expected ${expectedLines.size} lines, got ${actualLines.size})")
            }
        }
    }
}
