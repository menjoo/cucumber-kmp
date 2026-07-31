package io.github.menjoo.cucumberkmp.core.tag

/**
 * Parses tag expressions using the shunting-yard algorithm.
 *
 * Precedence, loosest first: `or`, `and`, `not`. `and` and `or` are left-associative, `not` is a
 * right-associative unary operator. Parentheses group.
 *
 * A tag containing `(`, `)`, a backslash or whitespace must escape it: `@x\(1\)`.
 *
 * ```kotlin
 * TagExpressionParser.parse("not @wip and (@smoke or @fast)")
 * ```
 */
public object TagExpressionParser {

    private const val ESCAPING_CHARACTER = '\\'

    /** @throws TagExpressionException if [infix] is not a well-formed tag expression. */
    public fun parse(infix: String): TagExpression {
        val tokens = tokenize(infix)
        if (tokens.isEmpty()) return TagTrue

        val operators = ArrayDeque<String>()
        val expressions = ArrayDeque<TagExpression>()
        var expected = TokenType.OPERAND

        for (token in tokens) {
            when {
                isUnary(token) -> {
                    check(infix, expected, TokenType.OPERAND)
                    operators.addLast(token)
                    expected = TokenType.OPERAND
                }

                isBinary(token) -> {
                    check(infix, expected, TokenType.OPERATOR)
                    while (operators.isNotEmpty() && shouldPopBefore(token, operators.last())) {
                        pushExpression(infix, popOrFail(infix, operators), expressions)
                    }
                    operators.addLast(token)
                    expected = TokenType.OPERAND
                }

                token == "(" -> {
                    check(infix, expected, TokenType.OPERAND)
                    operators.addLast(token)
                    expected = TokenType.OPERAND
                }

                token == ")" -> {
                    check(infix, expected, TokenType.OPERATOR)
                    while (operators.isNotEmpty() && operators.last() != "(") {
                        pushExpression(infix, popOrFail(infix, operators), expressions)
                    }
                    if (operators.isEmpty()) throw syntaxError(infix, "Unmatched )")
                    if (operators.last() == "(") popOrFail(infix, operators)
                    expected = TokenType.OPERATOR
                }

                else -> {
                    check(infix, expected, TokenType.OPERAND)
                    pushExpression(infix, token, expressions)
                    expected = TokenType.OPERATOR
                }
            }
        }

        while (operators.isNotEmpty()) {
            if (operators.last() == "(") throw syntaxError(infix, "Unmatched (")
            pushExpression(infix, popOrFail(infix, operators), expressions)
        }

        return popOrFail(infix, expressions)
    }

    /**
     * Splits on whitespace and parentheses, honouring escapes.
     *
     * Parentheses are their own tokens even when adjacent to a tag, so `x or(y)` tokenizes the
     * same as `x or ( y )`.
     */
    private fun tokenize(expression: String): List<String> {
        val tokens = mutableListOf<String>()
        val token = StringBuilder()
        var escaping = false

        for (character in expression) {
            when {
                escaping -> {
                    if (character == '(' || character == ')' ||
                        character == ESCAPING_CHARACTER || character.isWhitespace()
                    ) {
                        token.append(character)
                        escaping = false
                    } else {
                        throw syntaxError(expression, "Illegal escape before \"$character\"")
                    }
                }

                character == ESCAPING_CHARACTER -> escaping = true

                character == '(' || character == ')' || character.isWhitespace() -> {
                    if (token.isNotEmpty()) {
                        tokens += token.toString()
                        token.clear()
                    }
                    if (!character.isWhitespace()) tokens += character.toString()
                }

                else -> token.append(character)
            }
        }
        if (token.isNotEmpty()) tokens += token.toString()
        return tokens
    }

    /**
     * Whether the operator already on the stack binds at least as tightly as [token].
     *
     * Left-associative operators pop on equal precedence, right-associative ones only on strictly
     * greater, which is what makes `not not a` nest rather than fail.
     */
    private fun shouldPopBefore(token: String, stackOperator: String): Boolean {
        if (!isOperator(stackOperator)) return false
        return if (isLeftAssociative(token)) {
            precedence(token) <= precedence(stackOperator)
        } else {
            precedence(token) < precedence(stackOperator)
        }
    }

    private fun precedence(token: String): Int = when (token) {
        "(" -> -2
        ")" -> -1
        "or" -> 0
        "and" -> 1
        "not" -> 2
        else -> throw IllegalArgumentException(token)
    }

    private fun isLeftAssociative(token: String): Boolean = token == "or" || token == "and"

    private fun isUnary(token: String): Boolean = token == "not"

    private fun isBinary(token: String): Boolean = token == "or" || token == "and"

    private fun isOperator(token: String): Boolean = isBinary(token) || isUnary(token)

    private fun pushExpression(
        infix: String,
        token: String,
        expressions: ArrayDeque<TagExpression>,
    ) {
        val expression = when (token) {
            "and" -> {
                // Right operand is on top of the stack, so it must come off first.
                val right = popOrFail(infix, expressions)
                val left = popOrFail(infix, expressions)
                TagAnd(left, right)
            }

            "or" -> {
                val right = popOrFail(infix, expressions)
                val left = popOrFail(infix, expressions)
                TagOr(left, right)
            }

            "not" -> TagNot(popOrFail(infix, expressions))
            else -> TagLiteral(token)
        }
        expressions.addLast(expression)
    }

    private fun <T> popOrFail(infix: String, stack: ArrayDeque<T>): T =
        stack.removeLastOrNull() ?: throw syntaxError(infix, "Expected operand")

    private fun check(infix: String, expected: TokenType, actual: TokenType) {
        if (expected != actual) {
            throw syntaxError(infix, "Expected ${expected.name.lowercase()}")
        }
    }

    private fun syntaxError(infix: String, problem: String): TagExpressionException =
        TagExpressionException(
            "Tag expression \"$infix\" could not be parsed because of syntax error: $problem.",
        )

    private enum class TokenType { OPERAND, OPERATOR }
}
