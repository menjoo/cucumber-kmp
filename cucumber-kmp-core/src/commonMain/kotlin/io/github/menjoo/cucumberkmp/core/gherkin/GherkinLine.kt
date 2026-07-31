package io.github.menjoo.cucumberkmp.core.gherkin

import io.github.menjoo.cucumberkmp.core.SourceLocation

/**
 * One physical line of a `.feature` file, with the lexical operations Gherkin needs.
 *
 * Gherkin is a line-oriented language: every construct is recognised from a single line's leading
 * keyword, which is why there is no separate token stream. This class is that lexer.
 *
 * All columns it reports are 1-based and account for the line's indentation.
 */
internal class GherkinLine(
    val path: String,
    val lineNumber: Int,
    val text: String,
) {
    /** The line with leading whitespace removed. */
    val trimmed: String = text.trimStart()

    /** Number of leading whitespace characters, so column of `trimmed[i]` is `indent + i + 1`. */
    val indent: Int = text.length - trimmed.length

    val isBlank: Boolean = trimmed.isEmpty()

    /** Location of the first non-whitespace character. */
    fun location(): SourceLocation = SourceLocation(path, lineNumber, indent + 1)

    fun locationAtColumn(column: Int): SourceLocation = SourceLocation(path, lineNumber, column)

    fun startsWith(prefix: String): Boolean = trimmed.startsWith(prefix)

    /** The text after [prefix], trimmed. */
    fun textAfter(prefix: String): String = trimmed.substring(prefix.length).trim()

    /**
     * Splits a table row into cells.
     *
     * Recognises Gherkin's cell escapes — `\|` for a literal pipe, `\n` for a newline and `\\`
     * for a backslash — and reports each cell at the column of its first non-whitespace
     * character, so a failure can point inside a table rather than merely at it.
     *
     * Content after the final `|` is ignored, matching upstream.
     */
    fun tableCells(): List<TableCell> {
        val cells = mutableListOf<TableCell>()
        val cell = StringBuilder()
        var seenFirstDelimiter = false
        var cellStartIndex = 0
        var escaping = false

        for (index in trimmed.indices) {
            val char = trimmed[index]
            if (escaping) {
                when (char) {
                    'n' -> cell.append('\n')
                    '\\' -> cell.append('\\')
                    '|' -> cell.append('|')
                    // An unrecognised escape is preserved verbatim, backslash included.
                    else -> cell.append('\\').append(char)
                }
                escaping = false
                continue
            }
            when (char) {
                '\\' -> escaping = true
                '|' -> {
                    if (seenFirstDelimiter) {
                        cells += buildCell(cell.toString(), cellStartIndex)
                    } else {
                        seenFirstDelimiter = true
                    }
                    cell.clear()
                    cellStartIndex = index + 1
                }
                else -> cell.append(char)
            }
        }
        return cells
    }

    private fun buildCell(raw: String, startIndex: Int): TableCell {
        val leadingWhitespace = raw.indexOfFirst { !it.isWhitespace() }.let { if (it < 0) raw.length else it }
        return TableCell(
            location = locationAtColumn(indent + startIndex + leadingWhitespace + 1),
            value = raw.trim(),
        )
    }

    /**
     * Splits a tag line into tags.
     *
     * Splits on `@` rather than on whitespace, because Gherkin accepts `@a@b` as two tags. A
     * trailing `#` comment is stripped first. Returns the tags plus any errors found, since a
     * malformed tag should not abort the whole parse.
     */
    fun tags(): TagScanResult {
        val uncommented = stripTrailingComment(trimmed)
        val tags = mutableListOf<Tag>()
        val errors = mutableListOf<GherkinParseError>()

        val parts = uncommented.split('@')
        // parts[0] is the text before the first '@'; for a tag line it is empty.
        var index = parts[0].length
        for (partIndex in 1 until parts.size) {
            val part = parts[partIndex]
            val name = part.trim()
            val column = indent + index + 1
            if (name.isNotEmpty()) {
                if (name.any { it.isWhitespace() }) {
                    errors += GherkinParseError(
                        locationAtColumn(column),
                        "A tag may not contain whitespace",
                    )
                } else {
                    tags += Tag(locationAtColumn(column), "@$name")
                }
            }
            index += part.length + 1
        }
        return TagScanResult(tags, errors)
    }

    private fun stripTrailingComment(value: String): String {
        for (index in 1 until value.length) {
            if (value[index] == '#' && value[index - 1].isWhitespace()) {
                return value.substring(0, index)
            }
        }
        return value
    }

    override fun toString(): String = "$path:$lineNumber: $text"
}

internal data class TagScanResult(
    val tags: List<Tag>,
    val errors: List<GherkinParseError>,
)
