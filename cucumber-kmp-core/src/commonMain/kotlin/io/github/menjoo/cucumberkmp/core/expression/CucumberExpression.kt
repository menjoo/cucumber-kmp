package io.github.menjoo.cucumberkmp.core.expression

/**
 * A Cucumber Expression: the `I have {int} cucumbers` syntax, compiled to a regular expression.
 *
 * ```kotlin
 * val expression = CucumberExpression("I have {int} cucumber(s)", ParameterTypeRegistry())
 * expression.match("I have 3 cucumbers")?.single()?.value // 3
 * ```
 *
 * Compilation is eager: a malformed expression or an unregistered parameter type throws from the
 * constructor, so the failure surfaces where the expression is defined rather than at match time.
 *
 * See https://github.com/cucumber/cucumber-expressions
 */
public class CucumberExpression(
    override val source: String,
    private val parameterTypeRegistry: ParameterTypeRegistry,
) : Expression {

    private val parameterTypes = mutableListOf<ParameterType<*>>()

    /**
     * The capture-group index for each parameter, in order.
     *
     * Tracked while generating rather than recovered afterwards, because a parameter type's own
     * regexp may contain groups that shift every index after it.
     */
    private val parameterGroups = mutableListOf<Int>()

    private var groupCount = 0

    private val pattern: String = rewriteToRegex(CucumberExpressionParser.parse(source))

    override val regexp: Regex = Regex(pattern)

    override fun match(text: String): List<Argument>? {
        val match = regexp.matchEntire(text) ?: return null
        return parameterTypes.mapIndexed { index, parameterType ->
            val group = match.groups[parameterGroups[index]]?.value
            Argument(
                value = parameterType.convert(group),
                group = group,
                parameterTypeName = parameterType.name,
            )
        }
    }

    private fun rewriteToRegex(node: ExpressionNode): String = when (node.type) {
        ExpressionNodeType.TEXT_NODE -> escapeRegex(node.text)
        ExpressionNodeType.OPTIONAL_NODE -> rewriteOptional(node)
        ExpressionNodeType.ALTERNATION_NODE -> rewriteAlternation(node)
        ExpressionNodeType.ALTERNATIVE_NODE -> rewriteAlternative(node)
        ExpressionNodeType.PARAMETER_NODE -> rewriteParameter(node)
        ExpressionNodeType.EXPRESSION_NODE ->
            node.requireNodes().joinToString(separator = "", prefix = "^", postfix = "$") {
                rewriteToRegex(it)
            }
    }

    private fun rewriteOptional(node: ExpressionNode): String {
        assertNoNodeOfType(node, ExpressionNodeType.PARAMETER_NODE) {
            ExpressionErrors.parameterIsNotAllowedInOptional(it, source)
        }
        assertNoNodeOfType(node, ExpressionNodeType.OPTIONAL_NODE) {
            ExpressionErrors.optionalIsNotAllowedInOptional(it, source)
        }
        assertContainsText(node) { ExpressionErrors.optionalMayNotBeEmpty(it, source) }
        return node.requireNodes().joinToString(separator = "", prefix = "(?:", postfix = ")?") {
            rewriteToRegex(it)
        }
    }

    private fun rewriteAlternation(node: ExpressionNode): String {
        for (alternative in node.requireNodes()) {
            if (alternative.requireNodes().isEmpty()) {
                throw ExpressionErrors.alternativeMayNotBeEmpty(alternative, source)
            }
            assertContainsText(alternative) {
                ExpressionErrors.alternativeMayNotExclusivelyContainOptionals(it, source)
            }
        }
        return node.requireNodes().joinToString(separator = "|", prefix = "(?:", postfix = ")") {
            rewriteToRegex(it)
        }
    }

    private fun rewriteAlternative(node: ExpressionNode): String =
        node.requireNodes().joinToString(separator = "") { rewriteToRegex(it) }

    private fun rewriteParameter(node: ExpressionNode): String {
        val name = node.text
        val parameterType = parameterTypeRegistry.lookupByTypeName(name)
            ?: throw ExpressionErrors.undefinedParameterType(node, source, name)

        parameterTypes += parameterType

        groupCount++
        parameterGroups += groupCount

        val regexps = parameterType.regexps
        val body = if (regexps.size == 1) {
            regexps.single()
        } else {
            regexps.joinToString(separator = ")|(?:", prefix = "(?:", postfix = ")")
        }
        // A parameter's own regexp may capture; those groups sit inside ours and shift later indices.
        groupCount += countCapturingGroups(body)
        return "($body)"
    }

    private inline fun assertContainsText(
        node: ExpressionNode,
        createException: (ExpressionNode) -> CucumberExpressionException,
    ) {
        if (node.requireNodes().none { it.type == ExpressionNodeType.TEXT_NODE }) {
            throw createException(node)
        }
    }

    private inline fun assertNoNodeOfType(
        node: ExpressionNode,
        type: ExpressionNodeType,
        createException: (ExpressionNode) -> CucumberExpressionException,
    ) {
        node.requireNodes().firstOrNull { it.type == type }?.let { throw createException(it) }
    }

    override fun toString(): String = "CucumberExpression($source)"
}
