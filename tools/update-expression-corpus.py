#!/usr/bin/env python3
"""Generate the Cucumber Expressions conformance corpus from upstream.

cucumber/cucumber-expressions publishes its test data as language-agnostic YAML so that ports can
prove conformance — see ARCHITECTURE.md §2. Five families:

  cucumber-expression/tokenizer      expression -> tokens
  cucumber-expression/parser         expression -> AST
  cucumber-expression/transformation expression -> generated regex
  cucumber-expression/matching       expression + text -> arguments (or an exception message)
  regular-expression/matching        regex + text -> arguments

Emitted as Kotlin rather than test resources so the suite runs on Native and Wasm too, where
reading files at runtime is impossible. Regex divergence between the four platform engines is the
top correctness risk for this project (ARCHITECTURE.md §13.2), so running these on every target
rather than only the JVM is the point.

Requires PyYAML.

Usage:
    python3 tools/update-expression-corpus.py [--ref main]
"""

from __future__ import annotations

import argparse
import pathlib
import shutil
import subprocess
import tempfile

import yaml

REPO = "https://github.com/cucumber/cucumber-expressions"
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
    / "expression"
    / "ExpressionCorpus.kt"
)

CHUNK_SIZE = 8


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


def kotlin_nullable_string(value: str | None) -> str:
    return "null" if value is None else kotlin_string(value)


def render_arg(value) -> str:
    """Render an expected argument the way Kotlin's toString would."""
    if value is None:
        return "null"
    if value is True:
        return "true"
    if value is False:
        return "false"
    return str(value)


def escape_trace(value: str) -> str:
    return (
        value.replace("\\", "\\\\")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
    )


def token_trace(tokens: list[dict]) -> str:
    return "\n".join(
        f"{t['type']} {t['start']} {t['end']} {escape_trace(t.get('text') or '')}" for t in tokens
    )


def ast_trace(node: dict, depth: int = 0) -> str:
    indent = "  " * depth
    head = f"{indent}{node['type']} {node['start']} {node['end']}"
    if "token" in node and node["token"] is not None:
        return head + f" token={escape_trace(node['token'])}"
    lines = [head]
    for child in node.get("nodes") or []:
        lines.append(ast_trace(child, depth + 1))
    return "\n".join(lines)


def load(path: pathlib.Path) -> dict:
    return yaml.safe_load(path.read_text(encoding="utf-8"))


def render(families: dict, ref: str) -> str:
    out: list[str] = []
    out.append("// GENERATED FILE — DO NOT EDIT BY HAND.")
    out.append("//")
    out.append("// Regenerate with:  python3 tools/update-expression-corpus.py")
    out.append(f"// Source: {REPO} @ {ref}")
    counts = ", ".join(f"{len(v)} {k}" for k, v in families.items())
    out.append(f"// {counts}")
    out.append("")
    out.append("package io.github.menjoo.cucumberkmp.core.expression")
    out.append("")
    out.append("/** A fixture that pins one behaviour of the expression pipeline. */")
    out.append("internal data class ExpressionCase(")
    out.append("    val name: String,")
    out.append("    val expression: String,")
    out.append("    /** Only set for the matching families. */")
    out.append("    val text: String? = null,")
    out.append("    /** Canonical trace, generated regex, or null when an exception is expected. */")
    out.append("    val expected: String? = null,")
    out.append("    /** Rendered argument values, for the matching families. */")
    out.append("    val expectedArgs: List<String>? = null,")
    out.append("    /**")
    out.append("     * True when the expression must not match the text at all.")
    out.append("     *")
    out.append("     * Upstream distinguishes `expected_args:` with no value (no match) from")
    out.append("     * `expected_args: []` (matches, takes no arguments); conflating them would")
    out.append("     * silently pass four fixtures that assert non-matching.")
    out.append("     */")
    out.append("    val expectNoMatch: Boolean = false,")
    out.append("    /** The exact message upstream reports, when the case must fail. */")
    out.append("    val exception: String? = null,")
    out.append(")")
    out.append("")

    for family, cases in families.items():
        constant = f"EXPRESSION_{family.upper()}_CORPUS"
        prefix = family.lower()
        chunks = [cases[i : i + CHUNK_SIZE] for i in range(0, len(cases), CHUNK_SIZE)]
        out.append(f"internal val {constant}: List<ExpressionCase> = buildList {{")
        for index in range(len(chunks)):
            out.append(f"    addAll({prefix}Chunk{index}())")
        out.append("}")
        out.append("")
        for index, chunk in enumerate(chunks):
            out.append(f"private fun {prefix}Chunk{index}(): List<ExpressionCase> = listOf(")
            for case in chunk:
                out.append("    ExpressionCase(")
                out.append(f"        name = {kotlin_string(case['name'])},")
                out.append(f"        expression = {kotlin_string(case['expression'])},")
                if case.get("text") is not None:
                    out.append(f"        text = {kotlin_string(case['text'])},")
                if case.get("expected") is not None:
                    out.append(f"        expected = {kotlin_string(case['expected'])},")
                if case.get("expectedArgs") is not None:
                    rendered = ", ".join(kotlin_string(a) for a in case["expectedArgs"])
                    out.append(f"        expectedArgs = listOf({rendered}),")
                if case.get("expectNoMatch"):
                    out.append("        expectNoMatch = true,")
                if case.get("exception") is not None:
                    out.append(f"        exception = {kotlin_string(case['exception'])},")
                out.append("    ),")
            out.append(")")
            out.append("")

    return "\n".join(out)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--ref", default="main")
    args = parser.parse_args()

    workdir = pathlib.Path(tempfile.mkdtemp(prefix="expression-corpus-"))
    try:
        checkout = workdir / "cucumber-expressions"
        subprocess.run(
            ["git", "clone", "--depth", "1", "--branch", args.ref, "-q", REPO, str(checkout)],
            check=True,
        )
        ref = subprocess.run(
            ["git", "-C", str(checkout), "rev-parse", "HEAD"],
            check=True, capture_output=True, text=True,
        ).stdout.strip()

        testdata = checkout / "testdata"
        families: dict[str, list[dict]] = {}

        def collect(family: str, directory: pathlib.Path, build) -> None:
            cases = []
            for path in sorted(directory.glob("*.yaml")):
                data = load(path)
                case = {"name": path.stem, "expression": data["expression"]}
                if data.get("exception") is not None:
                    case["exception"] = data["exception"]
                else:
                    build(case, data)
                cases.append(case)
            families[family] = cases

        collect(
            "tokenizer",
            testdata / "cucumber-expression" / "tokenizer",
            lambda case, data: case.update(expected=token_trace(data["expected_tokens"])),
        )
        collect(
            "parser",
            testdata / "cucumber-expression" / "parser",
            lambda case, data: case.update(expected=ast_trace(data["expected_ast"])),
        )
        collect(
            "transformation",
            testdata / "cucumber-expression" / "transformation",
            lambda case, data: case.update(expected=data["expected_regex"]),
        )

        def matching(case, data):
            case["text"] = data["text"]
            expected = data.get("expected_args")
            # `expected_args:` with no value means "must not match"; an explicit `[]` means
            # "matches and takes no arguments". These are different assertions.
            if expected is None:
                case["expectNoMatch"] = True
            else:
                case["expectedArgs"] = [render_arg(a) for a in expected]

        collect("matching", testdata / "cucumber-expression" / "matching", matching)
        collect("regex", testdata / "regular-expression" / "matching", matching)

        OUTPUT.parent.mkdir(parents=True, exist_ok=True)
        OUTPUT.write_text(render(families, ref) + "\n", encoding="utf-8")
        print(f"wrote {OUTPUT.relative_to(ROOT)}")
        for family, cases in families.items():
            failing = sum(1 for c in cases if c.get("exception"))
            print(f"  {family}: {len(cases)} cases ({failing} expect an exception)")
        return 0
    finally:
        shutil.rmtree(workdir, ignore_errors=True)


if __name__ == "__main__":
    raise SystemExit(main())
