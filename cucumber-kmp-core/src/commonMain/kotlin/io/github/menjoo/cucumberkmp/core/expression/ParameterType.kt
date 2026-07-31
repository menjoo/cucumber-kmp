package io.github.menjoo.cucumberkmp.core.expression

/**
 * A named type that a `{placeholder}` in a Cucumber Expression can capture.
 *
 * [regexps] are alternatives: `int` accepts both `-?\d+` and `\d+`. Keep them conservative —
 * `kotlin.text.Regex` delegates to four different platform engines, so named groups, lookbehind
 * and Unicode property classes are not portable. See ARCHITECTURE.md §13.2.
 *
 * [transform] receives the whole text the parameter matched, quotes and all, and returns the value
 * handed to the step function.
 *
 * ```kotlin
 * ParameterType("colour", listOf("red|blue|green")) { Colour.valueOf(it.uppercase()) }
 * ```
 */
public class ParameterType<out T>(
    public val name: String,
    public val regexps: List<String>,
    public val useForSnippets: Boolean = true,
    public val preferForRegexpMatch: Boolean = false,
    internal val transform: (String) -> T?,
) {
    public constructor(
        name: String,
        regexp: String,
        useForSnippets: Boolean = true,
        preferForRegexpMatch: Boolean = false,
        transform: (String) -> T?,
    ) : this(name, listOf(regexp), useForSnippets, preferForRegexpMatch, transform)

    /** The `{}` placeholder, which captures anything and applies no conversion. */
    public val isAnonymous: Boolean get() = name == ANONYMOUS_NAME

    internal fun convert(group: String?): T? = group?.let(transform)

    override fun toString(): String = "ParameterType({$name})"

    public companion object {
        public const val ANONYMOUS_NAME: String = ""
    }
}

/**
 * The parameter types available to an expression.
 *
 * Pre-populated with Cucumber's built-ins: `{int}`, `{long}`, `{short}`, `{byte}`, `{float}`,
 * `{double}`, `{bigdecimal}`, `{biginteger}`, `{word}`, `{string}` and the anonymous `{}`.
 */
public class ParameterTypeRegistry {

    private val byName = mutableMapOf<String, ParameterType<*>>()

    init {
        // Integers accept an optional sign; the unsigned alternative exists so that `-` is not
        // swallowed when an expression puts it in surrounding text.
        defineParameterType(ParameterType("int", INTEGER_REGEXPS) { it.toInt() })
        defineParameterType(ParameterType("long", INTEGER_REGEXPS) { it.toLong() })
        defineParameterType(ParameterType("short", INTEGER_REGEXPS) { it.toShort() })
        defineParameterType(ParameterType("byte", INTEGER_REGEXPS) { it.toByte() })
        defineParameterType(ParameterType("float", FLOAT_REGEXPS) { it.stripGroupSeparators().toFloat() })
        defineParameterType(ParameterType("double", FLOAT_REGEXPS) { it.stripGroupSeparators().toDouble() })

        // Kotlin's common stdlib has no arbitrary-precision numerics, so these keep the raw text
        // rather than silently narrowing to Long/Double. See DEVIATIONS.md.
        defineParameterType(ParameterType("biginteger", INTEGER_REGEXPS) { it })
        defineParameterType(ParameterType("bigdecimal", FLOAT_REGEXPS) { it.stripGroupSeparators() })

        defineParameterType(ParameterType("word", WORD_REGEXPS) { it })
        defineParameterType(ParameterType("string", STRING_REGEXPS) { it.unquote() })
        defineParameterType(ParameterType(ParameterType.ANONYMOUS_NAME, ANONYMOUS_REGEXPS) { it })
    }

    /** @throws IllegalArgumentException if a type with the same name is already defined. */
    public fun defineParameterType(parameterType: ParameterType<*>) {
        require(byName.put(parameterType.name, parameterType) == null) {
            "There is already a parameter type with name ${parameterType.name}"
        }
    }

    public fun lookupByTypeName(name: String): ParameterType<*>? = byName[name]

    /**
     * Finds a parameter type whose regexp is exactly [regexp].
     *
     * Used when matching a raw regular expression, so that `(\d+)` yields an `Int` rather than a
     * `String`. Types marked [ParameterType.preferForRegexpMatch] win ties.
     */
    public fun lookupByRegexp(regexp: String): ParameterType<*>? {
        val candidates = byName.values.filter { regexp in it.regexps }
        return candidates.firstOrNull { it.preferForRegexpMatch } ?: candidates.firstOrNull()
    }

    public val parameterTypes: Collection<ParameterType<*>> get() = byName.values

    private companion object {
        val INTEGER_REGEXPS = listOf("-?\\d+", "\\d+")

        // Sign, then significand with optional group separators and fraction, then exponent.
        // Fixed to the English separators: a locale-aware variant would make the generated regex
        // depend on ambient state, which a compile-time framework cannot afford.
        val FLOAT_REGEXPS = listOf(
            "[-+]?(?:\\d+(?:[,]\\d+)*(?:[.]\\d+)?|[.]\\d+)(?:[eE][-+]?\\d+)?",
        )

        val WORD_REGEXPS = listOf("[^\\s]+")

        val STRING_REGEXPS = listOf(
            "\"([^\"\\\\]*(\\\\.[^\"\\\\]*)*)\"",
            "'([^'\\\\]*(\\\\.[^'\\\\]*)*)'",
        )

        val ANONYMOUS_REGEXPS = listOf(".*")

        fun String.stripGroupSeparators(): String = replace(",", "")

        /**
         * Removes the surrounding quotes and unescapes quotes within.
         *
         * Only `\"` and `\'` are unescaped — matching upstream, which deliberately leaves `\\`
         * alone.
         */
        fun String.unquote(): String =
            if (length >= 2) {
                substring(1, length - 1).replace("\\\"", "\"").replace("\\'", "'")
            } else {
                this
            }
    }
}
