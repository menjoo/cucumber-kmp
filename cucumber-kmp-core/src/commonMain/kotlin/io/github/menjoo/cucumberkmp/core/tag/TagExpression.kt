package io.github.menjoo.cucumberkmp.core.tag

/**
 * A boolean expression over tags, such as `@smoke and not @wip`.
 *
 * Used to select which scenarios run. See ARCHITECTURE.md §9 for how selection is applied at build
 * time versus run time.
 *
 * ```kotlin
 * val expression = TagExpressionParser.parse("@smoke and not @wip")
 * expression.evaluate(listOf("@smoke")) // true
 * ```
 *
 * See https://github.com/cucumber/tag-expressions
 */
public sealed interface TagExpression {

    /** True when [variables] — the tags in scope — satisfy this expression. */
    public fun evaluate(variables: Collection<String>): Boolean
}

/** A bare tag. Matches when it is present. */
internal class TagLiteral(val value: String) : TagExpression {

    override fun evaluate(variables: Collection<String>): Boolean = value in variables

    /**
     * Re-escapes the literal so the rendered expression parses back to itself.
     *
     * Order matters: backslashes first, otherwise the backslashes introduced when escaping `(`,
     * `)` and whitespace would themselves be doubled.
     */
    override fun toString(): String =
        value
            .replace("\\", "\\\\")
            .replace("(", "\\(")
            .replace(")", "\\)")
            .let { escaped -> escaped.map { if (it.isWhitespace()) "\\ " else it.toString() }.joinToString("") }
}

internal class TagAnd(val left: TagExpression, val right: TagExpression) : TagExpression {
    override fun evaluate(variables: Collection<String>): Boolean =
        left.evaluate(variables) && right.evaluate(variables)

    override fun toString(): String = "( $left and $right )"
}

internal class TagOr(val left: TagExpression, val right: TagExpression) : TagExpression {
    override fun evaluate(variables: Collection<String>): Boolean =
        left.evaluate(variables) || right.evaluate(variables)

    override fun toString(): String = "( $left or $right )"
}

internal class TagNot(val expression: TagExpression) : TagExpression {
    override fun evaluate(variables: Collection<String>): Boolean = !expression.evaluate(variables)

    // Binary operators already render their own parentheses, so adding another pair would
    // produce `not ( ( a and b ) )`.
    override fun toString(): String =
        if (expression is TagAnd || expression is TagOr) "not $expression" else "not ( $expression )"
}

/** The empty expression: selects everything. */
internal object TagTrue : TagExpression {
    override fun evaluate(variables: Collection<String>): Boolean = true

    override fun toString(): String = ""
}

/** Thrown when a tag expression cannot be parsed. */
public class TagExpressionException internal constructor(message: String) : RuntimeException(message)
