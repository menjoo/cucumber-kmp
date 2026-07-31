# cucumber-kmp — Architecture

> This document is the brief: what we build, why, and in what order. It stays authoritative as the
> code lands — the roadmap in §12 marks what is done, and anything that turned out differently in
> practice is corrected here rather than left as an aspiration.
>
> Status: **Phase 0 and Phase 1 complete.** The engine parses, compiles, matches and runs a
> `.feature` file end to end on all six Tier A targets, pinned against upstream Cucumber's own test
> data. Next: KSP annotations (Phase 2) and the Gradle plugin that generates one test per scenario
> (Phase 3) — the point at which a `.feature` file dropped in the repository simply runs.

## 1. Goal

**A Kotlin Multiplatform port of Cucumber.** Run Gherkin `.feature` files as real tests on
Kotlin/Native, JS and Wasm as well as the JVM — with no runtime reflection, no classpath scanning
and no runtime filesystem access.

Non-developers (PO, QA, analysts) keep writing plain-text `.feature` files in the repository.
Developers write step definitions in `commonTest`. The build wires the two together at
**compile time** and emits ordinary `kotlin.test` test functions, so `./gradlew jvmTest`,
`./gradlew connectedDebugAndroidTest`, `./gradlew iosSimulatorArm64Test` and
`./gradlew wasmJsTest` all execute the same scenarios.

Because this is a port and not a new BDD tool, **behavioural fidelity to Cucumber is a hard
requirement**, not an aspiration. See §2.

### Non-goals (for the first release)

- **Binary or source compatibility** with `io.cucumber` types. We port semantics and naming, not
  the Java class hierarchy — `cucumber-kmp` is not a drop-in jar replacement.
- Reflection-based dependency injection (`cucumber-picocontainer`, Spring, Guice integrations).
- Cucumber's JVM plugin ecosystem, `--dry-run`, and rerun files.
- **Markdown-with-Gherkin** (`.feature.md`). Upstream supports feature text embedded in Markdown;
  we support plain `.feature` files only.
- Running feature files discovered at *runtime* (downloaded, or edited between compile and run).
  Everything is resolved during the Gradle build.

## 2. Normative references

When a question about behaviour arises — keyword handling, expression matching, tag logic, step
ordering, hook semantics, error messages — **the answer is whatever Cucumber does**, not whatever
seems reasonable. Primary sources, in order of authority:

| Source | What it is normative for |
|---|---|
| <https://cucumber.io/docs> | Gherkin reference, Cucumber Expressions, tag expressions, hooks, data tables, step-definition semantics |
| <https://github.com/cucumber/cucumber-jvm> | The reference implementation. Its source *and its test suite* are the tiebreaker for anything the docs leave ambiguous |
| <https://github.com/cucumber/gherkin> | The parser spec, `gherkin-languages.json` dialect table, and the official good/bad parser test corpus |
| <https://github.com/cucumber/cucumber-expressions> | Expression grammar and the cross-language expression test suite |
| <https://github.com/cucumber/tag-expressions> | Tag expression grammar and its shared test suite |
| <https://github.com/cucumber/common> (Compatibility Kit) | The CCK — Cucumber's own cross-implementation conformance suite |

Two working rules follow from this:

1. **Port the test data, not just the code.** `cucumber/gherkin`, `cucumber-expressions` and
   `tag-expressions` all publish language-agnostic test fixtures precisely so that ports can prove
   conformance. Vendor those fixtures into our test resources and run them on every target. This
   is the cheapest possible correctness guarantee and it should land in Phase 1, not at the end.
2. **Record every intentional divergence in `DEVIATIONS.md`**, with the reason. There will be
   some — §9 and §10 already contain two — and an undocumented divergence in a port is a bug
   report waiting to happen.

## 3. Why cucumber-jvm cannot simply be recompiled

`cucumber-jvm` is architecturally bound to the JVM in four independent ways:

| JVM mechanism | What Cucumber uses it for | Why it cannot cross to Native/Wasm |
|---|---|---|
| Runtime reflection | Finding `@Given`/`@When`/`@Then` methods and invoking them with coerced arguments | Kotlin/Native and Wasm have no runtime reflection over arbitrary members, and no `java.lang.reflect.Type` for generic coercion |
| Classpath scanning | Discovering glue classes and feature resources | There is no classpath on iOS or in a browser |
| `java.io` / resource loading | Reading `.feature` files from disk at startup | An iOS app sandbox or a browser cannot read arbitrary project files |
| JUnit Platform `TestEngine` | Turning scenarios into dynamically generated tests | `kotlin.test` on Native/JS has no dynamic test registration; test functions must exist at compile time |

Every one of these is a *discovery-at-runtime* problem. The whole design below is the same answer
applied four times: **move discovery to build time and generate code**. Note what is *not* on this
list — the Gherkin grammar, expression matching and tag logic are all pure computation, and those
port more or less directly.

**Android is the interesting middle case.** It *does* have reflection, so rows 1 and 4 are not
hard blockers there — which is why a `cucumber-android` integration once existed before being
deprecated. But classpath scanning and reading `.feature` files off disk still fail inside an APK,
and more importantly a design that reflects on Android but generates code on iOS would mean two
execution paths and two sets of bugs. We generate code everywhere, Android included.

## 4. Architecture overview

```
  .feature files (src/commonTest/resources/features/**)
        │
        │  ┌───────────────────────────────────────────────┐
        │  │ 4a. Gradle plugin (cucumber-kmp-gradle-plugin) │
        └──▶ parses features, generates one Kotlin test     │
           │ class per feature, one @Test per scenario /    │
           │ Examples row, with the AST embedded as code    │
           └───────────────────────────────────────────────┘
                                  │
  @Given/@When/@Then functions     │            generated commonTest sources
  in commonTest                    │
        │                          │
        │  ┌────────────────────┐  │
        └──▶ 4b. KSP processor  │  │
           │ (cucumber-kmp-ksp) │  │
           │ generates a static │  │
           │ StepRegistry       │  │
           └────────────────────┘  │
                     │             │
                     ▼             ▼
        ┌───────────────────────────────────────┐
        │ 4c. Runtime engine (cucumber-kmp-core)│
        │ expression matching, argument         │
        │ conversion, hooks, scenario execution │
        └───────────────────────────────────────┘
                     │
                     ▼
        kotlin.test on JVM · Android (ART) · iOS · macOS · JS · WasmJs
```

Three build-time inputs, one pure-Kotlin runtime, zero reflection.

### 4a. Feature files → generated test classes (Gradle plugin)

`.feature` files are not Kotlin symbols, so KSP is the wrong tool for them. A Gradle task owns
this half:

- Input: a configurable feature source set (default `src/commonTest/resources/features`).
- Parse each file with the **JVM build of `cucumber-kmp-core`'s parser** — the parser is
  multiplatform, and the plugin simply runs it on the JVM during the build.
- Output: for `calculator.feature`, a `CalculatorFeatureTest.kt` into a generated source
  directory added to `commonTest`, containing:
  - the parsed `Feature` AST as Kotlin constructor calls (this is how the feature text reaches
    Native and Wasm — as compiled code, never as a file read);
  - one `@Test fun` per `Scenario`, and one per `Examples` row of a `Scenario Outline`;
  - each test body delegating to the engine: `CucumberRunner.run(FEATURE, scenarioIndex, StepRegistry)`.
- Task must be incremental and cacheable: `@InputDirectory` on features, `@OutputDirectory` on
  generated sources, no absolute paths in the output.

Generated file names and test function names must be **deterministic and stable** — they show up
in CI reports. Sanitise Gherkin names to valid Kotlin identifiers via backticked function names
where the target allows it, and keep a plain-ASCII fallback for targets that reject them.

### 4b. Step definitions → StepRegistry (KSP)

`cucumber-kmp-annotations` mirrors the `io.cucumber.java.en` annotation set:

```kotlin
@Target(AnnotationTarget.FUNCTION) annotation class Given(val value: String)
@Target(AnnotationTarget.FUNCTION) annotation class When(val value: String)
@Target(AnnotationTarget.FUNCTION) annotation class Then(val value: String)
@Target(AnnotationTarget.FUNCTION) annotation class Step(val value: String)   // keyword-agnostic
@Target(AnnotationTarget.FUNCTION) annotation class Before(val tagExpression: String = "")
@Target(AnnotationTarget.FUNCTION) annotation class After(val tagExpression: String = "")
@Target(AnnotationTarget.CLASS)    annotation class Steps                    // marks a glue class
```

The KSP processor (`cucumber-kmp-ksp`, a JVM-only artifact) collects annotated functions and
emits, with **KotlinPoet**, a single `GeneratedStepRegistry` object:

```kotlin
// generated — do not edit
public object GeneratedStepRegistry : StepRegistry {
  override val definitions: List<StepDefinition> = listOf(
    StepDefinition(
      expression = CucumberExpression("I have {int} eggs"),
      location   = SourceLocation("EggSteps.kt", 12),
      glue       = { scope -> scope.instanceOf(::EggSteps) },
      invoke     = { target, args -> (target as EggSteps).setEggs(args.int(0)) },
    ),
    // …
  )
  override val hooks: List<Hook> = listOf(/* … */)
}
```

The `invoke` lambda is the crux: an explicit, statically typed call replaces
`Method.invoke(...)`. Types are read from the KSP declaration, so argument conversion is decided
at compile time and a mismatch between `{int}` and a `String` parameter becomes a **build error
with a source location**, not a runtime `CucumberException`.

The processor rejects, at compile time and with a source location: an expression that does not
parse, a placeholder count that disagrees with the parameter count, a placeholder whose type does
not match the declared parameter, an undefined parameter type, duplicate expressions, `private` or
`abstract` step functions, step functions on an abstract or inner class or one without a no-arg
constructor, hooks that take parameters, and malformed hook tag expressions. It validates
expressions using the very parser it generates calls to — the processor depends on the JVM artifact
of `cucumber-kmp-core`, the same trick the Gradle plugin uses for `.feature` files.

**Generation is per target compilation, not over common metadata.** KSP's only metadata entry point
is `kspCommonMainMetadata`, so nothing can be generated into `commonTest`. Each target's test
compilation processes the shared `commonTest` sources and emits its own copy of the registry, which
means common code needs a one-line `expect`/`actual` shim to reach it. See §13.1.

### 4c. Runtime engine (`cucumber-kmp-core`, `commonMain`)

Pure Kotlin, `kotlinx-coroutines-core` as the only hard dependency. Contains:

- **Gherkin AST** — immutable data classes: `Feature`, `Rule`, `Background`, `Scenario`,
  `Examples`, `Step`, `DataTable`, `DocString`, `Tag`, each carrying a `SourceLocation`. Model this
  on `cucumber/gherkin`'s published AST so the shape is recognisable and the official test corpus
  maps onto it directly.
- **Gherkin parser** — a hand-written lexer + recursive-descent parser over the Gherkin
  specification, see §6.
- **Cucumber Expressions** — `{int}`, `{float}`, `{word}`, `{string}`, `{}`, optional text
  `(s)`, alternation `a/b`, plus a `ParameterTypeRegistry` for custom types. Compiles to
  `kotlin.text.Regex`, which is multiplatform. Regex literals in step annotations
  (`^…$`) are also supported and bypass expression compilation.
- **Tag expressions** — the boolean `and`/`or`/`not`/parentheses grammar, ported from
  `cucumber/tag-expressions` and validated against its shared test suite.
- **Matcher** — resolves a step text to exactly one `StepDefinition`; reports
  `Undefined` (with a copy-pasteable snippet) and `Ambiguous` (listing both source locations).
- **Runner** — executes background steps, then scenario steps, in order; applies hooks; short-
  circuits remaining steps once one fails; aggregates into a `ScenarioResult`. Step and hook
  ordering must match cucumber-jvm exactly, including `After` hooks running in reverse
  registration order and still running after a failure.
- **Scenario scope** — see §8.
- **Failure formatting** — an `AssertionError` whose message reproduces the Gherkin snippet with
  the failing line marked and the underlying assertion nested. This is the primary UX of the
  whole framework and deserves disproportionate care.

## 5. Module layout

```
cucumber-kmp/
├── settings.gradle.kts
├── gradle/libs.versions.toml
├── build-logic/                      # convention plugins (kmp target matrix, publishing)
├── cucumber-kmp-annotations/         # KMP, no deps            — @Given/@When/@Then
├── cucumber-kmp-core/                # KMP, coroutines only    — parser, AST, expressions, engine
├── cucumber-kmp-ksp/                 # JVM only                — KSP processor (KotlinPoet)
├── cucumber-kmp-gradle-plugin/       # JVM only                — feature → test codegen task
├── DEVIATIONS.md                     # intentional divergences from cucumber-jvm, with reasons
└── examples/
    └── calculator/                   # KMP consumer, full target matrix, green on all of them
        ├── src/commonTest/resources/features/   # the .feature files
        ├── androidApp/               # thin host for connectedDebugAndroidTest
        └── iosTestHost/              # thin XCTest host app, for on-device iosArm64 runs (§7)
```

`cucumber-kmp-core` deliberately has no `kotlin.test` dependency in `commonMain`; the generated
test code supplies `@Test`. That keeps the engine usable from a custom runner too.

## 6. Gherkin coverage

Phase in this order; each item is a testable increment against the official corpus:

1. `Feature`, `Scenario`, `Given/When/Then/And/But/*`, comments, blank lines.
2. `Background`.
3. `Scenario Outline` + `Examples`, with `<placeholder>` substitution in step text, doc strings
   and data tables.
4. `Tag`s on Feature / Rule / Scenario / Examples, with inheritance.
5. `DataTable` and `DocString` (including the content-type suffix and `"""`/```` ``` ```` forms).
6. `Rule` (Gherkin 6+).
7. **i18n** — the `# language: xx` header and the full keyword dialect table. Dutch matters here
   (`Functionaliteit`, `Scenario`, `Gegeven`, `Wanneer`, `Dan`, `En`, `Maar`, `Abstract Scenario`,
   `Voorbeelden`). Generate the dialect table from the upstream `gherkin-languages.json` into a
   Kotlin map — never hand-type it, and re-generate when upstream changes.

The parser must produce **precise line/column diagnostics** and recover enough to report more
than one syntax error per file. A malformed feature file is a build failure with the offending
line quoted. Error messages should match upstream's wording where practical, since that is what
users will search for.

## 7. Target matrix

Constrained to what can actually be **built and run with the hardware on hand**: Apple Silicon
MacBook, JDK 21 Temurin, Gradle 8.11.1, Xcode 26.6, iOS 26.5 simulator, Android SDK
(platforms 33–37, NDK 27/28, `Medium_Phone` AVD), Node 22.17, Chrome + Safari — plus a physical
iPhone 13 and an Android device.

### Tier A — tests execute on the machine, no device needed

```
jvm()                             // JDK 21 Temurin
androidTarget()                   // androidUnitTest → host JVM (see §7.1)
macosArm64()                      // host-native, fastest Native feedback loop
iosSimulatorArm64()               // iOS 26.5 simulator
js(IR) { nodejs(); browser() }    // Node 22, Chrome headless via Karma
wasmJs   { nodejs(); browser() }  // WasmGC — Node 22 and Chrome both support it
```

`macosArm64` is the workhorse: the cheapest way to prove the no-reflection design on
Kotlin/Native, with none of the simulator's startup cost. Use it as the default Native target
during development and treat `iosSimulatorArm64` as the confirmation run.

### Tier B — tests execute on a physical device

Both are genuinely runnable here, so neither is compile-only:

```
androidTarget()   // androidInstrumentedTest → connectedDebugAndroidTest, on device or AVD
iosArm64()        // on-device run via an XCTest host app (iPhone 13, paired)
```

The two differ sharply in how much plumbing they need:

- **Android is nearly free.** `connectedDebugAndroidTest` is a first-class AGP task. Attach the
  device (or boot `Medium_Phone`) and it runs. This is the tier that actually matters most — see
  §7.1.
- **iOS device runs need a harness.** Kotlin/Native builds a test *binary* for `iosArm64`, but
  Gradle has no built-in task to deploy and run it on a physical device the way
  `iosSimulatorArm64Test` does for the simulator. It needs a thin XCTest host app in the repo
  (`examples/calculator/iosTestHost`) that links the test binary and is driven by
  `xcodebuild test -destination 'platform=iOS,name=iPhone 13 van Menno'`. Perfectly doable, but
  it is a **discrete deliverable with its own signing and provisioning setup**, so it is scheduled
  as its own roadmap item rather than assumed. Until it lands, `iosArm64` is verified by
  `compileKotlinIosArm64` + `linkDebugTestIosArm64`.

### Deliberately excluded

- `linuxX64`, `linuxArm64`, `mingwX64` — cannot be executed on macOS. Add when there is a Linux
  CI runner, not before.
- `macosX64`, `iosX64` — Rosetta 2 is present so these are technically runnable, but they double
  the Native build time to verify a legacy architecture the arm64 equivalents already cover.
- `watchos*`, `tvos*` — a paired Apple Watch Series 6 makes `watchosArm64` reachable in principle,
  but there is no demand yet. Revisit only if someone asks.

### 7.1 Android specifics

Android is the one target that changes the risk picture rather than just widening the matrix,
because it has **two test source sets with completely different runtimes**:

| Source set | Gradle task | Runtime | What it proves |
|---|---|---|---|
| `androidUnitTest` | `testDebugUnitTest` | Host JVM (HotSpot) | Almost nothing new — it is `jvm()` with a different classpath |
| `androidInstrumentedTest` | `connectedDebugAndroidTest` | **ART on the device** | The real signal |

Do not mistake a green `testDebugUnitTest` for Android coverage. ART is a genuinely distinct
runtime, and one difference bites this project directly: **Android's `java.util.regex` is an
ICU-backed implementation, not OpenJDK's.** Since Cucumber Expressions compile down to regex and
`cucumber-jvm`'s expression-to-regex output assumes OpenJDK semantics, Android is a fourth distinct
regex engine to conform against (see §13.2) — and the only way to see those differences is an
instrumented run.

Build configuration:

- AGP applied to `core`, `annotations` and the example; version pinned alongside Kotlin and KSP,
  since AGP/Kotlin/KSP version alignment is its own class of build failure.
- `compileSdk = 36` (33–37.0 installed locally), `minSdk = 24`. The library touches **no Android
  APIs whatsoever**, so `minSdk` is essentially free and could go lower if a consumer needs it.
- `ANDROID_HOME` is currently unset on this machine. AGP will find `~/Library/Android/sdk` by
  default, but pin it in a git-ignored `local.properties` (`sdk.dir=...`) to avoid ambiguity.
- KSP works on `androidTarget()` without special handling; the generated `StepRegistry` is
  plain Kotlin.
- Instrumented tests need `androidx.test.runner` and a `testInstrumentationRunner` declaration —
  the one place the example gains an AndroidX dependency the library itself does not have.

### Platform notes

- **Native tests** run through Kotlin/Native's own test runner (XCTest-shaped on Apple targets).
  Generated `@Test` functions are all it needs — nothing special to configure.
- **JS/WasmJs** are single-threaded and their `kotlin.test` requires async tests to return a
  `Promise`. Suspending steps therefore go through `kotlinx.coroutines.test.runTest`, whose
  `TestResult` return type is exactly the platform-correct thing on each target. Generated test
  functions must return `TestResult`, not `Unit`, whenever any step in scope suspends — simplest
  is to always route through `runTest`.
- **Browser tests** need Chrome resolvable by Karma; keep them off the default `check` task if
  they prove flaky locally, but keep them in CI.

## 8. Scenario state without reflection

Cucumber-jvm gives each scenario a fresh instance of each glue class, wired by a DI container.
With no reflection we need explicit wiring, and the KSP processor already knows the constructors.

**Decision:** glue classes must have either a no-arg constructor or a constructor whose
parameters are all themselves glue-registerable. KSP emits a factory lambda per glue class
(`{ EggSteps() }`, `{ CartSteps(EggSteps()) }`) and the runner builds a fresh `ScenarioScope`
holding one instance per class per scenario, so shared state between steps of one scenario works
and leaks nothing between scenarios.

This preserves cucumber-jvm's **observable** contract (fresh state per scenario, shared within a
scenario) while dropping its mechanism. Real DI is out of scope; users who need it can inject
through a `World` object they own. Record in `DEVIATIONS.md`.

## 9. Tag filtering

Because tests are generated statically, filtering has two layers:

- **Build time** — a Gradle extension (`cucumberKmp { tags.set("@smoke and not @wip") }`) that
  decides which scenarios get generated, and emits filtered ones as `@Ignore`-annotated functions
  so they still appear in reports.
- **Run time** — the runner re-checks the tag expression so a single generated suite can be
  narrowed by a system property on JVM, and reports the scenario as skipped elsewhere.

**Deviation:** `kotlin.test` has no cross-platform runtime "assume/skip", so a runtime-filtered
scenario is reported as a *pass with a skipped note* rather than a platform-level skip. Document
this in `DEVIATIONS.md`; do not fake it.

## 10. Undefined and ambiguous steps

- **Undefined** → the scenario fails, with a generated snippet the developer can paste:
  ``@Given("I have {int} eggs") fun iHaveEggs(count: Int) { TODO() }``.
  Snippet formatting should follow cucumber-jvm's Kotlin snippet generator.
  Optionally promoted to a *build* failure via `cucumberKmp { strict.set(true) }`.
- **Ambiguous** → **deviation:** cucumber-jvm raises `AmbiguousStepDefinitionsException` at
  runtime; we can detect it in KSP and fail the build instead, listing both `SourceLocation`s.
  Strictly earlier and strictly better, but a behavioural difference — record it.

## 11. Reporting

Phase 1 output is the native test report of each target (JUnit XML on JVM, whatever the Native
runner emits) plus a rich console failure message. A pretty formatter and a
**Cucumber Messages**-compatible NDJSON emitter come later; that is also the gate for running the
Compatibility Kit, since the CCK compares emitted message streams. The AST already carries the
source locations and result data those formats need, so this is additive.

## 12. Roadmap

| Phase | Deliverable | Done when |
|---|---|---|
| 0 | Gradle skeleton, version catalog, convention plugins (incl. AGP) | ✅ `./gradlew build` green: 6 Tier A targets each running the same tests, `iosArm64` link-checked |
| 0b | **CI on GitHub Actions** | ✅ PRs gated on the Tier A matrix + `linkDebugTestIosArm64`, Gradle and Konan cached, browser tests in their own job, plus a scheduled upstream-drift check |
| 0c | **CD on GitHub Actions** | ✅ snapshots from `main`, releases on a `v*` tag, to GitHub Packages; Maven Central needs the Sonatype setup in §15 |
| 1a | AST + Gherkin parser + upstream parser corpus | ✅ all 50 parseable and 12 failing fixtures match upstream exactly, on every Tier A target |
| 1b | Cucumber Expressions | ✅ all 120 upstream fixtures pass on every Tier A target, exception messages byte-identical |
| 1c | Tag expressions | ✅ all 64 upstream fixtures pass on every Tier A target |
| 1d | Pickle compiler, step matcher, runner, `steps { }` DSL | ✅ all 50 upstream pickle traces match; a `.feature` file executes end to end on every Tier A target |
| 2 | KSP processor generating `GeneratedStepRegistry` | ✅ annotated steps run on every Tier A target; ten kinds of mistake are build errors with source locations |
| 3 | Gradle plugin generating test classes from `.feature` files | `examples/calculator` is green on `jvmTest`, `testDebugUnitTest`, `macosArm64Test`, `iosSimulatorArm64Test`, `jsTest`, `wasmJsTest` |
| 4 | Gherkin completeness (outlines, tables, doc strings, tags, rules, i18n) | The official "good" corpus parses; the "bad" corpus fails with the expected line numbers |
| 5 | **On-device verification** — Android instrumented tests, then the iOS XCTest host app | `connectedDebugAndroidTest` green on a device/AVD; `xcodebuild test` green on the iPhone 13; regex conformance confirmed on ART |
| 6 | Cucumber Messages output + Compatibility Kit | CCK scenarios pass on JVM; documented gaps elsewhere |
| 7 | Publishing to Maven Central, docs site | A third party can add the plugin and run a feature file |

**Phase 1 has no dependency on KSP or Gradle plumbing** and should be built and tested first —
it is where the actual risk lives, it is verifiable with plain `kotlin.test`, and the upstream
fixtures mean correctness is measurable from day one rather than asserted.

## 13. Key risks

1. ~~**KSP over `commonTest`.**~~ **Resolved in Phase 2, and the prediction was half right.** KSP
   does work per-compilation, and the real constraint turned out sharper than expected: KSP's only
   metadata entry point is `kspCommonMainMetadata`, so **there is no way to generate into
   `commonTest` at all**. Per-target generation works cleanly — `kspJvmTest`,
   `kspIosSimulatorArm64Test`, `kspAndroidHostTest` and friends all process `commonTest` sources
   and emit into their own compilation — but common code cannot *see* the result, so a one-line
   `expect`/`actual` shim per target bridges it. Phase 3's Gradle plugin should emit that shim so
   users never write it. See §4b.

   Two smaller traps cost real time and are worth not rediscovering:
   - Applying KSP with `alias(libs.plugins.ksp)` in a module drags in its own Kotlin Gradle plugin;
     two Kotlin plugins on separate classloaders both try to register the root `kotlinNodeJs`
     extension and the build fails. KSP therefore lives on **build-logic's** classpath and is
     applied through the `cucumberkmp.ksp` convention plugin.
   - `includeBuild("build-logic")` exposes build-logic's own precompiled script plugins, not the
     third-party plugins on its implementation classpath — hence the thin wrapper plugin rather
     than applying `com.google.devtools.ksp` directly in a module.

   The mitigation below stays regardless, because it is a better mechanism and not merely a hedge:
   the engine accepts a hand-written `StepRegistry`, and we ship a **pure-Kotlin registration
   DSL**:

   ```kotlin
   val eggSteps = steps {
       given("I have {int} eggs") { count: Int -> … }
       whenever("I eat {int} of them") { count: Int -> … }
       then("I should have {int} eggs left") { count: Int -> … }
   }
   ```

   This works on every target with **no KSP and no annotations at all**. Annotations are
   ergonomic sugar over a mechanism that must stand on its own. If Phase 2 stalls, the project is
   still shippable.
2. **Regex behaviour differences — the top correctness risk.** `kotlin.text.Regex` delegates to
   whatever the platform provides, and that is **four different engines**:

   | Target | Engine |
   |---|---|
   | JVM | OpenJDK `java.util.regex` |
   | Android (ART) | ICU-backed `java.util.regex` — *not* the same implementation |
   | Apple targets | `NSRegularExpression` (ICU) |
   | JS / WasmJs | ECMAScript `RegExp` |

   Named groups, lookbehind, Unicode property classes and `\b` semantics all diverge between
   these. Because Cucumber Expressions compile to regex, and `cucumber-jvm`'s expression-to-regex
   output assumes OpenJDK semantics, a faithful port can still misbehave off the JVM.
   *Mitigation:* generate only a conservative regex subset that is legal on all four; run the
   upstream `cucumber-expressions` suite on **every** target including an instrumented Android
   run; and treat any regex construct that needs per-platform `expect`/`actual` as a design smell
   to be removed rather than abstracted.
3. **Generated-name collisions and invalid identifiers** from free-text scenario names,
   especially non-ASCII. *Mitigation:* deterministic sanitiser + collision suffix, covered by
   golden-file tests.
4. **Build-time parser vs runtime parser drift** — the plugin parses on the JVM, the AST is
   embedded as code. Keep exactly one parser implementation in `core` and have the plugin depend
   on its JVM artifact.
5. **Porting by transliteration.** cucumber-jvm's structure is shaped by JVM constraints that
   don't apply here; copying its class layout wholesale would import complexity for no benefit.
   Port *behaviour and tests* faithfully; re-architect *structure* freely.

## 14. Conventions

- Kotlin, `explicitApi()` on all published modules, no `!!` in library code.
- English for code, KDoc, commit messages and docs.
- Every public type gets KDoc with a usage example, and a link to the cucumber.io page that
  defines its behaviour where one exists.
- Tests: `kotlin.test` only in test source sets; upstream fixtures for conformance; golden-file
  tests for both codegen paths.
- All versions live in `gradle/libs.versions.toml`. Pinned as of bootstrap: **Gradle 9.6.1,
  Kotlin 2.4.10, KSP 2.3.10, AGP 9.3.1, coroutines 1.11.0, KotlinPoet 2.3.0.**
- Three build-setup facts, each of which cost a build failure to learn — do not "simplify" them
  back:
  1. **AGP must be 9.x on Gradle 9.6+.** AGP 8.x uses `InternalProblems`, a Gradle internal API
     removed in Gradle 9.6.0; AGP 8.13.2 caps out at Gradle 9.5.
  2. **KMP modules use `com.android.kotlin.multiplatform.library`, not `com.android.library`.**
     Since AGP 9.0 the latter is hard-incompatible with the KMP plugin, and configuration moves
     from a top-level `android { }` block into `kotlin { android { } }`. (`androidLibrary { }` is
     the deprecated spelling of the same block.)
  3. **`libs.*` accessors do not exist inside precompiled script plugins.** The convention plugin
     reads the catalog through `the<VersionCatalogsExtension>().named("libs")` so versions still
     have one home.
- `gradle.properties` raises heap and metaspace deliberately: the Kotlin/Native compiler runs
  inside the daemons, and four Native targets linking concurrently exhaust the defaults. The
  symptom is a bare `Metaspace` task failure that names nothing useful.
- KSP 2.x is versioned independently of Kotlin and pins no Kotlin dependency, so the historical
  Kotlin↔KSP lockstep is no longer a constraint. AGP↔Gradle still is.
- Local toolchain: Apple Silicon, JDK 21 (Temurin), Gradle 8.11.1, Xcode 26.6 + iOS 26.5
  simulator, Android SDK (`~/Library/Android/sdk`, platforms 33–37.0, NDK 27/28, `Medium_Phone`
  AVD), Node 22.17, Chrome + Safari. Devices for Tier B: iPhone 13 (paired) and an Android phone.
- `local.properties` is git-ignored and holds `sdk.dir`.
- Track which upstream Cucumber version we are ported against, in `DEVIATIONS.md`.

## 15. Decisions and remaining setup

### Licence: MIT ✅

Matching the upstream Cucumber projects this is ported from. `LICENSE` holds the MIT text and
`NOTICE` records what is derived and what is embedded verbatim — the vendored fixtures are copied,
not merely referenced, so they are attributed per project with a pointer to the generated file.
Every generated file also names the upstream commit it came from.

### Publishing: GitHub Packages now, Maven Central later

`publish.yml` pushes snapshots from `main` and releases from a `v*` tag to GitHub Packages, which
needs nothing beyond the automatic `GITHUB_TOKEN`. Artifacts carry a Central-compliant POM already:
name, description, url, MIT licence, developer and SCM, plus sources and (empty) javadoc jars.

**To add Maven Central**, three things are needed that cannot be done from this repository:

1. A verified `io.github.menjoo` namespace on Sonatype — proven by creating a repository whose name
   is the code Sonatype gives you.
2. A GPG key, exported armoured, as the `SIGNING_KEY` secret with its passphrase as
   `SIGNING_PASSWORD`. Signing is already wired and activates when `SIGNING_KEY` is present;
   GitHub Packages ignores signatures, Central requires them.
3. A Central repository added to `publishing.repositories` in the convention plugin, with its
   credentials as secrets.

### Group and package name

`io.github.menjoo.cucumberkmp`, because `io.github.<user>` is the namespace Sonatype grants without
a domain. Changing it is a find-and-replace, but gets disruptive once anything is published — so
settle it before the first release rather than after.

### Versioning

The build declares `0.1.0-SNAPSHOT`. A release overrides it with `-Pcucumberkmp.version=<x.y.z>`,
which `publish.yml` derives from the tag. Nothing reads the version from git, so a tag is the single
source of truth for what a release is called.

## 16. Read before writing Phase 1

- The Gherkin specification, and `gherkin-languages.json` plus the good/bad parser corpus.
- The Cucumber Expressions specification and its shared test suite — the grammar is small and
  worth implementing exactly.
- `cucumber-jvm`'s `Runner`, `PickleStepTestStep` and `TestCase` classes, for step and hook
  execution order.
- Kotest `BehaviorSpec` — the KMP-native BDD baseline this project has to justify itself against.
  Our differentiator is exactly one thing: **plain-text `.feature` files that non-developers own**.
