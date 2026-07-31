package io.github.menjoo.cucumberkmp.core.expression

/**
 * Something a step's text can be matched against: a [CucumberExpression] or a [RegularExpression].
 */
public interface Expression {

    /** The expression as written by the author. */
    public val source: String

    /** The compiled pattern. Mostly of interest for diagnostics. */
    public val regexp: Regex

    /**
     * Matches [text], returning one [Argument] per parameter, or `null` if it does not match.
     *
     * An empty list means the expression matched but takes no arguments — distinct from `null`.
     */
    public fun match(text: String): List<Argument>?
}

/**
 * One captured parameter value.
 *
 * [group] keeps the raw matched text so failure messages can show what was captured before
 * conversion; it is `null` when the parameter's group did not participate in the match, which an
 * optional capture group in a regular expression allows.
 */
public data class Argument(
    public val value: Any?,
    public val group: String?,
    public val parameterTypeName: String,
)
