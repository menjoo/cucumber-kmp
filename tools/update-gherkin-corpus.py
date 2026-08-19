#!/usr/bin/env python3
"""Generate the Gherkin conformance corpus from upstream cucumber/gherkin testdata.

Upstream publishes 50 `.feature` files that must parse plus 12 that must fail, each with the
exact output its parser produces. Reusing those fixtures is the cheapest correctness guarantee
available to a port -- see ARCHITECTURE.md §2.

The corpus is emitted as Kotlin source rather than test resources on purpose: Kotlin/Native and
Wasm cannot read files at runtime, so embedding the fixtures as code is the only way to run the
same conformance suite on every target. It is also exactly the mechanism the framework itself
uses for `.feature` files, so this dogfoods it.

Expected ASTs are converted to a canonical line-based trace. We compare traces rather than
upstream's JSON because our AST deliberately differs in shape (sealed interfaces instead of
records with three nullable fields); the trace captures the semantics both must agree on.
`GherkinTrace.kt` renders the same format from our AST.

Usage:
    python3 tools/update-gherkin-corpus.py
    python3 tools/update-gherkin-corpus.py --ref <commit-or-branch>
"""

from __future__ import annotations

import argparse
import json
import pathlib
import shutil
import subprocess
import tempfile

REPO = "https://github.com/cucumber/gherkin"
ROOT = pathlib.Path(__file__).resolve().parent.parent
OUTPUT = (
    ROOT
    / "cucumber-kmp-core"
    / "src"
    / "commonTest"
    / "kotlin"
    / "io"
    / "github"
    / "menjoo"
    / "cucumberkmp"
    / "core"
    / "gherkin"
    / "GherkinCorpus.kt"
)

CHUNK_SIZE = 5

KEYWORD_TYPES = {
    "Context": "CONTEXT",
    "Action": "ACTION",
    "Outcome": "OUTCOME",
    "Conjunction": "CONJUNCTION",
    "Unknown": "UNKNOWN",
}


# --------------------------------------------------------------------------- trace


def escape(value: str) -> str:
    return (
        value.replace("\\", "\\\\")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
    )


def location(node: dict) -> str:
    loc = node.get("location", {})
    return f"{loc.get('line', 0)}:{loc.get('column', 0)}"


class TraceBuilder:
    def __init__(self) -> None:
        self.lines: list[str] = []

    def emit(self, depth: int, text: str) -> None:
        self.lines.append("  " * depth + text)

    def tags(self, node: dict, depth: int) -> None:
        for tag in node.get("tags", []):
            self.emit(depth, f"tag {location(tag)} {escape(tag['name'])}")

    def description(self, node: dict, depth: int) -> None:
        self.emit(depth, f"description {escape(node.get('description', ''))}")

    def steps(self, node: dict, depth: int) -> None:
        for step in node.get("steps", []):
            keyword_type = KEYWORD_TYPES.get(step.get("keywordType", "Unknown"), "UNKNOWN")
            self.emit(
                depth,
                f"step {location(step)} keyword={escape(step['keyword'])} "
                f"type={keyword_type} text={escape(step['text'])}",
            )
            if "docString" in step:
                doc = step["docString"]
                media = escape(doc["mediaType"]) if doc.get("mediaType") is not None else "-"
                self.emit(
                    depth + 1,
                    f"docstring {location(doc)} delimiter={escape(doc['delimiter'])} "
                    f"mediaType={media} content={escape(doc['content'])}",
                )
            if "dataTable" in step:
                table = step["dataTable"]
                self.emit(depth + 1, f"datatable {location(table)}")
                self.rows(table.get("rows", []), depth + 2)

    def rows(self, rows: list[dict], depth: int) -> None:
        for row in rows:
            self.emit(depth, f"row {location(row)}")
            for cell in row.get("cells", []):
                self.emit(depth + 1, f"cell {location(cell)} {escape(cell['value'])}")

    def examples(self, node: dict, depth: int) -> None:
        for example in node.get("examples", []):
            self.emit(
                depth,
                f"examples {location(example)} keyword={escape(example['keyword'])} "
                f"name={escape(example['name'])}",
            )
            self.tags(example, depth + 1)
            self.description(example, depth + 1)
            header = example.get("tableHeader")
            if header:
                self.emit(depth + 1, "header")
                self.rows([header], depth + 2)
            self.emit(depth + 1, "body")
            self.rows(example.get("tableBody", []), depth + 2)

    def background(self, node: dict, depth: int) -> None:
        self.emit(
            depth,
            f"background {location(node)} keyword={escape(node['keyword'])} "
            f"name={escape(node['name'])}",
        )
        self.description(node, depth + 1)
        self.steps(node, depth + 1)

    def scenario(self, node: dict, depth: int) -> None:
        self.emit(
            depth,
            f"scenario {location(node)} keyword={escape(node['keyword'])} "
            f"name={escape(node['name'])}",
        )
        self.tags(node, depth + 1)
        self.description(node, depth + 1)
        self.steps(node, depth + 1)
        self.examples(node, depth + 1)

    def rule(self, node: dict, depth: int) -> None:
        self.emit(
            depth,
            f"rule {location(node)} keyword={escape(node['keyword'])} "
            f"name={escape(node['name'])}",
        )
        self.tags(node, depth + 1)
        self.description(node, depth + 1)
        self.children(node, depth + 1)

    def children(self, node: dict, depth: int) -> None:
        for child in node.get("children", []):
            if "background" in child:
                self.background(child["background"], depth)
            elif "scenario" in child:
                self.scenario(child["scenario"], depth)
            elif "rule" in child:
                self.rule(child["rule"], depth)

    def document(self, doc: dict) -> str:
        self.emit(0, "document")
        for comment in doc.get("comments", []):
            self.emit(1, f"comment {location(comment)} {escape(comment['text'])}")
        feature = doc.get("feature")
        if feature is not None:
            self.emit(
                1,
                f"feature {location(feature)} language={feature['language']} "
                f"keyword={escape(feature['keyword'])} name={escape(feature['name'])}",
            )
            self.tags(feature, 2)
            self.description(feature, 2)
            self.children(feature, 2)
        return "\n".join(self.lines)


KEYWORD_TYPES_PICKLE = {
    "Context": "CONTEXT",
    "Action": "ACTION",
    "Outcome": "OUTCOME",
    "Unknown": "UNKNOWN",
}


def pickle_trace(pickles: list[dict]) -> str:
    """Canonical trace of the compiled test cases.

    Synthetic `id`/`astNodeIds` are omitted: they are upstream bookkeeping, not semantics.
    """
    lines: list[str] = []
    for p in pickles:
        loc = p.get("location", {})
        lines.append(
            f"pickle {loc.get('line', 0)}:{loc.get('column', 0)} "
            f"language={p['language']} name={escape(p['name'])}"
        )
        for tag in p.get("tags", []):
            lines.append(f"  tag {escape(tag['name'])}")
        for step in p.get("steps", []):
            # Upstream pickle steps carry no location -- they point back at AST nodes by
            # synthetic id. Ours carry a real SourceLocation, so it is left out of the
            # comparison rather than dropped from the model. See DEVIATIONS.md.
            kind = KEYWORD_TYPES_PICKLE.get(step.get("type", "Unknown"), "UNKNOWN")
            lines.append(f"  step type={kind} text={escape(step['text'])}")
            argument = step.get("argument") or {}
            if "docString" in argument:
                doc = argument["docString"]
                media = escape(doc["mediaType"]) if doc.get("mediaType") is not None else "-"
                lines.append(
                    f"    docstring mediaType={media} content={escape(doc['content'])}"
                )
            if "dataTable" in argument:
                lines.append("    datatable")
                for row in argument["dataTable"].get("rows", []):
                    cells = " | ".join(escape(c["value"]) for c in row.get("cells", []))
                    lines.append(f"      row {cells}")
    return "\n".join(lines)


# -------------------------------------------------------------------- kotlin output


def kotlin_string(value: str) -> str:
    out = (
        value.replace("\\", "\\\\")
        .replace('"', '\\"')
        .replace("$", "\\$")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
    )
    return f'"{out}"'


def render(good: list[tuple[str, str, str]], bad: list[tuple[str, str, list[str]]], ref: str) -> str:
    out: list[str] = []
    out.append("// GENERATED FILE — DO NOT EDIT BY HAND.")
    out.append("//")
    out.append("// Regenerate with:  python3 tools/update-gherkin-corpus.py")
    out.append(f"// Source: {REPO} @ {ref}")
    out.append(f"// {len(good)} parseable features, {len(bad)} failing features.")
    out.append("//")
    out.append("// Embedded as Kotlin rather than test resources because Native and Wasm cannot")
    out.append("// read files at runtime — the same constraint that shapes the whole framework.")
    out.append("")
    out.append("package io.github.menjoo.cucumberkmp.core.gherkin")
    out.append("")
    out.append("/** A feature upstream expects to parse, with its expected canonical traces. */")
    out.append("internal data class CorpusFeature(")
    out.append("    val name: String,")
    out.append("    val source: String,")
    out.append("    val expectedTrace: String,")
    out.append("    /** The compiled test cases upstream produces from this feature. */")
    out.append("    val expectedPickles: String,")
    out.append(")")
    out.append("")
    out.append("/** A feature upstream expects to fail, with the exact errors it reports. */")
    out.append("internal data class CorpusFailure(")
    out.append("    val name: String,")
    out.append("    val source: String,")
    out.append("    val expectedErrors: List<String>,")
    out.append(")")
    out.append("")

    good_chunks = [good[i : i + CHUNK_SIZE] for i in range(0, len(good), CHUNK_SIZE)]
    out.append("internal val GHERKIN_GOOD_CORPUS: List<CorpusFeature> = buildList {")
    for index in range(len(good_chunks)):
        out.append(f"    addAll(goodCorpusChunk{index}())")
    out.append("}")
    out.append("")
    for index, chunk in enumerate(good_chunks):
        out.append(f"private fun goodCorpusChunk{index}(): List<CorpusFeature> = listOf(")
        for name, source, trace, pickles in chunk:
            out.append("    CorpusFeature(")
            out.append(f"        name = {kotlin_string(name)},")
            out.append(f"        source = {kotlin_string(source)},")
            out.append(f"        expectedTrace = {kotlin_string(trace)},")
            out.append(f"        expectedPickles = {kotlin_string(pickles)},")
            out.append("    ),")
        out.append(")")
        out.append("")

    bad_chunks = [bad[i : i + CHUNK_SIZE] for i in range(0, len(bad), CHUNK_SIZE)]
    out.append("internal val GHERKIN_BAD_CORPUS: List<CorpusFailure> = buildList {")
    for index in range(len(bad_chunks)):
        out.append(f"    addAll(badCorpusChunk{index}())")
    out.append("}")
    out.append("")
    for index, chunk in enumerate(bad_chunks):
        out.append(f"private fun badCorpusChunk{index}(): List<CorpusFailure> = listOf(")
        for name, source, errors in chunk:
            out.append("    CorpusFailure(")
            out.append(f"        name = {kotlin_string(name)},")
            out.append(f"        source = {kotlin_string(source)},")
            rendered = ", ".join(kotlin_string(e) for e in errors)
            out.append(f"        expectedErrors = listOf({rendered}),")
            out.append("    ),")
        out.append(")")
        out.append("")

    return "\n".join(out)


def clone_and_pin(ref: str, destination: pathlib.Path) -> str:
    """Clone upstream at `ref` and return the last commit that touched `testdata/`.

    Pinning `HEAD` instead would re-pin on every unrelated upstream commit, and the drift
    check (.github/workflows/upstream-drift.yml) would then report news every week the
    fixtures had not moved. The clone is blobless rather than shallow because a shallow
    one cannot answer a path-scoped log; trees are cheap and blobs arrive on checkout.
    """
    subprocess.run(
        ["git", "clone", "--filter=blob:none", "--branch", ref, "-q", REPO, str(destination)],
        check=True,
    )
    log = subprocess.run(
        ["git", "-C", str(destination), "log", "-1", "--format=%H", "--", "testdata"],
        check=True,
        capture_output=True,
        text=True,
    ).stdout.strip()
    if log:
        return log
    # No commit touches testdata/ -- upstream moved it, so fall back to naming the checkout.
    return subprocess.run(
        ["git", "-C", str(destination), "rev-parse", "HEAD"],
        check=True,
        capture_output=True,
        text=True,
    ).stdout.strip()


# --------------------------------------------------------------------------- main


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--ref", default="main", help="upstream ref to generate from")
    args = parser.parse_args()

    workdir = pathlib.Path(tempfile.mkdtemp(prefix="gherkin-corpus-"))
    try:
        checkout = workdir / "gherkin"
        ref = clone_and_pin(args.ref, checkout)

        good: list[tuple[str, str, str, str]] = []
        for feature_path in sorted((checkout / "testdata" / "good").glob("*.feature")):
            ast_path = feature_path.with_suffix(".feature.ast.ndjson")
            if not ast_path.exists():
                continue
            document = json.loads(ast_path.read_text(encoding="utf-8"))["gherkinDocument"]

            pickles_path = feature_path.with_suffix(".feature.pickles.ndjson")
            pickles = []
            if pickles_path.exists():
                pickles = [
                    json.loads(line)["pickle"]
                    for line in pickles_path.read_text(encoding="utf-8").splitlines()
                    if line.strip()
                ]

            good.append(
                (
                    feature_path.name,
                    feature_path.read_text(encoding="utf-8"),
                    TraceBuilder().document(document),
                    pickle_trace(pickles),
                )
            )

        bad: list[tuple[str, str, list[str]]] = []
        for feature_path in sorted((checkout / "testdata" / "bad").glob("*.feature")):
            errors_path = feature_path.with_suffix(".feature.errors.ndjson")
            if not errors_path.exists():
                continue
            messages = [
                json.loads(line)["parseError"]["message"]
                for line in errors_path.read_text(encoding="utf-8").splitlines()
                if line.strip()
            ]
            bad.append(
                (feature_path.name, feature_path.read_text(encoding="utf-8"), messages)
            )

        OUTPUT.parent.mkdir(parents=True, exist_ok=True)
        OUTPUT.write_text(render(good, bad, ref) + "\n", encoding="utf-8")
        print(f"wrote {OUTPUT.relative_to(ROOT)}")
        print(f"  {len(good)} good features, {len(bad)} bad features, from {ref[:12]}")
        return 0
    finally:
        shutil.rmtree(workdir, ignore_errors=True)


if __name__ == "__main__":
    raise SystemExit(main())
