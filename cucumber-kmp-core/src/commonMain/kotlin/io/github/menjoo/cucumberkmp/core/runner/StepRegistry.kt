package io.github.menjoo.cucumberkmp.core.runner

import io.github.menjoo.cucumberkmp.core.SourceLocation
import io.github.menjoo.cucumberkmp.core.expression.CucumberExpression
import io.github.menjoo.cucumberkmp.core.expression.Expression
import io.github.menjoo.cucumberkmp.core.expression.ParameterTypeRegistry
import io.github.menjoo.cucumberkmp.core.expression.RegularExpression
import io.github.menjoo.cucumberkmp.core.tag.TagExpression
import io.github.menjoo.cucumberkmp.core.tag.TagExpressionParser

/** One step definition: an [Expression] to match against, and a body to run when it does. */
public class StepDefinition(
    public val expression: Expression,
    public val location: SourceLocation,
    public val body: suspend (List<Any?>) -> Unit,
) {
    override fun toString(): String = "${expression.source} ($location)"
}

/** When a [Hook] runs relative to a scenario. */
public enum class HookKind { BEFORE, AFTER }

/**
 * Setup or teardown around a scenario.
 *
 * [tagExpression] restricts the hook to matching scenarios; `null` means every scenario.
 */
public class Hook(
    public val kind: HookKind,
    public val tagExpression: TagExpression?,
    public val location: SourceLocation,
    public val body: suspend () -> Unit,
) {
    internal fun appliesTo(tags: List<String>): Boolean = tagExpression?.evaluate(tags) ?: true
}

/** The step definitions and hooks available to one scenario. */
public interface StepRegistry {
    public val definitions: List<StepDefinition>
    public val hooks: List<Hook>
}

/**
 * Produces a fresh [StepRegistry] per scenario.
 *
 * This is what gives Cucumber's per-scenario isolation without a DI container: because [steps]
 * re-runs its block for every scenario, any `var` declared inside it is fresh each time, and
 * closures over it cannot leak state between scenarios. See ARCHITECTURE.md §8.
 */
public fun interface StepRegistryFactory {
    public fun create(): StepRegistry
}

/**
 * Declares step definitions without annotations or code generation.
 *
 * This is the mechanism the framework stands on; the `@Given`/`@When`/`@Then` annotations are
 * ergonomic sugar over it, added by KSP in Phase 2. Keeping it usable on its own is deliberate —
 * see ARCHITECTURE.md §13.1.
 *
 * ```kotlin
 * val calculatorSteps = steps {
 *     var calculator = Calculator()   // fresh for every scenario
 *
 *     given("I have entered {int}") { value: Int -> calculator.enter(value) }
 *     whenever("I press add") { calculator.add() }
 *     then("the result should be {int}") { expected: Int ->
 *         assertEquals(expected, calculator.result)
 *     }
 * }
 * ```
 *
 * `given`, `whenever`, `then` and `step` are interchangeable: Gherkin does not match on the
 * keyword, so they exist only to make the definitions read like the feature file.
 */
public fun steps(block: StepsBuilder.() -> Unit): StepRegistryFactory =
    StepRegistryFactory {
        val builder = StepsBuilder()
        builder.block()
        builder.build()
    }

/** Receiver of the [steps] DSL. */
public class StepsBuilder internal constructor() {

    private val definitions = mutableListOf<StepDefinition>()
    private val hooks = mutableListOf<Hook>()

    /**
     * The parameter types available to expressions declared here.
     *
     * Register custom types on it before declaring the steps that use them.
     */
    public val parameterTypes: ParameterTypeRegistry = ParameterTypeRegistry()

    /**
     * Registers a step definition taking its arguments as a list.
     *
     * The typed [step] overloads delegate here; call it directly only for a variadic definition.
     * An expression wrapped in `^`/`$` is treated as a regular expression, matching Cucumber.
     */
    public fun define(expression: String, body: suspend (List<Any?>) -> Unit) {
        val compiled: Expression =
            if (expression.startsWith("^") && expression.endsWith("$")) {
                RegularExpression(Regex(expression), parameterTypes)
            } else {
                CucumberExpression(expression, parameterTypes)
            }
        definitions += StepDefinition(compiled, SourceLocation(DSL_LOCATION, definitions.size + 1), body)
    }

    /** Runs before each scenario, optionally only those matching [tagExpression]. */
    public fun before(tagExpression: String? = null, body: suspend () -> Unit) {
        hooks += Hook(
            HookKind.BEFORE,
            tagExpression?.let { TagExpressionParser.parse(it) },
            SourceLocation(DSL_LOCATION, hooks.size + 1),
            body,
        )
    }

    /** Runs after each scenario, including when it failed. */
    public fun after(tagExpression: String? = null, body: suspend () -> Unit) {
        hooks += Hook(
            HookKind.AFTER,
            tagExpression?.let { TagExpressionParser.parse(it) },
            SourceLocation(DSL_LOCATION, hooks.size + 1),
            body,
        )
    }

    internal fun build(): StepRegistry = object : StepRegistry {
        override val definitions: List<StepDefinition> = this@StepsBuilder.definitions.toList()
        override val hooks: List<Hook> = this@StepsBuilder.hooks.toList()
    }

    public companion object {
        /** Stand-in path for DSL-declared definitions, which have no file of their own. */
        public const val DSL_LOCATION: String = "<steps DSL>"
    }
}

// --- typed arities -------------------------------------------------------------------------
//
// One overload per arity, because Kotlin cannot infer a lambda's parameter count and types from a
// variadic signature. Arities 0-3 cover the overwhelming majority of step definitions; use
// `define` directly beyond that.

public fun StepsBuilder.step(expression: String, body: suspend () -> Unit): Unit =
    define(expression) { body() }

public inline fun <reified A> StepsBuilder.step(
    expression: String,
    crossinline body: suspend (A) -> Unit,
): Unit = define(expression) { arguments -> body(arguments.stepArgument(0)) }

public inline fun <reified A, reified B> StepsBuilder.step(
    expression: String,
    crossinline body: suspend (A, B) -> Unit,
): Unit = define(expression) { arguments ->
    body(arguments.stepArgument(0), arguments.stepArgument(1))
}

public inline fun <reified A, reified B, reified C> StepsBuilder.step(
    expression: String,
    crossinline body: suspend (A, B, C) -> Unit,
): Unit = define(expression) { arguments ->
    body(arguments.stepArgument(0), arguments.stepArgument(1), arguments.stepArgument(2))
}

public fun StepsBuilder.given(expression: String, body: suspend () -> Unit): Unit = step(expression, body)

public inline fun <reified A> StepsBuilder.given(
    expression: String,
    crossinline body: suspend (A) -> Unit,
): Unit = step(expression, body)

public inline fun <reified A, reified B> StepsBuilder.given(
    expression: String,
    crossinline body: suspend (A, B) -> Unit,
): Unit = step(expression, body)

public inline fun <reified A, reified B, reified C> StepsBuilder.given(
    expression: String,
    crossinline body: suspend (A, B, C) -> Unit,
): Unit = step(expression, body)

/** Named `whenever` because `when` is a Kotlin hard keyword. */
public fun StepsBuilder.whenever(expression: String, body: suspend () -> Unit): Unit = step(expression, body)

public inline fun <reified A> StepsBuilder.whenever(
    expression: String,
    crossinline body: suspend (A) -> Unit,
): Unit = step(expression, body)

public inline fun <reified A, reified B> StepsBuilder.whenever(
    expression: String,
    crossinline body: suspend (A, B) -> Unit,
): Unit = step(expression, body)

public inline fun <reified A, reified B, reified C> StepsBuilder.whenever(
    expression: String,
    crossinline body: suspend (A, B, C) -> Unit,
): Unit = step(expression, body)

public fun StepsBuilder.then(expression: String, body: suspend () -> Unit): Unit = step(expression, body)

public inline fun <reified A> StepsBuilder.then(
    expression: String,
    crossinline body: suspend (A) -> Unit,
): Unit = step(expression, body)

public inline fun <reified A, reified B> StepsBuilder.then(
    expression: String,
    crossinline body: suspend (A, B) -> Unit,
): Unit = step(expression, body)

public inline fun <reified A, reified B, reified C> StepsBuilder.then(
    expression: String,
    crossinline body: suspend (A, B, C) -> Unit,
): Unit = step(expression, body)

/**
 * Casts a captured argument to the type the step body declares.
 *
 * A mismatch throws here, at run time. KSP-generated registries check the same binding at *compile*
 * time, which is the whole reason the annotations exist — see ARCHITECTURE.md §4b.
 *
 * Note that on Kotlin/JS numeric type checks cannot distinguish `Int` from `Double`, so a wrong
 * numeric binding may slip through there. See DEVIATIONS.md.
 */
public inline fun <reified T> List<Any?>.stepArgument(index: Int): T {
    val value = getOrNull(index)
    if (value is T) return value
    throwStepArgumentTypeError(index, value, T::class.simpleName)
}

/** Thrown when a captured argument cannot be bound to the type a step body declares. */
public class StepArgumentTypeException internal constructor(message: String) : RuntimeException(message)

/** Exists so the public inline [stepArgument] need not reach the internal constructor. */
@PublishedApi
internal fun throwStepArgumentTypeError(index: Int, value: Any?, expected: String?): Nothing =
    throw StepArgumentTypeException(
        "Step argument $index is ${value?.let { it::class.simpleName } ?: "null"} " +
            "($value), which cannot be bound to ${expected ?: "the declared type"}",
    )
