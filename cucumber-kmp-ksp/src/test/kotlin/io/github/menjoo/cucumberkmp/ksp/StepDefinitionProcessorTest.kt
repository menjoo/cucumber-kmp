package io.github.menjoo.cucumberkmp.ksp

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.SourceFile
import com.tschuchort.compiletesting.configureKsp
import com.tschuchort.compiletesting.kspSourcesDir
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Compiles source snippets through the real processor and asserts on what it reports.
 *
 * Every one of these rules existed before this suite did, verified once by hand against a throwaway
 * fixture and then deleted — which proves nothing about the next change. The point of compiling for
 * real is that a diagnostic can only pass if the processor genuinely emits it.
 *
 * `withCompilation = true` means the generated registry is compiled too, so a success case also
 * proves the generated code is valid Kotlin rather than merely well-shaped text.
 */
@OptIn(ExperimentalCompilerApi::class)
class StepDefinitionProcessorTest {

    private class Outcome(
        val succeeded: Boolean,
        val messages: String,
        val generated: List<File>,
    ) {
        val registry: String?
            get() = generated.firstOrNull { it.name == "GeneratedStepRegistry.kt" }?.readText()
    }

    private fun compile(@Language("kotlin") source: String): Outcome {
        val compilation = KotlinCompilation().apply {
            sources = listOf(SourceFile.kotlin("Steps.kt", source))
            inheritClassPath = true
            messageOutputStream = java.io.ByteArrayOutputStream()
            configureKsp {
                symbolProcessorProviders += StepDefinitionProcessorProvider()
                processorOptions["cucumberkmp.generatedPackage"] = "test.generated"
                // Compile what the processor emits, so invalid generated Kotlin fails the test.
                withCompilation = true
            }
        }
        val result = compilation.compile()
        val generated = compilation.kspSourcesDir.walkTopDown().filter { it.isFile }.toList()
        return Outcome(
            succeeded = result.exitCode == KotlinCompilation.ExitCode.OK,
            messages = result.messages,
            generated = generated,
        )
    }

    /** Compiles and requires success, so a broken fixture is not mistaken for a passing rule. */
    private fun compileSuccessfully(@Language("kotlin") source: String): Outcome {
        val outcome = compile(source)
        assertTrue(outcome.succeeded, "expected compilation to succeed:\n${outcome.messages}")
        return outcome
    }

    private fun assertRejected(@Language("kotlin") source: String, vararg expected: String) {
        val outcome = compile(source)
        assertTrue(!outcome.succeeded, "expected compilation to fail, but it succeeded")
        for (fragment in expected) {
            assertContains(outcome.messages, fragment)
        }
    }

    private val preamble = """
        import io.github.menjoo.cucumberkmp.annotations.After
        import io.github.menjoo.cucumberkmp.annotations.Before
        import io.github.menjoo.cucumberkmp.annotations.Given
        import io.github.menjoo.cucumberkmp.annotations.Then
        import io.github.menjoo.cucumberkmp.annotations.When
        import io.github.menjoo.cucumberkmp.core.gherkin.DataTable
        import io.github.menjoo.cucumberkmp.core.gherkin.DocString

    """.trimIndent()

    // ------------------------------------------------------------------ generation

    @Test
    fun `generates a registry that compiles`() {
        val outcome = compileSuccessfully(
            preamble + """
            class Steps {
                @Given("I have {int} cukes")
                fun iHave(count: Int) = Unit
            }
            """.trimIndent(),
        )

        val registry = requireNotNull(outcome.registry) { "no registry generated" }
        assertContains(registry, "public val generatedStepRegistry")
        assertContains(registry, "CucumberExpression(\"I have {int} cukes\"")
        assertContains(registry, "arguments[0] as Int")
        // A fresh glue instance per scenario is the isolation contract; it must be inside the
        // factory lambda, not a top-level singleton.
        assertContains(registry, "val steps = Steps()")
    }

    @Test
    fun `generates nothing when there are no annotations`() {
        // Users of the steps { } DSL annotate nothing; emitting an empty registry would shadow it.
        val outcome = compileSuccessfully("class Steps { fun notAStep() = Unit }")
        assertEquals(null, outcome.registry)
    }

    @Test
    fun `hoists expressions so they compile once, not per scenario`() {
        val outcome = compileSuccessfully(
            preamble + """
            class Steps {
                @Given("a")
                fun a() = Unit
                @Given("b")
                fun b() = Unit
            }
            """.trimIndent(),
        )
        val registry = requireNotNull(outcome.registry)
        assertContains(registry, "private val EXPRESSION_0")
        assertContains(registry, "private val EXPRESSION_1")
    }

    @Test
    fun `supports top-level and object-hosted steps`() {
        val outcome = compileSuccessfully(
            preamble + """
            @Given("top level")
            fun topLevel() = Unit

            object Shared {
                @Given("on an object")
                fun onAnObject() = Unit
            }
            """.trimIndent(),
        )
        val registry = requireNotNull(outcome.registry)
        assertContains(registry, "topLevel()")
        // An object is referenced directly rather than instantiated per scenario.
        assertContains(registry, "Shared.onAnObject()")
    }

    @Test
    fun `accepts a trailing DataTable parameter without counting it as a placeholder`() {
        val outcome = compileSuccessfully(
            preamble + """
            class Steps {
                @Given("the users")
                fun theUsers(table: DataTable) = Unit

                @Given("payload {int}")
                fun payload(id: Int, body: DocString) = Unit
            }
            """.trimIndent(),
        )
        val registry = requireNotNull(outcome.registry)
        assertContains(registry, "arguments[0] as DataTable")
        assertContains(registry, "arguments[1] as DocString")
    }

    @Test
    fun `generates hooks with their tag expressions`() {
        val outcome = compileSuccessfully(
            preamble + """
            class Steps {
                @Before fun setUp() = Unit
                @Before("@smoke") fun taggedSetUp() = Unit
                @After fun tearDown() = Unit
                @Given("a") fun a() = Unit
            }
            """.trimIndent(),
        )
        val registry = requireNotNull(outcome.registry)
        assertContains(registry, "HookKind.BEFORE, null")
        assertContains(registry, "TagExpressionParser.parse(\"@smoke\")")
        assertContains(registry, "HookKind.AFTER")
    }

    // ------------------------------------------------------------------ diagnostics

    @Test
    fun `rejects a parameter type that does not match the placeholder`() = assertRejected(
        preamble + """
        class Steps {
            @Given("I have {int} cukes")
            fun iHave(count: String) = Unit
        }
        """.trimIndent(),
        "Parameter 1 is declared kotlin.String but '{int}' captures kotlin.Int",
    )

    @Test
    fun `rejects a placeholder count that disagrees with the parameters`() = assertRejected(
        preamble + """
        class Steps {
            @Given("{int} and {int}")
            fun two(a: Int) = Unit
        }
        """.trimIndent(),
        "captures 2 argument(s) but the function takes 1",
    )

    @Test
    fun `rejects an undefined parameter type`() = assertRejected(
        preamble + """
        class Steps {
            @Given("I have {gizmo}")
            fun gizmo(value: String) = Unit
        }
        """.trimIndent(),
        "Undefined parameter type '{gizmo}'",
    )

    @Test
    fun `rejects an unparseable expression, keeping upstream's caret diagram`() {
        val outcome = compile(
            preamble + """
            class Steps {
                @Given("three (blind mice")
                fun mice() = Unit
            }
            """.trimIndent(),
        )
        assertTrue(!outcome.succeeded)
        assertContains(outcome.messages, "Invalid Cucumber Expression")
        // The caret line is the most useful part of upstream's message; it must survive.
        assertContains(outcome.messages, "does not have a matching")
        assertContains(outcome.messages, "^")
    }

    @Test
    fun `rejects the same expression on two different functions`() = assertRejected(
        preamble + """
        class Steps {
            @Given("I have cukes")
            fun one() = Unit
            @Given("I have cukes")
            fun two() = Unit
        }
        """.trimIndent(),
        "is defined more than once",
    )

    @Test
    fun `allows equivalent annotations on a single function`() {
        // Regression: @When and @Given with the same text on one function is redundant, not
        // ambiguous — Gherkin does not match on the keyword. This was reported as a duplicate
        // until examples/calculator tripped over it.
        val outcome = compileSuccessfully(
            preamble + """
            class Steps {
                @When("I press add")
                @Given("I press add")
                fun press() = Unit
            }
            """.trimIndent(),
        )
        val registry = requireNotNull(outcome.registry)
        // Registered once, not twice.
        assertEquals(1, Regex("""StepDefinition\(""").findAll(registry).count())
    }

    @Test
    fun `rejects a private step function`() = assertRejected(
        preamble + """
        class Steps {
            @Given("a step")
            private fun hidden() = Unit
        }
        """.trimIndent(),
        "may not be private",
    )

    @Test
    fun `rejects a step on an abstract class`() = assertRejected(
        preamble + """
        abstract class Steps {
            @Given("a step")
            fun step() = Unit
        }
        """.trimIndent(),
        "may not be abstract",
    )

    @Test
    fun `rejects a glue class without a no-argument constructor`() = assertRejected(
        preamble + """
        class Steps(private val dependency: String) {
            @Given("a step")
            fun step() = Unit
        }
        """.trimIndent(),
        "needs a no-argument constructor",
    )

    @Test
    fun `rejects a hook that takes parameters`() = assertRejected(
        preamble + """
        class Steps {
            @Before fun setUp(unexpected: Int) = Unit
            @Given("a") fun a() = Unit
        }
        """.trimIndent(),
        "A hook must take no parameters",
    )

    @Test
    fun `rejects a malformed hook tag expression`() = assertRejected(
        preamble + """
        class Steps {
            @Before("@a and or") fun setUp() = Unit
            @Given("a") fun a() = Unit
        }
        """.trimIndent(),
        "Invalid tag expression",
    )
}

/** Marks a string as Kotlin so an IDE highlights the snippets; has no effect on the test. */
@Target(AnnotationTarget.VALUE_PARAMETER)
@Retention(AnnotationRetention.SOURCE)
private annotation class Language(val value: String)
