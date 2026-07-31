package io.github.menjoo.cucumberkmp.core.expression

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Tests for behaviour upstream's fixtures do not reach.
 *
 * The YAML corpus can only express built-in parameter types — it has no way to declare a custom
 * one — so everything about [ParameterTypeRegistry.defineParameterType] and regex-based type
 * inference is unpinned by it and covered here instead.
 */
class CucumberExpressionTest {

    private val registry = ParameterTypeRegistry()

    private fun match(expression: String, text: String): List<Argument>? =
        CucumberExpression(expression, registry).match(text)

    @Test
    fun convertsBuiltInTypes() {
        assertEquals(3, match("{int} cukes", "3 cukes")?.single()?.value)
        assertEquals("blind", match("three {string} mice", "three \"blind\" mice")?.single()?.value)
        assertEquals("banana", match("a {word}", "a banana")?.single()?.value)
    }

    @Test
    fun keepsTheRawGroupAlongsideTheConvertedValue() {
        // Failure messages need to show what was captured before conversion.
        val argument = assertNotNull(match("{int} cukes", "42 cukes")?.single())
        assertEquals(42, argument.value)
        assertEquals("42", argument.group)
        assertEquals("int", argument.parameterTypeName)
    }

    @Test
    fun supportsCustomParameterTypes() {
        registry.defineParameterType(
            ParameterType("planet", listOf("Mercury|Venus|Earth")) { it.uppercase() },
        )

        assertEquals("EARTH", match("I live on {planet}", "I live on Earth")?.single()?.value)
        assertNull(match("I live on {planet}", "I live on Pluto"))
    }

    @Test
    fun customParameterTypeMayContainItsOwnCaptureGroups() {
        // The parameter's own groups shift regex indices; the parameter must still receive the
        // whole text it matched, not the first inner group.
        registry.defineParameterType(
            ParameterType("pair", listOf("(\\d+)-(\\d+)")) { it },
        )

        val arguments = assertNotNull(match("range {pair} inclusive", "range 3-9 inclusive"))
        assertEquals("3-9", arguments.single().value)
    }

    @Test
    fun tracksGroupIndicesAcrossSeveralParameters() {
        // {string} contributes nested groups of its own, so a following parameter's index only
        // lands correctly if inner groups are counted.
        val arguments = assertNotNull(
            match("{string} has {int} letters", "\"word\" has 4 letters"),
        )
        assertEquals(listOf<Any?>("word", 4), arguments.map { it.value })
    }

    @Test
    fun rejectsDuplicateParameterTypeNames() {
        val failure = assertFailsWith<IllegalArgumentException> {
            registry.defineParameterType(ParameterType("int", listOf("\\d+")) { it })
        }
        assertTrue(assertNotNull(failure.message).contains("already a parameter type"))
    }

    @Test
    fun reportsUndefinedParameterTypeWithItsName() {
        val failure = assertFailsWith<UndefinedParameterTypeException> {
            CucumberExpression("I have {gizmo}", registry)
        }
        assertEquals("gizmo", failure.parameterTypeName)
        assertTrue(assertNotNull(failure.message).contains("Please register a ParameterType"))
    }

    @Test
    fun failsAtConstructionRatherThanAtMatchTime() {
        // The expression is compiled eagerly so a typo surfaces where it was written.
        assertFailsWith<CucumberExpressionException> { CucumberExpression("three (blind mice", registry) }
    }

    @Test
    fun anonymousParameterCapturesAnything() {
        assertEquals("anything at all", match("I see {}", "I see anything at all")?.single()?.value)
    }

    @Test
    fun optionalAndAlternationCompileToNonCapturingGroups() {
        // Otherwise they would consume parameter indices.
        val expression = CucumberExpression("I have {int} cucumber(s) in my belly/stomach", registry)
        assertEquals("^I have ((?:-?\\d+)|(?:\\d+)) cucumber(?:s)? in my (?:belly|stomach)$", expression.regexp.pattern)
        assertEquals(1, assertNotNull(expression.match("I have 1 cucumber in my belly")).size)
    }

    @Test
    fun regularExpressionInfersTypeFromGroupSource() {
        val expression = RegularExpression(Regex("^I have (\\d+) cukes$"), registry)
        val argument = assertNotNull(expression.match("I have 7 cukes")).single()

        // (\d+) is one of int's regexps, so the value arrives converted rather than as text.
        assertEquals(7, argument.value)
    }

    @Test
    fun regularExpressionFallsBackToTextForUnknownGroups() {
        val expression = RegularExpression(Regex("^I have ([a-z]+) cukes$"), registry)
        assertEquals("green", assertNotNull(expression.match("I have green cukes")).single().value)
    }

    @Test
    fun regularExpressionIgnoresNestedGroups() {
        // Only top-level groups are parameters; ((a)(b)) is one parameter, not three.
        val expression = RegularExpression(Regex("^I have ((a)(b)) cukes$"), registry)
        val arguments = assertNotNull(expression.match("I have ab cukes"))

        assertEquals(1, arguments.size)
        assertEquals("ab", arguments.single().value)
    }

    @Test
    fun regularExpressionReportsNullForAGroupThatDidNotParticipate() {
        val expression = RegularExpression(Regex("^a (b )?c$"), registry)
        val argument = assertNotNull(expression.match("a c")).single()

        assertNull(argument.value)
        assertNull(argument.group)
    }
}
