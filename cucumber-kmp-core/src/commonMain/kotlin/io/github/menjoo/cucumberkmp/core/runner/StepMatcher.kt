package io.github.menjoo.cucumberkmp.core.runner

import io.github.menjoo.cucumberkmp.core.expression.Argument

/** The outcome of resolving one step's text against the registered definitions. */
public sealed interface StepMatch {

    /** Exactly one definition matched. */
    public data class Matched(
        public val definition: StepDefinition,
        public val arguments: List<Argument>,
    ) : StepMatch

    /** No definition matched. [snippet] is a paste-able starting point. */
    public data class Undefined(public val snippet: String) : StepMatch

    /** More than one definition matched, which is always a bug. */
    public data class Ambiguous(public val definitions: List<StepDefinition>) : StepMatch
}

/**
 * Resolves step text to a single step definition.
 *
 * Ambiguity is reported rather than resolved: two definitions matching one step means the author
 * cannot know which will run. The KSP processor detects the same condition at compile time where it
 * can — see ARCHITECTURE.md §10.
 */
public class StepMatcher(private val registry: StepRegistry) {

    public fun match(stepText: String): StepMatch {
        val matches = registry.definitions.mapNotNull { definition ->
            definition.expression.match(stepText)?.let { definition to it }
        }

        return when (matches.size) {
            0 -> StepMatch.Undefined(snippetFor(stepText))
            1 -> StepMatch.Matched(matches.single().first, matches.single().second)
            else -> StepMatch.Ambiguous(matches.map { it.first })
        }
    }

    private companion object {

        private val NUMBER = Regex("\\d+")
        private val QUOTED = Regex("\"[^\"]*\"")

        /**
         * Builds a step-definition stub for an undefined step.
         *
         * Literal numbers and quoted strings become `{int}` and `{string}` placeholders, on the
         * assumption that a value written into a step is the thing that varies. Anything else is
         * left as text for the author to parameterise.
         */
        fun snippetFor(stepText: String): String {
            val parameters = mutableListOf<String>()
            var expression = QUOTED.replace(stepText) {
                parameters += "string"
                "{string}"
            }
            expression = NUMBER.replace(expression) {
                parameters += "int"
                "{int}"
            }

            val names = parameters.mapIndexed { index, type ->
                val name = if (type == "int") "number" else "text"
                val suffix = if (parameters.count { it == type } > 1) "${index + 1}" else ""
                "$name$suffix: ${if (type == "int") "Int" else "String"}"
            }
            val lambda = if (names.isEmpty()) "" else "${names.joinToString()} ->"

            return buildString {
                append("step(\"")
                append(expression.replace("\"", "\\\""))
                append("\") {")
                if (lambda.isNotEmpty()) append(" $lambda")
                append(" TODO(\"implement this step\") }")
            }
        }
    }
}
