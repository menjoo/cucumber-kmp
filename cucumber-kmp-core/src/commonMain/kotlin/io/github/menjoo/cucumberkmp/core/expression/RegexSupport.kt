package io.github.menjoo.cucumberkmp.core.expression

/**
 * Escapes the characters that carry meaning in a regular expression.
 *
 * Hand-written rather than delegating to a platform escape, because the four regex engines behind
 * `kotlin.text.Regex` do not agree on what a quoting construct means, and `\Q…\E` is not available
 * everywhere.
 *
 * Matches upstream's set exactly. Note that `/` and `-` are deliberately absent: they are literal
 * outside a character class on every engine.
 */
internal fun escapeRegex(text: String): String = buildString(text.length) {
    for (character in text) {
        if (character in REGEX_METACHARACTERS) append('\\')
        append(character)
    }
}

private const val REGEX_METACHARACTERS = "\\^[({$.|?*+})]"

/** A capturing group found in a pattern. */
internal data class CapturingGroup(
    /** 1-based index, as `MatchResult.groupValues` indexes them. */
    val index: Int,
    /** The pattern text between the group's parentheses. */
    val source: String,
)

/**
 * Finds the capturing groups of [pattern] that are not nested inside another capturing group.
 *
 * These are the groups that correspond to parameters. Intervening *non*-capturing groups do not
 * change nesting for this purpose, so in `(?:(a))` the `(a)` is still top level.
 *
 * This replaces upstream's `TreeRegexp`/`GroupBuilder` pair. We only ever need a parameter's whole
 * matched text, never its sub-groups, so a full group tree is unnecessary — see DEVIATIONS.md.
 */
internal fun topLevelCapturingGroups(pattern: String): List<CapturingGroup> {
    val groups = mutableListOf<CapturingGroup>()
    // Every group is pushed, capturing or not, so that each ')' pops its own '('.
    val open = ArrayDeque<OpenGroup>()
    var groupIndex = 0
    var capturingDepth = 0
    var index = 0
    var inCharacterClass = false

    while (index < pattern.length) {
        when (pattern[index]) {
            '\\' -> index++ // skip whatever is escaped
            '[' -> inCharacterClass = true
            ']' -> inCharacterClass = false
            '(' -> if (!inCharacterClass) {
                val capturing = isCapturing(pattern, index)
                if (capturing) {
                    groupIndex++
                    capturingDepth++
                }
                open.addLast(OpenGroup(capturing, groupIndex, index + 1))
            }
            ')' -> if (!inCharacterClass) {
                // An unbalanced ')' is left for the regex engine to complain about.
                val opened = open.removeLastOrNull()
                if (opened != null && opened.capturing) {
                    capturingDepth--
                    if (capturingDepth == 0) {
                        groups += CapturingGroup(
                            opened.groupIndex,
                            pattern.substring(opened.contentStart, index),
                        )
                    }
                }
            }
        }
        index++
    }
    return groups.sortedBy { it.index }
}

/**
 * Counts every capturing group in [pattern], nested ones included.
 *
 * Needed to keep a running group index while generating a pattern: a parameter type's own regexp
 * may contain groups, and they shift the indices of everything after it.
 */
internal fun countCapturingGroups(pattern: String): Int {
    var count = 0
    var index = 0
    var inCharacterClass = false
    while (index < pattern.length) {
        when (pattern[index]) {
            '\\' -> index++
            '[' -> inCharacterClass = true
            ']' -> inCharacterClass = false
            '(' -> if (!inCharacterClass && isCapturing(pattern, index)) count++
        }
        index++
    }
    return count
}

private class OpenGroup(val capturing: Boolean, val groupIndex: Int, val contentStart: Int)

/** A `(` starts a capturing group unless it is followed by `?` (any of the `(?…)` constructs). */
private fun isCapturing(pattern: String, openParenIndex: Int): Boolean =
    pattern.getOrNull(openParenIndex + 1) != '?'
