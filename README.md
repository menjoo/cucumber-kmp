# cucumber-kmp

A Kotlin Multiplatform port of [Cucumber](https://cucumber.io). Gherkin `.feature` files run as
real tests on the JVM, Android, iOS, macOS, JS and Wasm — with no runtime reflection, no classpath
scanning and no runtime filesystem access.

The point is that non-developers keep writing plain-text `.feature` files in the repository, while
the tests those files describe run natively on every target the app ships to.

> **Status: early.** The engine works end to end and is pinned against upstream Cucumber's own test
> data, but the KSP annotations and the Gradle plugin that generates one test per scenario are not
> built yet. Nothing is published to Maven Central. See [ARCHITECTURE.md](ARCHITECTURE.md) for the
> design and the roadmap.

## What works today

Parse, compile, match, run — on all six locally verifiable targets:

```kotlin
val calculatorSteps = steps {
    var calculator = Calculator()          // fresh for every scenario

    given("I have entered {int}") { value: Int -> calculator.enter(value) }
    whenever("I press add") { calculator.add() }
    then("the result should be {int}") { expected: Int ->
        assertEquals(expected, calculator.result)
    }
}

@Test
fun addsTwoNumbers() = runTest {
    val document = GherkinParser.parse("calculator.feature", featureSource)
    val pickles = PickleCompiler.compile(document)
    CucumberRunner(calculatorSteps).runOrThrow(pickles.single())
}
```

The `steps { }` DSL is the mechanism the framework stands on, not a fallback: because it returns a
factory the runner calls once per scenario, a `var` declared inside the block is fresh each time, so
per-scenario isolation falls out of ordinary Kotlin closures — no dependency-injection container and
no reflection. The `@Given`/`@When`/`@Then` annotations, when they arrive, will be sugar over this.

Also implemented: the full 80-language Gherkin dialect table, Cucumber Expressions with all of
Cucumber's built-in parameter types, tag expressions, backgrounds, scenario outlines, data tables,
doc strings, rules, hooks, and undefined/ambiguous step reporting with paste-able snippets.

## Conformance

This is a port, so behavioural fidelity is a requirement rather than an aspiration. Upstream's own
language-agnostic test data is embedded as generated Kotlin and run on **every** target:

| Suite | Cases |
|---|---|
| `cucumber/gherkin` parser — good and bad | 50 + 12 |
| `cucumber/gherkin` pickle compilation | 50 |
| `cucumber/cucumber-expressions` | 120 |
| `cucumber/tag-expressions` | 64 |

Error messages, positions and `expected: #Token, …` lists are byte-identical to upstream, because
those strings are what users compare against Cucumber's output. Every intentional difference is
recorded in [DEVIATIONS.md](DEVIATIONS.md).

The fixtures are embedded as code rather than read from test resources because Kotlin/Native and
Wasm cannot read files at runtime — the same constraint that shapes the whole framework.

## Building

Requires JDK 21 and, for the Apple targets, Xcode.

```bash
./gradlew build                                  # all Tier A targets
./gradlew -Pcucumberkmp.browserTests build       # adds Chrome/Karma runs
./gradlew linkDebugTestIosArm64                  # device target: compile and link check
```

Regenerate the vendored upstream data (needs Python 3 and PyYAML):

```bash
python3 tools/update-gherkin-dialects.py --download
python3 tools/update-gherkin-corpus.py
python3 tools/update-expression-corpus.py
python3 tools/update-tag-expression-corpus.py
```

A scheduled workflow runs those weekly and reports if upstream has moved.

## Licence

MIT, the same licence as Cucumber. See [LICENSE](LICENSE), and [NOTICE](NOTICE) for what is derived
from or embedded verbatim out of the upstream projects.

cucumber-kmp is an independent port. It is not published, endorsed or supported by Cucumber Ltd.
