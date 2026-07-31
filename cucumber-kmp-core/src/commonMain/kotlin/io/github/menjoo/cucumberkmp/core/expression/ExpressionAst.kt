package io.github.menjoo.cucumberkmp.core.expression

/**
 * A lexical token of a Cucumber Expression.
 *
 * [start] and [end] are offsets into the expression, counted in **code points** as upstream counts
 * them — so an astral character such as `😀` advances the offset by one, not two.
 */
public data class ExpressionToken(
    public val text: String,
    public val type: ExpressionTokenType,
    public val start: Int,
    public val end: Int,
)

/**
 * The token vocabulary, matching upstream's names because they appear verbatim in error messages
 * users compare against Cucumber's.
 */
public enum class ExpressionTokenType(
    internal val symbol: String? = null,
    internal val purpose: String? = null,
) {
    START_OF_LINE,
    END_OF_LINE,
    WHITE_SPACE,
    BEGIN_OPTIONAL("(", "optional text"),
    END_OPTIONAL(")", "optional text"),
    BEGIN_PARAMETER("{", "a parameter"),
    END_PARAMETER("}", "a parameter"),
    ALTERNATION("/", "alternation"),
    TEXT,
    ;

    internal fun requireSymbol(): String = requireNotNull(symbol) { "$name has no symbol" }

    internal fun requirePurpose(): String = requireNotNull(purpose) { "$name has no purpose" }

    internal companion object {
        const val ESCAPE_CHARACTER: Char = '\\'

        /**
         * Classifies one code point.
         *
         * Astral code points arrive as two-char strings and are always plain text: no structural
         * character or whitespace lives outside the BMP.
         */
        fun of(codePoint: String): ExpressionTokenType {
            if (codePoint.length != 1) return TEXT
            val character = codePoint[0]
            return when {
                character.isWhitespace() -> WHITE_SPACE
                character == '/' -> ALTERNATION
                character == '{' -> BEGIN_PARAMETER
                character == '}' -> END_PARAMETER
                character == '(' -> BEGIN_OPTIONAL
                character == ')' -> END_OPTIONAL
                else -> TEXT
            }
        }

        /** Only these characters carry a meaning worth escaping. */
        fun canEscape(codePoint: String): Boolean =
            codePoint.length == 1 && (codePoint[0].isWhitespace() || codePoint[0] in "\\/{}()")
    }
}

/** A node of the Cucumber Expression AST. */
public data class ExpressionNode(
    public val type: ExpressionNodeType,
    public val start: Int,
    public val end: Int,
    public val nodes: List<ExpressionNode>? = null,
    public val token: String? = null,
) {
    /**
     * The node's text: its own token, or its children's text concatenated.
     *
     * Concatenation is what gives a `PARAMETER_NODE` its parameter-type name, since the name
     * arrives as a run of child text nodes.
     */
    public val text: String
        get() = token ?: nodes?.joinToString(separator = "") { it.text } ?: ""

    internal fun requireNodes(): List<ExpressionNode> =
        requireNotNull(nodes) { "$type has no child nodes" }
}

public enum class ExpressionNodeType {
    TEXT_NODE,
    OPTIONAL_NODE,
    ALTERNATION_NODE,
    ALTERNATIVE_NODE,
    PARAMETER_NODE,
    EXPRESSION_NODE,
}
