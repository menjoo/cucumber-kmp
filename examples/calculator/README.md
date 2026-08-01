# calculator — end-to-end example

A standalone project that consumes cucumber-kmp the way a real one would: it resolves **published
artifacts**, not project dependencies. That is what makes it a real test of the Gradle plugin —
the one thing the plugin's own tests deliberately skip is whether generated sources actually reach
each target's test source set and compile there.

## Run it

```bash
# from the repository root
./gradlew publishToMavenLocal
./gradlew -p examples/calculator build
```

Six scenarios run on six targets: JVM, Android (host), macOS arm64, iOS simulator, JS/Node and
Wasm/Node. `./gradlew -p examples/calculator linkDebugTestIosArm64` additionally proves it links
for a physical iPhone.

## What it demonstrates

| File | Role |
|---|---|
| `src/commonMain/.../Calculator.kt` | Ordinary shared business logic. Knows nothing about Cucumber. |
| `src/commonTest/resources/features/calculator.feature` | What a PO or QA writes. Plain text, no Kotlin. |
| `src/commonTest/.../CalculatorSteps.kt` | Step definitions, annotated with `@Given`/`@When`/`@Then`. |
| `build.gradle.kts` | Wires KSP per target and points the plugin at the generated registry. |

The feature file exercises a background, two plain scenarios, a `Scenario Outline` with three
example rows, a tag, and a step taking a data table.

Nothing in the project references a generated symbol by hand. The build produces:

- a `StepRegistry` per target from the annotations (KSP), and
- one test class per feature with one `@Test` per scenario (the Gradle plugin),

so adding a scenario to the feature file adds a test, and nothing else has to change.

## The one piece of ceremony

`build.gradle.kts` lists the KSP configurations by name, one per target test compilation:

```kotlin
listOf("kspJvmTest", "kspAndroidHostTest", "kspMacosArm64Test", …)
```

That is unavoidable today: KSP's only metadata entry point is `kspCommonMainMetadata`, so a
processor cannot generate into `commonTest`, and each target compilation has to be wired
individually. See ARCHITECTURE.md §13.1.
