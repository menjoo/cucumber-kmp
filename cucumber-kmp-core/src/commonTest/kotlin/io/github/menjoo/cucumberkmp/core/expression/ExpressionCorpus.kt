// GENERATED FILE — DO NOT EDIT BY HAND.
//
// Regenerate with:  python3 tools/update-expression-corpus.py
// Source: https://github.com/cucumber/cucumber-expressions @ 7e7c482cbeb79a72580049c747aa23392b6fc20a
// 16 tokenizer, 28 parser, 8 transformation, 65 matching, 3 regex

package io.github.menjoo.cucumberkmp.core.expression

/** A fixture that pins one behaviour of the expression pipeline. */
internal data class ExpressionCase(
    val name: String,
    val expression: String,
    /** Only set for the matching families. */
    val text: String? = null,
    /** Canonical trace, generated regex, or null when an exception is expected. */
    val expected: String? = null,
    /** Rendered argument values, for the matching families. */
    val expectedArgs: List<String>? = null,
    /**
     * True when the expression must not match the text at all.
     *
     * Upstream distinguishes `expected_args:` with no value (no match) from
     * `expected_args: []` (matches, takes no arguments); conflating them would
     * silently pass four fixtures that assert non-matching.
     */
    val expectNoMatch: Boolean = false,
    /** The exact message upstream reports, when the case must fail. */
    val exception: String? = null,
)

internal val EXPRESSION_TOKENIZER_CORPUS: List<ExpressionCase> = buildList {
    addAll(tokenizerChunk0())
    addAll(tokenizerChunk1())
}

private fun tokenizerChunk0(): List<ExpressionCase> = listOf(
    ExpressionCase(
        name = "alternation-phrase",
        expression = "three blind/cripple mice",
        expected = "START_OF_LINE 0 0 \nTEXT 0 5 three\nWHITE_SPACE 5 6  \nTEXT 6 11 blind\nALTERNATION 11 12 /\nTEXT 12 19 cripple\nWHITE_SPACE 19 20  \nTEXT 20 24 mice\nEND_OF_LINE 24 24 ",
    ),
    ExpressionCase(
        name = "alternation",
        expression = "blind/cripple",
        expected = "START_OF_LINE 0 0 \nTEXT 0 5 blind\nALTERNATION 5 6 /\nTEXT 6 13 cripple\nEND_OF_LINE 13 13 ",
    ),
    ExpressionCase(
        name = "empty-string",
        expression = "",
        expected = "START_OF_LINE 0 0 \nEND_OF_LINE 0 0 ",
    ),
    ExpressionCase(
        name = "escape-non-reserved-character",
        expression = "\\[",
        exception = "This Cucumber Expression has a problem at column 2:\n\n\\[\n ^\nOnly the characters '{', '}', '(', ')', '\\', '/' and whitespace can be escaped.\nIf you did mean to use an '\\' you can use '\\\\' to escape it",
    ),
    ExpressionCase(
        name = "escaped-alternation",
        expression = "blind\\ and\\ famished\\/cripple mice",
        expected = "START_OF_LINE 0 0 \nTEXT 0 29 blind and famished/cripple\nWHITE_SPACE 29 30  \nTEXT 30 34 mice\nEND_OF_LINE 34 34 ",
    ),
    ExpressionCase(
        name = "escaped-char-has-start-index-of-text-token",
        expression = " \\/ ",
        expected = "START_OF_LINE 0 0 \nWHITE_SPACE 0 1  \nTEXT 1 3 /\nWHITE_SPACE 3 4  \nEND_OF_LINE 4 4 ",
    ),
    ExpressionCase(
        name = "escaped-end-of-line",
        expression = "\\",
        exception = "This Cucumber Expression has a problem at column 1:\n\n\\\n^\nThe end of line can not be escaped.\nYou can use '\\\\' to escape the '\\'",
    ),
    ExpressionCase(
        name = "escaped-optional",
        expression = "\\(blind\\)",
        expected = "START_OF_LINE 0 0 \nTEXT 0 9 (blind)\nEND_OF_LINE 9 9 ",
    ),
)

private fun tokenizerChunk1(): List<ExpressionCase> = listOf(
    ExpressionCase(
        name = "escaped-parameter",
        expression = "\\{string\\}",
        expected = "START_OF_LINE 0 0 \nTEXT 0 10 {string}\nEND_OF_LINE 10 10 ",
    ),
    ExpressionCase(
        name = "escaped-space",
        expression = "\\ ",
        expected = "START_OF_LINE 0 0 \nTEXT 0 2  \nEND_OF_LINE 2 2 ",
    ),
    ExpressionCase(
        name = "mutli-byte-character",
        expression = "😀 {int}",
        expected = "START_OF_LINE 0 0 \nTEXT 0 1 😀\nWHITE_SPACE 1 2  \nBEGIN_PARAMETER 2 3 {\nTEXT 3 6 int\nEND_PARAMETER 6 7 }\nEND_OF_LINE 7 7 ",
    ),
    ExpressionCase(
        name = "optional-phrase",
        expression = "three (blind) mice",
        expected = "START_OF_LINE 0 0 \nTEXT 0 5 three\nWHITE_SPACE 5 6  \nBEGIN_OPTIONAL 6 7 (\nTEXT 7 12 blind\nEND_OPTIONAL 12 13 )\nWHITE_SPACE 13 14  \nTEXT 14 18 mice\nEND_OF_LINE 18 18 ",
    ),
    ExpressionCase(
        name = "optional",
        expression = "(blind)",
        expected = "START_OF_LINE 0 0 \nBEGIN_OPTIONAL 0 1 (\nTEXT 1 6 blind\nEND_OPTIONAL 6 7 )\nEND_OF_LINE 7 7 ",
    ),
    ExpressionCase(
        name = "parameter-phrase",
        expression = "three {string} mice",
        expected = "START_OF_LINE 0 0 \nTEXT 0 5 three\nWHITE_SPACE 5 6  \nBEGIN_PARAMETER 6 7 {\nTEXT 7 13 string\nEND_PARAMETER 13 14 }\nWHITE_SPACE 14 15  \nTEXT 15 19 mice\nEND_OF_LINE 19 19 ",
    ),
    ExpressionCase(
        name = "parameter",
        expression = "{string}",
        expected = "START_OF_LINE 0 0 \nBEGIN_PARAMETER 0 1 {\nTEXT 1 7 string\nEND_PARAMETER 7 8 }\nEND_OF_LINE 8 8 ",
    ),
    ExpressionCase(
        name = "phrase",
        expression = "three blind mice",
        expected = "START_OF_LINE 0 0 \nTEXT 0 5 three\nWHITE_SPACE 5 6  \nTEXT 6 11 blind\nWHITE_SPACE 11 12  \nTEXT 12 16 mice\nEND_OF_LINE 16 16 ",
    ),
)

internal val EXPRESSION_PARSER_CORPUS: List<ExpressionCase> = buildList {
    addAll(parserChunk0())
    addAll(parserChunk1())
    addAll(parserChunk2())
    addAll(parserChunk3())
}

private fun parserChunk0(): List<ExpressionCase> = listOf(
    ExpressionCase(
        name = "alternation-followed-by-optional",
        expression = "three blind\\ rat/cat(s)",
        expected = "EXPRESSION_NODE 0 23\n  TEXT_NODE 0 5 token=three\n  TEXT_NODE 5 6 token= \n  ALTERNATION_NODE 6 23\n    ALTERNATIVE_NODE 6 16\n      TEXT_NODE 6 16 token=blind rat\n    ALTERNATIVE_NODE 17 23\n      TEXT_NODE 17 20 token=cat\n      OPTIONAL_NODE 20 23\n        TEXT_NODE 21 22 token=s",
    ),
    ExpressionCase(
        name = "alternation-phrase",
        expression = "three hungry/blind mice",
        expected = "EXPRESSION_NODE 0 23\n  TEXT_NODE 0 5 token=three\n  TEXT_NODE 5 6 token= \n  ALTERNATION_NODE 6 18\n    ALTERNATIVE_NODE 6 12\n      TEXT_NODE 6 12 token=hungry\n    ALTERNATIVE_NODE 13 18\n      TEXT_NODE 13 18 token=blind\n  TEXT_NODE 18 19 token= \n  TEXT_NODE 19 23 token=mice",
    ),
    ExpressionCase(
        name = "alternation-with-parameter",
        expression = "I select the {int}st/nd/rd/th",
        expected = "EXPRESSION_NODE 0 29\n  TEXT_NODE 0 1 token=I\n  TEXT_NODE 1 2 token= \n  TEXT_NODE 2 8 token=select\n  TEXT_NODE 8 9 token= \n  TEXT_NODE 9 12 token=the\n  TEXT_NODE 12 13 token= \n  PARAMETER_NODE 13 18\n    TEXT_NODE 14 17 token=int\n  ALTERNATION_NODE 18 29\n    ALTERNATIVE_NODE 18 20\n      TEXT_NODE 18 20 token=st\n    ALTERNATIVE_NODE 21 23\n      TEXT_NODE 21 23 token=nd\n    ALTERNATIVE_NODE 24 26\n      TEXT_NODE 24 26 token=rd\n    ALTERNATIVE_NODE 27 29\n      TEXT_NODE 27 29 token=th",
    ),
    ExpressionCase(
        name = "alternation-with-unused-end-optional",
        expression = "three )blind\\ mice/rats",
        expected = "EXPRESSION_NODE 0 23\n  TEXT_NODE 0 5 token=three\n  TEXT_NODE 5 6 token= \n  ALTERNATION_NODE 6 23\n    ALTERNATIVE_NODE 6 18\n      TEXT_NODE 6 7 token=)\n      TEXT_NODE 7 18 token=blind mice\n    ALTERNATIVE_NODE 19 23\n      TEXT_NODE 19 23 token=rats",
    ),
    ExpressionCase(
        name = "alternation-with-unused-start-optional",
        expression = "three blind\\ mice/rats(",
        exception = "This Cucumber Expression has a problem at column 23:\n\nthree blind\\ mice/rats(\n                      ^\nThe '(' does not have a matching ')'.\nIf you did not intend to use optional text you can use '\\(' to escape the optional text",
    ),
    ExpressionCase(
        name = "alternation-with-white-space",
        expression = "\\ three\\ hungry/blind\\ mice\\ ",
        expected = "EXPRESSION_NODE 0 29\n  ALTERNATION_NODE 0 29\n    ALTERNATIVE_NODE 0 15\n      TEXT_NODE 0 15 token= three hungry\n    ALTERNATIVE_NODE 16 29\n      TEXT_NODE 16 29 token=blind mice ",
    ),
    ExpressionCase(
        name = "alternation",
        expression = "mice/rats",
        expected = "EXPRESSION_NODE 0 9\n  ALTERNATION_NODE 0 9\n    ALTERNATIVE_NODE 0 4\n      TEXT_NODE 0 4 token=mice\n    ALTERNATIVE_NODE 5 9\n      TEXT_NODE 5 9 token=rats",
    ),
    ExpressionCase(
        name = "anonymous-parameter",
        expression = "{}",
        expected = "EXPRESSION_NODE 0 2\n  PARAMETER_NODE 0 2",
    ),
)

private fun parserChunk1(): List<ExpressionCase> = listOf(
    ExpressionCase(
        name = "closing-brace",
        expression = "}",
        expected = "EXPRESSION_NODE 0 1\n  TEXT_NODE 0 1 token=}",
    ),
    ExpressionCase(
        name = "closing-parenthesis",
        expression = ")",
        expected = "EXPRESSION_NODE 0 1\n  TEXT_NODE 0 1 token=)",
    ),
    ExpressionCase(
        name = "empty-alternation",
        expression = "/",
        expected = "EXPRESSION_NODE 0 1\n  ALTERNATION_NODE 0 1\n    ALTERNATIVE_NODE 0 0\n    ALTERNATIVE_NODE 1 1",
    ),
    ExpressionCase(
        name = "empty-alternations",
        expression = "//",
        expected = "EXPRESSION_NODE 0 2\n  ALTERNATION_NODE 0 2\n    ALTERNATIVE_NODE 0 0\n    ALTERNATIVE_NODE 1 1\n    ALTERNATIVE_NODE 2 2",
    ),
    ExpressionCase(
        name = "empty-string",
        expression = "",
        expected = "EXPRESSION_NODE 0 0",
    ),
    ExpressionCase(
        name = "escaped-alternation",
        expression = "mice\\/rats",
        expected = "EXPRESSION_NODE 0 10\n  TEXT_NODE 0 10 token=mice/rats",
    ),
    ExpressionCase(
        name = "escaped-backslash",
        expression = "\\\\",
        expected = "EXPRESSION_NODE 0 2\n  TEXT_NODE 0 2 token=\\\\",
    ),
    ExpressionCase(
        name = "escaped-opening-parenthesis",
        expression = "\\(",
        expected = "EXPRESSION_NODE 0 2\n  TEXT_NODE 0 2 token=(",
    ),
)

private fun parserChunk2(): List<ExpressionCase> = listOf(
    ExpressionCase(
        name = "escaped-optional-followed-by-optional",
        expression = "three \\((very) blind) mice",
        expected = "EXPRESSION_NODE 0 26\n  TEXT_NODE 0 5 token=three\n  TEXT_NODE 5 6 token= \n  TEXT_NODE 6 8 token=(\n  OPTIONAL_NODE 8 14\n    TEXT_NODE 9 13 token=very\n  TEXT_NODE 14 15 token= \n  TEXT_NODE 15 20 token=blind\n  TEXT_NODE 20 21 token=)\n  TEXT_NODE 21 22 token= \n  TEXT_NODE 22 26 token=mice",
    ),
    ExpressionCase(
        name = "escaped-optional-phrase",
        expression = "three \\(blind) mice",
        expected = "EXPRESSION_NODE 0 19\n  TEXT_NODE 0 5 token=three\n  TEXT_NODE 5 6 token= \n  TEXT_NODE 6 13 token=(blind\n  TEXT_NODE 13 14 token=)\n  TEXT_NODE 14 15 token= \n  TEXT_NODE 15 19 token=mice",
    ),
    ExpressionCase(
        name = "escaped-optional",
        expression = "\\(blind)",
        expected = "EXPRESSION_NODE 0 8\n  TEXT_NODE 0 7 token=(blind\n  TEXT_NODE 7 8 token=)",
    ),
    ExpressionCase(
        name = "multi-byte-character",
        expression = "😀 {int}",
        expected = "EXPRESSION_NODE 0 7\n  TEXT_NODE 0 1 token=😀\n  TEXT_NODE 1 2 token= \n  PARAMETER_NODE 2 7\n    TEXT_NODE 3 6 token=int",
    ),
    ExpressionCase(
        name = "opening-brace",
        expression = "{",
        exception = "This Cucumber Expression has a problem at column 1:\n\n{\n^\nThe '{' does not have a matching '}'.\nIf you did not intend to use a parameter you can use '\\{' to escape the a parameter",
    ),
    ExpressionCase(
        name = "opening-parenthesis",
        expression = "(",
        exception = "This Cucumber Expression has a problem at column 1:\n\n(\n^\nThe '(' does not have a matching ')'.\nIf you did not intend to use optional text you can use '\\(' to escape the optional text",
    ),
    ExpressionCase(
        name = "optional-containing-nested-optional",
        expression = "three ((very) blind) mice",
        expected = "EXPRESSION_NODE 0 25\n  TEXT_NODE 0 5 token=three\n  TEXT_NODE 5 6 token= \n  OPTIONAL_NODE 6 20\n    OPTIONAL_NODE 7 13\n      TEXT_NODE 8 12 token=very\n    TEXT_NODE 13 14 token= \n    TEXT_NODE 14 19 token=blind\n  TEXT_NODE 20 21 token= \n  TEXT_NODE 21 25 token=mice",
    ),
    ExpressionCase(
        name = "optional-phrase",
        expression = "three (blind) mice",
        expected = "EXPRESSION_NODE 0 18\n  TEXT_NODE 0 5 token=three\n  TEXT_NODE 5 6 token= \n  OPTIONAL_NODE 6 13\n    TEXT_NODE 7 12 token=blind\n  TEXT_NODE 13 14 token= \n  TEXT_NODE 14 18 token=mice",
    ),
)

private fun parserChunk3(): List<ExpressionCase> = listOf(
    ExpressionCase(
        name = "optional",
        expression = "(blind)",
        expected = "EXPRESSION_NODE 0 7\n  OPTIONAL_NODE 0 7\n    TEXT_NODE 1 6 token=blind",
    ),
    ExpressionCase(
        name = "parameter",
        expression = "{string}",
        expected = "EXPRESSION_NODE 0 8\n  PARAMETER_NODE 0 8\n    TEXT_NODE 1 7 token=string",
    ),
    ExpressionCase(
        name = "phrase",
        expression = "three blind mice",
        expected = "EXPRESSION_NODE 0 16\n  TEXT_NODE 0 5 token=three\n  TEXT_NODE 5 6 token= \n  TEXT_NODE 6 11 token=blind\n  TEXT_NODE 11 12 token= \n  TEXT_NODE 12 16 token=mice",
    ),
    ExpressionCase(
        name = "unfinished-parameter",
        expression = "{string",
        exception = "This Cucumber Expression has a problem at column 1:\n\n{string\n^\nThe '{' does not have a matching '}'.\nIf you did not intend to use a parameter you can use '\\{' to escape the a parameter",
    ),
)

internal val EXPRESSION_TRANSFORMATION_CORPUS: List<ExpressionCase> = buildList {
    addAll(transformationChunk0())
}

private fun transformationChunk0(): List<ExpressionCase> = listOf(
    ExpressionCase(
        name = "alternation-with-optional",
        expression = "a/b(c)",
        expected = "^(?:a|b(?:c)?)\$",
    ),
    ExpressionCase(
        name = "alternation",
        expression = "a/b c/d/e",
        expected = "^(?:a|b) (?:c|d|e)\$",
    ),
    ExpressionCase(
        name = "empty",
        expression = "",
        expected = "^\$",
    ),
    ExpressionCase(
        name = "escape-regex-characters",
        expression = "^\$[]\\(\\){}\\\\.|?*+",
        expected = "^\\^\\\$\\[\\]\\(\\)(.*)\\\\\\.\\|\\?\\*\\+\$",
    ),
    ExpressionCase(
        name = "optional",
        expression = "(a)",
        expected = "^(?:a)?\$",
    ),
    ExpressionCase(
        name = "parameter",
        expression = "{int}",
        expected = "^((?:-?\\d+)|(?:\\d+))\$",
    ),
    ExpressionCase(
        name = "text",
        expression = "a",
        expected = "^a\$",
    ),
    ExpressionCase(
        name = "unicode",
        expression = "Привет, Мир(ы)!",
        expected = "^Привет, Мир(?:ы)?!\$",
    ),
)

internal val EXPRESSION_MATCHING_CORPUS: List<ExpressionCase> = buildList {
    addAll(matchingChunk0())
    addAll(matchingChunk1())
    addAll(matchingChunk2())
    addAll(matchingChunk3())
    addAll(matchingChunk4())
    addAll(matchingChunk5())
    addAll(matchingChunk6())
    addAll(matchingChunk7())
    addAll(matchingChunk8())
}

private fun matchingChunk0(): List<ExpressionCase> = listOf(
    ExpressionCase(
        name = "allows-escaped-optional-parameter-types",
        expression = "\\({int})",
        text = "(3)",
        expectedArgs = listOf("3"),
    ),
    ExpressionCase(
        name = "allows-parameter-type-in-alternation-1",
        expression = "a/i{int}n/y",
        text = "i18n",
        expectedArgs = listOf("18"),
    ),
    ExpressionCase(
        name = "allows-parameter-type-in-alternation-2",
        expression = "a/i{int}n/y",
        text = "a11y",
        expectedArgs = listOf("11"),
    ),
    ExpressionCase(
        name = "does-allow-parameter-adjacent-to-alternation",
        expression = "{int}st/nd/rd/th",
        text = "3rd",
        expectedArgs = listOf("3"),
    ),
    ExpressionCase(
        name = "does-not-allow-alternation-in-optional",
        expression = "three( brown/black) mice",
        exception = "This Cucumber Expression has a problem at column 13:\n\nthree( brown/black) mice\n            ^\nAn alternation can not be used inside an optional.\nIf you did not mean to use an alternation you can use '\\/' to escape the '/'. Otherwise rephrase your expression or consider using a regular expression instead.",
    ),
    ExpressionCase(
        name = "does-not-allow-alternation-with-empty-alternative-by-adjacent-left-parameter",
        expression = "{int}/x",
        exception = "This Cucumber Expression has a problem at column 6:\n\n{int}/x\n     ^\nAlternative may not be empty.\nIf you did not mean to use an alternative you can use '\\/' to escape the '/'",
    ),
    ExpressionCase(
        name = "does-not-allow-alternation-with-empty-alternative-by-adjacent-optional",
        expression = "three (brown)/black mice",
        exception = "This Cucumber Expression has a problem at column 7:\n\nthree (brown)/black mice\n      ^-----^\nAn alternative may not exclusively contain optionals.\nIf you did not mean to use an optional you can use '\\(' to escape the '('",
    ),
    ExpressionCase(
        name = "does-not-allow-alternation-with-empty-alternative-by-adjacent-right-parameter",
        expression = "x/{int}",
        exception = "This Cucumber Expression has a problem at column 3:\n\nx/{int}\n  ^\nAlternative may not be empty.\nIf you did not mean to use an alternative you can use '\\/' to escape the '/'",
    ),
)

private fun matchingChunk1(): List<ExpressionCase> = listOf(
    ExpressionCase(
        name = "does-not-allow-alternation-with-empty-alternative",
        expression = "three brown//black mice",
        exception = "This Cucumber Expression has a problem at column 13:\n\nthree brown//black mice\n            ^\nAlternative may not be empty.\nIf you did not mean to use an alternative you can use '\\/' to escape the '/'",
    ),
    ExpressionCase(
        name = "does-not-allow-empty-optional",
        expression = "three () mice",
        exception = "This Cucumber Expression has a problem at column 7:\n\nthree () mice\n      ^^\nAn optional must contain some text.\nIf you did not mean to use an optional you can use '\\(' to escape the '('",
    ),
    ExpressionCase(
        name = "does-not-allow-nested-optional",
        expression = "(a(b))",
        exception = "This Cucumber Expression has a problem at column 3:\n\n(a(b))\n  ^-^\nAn optional may not contain an other optional.\nIf you did not mean to use an optional type you can use '\\(' to escape the '('. For more complicated expressions consider using a regular expression instead.",
    ),
    ExpressionCase(
        name = "does-not-allow-optional-parameter-types",
        expression = "({int})",
        exception = "This Cucumber Expression has a problem at column 2:\n\n({int})\n ^---^\nAn optional may not contain a parameter type.\nIf you did not mean to use an parameter type you can use '\\{' to escape the '{'",
    ),
    ExpressionCase(
        name = "does-not-allow-parameter-name-with-reserved-characters",
        expression = "{(string)}",
        exception = "This Cucumber Expression has a problem at column 2:\n\n{(string)}\n ^\nParameter names may not contain '{', '}', '(', ')', '\\' or '/'.\nDid you mean to use a regular expression?",
    ),
    ExpressionCase(
        name = "does-not-allow-unfinished-parenthesis-1",
        expression = "three (exceptionally\\) {string\\} mice",
        exception = "This Cucumber Expression has a problem at column 24:\n\nthree (exceptionally\\) {string\\} mice\n                       ^\nThe '{' does not have a matching '}'.\nIf you did not intend to use a parameter you can use '\\{' to escape the a parameter",
    ),
    ExpressionCase(
        name = "does-not-allow-unfinished-parenthesis-2",
        expression = "three (exceptionally\\) {string} mice",
        exception = "This Cucumber Expression has a problem at column 7:\n\nthree (exceptionally\\) {string} mice\n      ^\nThe '(' does not have a matching ')'.\nIf you did not intend to use optional text you can use '\\(' to escape the optional text",
    ),
    ExpressionCase(
        name = "does-not-allow-unfinished-parenthesis-3",
        expression = "three ((exceptionally\\) strong) mice",
        exception = "This Cucumber Expression has a problem at column 7:\n\nthree ((exceptionally\\) strong) mice\n      ^\nThe '(' does not have a matching ')'.\nIf you did not intend to use optional text you can use '\\(' to escape the optional text",
    ),
)

private fun matchingChunk2(): List<ExpressionCase> = listOf(
    ExpressionCase(
        name = "does-not-match-misquoted-string",
        expression = "three {string} mice",
        text = "three \"blind' mice",
        expectNoMatch = true,
    ),
    ExpressionCase(
        name = "does-not-match-single-minus-as-double",
        expression = "{double}",
        text = "-",
        expectNoMatch = true,
    ),
    ExpressionCase(
        name = "does-not-match-single-minus-as-int",
        expression = "{int}",
        text = "-",
        expectNoMatch = true,
    ),
    ExpressionCase(
        name = "doesnt-match-float-as-int",
        expression = "{int}",
        text = "1.22",
        expectNoMatch = true,
    ),
    ExpressionCase(
        name = "matches-alternation",
        expression = "mice/rats and rats\\/mice",
        text = "rats and rats/mice",
        expectedArgs = listOf(),
    ),
    ExpressionCase(
        name = "matches-anonymous-parameter-type",
        expression = "{}",
        text = "0.22",
        expectedArgs = listOf("0.22"),
    ),
    ExpressionCase(
        name = "matches-bigdecimal",
        expression = "{bigdecimal}",
        text = "3.1415926535897932384626433832795028841971693993751",
        expectedArgs = listOf("3.1415926535897932384626433832795028841971693993751"),
    ),
    ExpressionCase(
        name = "matches-biginteger",
        expression = "{biginteger}",
        text = "31415926535897932384626433832795028841971693993751058209749445923078164062862089986280348253421170679",
        expectedArgs = listOf("31415926535897932384626433832795028841971693993751058209749445923078164062862089986280348253421170679"),
    ),
)

private fun matchingChunk3(): List<ExpressionCase> = listOf(
    ExpressionCase(
        name = "matches-byte",
        expression = "{byte}",
        text = "127",
        expectedArgs = listOf("127"),
    ),
    ExpressionCase(
        name = "matches-double-quoted-empty-string-as-empty-string-along-with-other-strings",
        expression = "three {string} and {string} mice",
        text = "three \"\" and \"handsome\" mice",
        expectedArgs = listOf("", "handsome"),
    ),
    ExpressionCase(
        name = "matches-double-quoted-empty-string-as-empty-string",
        expression = "three {string} mice",
        text = "three \"\" mice",
        expectedArgs = listOf(""),
    ),
    ExpressionCase(
        name = "matches-double-quoted-string-with-escaped-double-quote",
        expression = "three {string} mice",
        text = "three \"bl\\\"nd\" mice",
        expectedArgs = listOf("bl\"nd"),
    ),
    ExpressionCase(
        name = "matches-double-quoted-string-with-single-quotes",
        expression = "three {string} mice",
        text = "three \"'blind'\" mice",
        expectedArgs = listOf("'blind'"),
    ),
    ExpressionCase(
        name = "matches-double-quoted-string",
        expression = "three {string} mice",
        text = "three \"blind\" mice",
        expectedArgs = listOf("blind"),
    ),
    ExpressionCase(
        name = "matches-double",
        expression = "{double}",
        text = "3.141592653589793",
        expectedArgs = listOf("3.141592653589793"),
    ),
    ExpressionCase(
        name = "matches-doubly-escaped-parenthesis",
        expression = "three \\\\(exceptionally) \\\\{string} mice",
        text = "three \\exceptionally \\\"blind\" mice",
        expectedArgs = listOf("blind"),
    ),
)

private fun matchingChunk4(): List<ExpressionCase> = listOf(
    ExpressionCase(
        name = "matches-doubly-escaped-slash-1",
        expression = "12\\\\/2020",
        text = "12\\",
        expectedArgs = listOf(),
    ),
    ExpressionCase(
        name = "matches-doubly-escaped-slash-2",
        expression = "12\\\\/2020",
        text = "2020",
        expectedArgs = listOf(),
    ),
    ExpressionCase(
        name = "matches-escaped-parenthesis-1",
        expression = "three \\(exceptionally) \\{string} mice",
        text = "three (exceptionally) {string} mice",
        expectedArgs = listOf(),
    ),
    ExpressionCase(
        name = "matches-escaped-parenthesis-2",
        expression = "three \\((exceptionally)) \\{{string}} mice",
        text = "three (exceptionally) {\"blind\"} mice",
        expectedArgs = listOf("blind"),
    ),
    ExpressionCase(
        name = "matches-escaped-parenthesis-3",
        expression = "three \\((exceptionally)) \\{{string}} mice",
        text = "three (exceptionally) {\"blind\"} mice",
        expectedArgs = listOf("blind"),
    ),
    ExpressionCase(
        name = "matches-escaped-slash",
        expression = "12\\/2020",
        text = "12/2020",
        expectedArgs = listOf(),
    ),
    ExpressionCase(
        name = "matches-float-leading-plus-scientific-notation",
        expression = "{float}",
        text = "+1.5E+3",
        expectedArgs = listOf("1500.0"),
    ),
    ExpressionCase(
        name = "matches-float-leading-plus",
        expression = "{float}",
        text = "+3.141593",
        expectedArgs = listOf("3.141593"),
    ),
)

private fun matchingChunk5(): List<ExpressionCase> = listOf(
    ExpressionCase(
        name = "matches-float-negative",
        expression = "{float}",
        text = "-3.141593",
        expectedArgs = listOf("-3.141593"),
    ),
    ExpressionCase(
        name = "matches-float-scientific-notation",
        expression = "{float}",
        text = "1.5E+3",
        expectedArgs = listOf("1500.0"),
    ),
    ExpressionCase(
        name = "matches-float-with-integer-part",
        expression = "{float}",
        text = "0.22",
        expectedArgs = listOf("0.22"),
    ),
    ExpressionCase(
        name = "matches-float-without-integer-part",
        expression = "{float}",
        text = ".22",
        expectedArgs = listOf("0.22"),
    ),
    ExpressionCase(
        name = "matches-float",
        expression = "{float}",
        text = "3.141593",
        expectedArgs = listOf("3.141593"),
    ),
    ExpressionCase(
        name = "matches-int-negative",
        expression = "{int}",
        text = "-2147483647",
        expectedArgs = listOf("-2147483647"),
    ),
    ExpressionCase(
        name = "matches-int",
        expression = "{int}",
        text = "2147483647",
        expectedArgs = listOf("2147483647"),
    ),
    ExpressionCase(
        name = "matches-long",
        expression = "{long}",
        text = "9223372036854775807",
        expectedArgs = listOf("9223372036854775807"),
    ),
)

private fun matchingChunk6(): List<ExpressionCase> = listOf(
    ExpressionCase(
        name = "matches-multiple-double-quoted-strings",
        expression = "three {string} and {string} mice",
        text = "three \"blind\" and \"crippled\" mice",
        expectedArgs = listOf("blind", "crippled"),
    ),
    ExpressionCase(
        name = "matches-multiple-single-quoted-strings",
        expression = "three {string} and {string} mice",
        text = "three 'blind' and 'crippled' mice",
        expectedArgs = listOf("blind", "crippled"),
    ),
    ExpressionCase(
        name = "matches-optional-before-alternation-1",
        expression = "three (brown )mice/rats",
        text = "three brown mice",
        expectedArgs = listOf(),
    ),
    ExpressionCase(
        name = "matches-optional-before-alternation-2",
        expression = "three (brown )mice/rats",
        text = "three rats",
        expectedArgs = listOf(),
    ),
    ExpressionCase(
        name = "matches-optional-before-alternation-with-regex-characters-1",
        expression = "I wait {int} second(s)./second(s)?",
        text = "I wait 2 seconds?",
        expectedArgs = listOf("2"),
    ),
    ExpressionCase(
        name = "matches-optional-before-alternation-with-regex-characters-2",
        expression = "I wait {int} second(s)./second(s)?",
        text = "I wait 1 second.",
        expectedArgs = listOf("1"),
    ),
    ExpressionCase(
        name = "matches-optional-in-alternation-1",
        expression = "{int} rat(s)/mouse/mice",
        text = "3 rats",
        expectedArgs = listOf("3"),
    ),
    ExpressionCase(
        name = "matches-optional-in-alternation-2",
        expression = "{int} rat(s)/mouse/mice",
        text = "2 mice",
        expectedArgs = listOf("2"),
    ),
)

private fun matchingChunk7(): List<ExpressionCase> = listOf(
    ExpressionCase(
        name = "matches-optional-in-alternation-3",
        expression = "{int} rat(s)/mouse/mice",
        text = "1 mouse",
        expectedArgs = listOf("1"),
    ),
    ExpressionCase(
        name = "matches-short",
        expression = "{short}",
        text = "32767",
        expectedArgs = listOf("32767"),
    ),
    ExpressionCase(
        name = "matches-single-quoted-empty-string-as-empty-string-along-with-other-strings",
        expression = "three {string} and {string} mice",
        text = "three '' and 'handsome' mice",
        expectedArgs = listOf("", "handsome"),
    ),
    ExpressionCase(
        name = "matches-single-quoted-empty-string-as-empty-string",
        expression = "three {string} mice",
        text = "three '' mice",
        expectedArgs = listOf(""),
    ),
    ExpressionCase(
        name = "matches-single-quoted-string-with-double-quotes",
        expression = "three {string} mice",
        text = "three '\"blind\"' mice",
        expectedArgs = listOf("\"blind\""),
    ),
    ExpressionCase(
        name = "matches-single-quoted-string-with-escaped-single-quote",
        expression = "three {string} mice",
        text = "three 'bl\\'nd' mice",
        expectedArgs = listOf("bl'nd"),
    ),
    ExpressionCase(
        name = "matches-single-quoted-string",
        expression = "three {string} mice",
        text = "three 'blind' mice",
        expectedArgs = listOf("blind"),
    ),
    ExpressionCase(
        name = "matches-word",
        expression = "three {word} mice",
        text = "three blind mice",
        expectedArgs = listOf("blind"),
    ),
)

private fun matchingChunk8(): List<ExpressionCase> = listOf(
    ExpressionCase(
        name = "throws-unknown-parameter-type",
        expression = "{unknown}",
        exception = "This Cucumber Expression has a problem at column 1:\n\n{unknown}\n^-------^\nUndefined parameter type 'unknown'.\nPlease register a ParameterType for 'unknown'",
    ),
)

internal val EXPRESSION_REGEX_CORPUS: List<ExpressionCase> = buildList {
    addAll(regexChunk0())
}

private fun regexChunk0(): List<ExpressionCase> = listOf(
    ExpressionCase(
        name = "optional-capture-groups-all",
        expression = "^a (b )?c (d )?e (f )?g\$",
        text = "a b c d e f g",
        expectedArgs = listOf("b ", "d ", "f "),
    ),
    ExpressionCase(
        name = "optional-capture-groups-issue",
        expression = "^I should( not)? be on the map\$",
        text = "I should be on the map",
        expectedArgs = listOf("null"),
    ),
    ExpressionCase(
        name = "optional-capture-groups-some",
        expression = "^a (b )?c (d )?e (f )?g\$",
        text = "a b c e f g",
        expectedArgs = listOf("b ", "null", "f "),
    ),
)

