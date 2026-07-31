package io.github.menjoo.cucumberkmp.annotations

/**
 * Marks a function as a step definition for a `Given` step.
 *
 * The [value] is a Cucumber Expression (or a regular expression, if it is wrapped in `^`/`$`).
 * Mirrors `io.cucumber.java.en.Given`.
 *
 * ```kotlin
 * @Given("I have {int} eggs")
 * fun iHaveEggs(count: Int) { … }
 * ```
 *
 * Cucumber does not distinguish keywords when matching a step — `Given`, `When` and `Then` are
 * interchangeable at match time, and the keyword exists for readability. Use [Step] when a
 * definition is genuinely keyword-agnostic.
 *
 * See https://cucumber.io/docs/cucumber/step-definitions/
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
public annotation class Given(val value: String)

/**
 * Marks a function as a step definition for a `When` step. See [Given] for matching semantics.
 *
 * Named `When` to mirror `io.cucumber.java.en.When`. Note that `when` is a Kotlin hard keyword,
 * so referring to this annotation in code that also uses `when` expressions is unambiguous only
 * because annotation use sites are prefixed with `@`.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
public annotation class When(val value: String)

/**
 * Marks a function as a step definition for a `Then` step. See [Given] for matching semantics.
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
public annotation class Then(val value: String)

/**
 * Marks a function as a keyword-agnostic step definition.
 *
 * Equivalent to annotating the same function with [Given], [When] and [Then].
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
public annotation class Step(val value: String)

/**
 * Runs the annotated function before each scenario.
 *
 * [tagExpression] optionally restricts the hook to scenarios matching a tag expression such as
 * `"@smoke and not @wip"`; an empty expression runs for every scenario.
 *
 * See https://cucumber.io/docs/cucumber/api/#hooks
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
public annotation class Before(val tagExpression: String = "")

/**
 * Runs the annotated function after each scenario, including when the scenario failed.
 *
 * `After` hooks run in reverse registration order, matching cucumber-jvm.
 *
 * See https://cucumber.io/docs/cucumber/api/#hooks
 */
@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.SOURCE)
public annotation class After(val tagExpression: String = "")

/**
 * Marks a class as containing step definitions ("glue", in Cucumber's terminology).
 *
 * The annotation processor instantiates one instance per scenario, so mutable properties are a
 * safe place to carry state between the steps of a single scenario. See ARCHITECTURE.md §8.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
public annotation class Steps
