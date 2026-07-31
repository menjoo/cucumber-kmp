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

### Error message wording is close, not identical

We emit upstream's shape — `(line:column): expected: #EOF, #TagLine, …, got 'text'` — because
that is what users search for, but the wording is not guaranteed identical token-for-token. To be
verified against upstream's "bad" corpus in Phase 4; treat any mismatch found there as a bug in
this table, not a licence to diverge.

### The language header must precede tags

`# language: xx` appearing after a feature's tags is reported as an error. Upstream's grammar
places `#Language` before `#TagLine`, so this should agree, but it is an explicit choice here
rather than a consequence of a generated state machine.

## Unverified assumptions

Not deviations — places where we made a judgement call that the upstream corpus will settle in
Phase 4 (task #6). Listed so they are checked rather than forgotten.

| Assumption | Where |
|---|---|
| Blank lines between table rows do **not** end the table; only a new construct does | `GherkinParser.parseTableRows` |
| Descriptions preserve each line's original indentation, dropping only leading/trailing blank lines | `GherkinParser.parseDescription` |
| A comment line inside a description ends the description | `GherkinParser.isStructural` |
| Content after a table row's final `|` is ignored | `GherkinLine.tableCells` |
| An unrecognised cell escape (`\x`) is preserved verbatim, backslash included | `GherkinLine.tableCells` |
| Cell columns are computed on the unescaped buffer, so escapes before cell content shift the reported column | `GherkinLine.buildCell` |
