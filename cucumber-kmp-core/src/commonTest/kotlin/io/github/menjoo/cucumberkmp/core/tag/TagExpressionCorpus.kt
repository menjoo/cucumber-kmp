// GENERATED FILE — DO NOT EDIT BY HAND.
//
// Regenerate with:  python3 tools/update-tag-expression-corpus.py
// Source: https://github.com/cucumber/tag-expressions @ 3046b904a97b088855029abf5b0f2c935024d4c6
// 23 parsing, 26 evaluation, 15 error cases.

package io.github.menjoo.cucumberkmp.core.tag

internal data class TagParsingCase(val expression: String, val formatted: String)

internal data class TagEvaluationCase(
    val expression: String,
    val variables: List<String>,
    val result: Boolean,
)

internal data class TagErrorCase(val expression: String, val error: String)

internal val TAG_PARSING_CORPUS: List<TagParsingCase> = listOf(
    TagParsingCase("", ""),
    TagParsingCase("a and b", "( a and b )"),
    TagParsingCase("a or b", "( a or b )"),
    TagParsingCase("not a", "not ( a )"),
    TagParsingCase("a and b and c", "( ( a and b ) and c )"),
    TagParsingCase("( a and b ) or ( c and d )", "( ( a and b ) or ( c and d ) )"),
    TagParsingCase("not a or b and not c or not d or e and f", "( ( ( not ( a ) or ( b and not ( c ) ) ) or not ( d ) ) or ( e and f ) )"),
    TagParsingCase("not a\\(\\) or b and not c or not d or e and f", "( ( ( not ( a\\(\\) ) or ( b and not ( c ) ) ) or not ( d ) ) or ( e and f ) )"),
    TagParsingCase("not (a and b)", "not ( a and b )"),
    TagParsingCase("not (a or b)", "not ( a or b )"),
    TagParsingCase("not (a and b) and c or not (d or f)", "( ( not ( a and b ) and c ) or not ( d or f ) )"),
    TagParsingCase("a\\\\ and b", "( a\\\\ and b )"),
    TagParsingCase("\\\\a and b", "( \\\\a and b )"),
    TagParsingCase("a\\\\ and b", "( a\\\\ and b )"),
    TagParsingCase("a and b\\\\", "( a and b\\\\ )"),
    TagParsingCase("( a and b\\\\\\\\)", "( a and b\\\\\\\\ )"),
    TagParsingCase("a\\\\\\( and b\\\\\\)", "( a\\\\\\( and b\\\\\\) )"),
    TagParsingCase("(a and \\\\b)", "( a and \\\\b )"),
    TagParsingCase("x or(y) ", "( x or y )"),
    TagParsingCase("x\\(1\\) or(y\\(2\\))", "( x\\(1\\) or y\\(2\\) )"),
    TagParsingCase("\\\\x or y\\\\ or z\\\\", "( ( \\\\x or y\\\\ ) or z\\\\ )"),
    TagParsingCase("x\\\\ or(y\\\\\\)) or(z\\\\)", "( ( x\\\\ or y\\\\\\) ) or z\\\\ )"),
    TagParsingCase("x\\  or y", "( x\\  or y )"),
)

internal val TAG_EVALUATION_CORPUS: List<TagEvaluationCase> = listOf(
    TagEvaluationCase("", listOf(), true),
    TagEvaluationCase("", listOf("x"), true),
    TagEvaluationCase("", listOf("y"), true),
    TagEvaluationCase("not x", listOf("x"), false),
    TagEvaluationCase("not x", listOf("y"), true),
    TagEvaluationCase("x and y", listOf("x", "y"), true),
    TagEvaluationCase("x and y", listOf("x"), false),
    TagEvaluationCase("x and y", listOf("y"), false),
    TagEvaluationCase("x or y", listOf(), false),
    TagEvaluationCase("x or y", listOf("x", "y"), true),
    TagEvaluationCase("x or y", listOf("x"), true),
    TagEvaluationCase("x or y", listOf("y"), true),
    TagEvaluationCase("x\\(1\\) or y\\(2\\)", listOf("x(1)"), true),
    TagEvaluationCase("x\\(1\\) or y\\(2\\)", listOf("y(2)"), true),
    TagEvaluationCase("x\\\\ or y\\\\\\) or z\\\\", listOf("x\\"), true),
    TagEvaluationCase("x\\\\ or y\\\\\\) or z\\\\", listOf("y\\)"), true),
    TagEvaluationCase("x\\\\ or y\\\\\\) or z\\\\", listOf("z\\"), true),
    TagEvaluationCase("x\\\\ or y\\\\\\) or z\\\\", listOf("x"), false),
    TagEvaluationCase("x\\\\ or y\\\\\\) or z\\\\", listOf("y)"), false),
    TagEvaluationCase("x\\\\ or y\\\\\\) or z\\\\", listOf("z"), false),
    TagEvaluationCase("\\\\x or y\\\\ or z\\\\", listOf("\\x"), true),
    TagEvaluationCase("\\\\x or y\\\\ or z\\\\", listOf("y\\"), true),
    TagEvaluationCase("\\\\x or y\\\\ or z\\\\", listOf("z\\"), true),
    TagEvaluationCase("\\\\x or y\\\\ or z\\\\", listOf("x"), false),
    TagEvaluationCase("\\\\x or y\\\\ or z\\\\", listOf("y"), false),
    TagEvaluationCase("\\\\x or y\\\\ or z\\\\", listOf("z"), false),
)

internal val TAG_ERROR_CORPUS: List<TagErrorCase> = listOf(
    TagErrorCase("@a @b or", "Tag expression \"@a @b or\" could not be parsed because of syntax error: Expected operator."),
    TagErrorCase("@a and (@b not)", "Tag expression \"@a and (@b not)\" could not be parsed because of syntax error: Expected operator."),
    TagErrorCase("@a and (@b @c) or", "Tag expression \"@a and (@b @c) or\" could not be parsed because of syntax error: Expected operator."),
    TagErrorCase("@a and or", "Tag expression \"@a and or\" could not be parsed because of syntax error: Expected operand."),
    TagErrorCase("or or", "Tag expression \"or or\" could not be parsed because of syntax error: Expected operand."),
    TagErrorCase("a and or", "Tag expression \"a and or\" could not be parsed because of syntax error: Expected operand."),
    TagErrorCase("a b", "Tag expression \"a b\" could not be parsed because of syntax error: Expected operator."),
    TagErrorCase("( a and b ) )", "Tag expression \"( a and b ) )\" could not be parsed because of syntax error: Unmatched )."),
    TagErrorCase("( ( a and b )", "Tag expression \"( ( a and b )\" could not be parsed because of syntax error: Unmatched (."),
    TagErrorCase("x or \\y or z", "Tag expression \"x or \\y or z\" could not be parsed because of syntax error: Illegal escape before \"y\"."),
    TagErrorCase("x\\ or y", "Tag expression \"x\\ or y\" could not be parsed because of syntax error: Expected operator."),
    TagErrorCase("a and", "Tag expression \"a and\" could not be parsed because of syntax error: Expected operand."),
    TagErrorCase("a or", "Tag expression \"a or\" could not be parsed because of syntax error: Expected operand."),
    TagErrorCase("not", "Tag expression \"not\" could not be parsed because of syntax error: Expected operand."),
    TagErrorCase("a and not", "Tag expression \"a and not\" could not be parsed because of syntax error: Expected operand."),
)

