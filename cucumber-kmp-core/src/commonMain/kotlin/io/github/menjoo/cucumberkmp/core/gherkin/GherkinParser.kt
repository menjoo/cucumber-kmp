package io.github.menjoo.cucumberkmp.core.gherkin

import io.github.menjoo.cucumberkmp.core.SourceLocation
import io.github.menjoo.cucumberkmp.core.gherkin.DocString.Companion.DOC_STRING_BACKTICKS
import io.github.menjoo.cucumberkmp.core.gherkin.DocString.Companion.DOC_STRING_QUOTES

/**
 * Parses Gherkin source into a [GherkinDocument].
 *
 * Gherkin is line-oriented — every construct is recognised from one line's leading keyword — so
 * this is a recursive-descent parser directly over lines, with [GherkinLine] as the lexer. There
 * is no separate token stream.
 *
 * The parser runs on every target and never touches the filesystem: callers supply the source as a
 * string. On the JVM the Gradle plugin reads the file; on Native and Wasm the text arrives as
 * generated code. See ARCHITECTURE.md §4a.
 *
 * ```kotlin
 * val document = GherkinParser.parse("calculator.feature", source)
 * ```
 */
public object GherkinParser {

    /**
     * Parses [source], throwing on any syntax error.
     *
     * @throws GherkinParseException carrying every error found, not just the first.
     */
    public fun parse(path: String, source: String): GherkinDocument {
        val outcome = tryParse(path, source)
        if (outcome.errors.isNotEmpty()) throw GherkinParseException(path, outcome.errors)
        return outcome.document
    }

    /**
     * Parses [source], returning the document alongside any errors instead of throwing.
     *
     * The document is always returned — partially built if parsing failed — so tooling can report
     * every error in a file and still show what it understood.
     */
    public fun tryParse(path: String, source: String): GherkinParseOutcome =
        Parser(path, source).parse()
}

/** The result of a non-throwing parse. Successful when [errors] is empty. */
public data class GherkinParseOutcome(
    public val document: GherkinDocument,
    public val errors: List<GherkinParseError>,
) {
    public val isSuccess: Boolean get() = errors.isEmpty()
}

private val LANGUAGE_HEADER = Regex("^#\\s*language\\s*:\\s*([a-zA-Z0-9_-]+)\\s*$")

private class Parser(private val path: String, source: String) {

    private val lines: List<GherkinLine> = splitLines(path, source)
    private var position = 0

    private val errors = mutableListOf<GherkinParseError>()

    // Keyed by line number so that re-reading a line is idempotent. The Examples lookahead in
    // parseScenario rewinds the cursor, and a plain list would record those comments twice.
    private val comments = mutableMapOf<Int, Comment>()

    private var language = GherkinDialects.DEFAULT_LANGUAGE
    private var dialect = GherkinDialects.default

    fun parse(): GherkinParseOutcome =
        GherkinParseOutcome(
            document = GherkinDocument(
                path = path,
                feature = parseFeature(),
                comments = comments.entries.sortedBy { it.key }.map { it.value },
            ),
            errors = errors.toList(),
        )

    // ---------------------------------------------------------------- cursor

    private fun current(): GherkinLine? = lines.getOrNull(position)

    private fun advance() {
        position++
    }

    /** Consumes blank lines and comment lines, collecting the latter into the document. */
    private fun skipBlanksAndComments() {
        while (true) {
            val line = current() ?: return
            when {
                line.isBlank -> advance()
                isComment(line) -> {
                    comments[line.lineNumber] = Comment(line.locationAtColumn(1), line.text)
                    advance()
                }
                else -> return
            }
        }
    }

    private fun report(line: GherkinLine, expected: List<String>) {
        report(line.location(), "expected: ${expected.joinToString()}, got '${line.trimmed}'")
    }

    private fun report(location: SourceLocation, message: String) {
        errors += GherkinParseError(location, message)
    }

    // ------------------------------------------------------------ line kinds

    private fun isComment(line: GherkinLine): Boolean = line.startsWith("#")

    private fun isTagLine(line: GherkinLine): Boolean = line.startsWith("@")

    private fun isFeatureLine(line: GherkinLine) = matchKeyword(line, dialect.featureKeywords) != null

    private fun isRuleLine(line: GherkinLine) = matchKeyword(line, dialect.ruleKeywords) != null

    private fun isBackgroundLine(line: GherkinLine) =
        matchKeyword(line, dialect.backgroundKeywords) != null

    private fun isScenarioLine(line: GherkinLine) = matchScenarioKeyword(line) != null

    private fun isExamplesLine(line: GherkinLine) =
        matchKeyword(line, dialect.examplesKeywords) != null

    private fun isStepLine(line: GherkinLine) = matchStepKeyword(line) != null

    private fun isTableRow(line: GherkinLine): Boolean = line.startsWith("|")

    private fun docStringDelimiter(line: GherkinLine): String? = when {
        line.startsWith(DOC_STRING_QUOTES) -> DOC_STRING_QUOTES
        line.startsWith(DOC_STRING_BACKTICKS) -> DOC_STRING_BACKTICKS
        else -> null
    }

    /** True for any line that begins a new construct, and therefore ends a description. */
    private fun isStructural(line: GherkinLine): Boolean =
        isTagLine(line) ||
            isFeatureLine(line) ||
            isRuleLine(line) ||
            isBackgroundLine(line) ||
            isScenarioLine(line) ||
            isExamplesLine(line) ||
            isStepLine(line) ||
            isTableRow(line) ||
            docStringDelimiter(line) != null ||
            isComment(line)

    /**
     * Matches a `Keyword:` line, longest keyword first.
     *
     * Longest-first guards dialects where one keyword prefixes another; the trailing colon already
     * separates e.g. `Example:` from `Examples:`.
     */
    private fun matchKeyword(line: GherkinLine, keywords: List<String>): KeywordMatch? {
        for (keyword in keywords.sortedByDescending { it.length }) {
            if (line.startsWith("$keyword:")) {
                return KeywordMatch(keyword, line.textAfter("$keyword:"))
            }
        }
        return null
    }

    /** Matches a scenario or scenario-outline line, preferring the outline keyword. */
    private fun matchScenarioKeyword(line: GherkinLine): KeywordMatch? =
        matchKeyword(line, dialect.scenarioOutlineKeywords) ?: matchKeyword(line, dialect.scenarioKeywords)

    /** Step keywords keep their trailing space and take no colon. */
    private fun matchStepKeyword(line: GherkinLine): KeywordMatch? {
        for (keyword in dialect.stepKeywords) {
            if (line.startsWith(keyword)) {
                return KeywordMatch(keyword, line.textAfter(keyword))
            }
        }
        return null
    }

    // -------------------------------------------------------------- document

    private fun parseFeature(): Feature? {
        var tags = emptyList<Tag>()
        var featureLine: GherkinLine? = null

        // Preamble: blank lines, comments, an optional language header, and feature tags.
        while (featureLine == null) {
            val line = current() ?: return null // an empty file is a valid, feature-less document
            when {
                line.isBlank -> advance()
                isLanguageHeader(line) -> {
                    if (tags.isNotEmpty()) {
                        report(line.location(), "the language header must appear before any tags")
                    }
                    applyLanguageHeader(line)
                    advance()
                }
                isComment(line) -> {
                    comments[line.lineNumber] = Comment(line.locationAtColumn(1), line.text)
                    advance()
                }
                isTagLine(line) -> {
                    tags = tags + readTagLine(line)
                    advance()
                }
                isFeatureLine(line) -> featureLine = line
                else -> {
                    report(line, EXPECTED_BEFORE_FEATURE)
                    advance()
                }
            }
        }

        val match = matchKeyword(featureLine, dialect.featureKeywords) ?: return null
        advance()

        return Feature(
            location = featureLine.location(),
            language = language,
            keyword = match.keyword,
            name = match.text,
            description = parseDescription(),
            tags = tags,
            children = parseFeatureChildren(),
        )
    }

    private fun isLanguageHeader(line: GherkinLine): Boolean =
        isComment(line) && LANGUAGE_HEADER.matches(line.trimmed)

    private fun applyLanguageHeader(line: GherkinLine) {
        val tag = LANGUAGE_HEADER.find(line.trimmed)?.groupValues?.get(1) ?: return
        val resolved = GherkinDialects.forLanguageOrNull(tag)
        if (resolved == null) {
            report(line.location(), "Language not supported: $tag")
            return
        }
        language = tag
        dialect = resolved
    }

    private fun readTagLine(line: GherkinLine): List<Tag> {
        val scan = line.tags()
        errors += scan.errors
        return scan.tags
    }

    /**
     * Collects free-text description lines.
     *
     * Interior blank lines are preserved, leading and trailing ones dropped. Original indentation
     * is kept, matching upstream.
     */
    private fun parseDescription(): String {
        val collected = mutableListOf<String>()
        while (true) {
            val line = current() ?: break
            if (!line.isBlank && isStructural(line)) break
            collected += line.text
            advance()
        }
        return collected
            .dropWhile { it.isBlank() }
            .dropLastWhile { it.isBlank() }
            .joinToString("\n")
    }

    // -------------------------------------------------------------- children

    private fun parseFeatureChildren(): List<FeatureChild> {
        val children = mutableListOf<FeatureChild>()
        var pendingTags = emptyList<Tag>()

        while (true) {
            skipBlanksAndComments()
            val line = current() ?: break
            when {
                isTagLine(line) -> {
                    pendingTags = pendingTags + readTagLine(line)
                    advance()
                }
                isRuleLine(line) -> {
                    children += parseRule(pendingTags)
                    pendingTags = emptyList()
                }
                isBackgroundLine(line) -> {
                    if (pendingTags.isNotEmpty()) {
                        report(line.location(), "a Background may not have tags")
                        pendingTags = emptyList()
                    }
                    children += parseBackground()
                }
                isScenarioLine(line) -> {
                    children += parseScenario(pendingTags)
                    pendingTags = emptyList()
                }
                else -> {
                    report(line, EXPECTED_IN_FEATURE)
                    advance()
                }
            }
        }
        return children
    }

    private fun parseRule(tags: List<Tag>): Rule {
        val ruleLine = requireNotNull(current())
        val match = requireNotNull(matchKeyword(ruleLine, dialect.ruleKeywords))
        advance()

        val description = parseDescription()
        val children = mutableListOf<RuleChild>()
        var pendingTags = emptyList<Tag>()

        while (true) {
            skipBlanksAndComments()
            val line = current() ?: break
            // A Rule line ends this rule and starts the next one.
            if (isRuleLine(line)) break
            when {
                isTagLine(line) -> {
                    pendingTags = pendingTags + readTagLine(line)
                    advance()
                }
                isBackgroundLine(line) -> {
                    if (pendingTags.isNotEmpty()) {
                        report(line.location(), "a Background may not have tags")
                        pendingTags = emptyList()
                    }
                    children += parseBackground()
                }
                isScenarioLine(line) -> {
                    children += parseScenario(pendingTags)
                    pendingTags = emptyList()
                }
                else -> {
                    report(line, EXPECTED_IN_RULE)
                    advance()
                }
            }
        }

        return Rule(
            location = ruleLine.location(),
            keyword = match.keyword,
            name = match.text,
            description = description,
            tags = tags,
            children = children,
        )
    }

    private fun parseBackground(): Background {
        val line = requireNotNull(current())
        val match = requireNotNull(matchKeyword(line, dialect.backgroundKeywords))
        advance()
        return Background(
            location = line.location(),
            keyword = match.keyword,
            name = match.text,
            description = parseDescription(),
            steps = parseSteps(),
        )
    }

    private fun parseScenario(tags: List<Tag>): Scenario {
        val line = requireNotNull(current())
        val match = requireNotNull(matchScenarioKeyword(line))
        advance()

        val description = parseDescription()
        val steps = parseSteps()

        val examples = mutableListOf<Examples>()
        var pendingTags = emptyList<Tag>()
        var savedPosition = position

        while (true) {
            skipBlanksAndComments()
            val next = current() ?: break
            when {
                isTagLine(next) -> {
                    // These tags may belong to an Examples block or to the next scenario; only
                    // committing once an Examples line follows keeps the lookahead honest.
                    if (pendingTags.isEmpty()) savedPosition = position
                    pendingTags = pendingTags + readTagLine(next)
                    advance()
                }
                isExamplesLine(next) -> {
                    examples += parseExamples(pendingTags)
                    pendingTags = emptyList()
                }
                else -> {
                    if (pendingTags.isNotEmpty()) position = savedPosition // hand the tags back
                    break
                }
            }
        }

        return Scenario(
            location = line.location(),
            keyword = match.keyword,
            name = match.text,
            description = description,
            tags = tags,
            steps = steps,
            examples = examples,
        )
    }

    private fun parseExamples(tags: List<Tag>): Examples {
        val line = requireNotNull(current())
        val match = requireNotNull(matchKeyword(line, dialect.examplesKeywords))
        advance()

        val description = parseDescription()
        val rows = parseTableRows()

        return Examples(
            location = line.location(),
            keyword = match.keyword,
            name = match.text,
            description = description,
            tags = tags,
            tableHeader = rows.firstOrNull(),
            tableBody = rows.drop(1),
        ).also { validateCellCounts(rows, "examples table") }
    }

    // ----------------------------------------------------------------- steps

    private fun parseSteps(): List<Step> {
        val steps = mutableListOf<Step>()
        while (true) {
            skipBlanksAndComments()
            val line = current() ?: break
            val match = matchStepKeyword(line) ?: break
            advance()

            var dataTable: DataTable? = null
            var docString: DocString? = null

            skipBlanksAndComments()
            val argument = current()
            if (argument != null) {
                val delimiter = docStringDelimiter(argument)
                when {
                    delimiter != null -> docString = parseDocString(argument, delimiter)
                    isTableRow(argument) -> {
                        val rows = parseTableRows()
                        validateCellCounts(rows, "table")
                        dataTable = rows.firstOrNull()?.let { DataTable(it.location, rows) }
                    }
                }
            }

            steps += Step(
                location = line.location(),
                keyword = match.keyword,
                keywordType = dialect.stepKeywordType(match.keyword),
                text = match.text,
                dataTable = dataTable,
                docString = docString,
            )
        }
        return steps
    }

    /**
     * Reads consecutive table rows.
     *
     * Blank lines and comments between rows do not end the table — Gherkin allows them almost
     * everywhere, and a table is terminated by the next construct instead.
     */
    private fun parseTableRows(): List<TableRow> {
        val rows = mutableListOf<TableRow>()
        while (true) {
            skipBlanksAndComments()
            val line = current() ?: break
            if (!isTableRow(line)) break
            rows += TableRow(line.location(), line.tableCells())
            advance()
        }
        return rows
    }

    private fun validateCellCounts(rows: List<TableRow>, what: String) {
        val expected = rows.firstOrNull()?.cells?.size ?: return
        for (row in rows.drop(1)) {
            if (row.cells.size != expected) {
                report(row.location, "inconsistent cell count within the $what")
            }
        }
    }

    private fun parseDocString(open: GherkinLine, delimiter: String): DocString {
        val indentToRemove = open.indent
        val mediaType = open.textAfter(delimiter).ifEmpty { null }
        advance()

        val content = mutableListOf<String>()
        var closed = false
        while (true) {
            val line = current() ?: break
            if (line.startsWith(delimiter)) {
                advance()
                closed = true
                break
            }
            content += unescapeDocString(stripIndent(line.text, indentToRemove), delimiter)
            advance()
        }

        if (!closed) {
            report(open.location(), "unexpected end of file, expected: #DocStringSeparator")
        }

        return DocString(
            location = open.location(),
            content = content.joinToString("\n"),
            mediaType = mediaType,
            delimiter = delimiter,
        )
    }

    /** Removes up to [amount] leading whitespace characters, never more than are present. */
    private fun stripIndent(text: String, amount: Int): String {
        var removed = 0
        while (removed < amount && removed < text.length && text[removed].isWhitespace()) removed++
        return text.substring(removed)
    }

    private fun unescapeDocString(text: String, delimiter: String): String =
        text.replace("\\$delimiter", delimiter)

    private companion object {
        val EXPECTED_BEFORE_FEATURE =
            listOf("#EOF", "#Language", "#TagLine", "#FeatureLine", "#Comment", "#Empty")
        val EXPECTED_IN_FEATURE =
            listOf("#EOF", "#TagLine", "#BackgroundLine", "#ScenarioLine", "#RuleLine", "#Comment", "#Empty")
        val EXPECTED_IN_RULE =
            listOf("#EOF", "#TagLine", "#BackgroundLine", "#ScenarioLine", "#RuleLine", "#Comment", "#Empty")

        fun splitLines(path: String, source: String): List<GherkinLine> =
            source
                .removePrefix("﻿") // a UTF-8 BOM is not part of the first keyword
                .split('\n')
                .mapIndexed { index, text ->
                    GherkinLine(path, index + 1, text.removeSuffix("\r"))
                }
    }
}

private data class KeywordMatch(val keyword: String, val text: String)
