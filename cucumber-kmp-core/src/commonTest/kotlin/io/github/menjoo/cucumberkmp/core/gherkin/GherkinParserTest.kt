package io.github.menjoo.cucumberkmp.core.gherkin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private const val PATH = "test.feature"

private fun source(vararg lines: String): String = lines.joinToString("\n")

private fun parse(vararg lines: String): GherkinDocument =
    GherkinParser.parse(PATH, source(*lines))

private fun featureOf(vararg lines: String): Feature = assertNotNull(parse(*lines).feature)

private fun errorsOf(vararg lines: String): List<GherkinParseError> =
    GherkinParser.tryParse(PATH, source(*lines)).errors

private val Feature.scenarios: List<Scenario> get() = children.filterIsInstance<Scenario>()

class GherkinParserTest {

    @Test
    fun parsesMinimalFeature() {
        val feature = featureOf("Feature: Guess the word")

        assertEquals("Feature", feature.keyword)
        assertEquals("Guess the word", feature.name)
        assertEquals("en", feature.language)
        assertEquals(1, feature.location.line)
        assertEquals(1, feature.location.column)
        assertTrue(feature.children.isEmpty())
    }

    @Test
    fun emptyFileHasNoFeature() {
        assertNull(parse("").feature)
        assertNull(parse("", "   ", "").feature)
    }

    @Test
    fun commentOnlyFileHasNoFeatureButKeepsComments() {
        val document = parse("# just a note")
        assertNull(document.feature)
        assertEquals(1, document.comments.size)
        assertEquals("# just a note", document.comments.single().text)
    }

    @Test
    fun parsesDescriptionPreservingInteriorBlankLines() {
        val feature = featureOf(
            "Feature: Name",
            "  first line",
            "",
            "  third line",
            "",
            "  Scenario: S",
        )

        assertEquals("  first line\n\n  third line", feature.description)
        assertEquals(1, feature.scenarios.size)
    }

    @Test
    fun parsesStepsWithKeywordTypes() {
        val scenario = featureOf(
            "Feature: F",
            "  Scenario: S",
            "    Given a precondition",
            "    When something happens",
            "    Then an outcome",
            "    And another outcome",
            "    But not this",
            "    * a wildcard",
        ).scenarios.single()

        assertEquals(6, scenario.steps.size)
        assertEquals(
            listOf("Given ", "When ", "Then ", "And ", "But ", "* "),
            scenario.steps.map { it.keyword },
        )
        assertEquals(
            listOf(
                StepKeywordType.CONTEXT,
                StepKeywordType.ACTION,
                StepKeywordType.OUTCOME,
                StepKeywordType.CONJUNCTION,
                StepKeywordType.CONJUNCTION,
                // `*` belongs to every category, so its meaning is not lexically decidable.
                StepKeywordType.UNKNOWN,
            ),
            scenario.steps.map { it.keywordType },
        )
        assertEquals("a precondition", scenario.steps.first().text)
        assertEquals("a wildcard", scenario.steps.last().text)
    }

    @Test
    fun parsesBackground() {
        val feature = featureOf(
            "Feature: F",
            "  Background: shared setup",
            "    Given a logged-in user",
            "  Scenario: S",
            "    Then it works",
        )

        val background = feature.children.filterIsInstance<Background>().single()
        assertEquals("Background", background.keyword)
        assertEquals("shared setup", background.name)
        assertEquals(1, background.steps.size)
        assertEquals(1, feature.scenarios.single().steps.size)
    }

    @Test
    fun parsesScenarioOutlineWithExamples() {
        val scenario = featureOf(
            "Feature: F",
            "  Scenario Outline: eating",
            "    Given there are <start> cucumbers",
            "    When I eat <eat> cucumbers",
            "    Then I should have <left> cucumbers",
            "",
            "    Examples:",
            "      | start | eat | left |",
            "      | 12    | 5   | 7    |",
            "      | 20    | 5   | 15   |",
        ).scenarios.single()

        assertEquals("Scenario Outline", scenario.keyword)
        assertTrue(scenario.isOutline)

        val examples = scenario.examples.single()
        assertEquals("Examples", examples.keyword)
        assertEquals(
            listOf("start", "eat", "left"),
            assertNotNull(examples.tableHeader).cells.map { it.value },
        )
        assertEquals(2, examples.tableBody.size)
        assertEquals(listOf("12", "5", "7"), examples.tableBody.first().cells.map { it.value })
    }

    @Test
    fun plainScenarioIsNotAnOutline() {
        val scenario = featureOf("Feature: F", "  Scenario: S", "    Given x").scenarios.single()
        assertTrue(!scenario.isOutline)
        assertTrue(scenario.examples.isEmpty())
    }

    @Test
    fun parsesDataTableWithEscapes() {
        val step = featureOf(
            "Feature: F",
            "  Scenario: S",
            "    Given the users",
            "      | name  | note        |",
            "      | a\\|b  | line\\nbreak |",
            "      | c\\\\d  | plain       |",
        ).scenarios.single().steps.single()

        val table = assertNotNull(step.dataTable)
        assertEquals(3, table.rows.size)
        assertEquals(listOf("name", "note"), table.rows[0].cells.map { it.value })
        // \| is a literal pipe, \n a newline, \\ a single backslash.
        assertEquals(listOf("a|b", "line\nbreak"), table.rows[1].cells.map { it.value })
        assertEquals(listOf("c\\d", "plain"), table.rows[2].cells.map { it.value })
    }

    @Test
    fun reportsTableCellColumns() {
        val table = assertNotNull(
            featureOf(
                "Feature: F",
                "  Scenario: S",
                "    Given the users",
                "      | ab | cd |",
            ).scenarios.single().steps.single().dataTable,
        )

        val cells = table.rows.single().cells
        // "      | ab | cd |" — 'a' is the 9th character on the line, 'c' the 14th.
        assertEquals(9, cells[0].location.column)
        assertEquals(14, cells[1].location.column)
        assertEquals(4, table.rows.single().location.line)
    }

    @Test
    fun detectsInconsistentCellCount() {
        val errors = errorsOf(
            "Feature: F",
            "  Scenario: S",
            "    Given the users",
            "      | a | b |",
            "      | c |",
        )

        assertEquals(1, errors.size)
        assertEquals(5, errors.single().location.line)
        assertTrue(errors.single().message.contains("inconsistent cell count"))
    }

    @Test
    fun parsesDocStringWithQuotes() {
        val step = featureOf(
            "Feature: F",
            "  Scenario: S",
            "    Given a payload",
            "      \"\"\"",
            "      first",
            "        indented",
            "      \"\"\"",
        ).scenarios.single().steps.single()

        val docString = assertNotNull(step.docString)
        assertEquals("first\n  indented", docString.content)
        assertNull(docString.mediaType)
        assertEquals("\"\"\"", docString.delimiter)
        assertNull(step.dataTable)
    }

    @Test
    fun parsesDocStringWithBackticksAndMediaType() {
        val docString = assertNotNull(
            featureOf(
                "Feature: F",
                "  Scenario: S",
                "    Given a payload",
                "      ```json",
                "      {\"a\": 1}",
                "      ```",
            ).scenarios.single().steps.single().docString,
        )

        assertEquals("json", docString.mediaType)
        assertEquals("{\"a\": 1}", docString.content)
        assertEquals("```", docString.delimiter)
    }

    @Test
    fun unescapesDocStringDelimiter() {
        val docString = assertNotNull(
            featureOf(
                "Feature: F",
                "  Scenario: S",
                "    Given a payload",
                "      \"\"\"",
                "      \\\"\"\" not a terminator",
                "      \"\"\"",
            ).scenarios.single().steps.single().docString,
        )

        assertEquals("\"\"\" not a terminator", docString.content)
    }

    @Test
    fun reportsUnterminatedDocString() {
        val errors = errorsOf(
            "Feature: F",
            "  Scenario: S",
            "    Given a payload",
            "      \"\"\"",
            "      dangling",
        )

        assertEquals(1, errors.size)
        assertTrue(errors.single().message.contains("unexpected end of file"))
        assertEquals(4, errors.single().location.line)
    }

    @Test
    fun parsesTagsAtEveryLevel() {
        val feature = featureOf(
            "@feature-tag",
            "Feature: F",
            "  @scenario-tag @second",
            "  Scenario Outline: S",
            "    Given <x>",
            "    @examples-tag",
            "    Examples:",
            "      | x |",
            "      | 1 |",
        )

        assertEquals(listOf("@feature-tag"), feature.tags.map { it.name })
        val scenario = feature.scenarios.single()
        assertEquals(listOf("@scenario-tag", "@second"), scenario.tags.map { it.name })
        assertEquals(listOf("@examples-tag"), scenario.examples.single().tags.map { it.name })
    }

    @Test
    fun splitsAdjacentTagsOnAtSign() {
        // Gherkin splits tag lines on '@', not on whitespace, so @a@b is two tags.
        val feature = featureOf("@a@b", "Feature: F")
        assertEquals(listOf("@a", "@b"), feature.tags.map { it.name })
    }

    @Test
    fun tagsBelongingToTheNextScenarioAreNotStolenByExamples() {
        val feature = featureOf(
            "Feature: F",
            "  Scenario: first",
            "    Given x",
            "  @second-tag",
            "  Scenario: second",
            "    Given y",
        )

        val scenarios = feature.scenarios
        assertEquals(2, scenarios.size)
        assertTrue(scenarios[0].tags.isEmpty())
        assertEquals(listOf("@second-tag"), scenarios[1].tags.map { it.name })
    }

    @Test
    fun parsesRuleWithOwnBackgroundAndScenarios() {
        val feature = featureOf(
            "Feature: F",
            "  Rule: first rule",
            "    Background:",
            "      Given setup",
            "    Scenario: a",
            "      Given x",
            "  Rule: second rule",
            "    Scenario: b",
            "      Given y",
        )

        val rules = feature.children.filterIsInstance<Rule>()
        assertEquals(2, rules.size)
        assertEquals("first rule", rules[0].name)
        assertEquals(1, rules[0].children.filterIsInstance<Background>().size)
        assertEquals(listOf("a"), rules[0].children.filterIsInstance<Scenario>().map { it.name })
        assertEquals(listOf("b"), rules[1].children.filterIsInstance<Scenario>().map { it.name })
    }

    @Test
    fun collectsCommentsWithoutDuplicating() {
        val document = parse(
            "# leading",
            "Feature: F",
            "  # inside",
            "  Scenario: S",
            "    Given x",
            "  # trailing",
        )

        assertEquals(
            listOf("# leading", "  # inside", "  # trailing"),
            document.comments.map { it.text },
        )
        assertEquals(listOf(1, 3, 6), document.comments.map { it.location.line })
    }

    @Test
    fun parsesDutchDialect() {
        val feature = featureOf(
            "# language: nl",
            "Functionaliteit: Korting berekenen",
            "  Abstract Scenario: Premium lid krijgt korting",
            "    Gegeven een gebruiker met een \"Premium\" account",
            "    Wanneer het winkelmandje een totaal heeft van 100 euro",
            "    Dan moet de uiteindelijke prijs 80 euro zijn",
            "    Voorbeelden:",
            "      | x |",
            "      | 1 |",
        )

        assertEquals("nl", feature.language)
        assertEquals("Functionaliteit", feature.keyword)
        assertEquals("Korting berekenen", feature.name)

        val scenario = feature.scenarios.single()
        assertEquals("Abstract Scenario", scenario.keyword)
        assertTrue(scenario.isOutline)
        assertEquals("Voorbeelden", scenario.examples.single().keyword)
        assertEquals(
            listOf(
                StepKeywordType.CONTEXT,
                StepKeywordType.ACTION,
                StepKeywordType.OUTCOME,
            ),
            scenario.steps.map { it.keywordType },
        )
        assertEquals("een gebruiker met een \"Premium\" account", scenario.steps.first().text)
    }

    @Test
    fun reportsUnsupportedLanguage() {
        val errors = errorsOf("# language: klingon", "Feature: F")
        assertEquals(1, errors.size)
        assertTrue(errors.single().message.contains("Language not supported: klingon"))
    }

    @Test
    fun reportsJunkBeforeFeatureAndKeepsParsing() {
        val outcome = GherkinParser.tryParse(
            PATH,
            source("not a feature", "Feature: F", "  Scenario: S", "    Given x"),
        )

        assertEquals(1, outcome.errors.size)
        assertEquals(1, outcome.errors.single().location.line)
        assertTrue(outcome.errors.single().message.contains("#FeatureLine"))
        // Recovery: the feature after the junk still parses.
        assertEquals("F", assertNotNull(outcome.document.feature).name)
    }

    @Test
    fun reportsEveryErrorNotJustTheFirst() {
        val errors = errorsOf(
            "Feature: F",
            "  Scenario: S",
            "    Given the users",
            "      | a | b |",
            "      | c |",
            "  Scenario: T",
            "    Given more users",
            "      | d | e |",
            "      | f |",
        )

        assertEquals(2, errors.size)
        assertEquals(listOf(5, 9), errors.map { it.location.line })
    }

    @Test
    fun throwingParseReportsPathAndAllErrors() {
        val exception = assertFailsWith<GherkinParseException> {
            parse("Feature: F", "  Scenario: S", "    Given x", "      \"\"\"")
        }

        assertEquals(PATH, exception.path)
        assertEquals(1, exception.errors.size)
        assertTrue(assertNotNull(exception.message).contains(PATH))
    }

    @Test
    fun handlesCarriageReturnsAndByteOrderMark() {
        val document = GherkinParser.parse(
            PATH,
            "﻿Feature: F\r\n  Scenario: S\r\n    Given x\r\n",
        )

        val feature = assertNotNull(document.feature)
        assertEquals("F", feature.name)
        assertEquals("x", feature.scenarios.single().steps.single().text)
    }

    @Test
    fun supportsAllUpstreamLanguages() {
        // The dialect table is generated from upstream; this guards against a truncated
        // generation (a dropped chunk would cost 10 languages) without failing merely because
        // upstream added a language.
        assertTrue(GherkinDialects.supportedLanguages.size >= 80)
        assertTrue("en" in GherkinDialects.supportedLanguages)
        assertTrue("nl" in GherkinDialects.supportedLanguages)
        assertEquals("Nederlands", GherkinDialects.forLanguage("nl").nativeName)
    }
}
