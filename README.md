# cucumber-kmp

[![CI](https://github.com/menjoo/cucumber-kmp/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/menjoo/cucumber-kmp/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.menjoo.cucumberkmp/cucumber-kmp-core)](https://central.sonatype.com/artifact/io.github.menjoo.cucumberkmp/cucumber-kmp-core)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.10-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

![JVM](https://img.shields.io/badge/JVM-supported-success)
![Android](https://img.shields.io/badge/Android-supported-success)
![iOS](https://img.shields.io/badge/iOS-supported-success)
![macOS](https://img.shields.io/badge/macOS-supported-success)
![JS](https://img.shields.io/badge/JS-supported-success)
![Wasm](https://img.shields.io/badge/Wasm-supported-success)

A Kotlin Multiplatform port of [Cucumber](https://cucumber.io). Gherkin `.feature` files run as
real tests on the JVM, Android, iOS, macOS, JS and Wasm — with no runtime reflection, no classpath
scanning and no runtime filesystem access.

The point is that non-developers keep writing plain-text `.feature` files in the repository, while
the tests those files describe run natively on every target the app ships to.

> **Status: early but usable.** Published to Maven Central and verified end to end on six targets.
> The API may still change between 0.x releases. On-device Android and iOS runs, and the Cucumber
> Compatibility Kit, are not done yet — see [ARCHITECTURE.md](ARCHITECTURE.md) for the roadmap.

## Installation

`settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories { mavenCentral(); gradlePluginPortal() }
}
```

`build.gradle.kts`:

```kotlin
plugins {
    kotlin("multiplatform")
    id("com.google.devtools.ksp") version "2.3.10"
    id("io.github.menjoo.cucumberkmp") version "0.1.2"
}

kotlin {
    sourceSets {
        commonTest.dependencies {
            implementation("io.github.menjoo.cucumberkmp:cucumber-kmp-core:0.1.2")
            implementation("io.github.menjoo.cucumberkmp:cucumber-kmp-annotations:0.1.2")
        }
    }
}
```

That is the whole configuration. The plugin adds `cucumber-kmp-ksp` to each test compilation
itself — KSP generates the step registry per target compilation because it cannot generate into
`commonTest`, and the plugin already knows which compilations those are.

The generated tests and the generated step registry share one package, which the plugin hands to
KSP itself, so `cucumberKmp { }` needs nothing unless you want to choose that package:

```kotlin
cucumberKmp {
    generatedPackage.set("com.example.cucumber")
}
```

Feature files go in `src/commonTest/resources/features`. See
[`examples/calculator`](examples/calculator) for a complete, working project.

Only `cucumber-kmp-core` is needed if you use the `steps { }` DSL rather than annotations — with
no KSP in play, point `cucumberKmp { stepRegistry }` at whichever property holds your factory.

## How it looks

Write the feature — this is the file a PO or QA owns, and no Kotlin appears in it:

```gherkin
Feature: Calculator
  Scenario Outline: a <percentage>% discount on <total>
    Given I have entered <total>
    And I press add
    When I apply a discount of <percentage> percent
    Then the result should be <expected>

    Examples:
      | total | percentage | expected |
      | 100   | 20         | 80       |
      | 250   | 10         | 225      |
```

Write the step definitions once, in `commonTest`:

```kotlin
@Steps
class CalculatorSteps {
    private val calculator = Calculator()      // fresh instance for every scenario

    @Given("I have entered {double}")
    fun iHaveEntered(value: Double) = calculator.enter(value)

    @When("I apply a discount of {int} percent")
    fun iApplyADiscount(percentage: Int) = calculator.applyDiscount(percentage)

    @Then("the result should be {double}")
    fun theResultShouldBe(expected: Double) = assertEquals(expected, calculator.result)
}
```

The build does the rest: KSP turns the annotations into a static step registry, and the Gradle
plugin turns each scenario into a `@Test`. Adding a row to that `Examples` table adds a test — on
every target — and nothing else changes.

Annotations are optional. The `steps { }` DSL underneath them needs no code generation at all:

```kotlin
val calculatorSteps = steps {
    var calculator = Calculator()              // fresh for every scenario
    given("I have entered {int}") { value: Int -> calculator.enter(value) }
    whenever("I press add") { calculator.add() }
}
```

Because `steps { }` returns a factory the runner calls once per scenario, a `var` declared inside
the block is fresh each time — per-scenario isolation falls out of ordinary Kotlin closures, with no
dependency-injection container and no reflection.

Also implemented: the full 80-language Gherkin dialect table, Cucumber Expressions with all of
Cucumber's built-in parameter types, tag expressions, backgrounds, scenario outlines, data tables (with upstream's
`asList`/`asLists`/`asMap`/`asMaps` conversions),
doc strings, rules, hooks, tag filtering, and undefined/ambiguous step reporting with paste-able
snippets.

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
