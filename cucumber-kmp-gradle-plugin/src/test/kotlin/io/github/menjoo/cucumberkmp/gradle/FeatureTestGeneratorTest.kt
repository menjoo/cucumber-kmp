package io.github.menjoo.cucumberkmp.gradle

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeatureTestGeneratorTest {

    /** A literal `$`, so the hostile-input fixtures below read as what they are. */
    private val DOLLAR = "$"

    private fun generate(source: String, tagFilter: String? = null): String =
        FeatureTestGenerator.generate(
            path = "calculator.feature",
            source = source,
            packageName = "com.example.generated",
            stepRegistry = "com.example.steps",
            tagFilter = tagFilter,
        ).code

    @Test
    fun `generates one test function per scenario`() {
        val code = generate(
            """
            Feature: Calculator
              Scenario: adds two numbers
                Given I have entered 5
                Then the result should be 5
              Scenario: subtracts two numbers
                Given I have entered 3
            """.trimIndent(),
        )

        assertTrue(code.contains("public class CalculatorFeatureTest"), code)
        assertTrue(code.contains("addsTwoNumbers"), code)
        assertTrue(code.contains("subtractsTwoNumbers"), code)
        assertTrue(code.contains("RUNNER.runOrThrow(PICKLE_0)"), code)
        assertTrue(code.contains("RUNNER.runOrThrow(PICKLE_1)"), code)
    }

    @Test
    fun `embeds the scenario as constructor calls rather than feature text`() {
        // This is the mechanism that lets a feature reach Native and Wasm, where the file cannot
        // be read at runtime.
        val code = generate(
            """
            Feature: F
              Scenario: S
                Given I have entered 5
            """.trimIndent(),
        )

        assertTrue(code.contains("Pickle("), code)
        assertTrue(code.contains("""text = "I have entered 5""""), code)
        assertTrue(code.contains("type = StepKeywordType.CONTEXT"), code)
        assertTrue(code.contains("SourceLocation(\"calculator.feature\""), code)
    }

    @Test
    fun `returns TestResult so async targets work`() {
        // On JS and Wasm a test that suspends must return the runTest result; declaring it on
        // every generated function keeps one shape across all targets.
        // KotlinPoet collapses a lone `return` into an expression body.
        val code = generate("Feature: F\n  Scenario: S\n    Given x")
        assertTrue(code.contains("): TestResult = runTest {"), code)
    }

    @Test
    fun `expands a scenario outline into one test per row`() {
        val code = generate(
            """
            Feature: F
              Scenario Outline: eating <start> cucumbers
                Given I have <start> cucumbers
                Examples:
                  | start |
                  | 12    |
                  | 20    |
            """.trimIndent(),
        )

        // Names are interpolated per row, and the collision suffix keeps them unique.
        assertTrue(code.contains("eating12Cucumbers"), code)
        assertTrue(code.contains("eating20Cucumbers"), code)
        assertTrue(code.contains("PICKLE_1"), code)
    }

    @Test
    fun `inlines background steps into every scenario`() {
        val code = generate(
            """
            Feature: F
              Background:
                Given a logged-in user
              Scenario: S
                Then it works
            """.trimIndent(),
        )
        assertTrue(code.contains("""text = "a logged-in user""""), code)
    }

    @Test
    fun `embeds data tables and doc strings`() {
        val code = generate(
            """
            Feature: F
              Scenario: S
                Given the users
                  | name | age |
                  | ann  | 30  |
                And a payload
                  ${"\"\"\""}json
                  {"a": 1}
                  ${"\"\"\""}
            """.trimIndent(),
        )

        assertTrue(code.contains("DataTable("), code)
        assertTrue(code.contains("TableCell("), code)
        assertTrue(code.contains(""""ann""""), code)
        assertTrue(code.contains("DocString("), code)
        assertTrue(code.contains("""mediaType = "json""""), code)
    }

    @Test
    fun `marks scenarios excluded by the tag filter as ignored rather than dropping them`() {
        val code = generate(
            """
            Feature: F
              @smoke
              Scenario: included
                Given x
              @wip
              Scenario: excluded
                Given y
            """.trimIndent(),
            tagFilter = "@smoke and not @wip",
        )

        assertTrue(code.contains("included"), code)
        assertTrue(code.contains("excluded"), code)
        assertTrue(code.contains("@Ignore"), code)
        // Only the excluded one is ignored.
        assertEquals(1, Regex("@Ignore").findAll(code).count(), code)
    }

    @Test
    fun `derives a class name from the file name`() {
        assertEquals("CalculatorFeatureTest", FeatureTestGenerator.testClassName("calculator.feature"))
        assertEquals(
            "ShoppingCartFeatureTest",
            FeatureTestGenerator.testClassName("nested/shopping-cart.feature"),
        )
        assertEquals("MyFeatureFeatureTest", FeatureTestGenerator.testClassName("my_feature.feature"))
    }

    @Test
    fun `builds portable identifiers from scenario names`() {
        val used = mutableSetOf<String>()
        // Backticked names with spaces are not portable across Android's dexer and JS toolchains.
        assertEquals(
            "addsTwoNumbers",
            FeatureTestGenerator.uniqueFunctionName("Adds two numbers", 0, used),
        )
        // Non-alphanumerics are separators, not transliterated — `€` and `&` simply disappear.
        assertEquals(
            "handlesAnd",
            FeatureTestGenerator.uniqueFunctionName("Handles € and & ", 1, used),
        )
        assertEquals(
            "scenario3BlindMice",
            FeatureTestGenerator.uniqueFunctionName("3 blind mice", 2, used),
        )
        assertEquals("scenario", FeatureTestGenerator.uniqueFunctionName("...", 3, used))
    }

    @Test
    fun `disambiguates duplicate scenario names`() {
        val used = mutableSetOf<String>()
        assertEquals("same", FeatureTestGenerator.uniqueFunctionName("same", 0, used))
        assertEquals("same2", FeatureTestGenerator.uniqueFunctionName("same", 1, used))
        assertEquals("same3", FeatureTestGenerator.uniqueFunctionName("same", 2, used))
    }

    // ---------------------------------------------------------------- hostile input
    //
    // A .feature file is data, reviewed as prose. Anything from it that lands in the generated
    // source has to be escaped, or the data channel becomes a code channel: the generated class is
    // compiled and run by `./gradlew build` on whatever machine builds the project.

    @Test
    fun `escapes a tag that would otherwise interpolate`() {
        // A tag may contain anything except whitespace, and `${'$'}{...}` needs none.
        val code = generate(
            """
            Feature: F
              @a$DOLLAR{Runtime.getRuntime()}
              Scenario: S
                Given x
            """.trimIndent(),
        )

        assertTrue(code.contains("@a$DOLLAR{'$DOLLAR'}{Runtime.getRuntime()}"), code)
        // The raw form would be a live template in the consumer's test source.
        assertFalse(code.contains("\"@a$DOLLAR{Runtime.getRuntime()}\""), code)
    }

    @Test
    fun `escapes a tag containing a quote`() {
        val code = generate(
            """
            Feature: F
              @b"c
              Scenario: S
                Given x
            """.trimIndent(),
        )
        assertTrue(code.contains("""tags = listOf("@b\"c")"""), code)
    }

    @Test
    fun `escapes a doc string media type that would close the literal`() {
        // The media type is unconstrained text after the fence — quotes and all.
        val code = generate(
            """
            Feature: F
              Scenario: S
                Given a payload
                  ${"\"\"\""}json"); error("pwned"); DocString(null, "
                  body
                  ${"\"\"\""}
            """.trimIndent(),
        )

        assertTrue(code.contains("""mediaType = "json\"); error(\"pwned\"); DocString(null, \"""""), code)
        // Unescaped, this would have been a statement rather than a value.
        assertFalse(code.contains("""); error("pwned")"""), code)
    }

    @Test
    fun `neutralises a scenario name that would close the KDoc comment`() {
        // The name is echoed into the function's KDoc, where a bare `*/` would end the block early
        // and leave the rest as source. KotlinPoet already escapes the `*` to `&#42;`, so this is a
        // guard on behaviour we depend on rather than a bug that was fixed here.
        val code = generate(
            """
            Feature: F
              Scenario: ends the comment */ val pwned = error("x") /*
                Given x
            """.trimIndent(),
        )

        assertTrue(code.contains("ends the comment &#42;/ val pwned"), code)
        // The same text as a `name` argument is a string literal, where it is inert.
        assertTrue(code.contains("""name = "ends the comment */ val pwned = error(\"x\") /*""""), code)
    }

    @Test
    fun `generated code is stable across runs`() {
        // The build cache depends on this: identical input must produce byte-identical output.
        val source = "Feature: F\n  Scenario: S\n    Given x"
        assertEquals(generate(source), generate(source))
    }
}
