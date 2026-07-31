# Deviations from Cucumber

`cucumber-kmp` is a port, so behavioural fidelity to Cucumber is a requirement, not an
aspiration — see ARCHITECTURE.md §2. Every intentional difference belongs here, with its reason.
An undocumented divergence in a port is a bug report waiting to happen.

**Ported against:** upstream `cucumber/gherkin` `main` as of 2026-07-31 (80 languages).

## Architectural

These follow from targeting Kotlin/Native and Wasm, where Cucumber's JVM mechanisms do not exist.

### No `io.cucumber` API compatibility

We port semantics and naming, not the Java class hierarchy. `cucumber-kmp` is not a drop-in jar
replacement, and annotations live in our own package.

### Step discovery is compile-time, not reflective

Cucumber scans the classpath and invokes steps reflectively. We generate a static registry with
KSP. Observable consequence: **glue classes must have a no-arg constructor, or one whose
parameters are themselves glue-registerable.** No DI container is supported. The scenario-scoping
contract is preserved: fresh instances per scenario, shared across the steps within one.
See ARCHITECTURE.md §8.

### Ambiguous steps fail the build, not the test run

cucumber-jvm throws `AmbiguousStepDefinitionsException` at runtime. Two definitions matching one
step is always a bug, and KSP can see it at compile time, so we fail the build and name both
source locations. Strictly earlier; still a behavioural difference.

### Runtime tag filtering cannot report a real skip

`kotlin.test` has no cross-platform assume/skip. A scenario excluded by a *runtime* tag expression
is reported as a **pass with a skipped note** rather than a platform-level skip. Build-time
filtering does not have this problem: those scenarios are generated as `@Ignore` and are reported
as skipped properly. See ARCHITECTURE.md §9.

## Parser

The parser is pinned by upstream's own test data: all 50 parseable fixtures produce a matching AST
and all 12 failing fixtures produce byte-identical error messages, on every target. See
`GherkinCorpusTest`. Error messages, positions and `expected: #Token, …` lists are therefore
**exact**, not approximate.

### Markdown-with-Gherkin is not supported

Upstream's test data includes `.feature.md` fixtures — feature text embedded in a Markdown
document. We skip them and support only plain `.feature` files.

### The end-of-file token list is copied, not derived

Upstream reports a file ending in dangling tags as
`unexpected end of file, expected: #TagLine, #RuleLine, #Comment, #Empty`, and prints that same
list whether the tags sit at feature level or inside a scenario outline — even where a
`#ScenarioLine` or `#ExamplesLine` would plainly be valid. Our list is reproduced from its
observed output rather than derived from a state machine.

Consequence: **if upstream changes that list, ours must be changed to match** — it cannot be
re-derived from first principles. Pinned by `unexpected_eof.feature` and
`unexpected_end_of_file.feature`.

### The language header must precede tags

`# language: xx` appearing after a feature's tags is reported as an error. Upstream's grammar
places `#Language` before `#TagLine`, so this should agree, but no fixture covers it, so it
remains our choice rather than a verified behaviour.

## Unverified assumptions

Judgement calls no upstream fixture exercises. Listed so they are checked rather than forgotten.

| Assumption | Where |
|---|---|
| Content after a table row's final `|` is ignored | `GherkinLine.tableCells` |
| An unrecognised cell escape (`\x`) is preserved verbatim, backslash included | `GherkinLine.tableCells` |
| A second data table on one step ends the step rather than erroring (a second *doc string* is an error, and is covered) | `GherkinParser.parseStep` |
| A step line appearing after an `Examples` block is an error rather than a further step | `GherkinParser.parseScenario` |
| `#ExamplesLine` is omitted from the expected-token list inside a `Background` | `GherkinParser.expectedAfterStep` |
