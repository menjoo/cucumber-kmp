package io.github.menjoo.cucumberkmp.core.gherkin

import io.github.menjoo.cucumberkmp.core.SourceLocation

/**
 * The parsed form of one `.feature` file.
 *
 * Modelled on the AST that cucumber/gherkin publishes, so upstream's test corpus maps onto it
 * directly and anyone who knows Cucumber recognises the shape. Deliberate differences: nodes are
 * Kotlin data classes rather than protobuf-derived types, `FeatureChild`/`RuleChild` are sealed
 * interfaces instead of records with three nullable fields, and locations are [SourceLocation].
 *
 * The whole tree is immutable and serialisable as Kotlin constructor calls — which is how a
 * feature file reaches Kotlin/Native and Wasm, where reading it from disk is impossible.
 * See ARCHITECTURE.md §4a.
 */
public data class GherkinDocument(
    public val path: String,
    public val feature: Feature?,
    public val comments: List<Comment> = emptyList(),
)

/** A whole-line comment. Gherkin has no trailing comments. */
public data class Comment(
    public val location: SourceLocation,
    public val text: String,
)

/** A `@tag` on a feature, rule, scenario or examples table. [name] includes the leading `@`. */
public data class Tag(
    public val location: SourceLocation,
    public val name: String,
)

/** A node that may appear directly under a [Feature]. */
public sealed interface FeatureChild

/** A node that may appear directly under a [Rule]. */
public sealed interface RuleChild

public data class Feature(
    public val location: SourceLocation,
    public val language: String,
    public val keyword: String,
    public val name: String,
    public val description: String = "",
    public val tags: List<Tag> = emptyList(),
    public val children: List<FeatureChild> = emptyList(),
)

/**
 * A `Rule` (Gherkin 6+): a grouping of scenarios that share a business rule, optionally with its
 * own `Background`.
 *
 * See https://cucumber.io/docs/gherkin/reference/#rule
 */
public data class Rule(
    public val location: SourceLocation,
    public val keyword: String,
    public val name: String,
    public val description: String = "",
    public val tags: List<Tag> = emptyList(),
    public val children: List<RuleChild> = emptyList(),
) : FeatureChild

/**
 * Steps run before each scenario in the enclosing [Feature] or [Rule].
 *
 * A background has no tags — it is not independently selectable.
 */
public data class Background(
    public val location: SourceLocation,
    public val keyword: String,
    public val name: String,
    public val description: String = "",
    public val steps: List<Step> = emptyList(),
) : FeatureChild, RuleChild

/**
 * A `Scenario`, or a `Scenario Outline` when [examples] is non-empty.
 *
 * Gherkin does not model outlines as a separate node type; the distinction is the presence of an
 * `Examples` table, with [keyword] preserving what the author wrote.
 */
public data class Scenario(
    public val location: SourceLocation,
    public val keyword: String,
    public val name: String,
    public val description: String = "",
    public val tags: List<Tag> = emptyList(),
    public val steps: List<Step> = emptyList(),
    public val examples: List<Examples> = emptyList(),
) : FeatureChild, RuleChild {

    /** True when this scenario is parameterised by one or more `Examples` tables. */
    public val isOutline: Boolean get() = examples.isNotEmpty()
}

/**
 * An `Examples` table supplying values for a `Scenario Outline`'s `<placeholder>`s.
 *
 * [tableHeader] is null only for an `Examples` block with no rows at all, which Gherkin permits.
 */
public data class Examples(
    public val location: SourceLocation,
    public val keyword: String,
    public val name: String,
    public val description: String = "",
    public val tags: List<Tag> = emptyList(),
    public val tableHeader: TableRow? = null,
    public val tableBody: List<TableRow> = emptyList(),
)

/**
 * One step of a scenario or background.
 *
 * [keyword] keeps its trailing space, exactly as Gherkin reports it (`"Given "`), and [text] is
 * everything after it.
 *
 * A step may carry a [dataTable], a [docString], or — contrary to the usual reading of "one
 * argument per step" — **both**, in either order. Upstream accepts this, so we do too.
 */
public data class Step(
    public val location: SourceLocation,
    public val keyword: String,
    public val keywordType: StepKeywordType,
    public val text: String,
    public val dataTable: DataTable? = null,
    public val docString: DocString? = null,
)

/**
 * What a step keyword means.
 *
 * `*` maps to [UNKNOWN] rather than [CONJUNCTION]: it is a member of every keyword category, so
 * its meaning cannot be determined lexically. This matches upstream.
 */
public enum class StepKeywordType {
    CONTEXT,
    ACTION,
    OUTCOME,
    CONJUNCTION,
    UNKNOWN,
}

/** A table attached to a step as its argument. All rows are guaranteed the same cell count. */
public data class DataTable(
    public val location: SourceLocation,
    public val rows: List<TableRow>,
)

public data class TableRow(
    public val location: SourceLocation,
    public val cells: List<TableCell>,
)

public data class TableCell(
    public val location: SourceLocation,
    public val value: String,
)

/**
 * A multi-line string argument delimited by `"""` or ` ``` `.
 *
 * [content] has the opening delimiter's indentation stripped from every line and delimiter
 * escapes resolved. [mediaType] is the optional annotation on the opening line, e.g.
 * ` ```json `.
 */
public data class DocString(
    public val location: SourceLocation,
    public val content: String,
    public val mediaType: String? = null,
    public val delimiter: String = DOC_STRING_QUOTES,
) {
    public companion object {
        public const val DOC_STRING_QUOTES: String = "\"\"\""
        public const val DOC_STRING_BACKTICKS: String = "```"
    }
}
