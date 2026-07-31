package io.github.menjoo.cucumberkmp.core.expression

import io.github.menjoo.cucumberkmp.core.expression.ExpressionNodeType.ALTERNATION_NODE
import io.github.menjoo.cucumberkmp.core.expression.ExpressionNodeType.ALTERNATIVE_NODE
import io.github.menjoo.cucumberkmp.core.expression.ExpressionNodeType.EXPRESSION_NODE
import io.github.menjoo.cucumberkmp.core.expression.ExpressionNodeType.OPTIONAL_NODE
import io.github.menjoo.cucumberkmp.core.expression.ExpressionNodeType.PARAMETER_NODE
import io.github.menjoo.cucumberkmp.core.expression.ExpressionNodeType.TEXT_NODE
import io.github.menjoo.cucumberkmp.core.expression.ExpressionTokenType.ALTERNATION
import io.github.menjoo.cucumberkmp.core.expression.ExpressionTokenType.BEGIN_OPTIONAL
import io.github.menjoo.cucumberkmp.core.expression.ExpressionTokenType.BEGIN_PARAMETER
import io.github.menjoo.cucumberkmp.core.expression.ExpressionTokenType.END_OF_LINE
import io.github.menjoo.cucumberkmp.core.expression.ExpressionTokenType.END_OPTIONAL
import io.github.menjoo.cucumberkmp.core.expression.ExpressionTokenType.END_PARAMETER
import io.github.menjoo.cucumberkmp.core.expression.ExpressionTokenType.START_OF_LINE
import io.github.menjoo.cucumberkmp.core.expression.ExpressionTokenType.TEXT
import io.github.menjoo.cucumberkmp.core.expression.ExpressionTokenType.WHITE_SPACE

/**
 * Parses a Cucumber Expression into an [ExpressionNode] tree.
 *
 * The grammar, as upstream states it:
 *
 * ```text
 * cucumber-expression := ( alternation | optional | parameter | text )*
 * alternation         := (?<=boundary) alternative* ( '/' alternative* )+ (?=boundary)
 * alternative         := optional | parameter | text
 * optional            := '(' option* ')'
 * option              := optional | parameter | text
 * parameter           := '{' name* '}'
 * ```
 *
 * Alternation is boundary-sensitive: a `/` only starts one when preceded by the start of the
 * expression, whitespace or `}`, which is why `and/or` alternates but `a/b` inside a word does too
 * while `http://x` does not split at every slash.
 */
public object CucumberExpressionParser {

    /** @throws CucumberExpressionException if [expression] is malformed. */
    public fun parse(expression: String): ExpressionNode {
        val tokens = ExpressionTokenizer.tokenize(expression)
        return expressionParser(expression, tokens, 0).ast.first()
    }
}

private class ParseResult(val consumed: Int, val ast: List<ExpressionNode>) {
    constructor(consumed: Int) : this(consumed, emptyList())
    constructor(consumed: Int, node: ExpressionNode) : this(consumed, listOf(node))
}

private typealias SubParser = (String, List<ExpressionToken>, Int) -> ParseResult

/** `text := whitespace | ')' | '}' | .` */
private val textParser: SubParser = { expression, tokens, current ->
    val token = tokens[current]
    when (token.type) {
        WHITE_SPACE, TEXT, END_PARAMETER, END_OPTIONAL ->
            ParseResult(1, ExpressionNode(TEXT_NODE, token.start, token.end, token = token.text))
        ALTERNATION -> throw ExpressionErrors.alternationNotAllowedInOptional(expression, token)
        else -> ParseResult(0)
    }
}

/** `name := whitespace | .` — a parameter type name may not contain the structural characters. */
private val nameParser: SubParser = { expression, tokens, current ->
    val token = tokens[current]
    when (token.type) {
        WHITE_SPACE, TEXT ->
            ParseResult(1, ExpressionNode(TEXT_NODE, token.start, token.end, token = token.text))
        BEGIN_OPTIONAL, END_OPTIONAL, BEGIN_PARAMETER, END_PARAMETER, ALTERNATION ->
            throw ExpressionErrors.invalidParameterTypeName(token, expression)
        else -> ParseResult(0)
    }
}

/** `parameter := '{' name* '}'` */
private val parameterParser: SubParser =
    parseBetween(PARAMETER_NODE, BEGIN_PARAMETER, END_PARAMETER) { listOf(nameParser) }

/**
 * `optional := '(' option* ')'`
 *
 * An optional may nest, so its child parsers include itself. The list is supplied lazily to break
 * the initialisation cycle — upstream ties the same knot with a static initialiser and a mutable
 * list, but a lazy provider makes the order deterministic rather than file-order dependent.
 */
private val optionalChildParsers: List<SubParser> by lazy {
    listOf(optionalParser, parameterParser, textParser)
}
private val optionalParser: SubParser =
    parseBetween(OPTIONAL_NODE, BEGIN_OPTIONAL, END_OPTIONAL) { optionalChildParsers }

private val alternativeSeparator: SubParser = { _, tokens, current ->
    if (!lookingAt(tokens, current, ALTERNATION)) {
        ParseResult(0)
    } else {
        val token = tokens[current]
        ParseResult(1, ExpressionNode(ALTERNATIVE_NODE, token.start, token.end, token = token.text))
    }
}

private val alternativeParsers: List<SubParser> =
    listOf(alternativeSeparator, optionalParser, parameterParser, textParser)

private val alternationParser: SubParser = { expression, tokens, current ->
    // Left boundary: only start of line, whitespace or a closing '}' may precede an alternation.
    if (!lookingAtAny(tokens, current - 1, START_OF_LINE, WHITE_SPACE, END_PARAMETER)) {
        ParseResult(0)
    } else {
        val result = parseTokensUntil(
            expression,
            alternativeParsers,
            tokens,
            current,
            listOf(WHITE_SPACE, END_OF_LINE, BEGIN_PARAMETER),
        )
        val subCurrent = current + result.consumed
        if (result.ast.none { it.type == ALTERNATIVE_NODE }) {
            ParseResult(0)
        } else {
            val start = tokens[current].start
            val end = tokens[subCurrent].start
            // Deliberately does not consume the right-hand boundary token.
            ParseResult(
                result.consumed,
                ExpressionNode(
                    ALTERNATION_NODE,
                    start,
                    end,
                    nodes = splitAlternatives(start, end, result.ast),
                ),
            )
        }
    }
}

private val expressionParser: SubParser = parseBetween(EXPRESSION_NODE, START_OF_LINE, END_OF_LINE) {
    listOf(alternationParser, optionalParser, parameterParser, textParser)
}

private fun parseBetween(
    type: ExpressionNodeType,
    beginToken: ExpressionTokenType,
    endToken: ExpressionTokenType,
    parsers: () -> List<SubParser>,
): SubParser = { expression, tokens, current ->
    if (!lookingAt(tokens, current, beginToken)) {
        ParseResult(0)
    } else {
        var subCurrent = current + 1
        val result = parseTokensUntil(expression, parsers(), tokens, subCurrent, listOf(endToken, END_OF_LINE))
        subCurrent += result.consumed

        if (!lookingAt(tokens, subCurrent, endToken)) {
            throw ExpressionErrors.missingEndToken(expression, beginToken, endToken, tokens[current])
        }
        ParseResult(
            subCurrent + 1 - current,
            ExpressionNode(type, tokens[current].start, tokens[subCurrent].end, nodes = result.ast),
        )
    }
}

private fun parseTokensUntil(
    expression: String,
    parsers: List<SubParser>,
    tokens: List<ExpressionToken>,
    startAt: Int,
    endTokens: List<ExpressionTokenType>,
): ParseResult {
    var current = startAt
    val ast = mutableListOf<ExpressionNode>()
    while (current < tokens.size) {
        if (lookingAtAny(tokens, current, *endTokens.toTypedArray())) break
        val result = parseToken(expression, parsers, tokens, current)
        current += result.consumed
        ast += result.ast
    }
    return ParseResult(current - startAt, ast)
}

private fun parseToken(
    expression: String,
    parsers: List<SubParser>,
    tokens: List<ExpressionToken>,
    startAt: Int,
): ParseResult {
    for (parser in parsers) {
        val result = parser(expression, tokens, startAt)
        if (result.consumed != 0) return result
    }
    // Unreachable while the parser lists stay exhaustive; guards against an infinite loop.
    error("No eligible parsers for $tokens")
}

private fun lookingAtAny(
    tokens: List<ExpressionToken>,
    at: Int,
    vararg tokenTypes: ExpressionTokenType,
): Boolean = tokenTypes.any { lookingAt(tokens, at, it) }

private fun lookingAt(tokens: List<ExpressionToken>, at: Int, type: ExpressionTokenType): Boolean =
    when {
        at < 0 -> type == START_OF_LINE
        at >= tokens.size -> type == END_OF_LINE
        else -> tokens[at].type == type
    }

/**
 * Regroups a flat run of nodes and `/` separators into one `ALTERNATIVE_NODE` per alternative.
 *
 * Each alternative spans from the previous separator's end to the next separator's start, so the
 * separators themselves fall outside every alternative.
 */
private fun splitAlternatives(
    start: Int,
    end: Int,
    alternation: List<ExpressionNode>,
): List<ExpressionNode> {
    val separators = mutableListOf<ExpressionNode>()
    val alternatives = mutableListOf<List<ExpressionNode>>()
    var alternative = mutableListOf<ExpressionNode>()

    for (node in alternation) {
        if (node.type == ALTERNATIVE_NODE) {
            separators += node
            alternatives += alternative
            alternative = mutableListOf()
        } else {
            alternative += node
        }
    }
    alternatives += alternative

    return alternatives.mapIndexed { index, nodes ->
        when {
            index == 0 -> ExpressionNode(ALTERNATIVE_NODE, start, separators[index].start, nodes = nodes)
            index == alternatives.lastIndex ->
                ExpressionNode(ALTERNATIVE_NODE, separators[index - 1].end, end, nodes = nodes)
            else -> ExpressionNode(
                ALTERNATIVE_NODE,
                separators[index - 1].end,
                separators[index].start,
                nodes = nodes,
            )
        }
    }
}
