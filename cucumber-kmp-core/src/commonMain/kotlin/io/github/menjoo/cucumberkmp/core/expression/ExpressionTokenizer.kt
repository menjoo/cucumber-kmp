package io.github.menjoo.cucumberkmp.core.expression

import io.github.menjoo.cucumberkmp.core.expression.ExpressionTokenType.Companion.ESCAPE_CHARACTER

/**
 * Splits a Cucumber Expression into [ExpressionToken]s.
 *
 * Runs of `TEXT` and `WHITE_SPACE` coalesce into single tokens; every other type is one character.
 * An escaped character becomes text, and its backslash is counted so that token offsets still refer
 * to positions in the original expression.
 *
 * Offsets count **code points**, not UTF-16 units, so `😀 {int}` puts `{` at offset 2 rather than 3.
 * That matters beyond tidiness: offsets drive the caret in error messages, and a caret indented in
 * UTF-16 units would sit under the wrong character.
 */
internal object ExpressionTokenizer {

    fun tokenize(expression: String): List<ExpressionToken> {
        val codePoints = expression.toCodePoints()
        val tokens = mutableListOf<ExpressionToken>()
        val buffer = StringBuilder()

        var bufferCodePoints = 0
        var previousType: ExpressionTokenType?
        var bufferStart = 0
        var escapedCount = 0
        var treatAsText = false

        fun flush(type: ExpressionTokenType): ExpressionToken {
            // Escapes consumed a backslash that is absent from the buffer but present in the
            // source, so the end offset has to account for them.
            val escapes = if (type == ExpressionTokenType.TEXT) escapedCount else 0
            if (type == ExpressionTokenType.TEXT) escapedCount = 0
            val end = bufferStart + bufferCodePoints + escapes
            val token = ExpressionToken(buffer.toString(), type, bufferStart, end)
            buffer.clear()
            bufferCodePoints = 0
            bufferStart = end
            return token
        }

        tokens += flush(ExpressionTokenType.START_OF_LINE)
        previousType = ExpressionTokenType.START_OF_LINE

        for (codePoint in codePoints) {
            if (!treatAsText && codePoint.length == 1 && codePoint[0] == ESCAPE_CHARACTER) {
                escapedCount++
                treatAsText = true
                continue
            }
            val currentType = if (treatAsText) {
                if (!ExpressionTokenType.canEscape(codePoint)) {
                    throw ExpressionErrors.cantEscape(
                        expression,
                        bufferStart + bufferCodePoints + escapedCount,
                    )
                }
                ExpressionTokenType.TEXT
            } else {
                ExpressionTokenType.of(codePoint)
            }
            treatAsText = false

            val continues = currentType == previousType &&
                (currentType == ExpressionTokenType.WHITE_SPACE || currentType == ExpressionTokenType.TEXT)

            if (previousType != ExpressionTokenType.START_OF_LINE && !continues) {
                tokens += flush(requireNotNull(previousType))
            }
            buffer.append(codePoint)
            bufferCodePoints++
            previousType = currentType
        }

        if (buffer.isNotEmpty()) {
            tokens += flush(requireNotNull(previousType))
        }

        if (treatAsText) throw ExpressionErrors.theEndOfLineCanNotBeEscaped(expression)

        tokens += flush(ExpressionTokenType.END_OF_LINE)
        return tokens
    }
}

/**
 * Splits a string into code points, each as a one- or two-char string.
 *
 * Kotlin's common stdlib has no code-point iteration, and offsets in Cucumber Expressions are
 * defined in code points.
 */
internal fun String.toCodePoints(): List<String> {
    val result = mutableListOf<String>()
    var index = 0
    while (index < length) {
        val character = this[index]
        if (character.isHighSurrogate() && index + 1 < length && this[index + 1].isLowSurrogate()) {
            result += substring(index, index + 2)
            index += 2
        } else {
            result += character.toString()
            index++
        }
    }
    return result
}

internal fun String.codePointCount(): Int {
    var count = 0
    var index = 0
    while (index < length) {
        index += if (this[index].isHighSurrogate() && index + 1 < length && this[index + 1].isLowSurrogate()) 2 else 1
        count++
    }
    return count
}
