package io.github.menjoo.cucumberkmp.core

/**
 * A position in a source file — either a `.feature` file or a Kotlin file containing step
 * definitions.
 *
 * Every AST node and every step definition carries one. Failure messages are only as good as
 * these locations, so they are threaded through the parser rather than reconstructed later.
 *
 * [line] and [column] are 1-based, matching Gherkin's own convention and every editor's gutter.
 *
 * ```kotlin
 * SourceLocation("calculator.feature", line = 7, column = 3).toString() // calculator.feature:7:3
 * ```
 */
public data class SourceLocation(
    public val path: String,
    public val line: Int,
    public val column: Int = 1,
) {
    init {
        require(line >= 1) { "line is 1-based, got $line" }
        require(column >= 1) { "column is 1-based, got $column" }
    }

    /** Renders as `path:line:column`, the form terminals and IDEs turn into a clickable link. */
    override fun toString(): String = "$path:$line:$column"
}
