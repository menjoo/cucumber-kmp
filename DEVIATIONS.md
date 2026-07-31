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

## Cucumber Expressions

Pinned by upstream's own test data: all 120 fixtures across the tokenizer, parser, regex-generation
and matching families pass on every target, with byte-identical exception messages including the
caret line. See `ExpressionCorpusTest`.

### No capture-group transformers

Upstream's `TreeRegexp`/`GroupBuilder` build a tree of capture groups so a `CaptureGroupTransformer`
can receive a parameter's *sub*-groups. We track only each parameter's own group index, so a
transformer receives the **whole text the parameter matched** and must parse it itself. The built-in
`{string}` type does exactly that: it strips the surrounding quotes from the matched text rather
than reading inner groups.

Nested groups inside a custom parameter type's regexp are supported and correctly skipped when
computing indices — they simply are not exposed to the transformer.

### `{biginteger}` and `{bigdecimal}` yield `String`

Kotlin's common standard library has no arbitrary-precision numeric types. Narrowing to `Long` or
`Double` would silently lose the precision the author asked for, so these types hand back the raw
matched text (with group separators removed, for `bigdecimal`). Revisit if a multiplatform
big-number library becomes a dependency worth taking.

### `{float}`, `{double}` and `{bigdecimal}` use English separators

Upstream derives the number regex from the ambient locale. A compile-time framework cannot: the
generated regex is baked into generated code at build time, so it must not depend on the machine
that ran the build. `.` is the decimal separator and `,` a group separator, always.

### Regular expressions infer types by exact regexp match only

A capture group whose source is exactly one of a registered type's regexps adopts that type, so
`(\d+)` yields an `Int`. Anything else yields the matched text. Upstream's matching is likewise
source-based, but we do not attempt the parameter-type preference resolution it performs for
ambiguous cases beyond honouring `preferForRegexpMatch`.

## Portability hazards

Not deviations from Cucumber — cross-target traps this port has hit, recorded so they are not
rediscovered.

### `Float.toString()` and `Double.toString()` are not portable

Kotlin/JS has one number type, so `1500.0` prints as `1500` there and `1500.0` on the JVM and
Native. Anything that renders a captured value into a message — a failure report, a generated
snippet — must normalise rather than rely on `toString`.

### Runtime type checks cannot distinguish numeric types on JS

`1500.0 is Int` is **true** on Kotlin/JS. Code that branches on a captured value's runtime type
will behave differently there; branch on the declared parameter type instead.

## Unverified assumptions

Judgement calls no upstream fixture exercises. Listed so they are checked rather than forgotten.

| Assumption | Where |
|---|---|
| Content after a table row's final `|` is ignored | `GherkinLine.tableCells` |
| An unrecognised cell escape (`\x`) is preserved verbatim, backslash included | `GherkinLine.tableCells` |
| A second data table on one step ends the step rather than erroring (a second *doc string* is an error, and is covered) | `GherkinParser.parseStep` |
| A step line appearing after an `Examples` block is an error rather than a further step | `GherkinParser.parseScenario` |
| `#ExamplesLine` is omitted from the expected-token list inside a `Background` | `GherkinParser.expectedAfterStep` |
