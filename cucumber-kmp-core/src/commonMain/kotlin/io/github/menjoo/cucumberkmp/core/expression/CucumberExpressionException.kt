package io.github.menjoo.cucumberkmp.core.expression

/**
 * Thrown when a Cucumber Expression is malformed or refers to an unknown parameter type.
 *
 * Messages are reproduced verbatim from upstream, including the caret that points at the offending
 * column, because these strings are what users see and search for:
 *
 * ```text
 * This Cucumber Expression has a problem at column 13:
 *
 * three( brown/black) mice
 *             ^
 * An alternation can not be used inside an optional.
 * If you did not mean to use an alternation you can use '\/' to escape the '/'. …
 * ```
 */
public open class CucumberExpressionException internal constructor(
    message: String,
) : RuntimeException(message)

/** Thrown when an expression names a parameter type that was never registered. */
public class UndefinedParameterTypeException internal constructor(
    message: String,
    public val parameterTypeName: String,
) : CucumberExpressionException(message)

internal object ExpressionErrors {

    fun message(index: Int, expression: String, pointer: String, problem: String, solution: String): String =
        "This Cucumber Expression has a problem at column ${index + 1}:\n" +
            "\n" +
            expression + "\n" +
            pointer + "\n" +
            problem + ".\n" +
            solution

    private fun pointAt(index: Int): String = " ".repeat(index) + "^"

    /** A caret under a single column, or a `^---^` span when the node covers several. */
    private fun pointAt(start: Int, end: Int): String = buildString {
        append(pointAt(start))
        if (start + 1 < end) {
            repeat(end - start - 2) { append('-') }
            append('^')
        }
    }

    fun missingEndToken(
        expression: String,
        beginToken: ExpressionTokenType,
        endToken: ExpressionTokenType,
        current: ExpressionToken,
    ): CucumberExpressionException = CucumberExpressionException(
        message(
            current.start,
            expression,
            pointAt(current.start, current.end),
            "The '${beginToken.requireSymbol()}' does not have a matching '${endToken.requireSymbol()}'",
            "If you did not intend to use ${beginToken.requirePurpose()} you can use " +
                "'\\${beginToken.requireSymbol()}' to escape the ${beginToken.requirePurpose()}",
        ),
    )

    fun alternationNotAllowedInOptional(
        expression: String,
        current: ExpressionToken,
    ): CucumberExpressionException = CucumberExpressionException(
        message(
            current.start,
            expression,
            pointAt(current.start, current.end),
            "An alternation can not be used inside an optional",
            "If you did not mean to use an alternation you can use '\\/' to escape the '/'. " +
                "Otherwise rephrase your expression or consider using a regular expression instead.",
        ),
    )

    fun theEndOfLineCanNotBeEscaped(expression: String): CucumberExpressionException {
        val index = expression.codePointCount() - 1
        return CucumberExpressionException(
            message(
                index,
                expression,
                pointAt(index),
                "The end of line can not be escaped",
                "You can use '\\\\' to escape the '\\'",
            ),
        )
    }

    fun cantEscape(expression: String, index: Int): CucumberExpressionException =
        CucumberExpressionException(
            message(
                index,
                expression,
                pointAt(index),
                "Only the characters '{', '}', '(', ')', '\\', '/' and whitespace can be escaped",
                "If you did mean to use an '\\' you can use '\\\\' to escape it",
            ),
        )

    fun invalidParameterTypeName(
        token: ExpressionToken,
        expression: String,
    ): CucumberExpressionException = CucumberExpressionException(
        message(
            token.start,
            expression,
            pointAt(token.start, token.end),
            "Parameter names may not contain '{', '}', '(', ')', '\\' or '/'",
            // Deliberately vague upstream, because it must not name any one language's syntax.
            "Did you mean to use a regular expression?",
        ),
    )

    fun alternativeMayNotBeEmpty(node: ExpressionNode, expression: String): CucumberExpressionException =
        nodeProblem(
            node,
            expression,
            "Alternative may not be empty",
            "If you did not mean to use an alternative you can use '\\/' to escape the '/'",
        )

    fun alternativeMayNotExclusivelyContainOptionals(
        node: ExpressionNode,
        expression: String,
    ): CucumberExpressionException = nodeProblem(
        node,
        expression,
        "An alternative may not exclusively contain optionals",
        "If you did not mean to use an optional you can use '\\(' to escape the '('",
    )

    fun optionalMayNotBeEmpty(node: ExpressionNode, expression: String): CucumberExpressionException =
        nodeProblem(
            node,
            expression,
            "An optional must contain some text",
            "If you did not mean to use an optional you can use '\\(' to escape the '('",
        )

    fun parameterIsNotAllowedInOptional(
        node: ExpressionNode,
        expression: String,
    ): CucumberExpressionException = nodeProblem(
        node,
        expression,
        "An optional may not contain a parameter type",
        "If you did not mean to use an parameter type you can use '\\{' to escape the '{'",
    )

    fun optionalIsNotAllowedInOptional(
        node: ExpressionNode,
        expression: String,
    ): CucumberExpressionException = nodeProblem(
        node,
        expression,
        "An optional may not contain an other optional",
        "If you did not mean to use an optional type you can use '\\(' to escape the '('. " +
            "For more complicated expressions consider using a regular expression instead.",
    )

    fun undefinedParameterType(
        node: ExpressionNode,
        expression: String,
        name: String,
    ): UndefinedParameterTypeException = UndefinedParameterTypeException(
        message(
            node.start,
            expression,
            pointAt(node.start, node.end),
            "Undefined parameter type '$name'",
            "Please register a ParameterType for '$name'",
        ),
        name,
    )

    private fun nodeProblem(
        node: ExpressionNode,
        expression: String,
        problem: String,
        solution: String,
    ): CucumberExpressionException = CucumberExpressionException(
        message(node.start, expression, pointAt(node.start, node.end), problem, solution),
    )
}
