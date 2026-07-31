package io.github.menjoo.cucumberkmp.core.gherkin

import io.github.menjoo.cucumberkmp.core.SourceLocation

/**
 * One syntax error, anchored to the exact position that caused it.
 *
 * Rendered as `(line:column): message`, which is the shape upstream Gherkin uses and therefore
 * the shape users will search for when they hit one.
 */
public data class GherkinParseError(
    public val location: SourceLocation,
    public val message: String,
) {
    override fun toString(): String = "(${location.line}:${location.column}): $message"
}

/**
 * Thrown when a `.feature` file cannot be parsed.
 *
 * Carries *all* errors found, not just the first: a malformed feature file usually has more than
 * one problem, and reporting them one build at a time is a poor experience. The parser recovers by
 * skipping the offending line and continuing.
 */
public class GherkinParseException(
    public val path: String,
    public val errors: List<GherkinParseError>,
) : Exception(renderMessage(path, errors)) {

    public companion object {
        private fun renderMessage(path: String, errors: List<GherkinParseError>): String {
            val heading =
                if (errors.size == 1) "Parse error in $path" else "${errors.size} parse errors in $path"
            return errors.joinToString(separator = "\n", prefix = "$heading:\n") { "  $it" }
        }
    }
}
