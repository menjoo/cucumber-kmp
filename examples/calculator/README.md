# calculator — end-to-end example

A standalone project that consumes cucumber-kmp the way a real one would: it resolves **published
artifacts**, not project dependencies. That is what makes it a real test of the Gradle plugin —
the one thing the plugin's own tests deliberately skip is whether generated sources actually reach
each target's test source set and compile there.

## Run it

```bash
# from the repository root — nothing to publish first
./gradlew -p examples/calculator build
```

Six scenarios run on six targets: JVM, Android (host), macOS arm64, iOS simulator, JS/Node and
Wasm/Node. `./gradlew -p examples/calculator linkDebugTestIosArm64` additionally proves it links
for a physical iPhone.

## Run it from an IDE

**Open `examples/calculator` itself as the project**, not the repository root. The root build does
not contain this example — it cannot, because the example applies a plugin the root build produces,
and a build cannot include itself.

Opening this directory gets you the whole repository anyway: `settings.gradle.kts` includes the
root as a composite build, so IntelliJ shows the library sources too, and you can navigate,
edit and set breakpoints in them from here. Library changes are picked up on the next build with no
publishing step.

Two things this directory carries so an IDE can build it on its own:

- **Its own Gradle wrapper**, pinned to the same version as the repository root. Without it the IDE
  falls back to whatever Gradle it bundles, which is currently older than AGP's minimum and fails
  the sync with `Minimum supported Gradle version is 9.5.0`. Point the IDE's Gradle setting at the
  wrapper, which is the default.
- **Its own `gradle.properties`**, raising heap and metaspace. The Kotlin/Native compiler runs
  inside the daemons and exhausts the JVM defaults while linking several Native targets, failing
  with a bare `Metaspace` that explains nothing. Any KMP project with Native targets needs this.

The Android target also needs an SDK location — `ANDROID_HOME`, or a `local.properties` with
`sdk.dir`. Android Studio writes one on first sync; plain IntelliJ may not.

After the first Gradle sync you can run scenarios from the gutter:

- **A whole feature** — run `CalculatorFeatureTest` in `build/generated/cucumber/kotlin`.
- **One scenario** — run its generated test function, for example `addingTwoNumbers`.
- **Everything on one target** — run the `jvmTest` (or `macosArm64Test`, …) Gradle task.

The generated tests exist as soon as the IDE syncs: the plugin hooks generation into
`prepareKotlinIdeaImport`, so the test tree is populated without running a build first. If a
generated class ever looks stale, re-run `generateCucumberTests` and re-sync.

Two honest limitations:

- You cannot run a scenario from the `.feature` file itself. That needs an IDE plugin, which does
  not exist for cucumber-kmp — start from the generated test class instead.
- Renaming a scenario renames its generated test function, so a saved IDE run configuration
  pointing at the old name will stop resolving.

## Two modes

| Mode | Command | What it proves |
|---|---|---|
| Composite (default) | `./gradlew -p examples/calculator build` | Fast inner loop; library substituted from source |
| Published | `./gradlew publishToMavenLocal` then the same with `-Pcucumberkmp.composite=false` | Real artifacts, so packaging and source-set wiring are exercised rather than substituted away |

CI runs both: composite on JVM to catch substitution breaking, published across the full matrix.

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
