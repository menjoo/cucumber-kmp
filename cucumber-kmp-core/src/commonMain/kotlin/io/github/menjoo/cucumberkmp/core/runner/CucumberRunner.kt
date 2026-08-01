package io.github.menjoo.cucumberkmp.core.runner

import io.github.menjoo.cucumberkmp.core.gherkin.Pickle
import io.github.menjoo.cucumberkmp.core.gherkin.PickleStep

/** How one step of a scenario ended. */
public enum class StepStatus {
    PASSED,

    /** The step's body threw. */
    FAILED,

    /** An earlier step failed, so this one was never attempted. */
    SKIPPED,

    /** No step definition matched. */
    UNDEFINED,

    /** More than one step definition matched. */
    AMBIGUOUS,
    ;

    public val isFailure: Boolean get() = this == FAILED || this == UNDEFINED || this == AMBIGUOUS
}

public data class StepResult(
    public val step: PickleStep,
    public val status: StepStatus,
    public val failure: Throwable? = null,
    /** Set for [StepStatus.UNDEFINED]: a paste-able step definition stub. */
    public val snippet: String? = null,
)

/** The outcome of running one scenario. */
public data class ScenarioResult(
    public val pickle: Pickle,
    public val steps: List<StepResult>,
    /** A hook failure, which is not attributable to any single step. */
    public val hookFailure: Throwable? = null,
    public val skipped: Boolean = false,
) {
    public val isPassed: Boolean
        get() = !skipped && hookFailure == null && steps.none { it.status.isFailure }

    /** The first thing that went wrong, for reporting. */
    public val failure: Throwable?
        get() = hookFailure ?: steps.firstOrNull { it.status.isFailure }?.failure
}

/**
 * Executes compiled scenarios against a set of step definitions.
 *
 * Ordering follows cucumber-jvm: `Before` hooks in registration order, then steps in order, then
 * `After` hooks in **reverse** registration order — and the `After` hooks run even when a step
 * failed, so teardown is not skipped.
 *
 * Once a step fails, the remaining steps are reported [StepStatus.SKIPPED] rather than attempted,
 * because they would be running against state the failed step never established.
 *
 * ```kotlin
 * val runner = CucumberRunner(calculatorSteps)
 * runner.runOrThrow(pickle)   // from inside a kotlin.test @Test
 * ```
 */
public class CucumberRunner(
    private val registryFactory: StepRegistryFactory,
    /** Restricts which scenarios run. `null` runs every scenario. */
    private val tagFilter: String? = null,
) {

    /** Runs [pickle] and reports what happened, without throwing. */
    public suspend fun run(pickle: Pickle): ScenarioResult {
        // A fresh registry per scenario is what isolates state between scenarios.
        val registry = registryFactory.create()

        if (!selects(pickle)) {
            return ScenarioResult(
                pickle = pickle,
                steps = pickle.steps.map { StepResult(it, StepStatus.SKIPPED) },
                skipped = true,
            )
        }

        val matcher = StepMatcher(registry)
        val results = mutableListOf<StepResult>()
        var hookFailure: Throwable? = null
        var failed = false

        hookFailure = runHooks(registry, HookKind.BEFORE, pickle)
        if (hookFailure != null) failed = true

        for (step in pickle.steps) {
            if (failed) {
                results += StepResult(step, StepStatus.SKIPPED)
                continue
            }
            val result = runStep(matcher, step)
            results += result
            if (result.status.isFailure) failed = true
        }

        // After hooks run in reverse, and regardless of failure.
        val afterFailure = runHooks(registry, HookKind.AFTER, pickle, reversed = true)

        return ScenarioResult(
            pickle = pickle,
            steps = results,
            hookFailure = hookFailure ?: afterFailure,
        )
    }

    /**
     * Runs [pickle] and throws a formatted [AssertionError] if it did not pass.
     *
     * This is the entry point generated test functions call.
     */
    public suspend fun runOrThrow(pickle: Pickle) {
        val result = run(pickle)
        if (!result.isPassed) throw ScenarioFailure(format(result), result.failure)
    }

    private fun selects(pickle: Pickle): Boolean =
        tagFilter == null ||
            io.github.menjoo.cucumberkmp.core.tag.TagExpressionParser.parse(tagFilter)
                .evaluate(pickle.tags)

    private suspend fun runStep(matcher: StepMatcher, step: PickleStep): StepResult =
        when (val match = matcher.match(step.text)) {
            is StepMatch.Undefined ->
                StepResult(step, StepStatus.UNDEFINED, snippet = match.snippet)

            is StepMatch.Ambiguous -> StepResult(
                step,
                StepStatus.AMBIGUOUS,
                failure = AmbiguousStepException(step.text, match.definitions),
            )

            is StepMatch.Matched -> try {
                match.definition.body(argumentsFor(step, match))
                StepResult(step, StepStatus.PASSED)
            } catch (failure: Throwable) {
                StepResult(step, StepStatus.FAILED, failure = failure)
            }
        }

    /**
     * The values handed to a step body: the expression's captures, then the step's attachment.
     *
     * A `DataTable` or `DocString` written under a step is an argument to it, so it arrives after
     * the captured parameters — matching Cucumber, where the attachment is always the last
     * parameter of the step definition.
     */
    private fun argumentsFor(step: PickleStep, match: StepMatch.Matched): List<Any?> =
        match.arguments.map { it.value } + listOfNotNull(step.dataTable, step.docString)

    private suspend fun runHooks(
        registry: StepRegistry,
        kind: HookKind,
        pickle: Pickle,
        reversed: Boolean = false,
    ): Throwable? {
        val applicable = registry.hooks
            .filter { it.kind == kind && it.appliesTo(pickle.tags) }
            .let { if (reversed) it.reversed() else it }

        var firstFailure: Throwable? = null
        for (hook in applicable) {
            try {
                hook.body()
            } catch (failure: Throwable) {
                // Keep running the remaining hooks: teardown that is skipped leaks resources.
                if (firstFailure == null) firstFailure = failure
            }
        }
        return firstFailure
    }

    private companion object {

        /**
         * Renders the scenario with its step statuses and the failure nested underneath.
         *
         * This message is the primary user experience of the whole framework — it is what someone
         * sees when a test goes red — so it reproduces the Gherkin rather than only naming a step.
         */
        fun format(result: ScenarioResult): String = buildString {
            append("Scenario failed: ")
            append(result.pickle.name.ifEmpty { "(unnamed)" })
            append(" (")
            append(result.pickle.location)
            appendLine(")")
            appendLine()

            for (step in result.steps) {
                append("  ")
                append(marker(step.status))
                append(' ')
                append(step.step.text)
                append("   # ")
                append(step.status.name.lowercase())
                appendLine()
            }

            result.steps.firstOrNull { it.status == StepStatus.UNDEFINED }?.let { undefined ->
                appendLine()
                appendLine("Undefined step. Implement it with:")
                appendLine()
                appendLine("    ${undefined.snippet}")
            }

            result.failure?.let { failure ->
                appendLine()
                append(failure::class.simpleName)
                failure.message?.let {
                    append(": ")
                    append(it)
                }
                appendLine()
            }
        }.trimEnd()

        fun marker(status: StepStatus): String = when (status) {
            StepStatus.PASSED -> "✓"
            StepStatus.FAILED -> "✗"
            StepStatus.SKIPPED -> "-"
            StepStatus.UNDEFINED -> "?"
            StepStatus.AMBIGUOUS -> "!"
        }
    }
}

/** Thrown by [CucumberRunner.runOrThrow] when a scenario does not pass. */
public class ScenarioFailure internal constructor(
    message: String,
    override val cause: Throwable?,
) : AssertionError(message)

/** Thrown when a step's text matches more than one step definition. */
public class AmbiguousStepException internal constructor(
    stepText: String,
    public val definitions: List<StepDefinition>,
) : RuntimeException(
    "Step '$stepText' matches ${definitions.size} step definitions:\n" +
        definitions.joinToString("\n") { "  $it" },
)
