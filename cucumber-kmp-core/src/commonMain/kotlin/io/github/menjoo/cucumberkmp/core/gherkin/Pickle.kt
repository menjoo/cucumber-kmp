package io.github.menjoo.cucumberkmp.core.gherkin

import io.github.menjoo.cucumberkmp.core.SourceLocation

/**
 * A single runnable test case compiled from a feature.
 *
 * Cucumber calls these "pickles". Compilation flattens everything the runner would otherwise have
 * to understand: backgrounds are inlined, `Scenario Outline` rows are expanded into one pickle each
 * with `<placeholders>` substituted, and tags are inherited from the feature and rule.
 *
 * The runner therefore never sees the AST — which is what lets the Gradle plugin emit one
 * `@Test` function per pickle. See ARCHITECTURE.md §4a.
 */
public data class Pickle(
    public val name: String,
    public val language: String,
    /** The scenario's location, or the `Examples` row's for a compiled outline row. */
    public val location: SourceLocation,
    public val tags: List<String>,
    public val steps: List<PickleStep>,
)

/** One step of a [Pickle], with placeholders already substituted. */
public data class PickleStep(
    public val text: String,
    public val type: StepKeywordType,
    public val location: SourceLocation,
    public val dataTable: DataTable? = null,
    public val docString: DocString? = null,
)

/**
 * Compiles a [GherkinDocument] into the test cases a runner executes.
 *
 * ```kotlin
 * val pickles = PickleCompiler.compile(GherkinParser.parse(path, source))
 * ```
 */
public object PickleCompiler {

    public fun compile(document: GherkinDocument): List<Pickle> {
        val feature = document.feature ?: return emptyList()
        val pickles = mutableListOf<Pickle>()
        var featureBackground = emptyList<Step>()

        for (child in feature.children) {
            when (child) {
                is Background -> featureBackground = featureBackground + child.steps
                is Scenario -> pickles += compileScenario(
                    feature = feature,
                    scenario = child,
                    inheritedTags = feature.tags.names(),
                    backgroundSteps = featureBackground,
                )
                is Rule -> {
                    var ruleBackground = featureBackground
                    for (ruleChild in child.children) {
                        when (ruleChild) {
                            is Background -> ruleBackground = ruleBackground + ruleChild.steps
                            is Scenario -> pickles += compileScenario(
                                feature = feature,
                                scenario = ruleChild,
                                inheritedTags = feature.tags.names() + child.tags.names(),
                                backgroundSteps = ruleBackground,
                            )
                        }
                    }
                }
            }
        }
        return pickles
    }

    private fun compileScenario(
        feature: Feature,
        scenario: Scenario,
        inheritedTags: List<String>,
        backgroundSteps: List<Step>,
    ): List<Pickle> {
        val tags = inheritedTags + scenario.tags.names()

        // A stepless scenario still produces a test case — it simply has nothing to run. Its
        // background is *not* inlined: running setup for a scenario that asserts nothing would be
        // surprising, and upstream's incomplete_scenario.feature pins the behaviour.
        val steps = if (scenario.steps.isEmpty()) emptyList() else backgroundSteps + scenario.steps

        return if (scenario.examples.isEmpty()) {
            listOf(
                Pickle(
                    name = scenario.name,
                    language = feature.language,
                    location = scenario.location,
                    tags = tags,
                    steps = pickleSteps(steps, emptyMap()),
                ),
            )
        } else {
            scenario.examples.flatMap { examples ->
                val header = examples.tableHeader?.cells?.map { it.value } ?: return@flatMap emptyList()
                examples.tableBody.map { row ->
                    val substitutions = header.zip(row.cells.map { it.value }).toMap()
                    Pickle(
                        // The scenario name is interpolated too, so outline rows are
                        // distinguishable in a test report.
                        name = substitute(scenario.name, substitutions),
                        language = feature.language,
                        // An expanded row reports at the row, not at the outline.
                        location = row.location,
                        tags = tags + examples.tags.names(),
                        steps = pickleSteps(steps, substitutions),
                    )
                }
            }
        }
    }

    /**
     * Converts AST steps into pickle steps.
     *
     * Conjunctions (`And`, `But`, `*`) have no meaning of their own, so they inherit the type of
     * the most recent step that did — `Given x / And y` makes both `CONTEXT`.
     */
    private fun pickleSteps(steps: List<Step>, substitutions: Map<String, String>): List<PickleStep> {
        var lastMeaningfulType = StepKeywordType.UNKNOWN
        return steps.map { step ->
            val type = when (step.keywordType) {
                StepKeywordType.CONTEXT, StepKeywordType.ACTION, StepKeywordType.OUTCOME -> {
                    lastMeaningfulType = step.keywordType
                    step.keywordType
                }
                StepKeywordType.CONJUNCTION, StepKeywordType.UNKNOWN -> lastMeaningfulType
            }
            PickleStep(
                text = substitute(step.text, substitutions),
                type = type,
                location = step.location,
                dataTable = step.dataTable?.substitute(substitutions),
                docString = step.docString?.substitute(substitutions),
            )
        }
    }

    private fun List<Tag>.names(): List<String> = map { it.name }

    private fun DataTable.substitute(substitutions: Map<String, String>): DataTable =
        if (substitutions.isEmpty()) {
            this
        } else {
            copy(
                rows = rows.map { row ->
                    row.copy(cells = row.cells.map { it.copy(value = substitute(it.value, substitutions)) })
                },
            )
        }

    /** Placeholders are substituted in the media type as well as the content. */
    private fun DocString.substitute(substitutions: Map<String, String>): DocString =
        if (substitutions.isEmpty()) {
            this
        } else {
            copy(
                content = substitute(content, substitutions),
                mediaType = mediaType?.let { substitute(it, substitutions) },
            )
        }

    /** Replaces every `<name>` for which a value is supplied; unknown placeholders are left alone. */
    private fun substitute(text: String, substitutions: Map<String, String>): String {
        if (substitutions.isEmpty() || '<' !in text) return text
        var result = text
        for ((name, value) in substitutions) {
            result = result.replace("<$name>", value)
        }
        return result
    }
}
