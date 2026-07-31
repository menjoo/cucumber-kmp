#!/usr/bin/env python3
"""Generate the tag-expression conformance corpus from upstream cucumber/tag-expressions.

Three fixture files, each pinning a different aspect:

  parsing.yml     expression -> canonical rendered form (round-trips the AST)
  evaluations.yml expression + tags -> boolean result
  errors.yml      expression -> exact error message

Emitted as Kotlin so the suite runs on every target, matching the other corpora.
See ARCHITECTURE.md §2.

Requires PyYAML.

Usage:
    python3 tools/update-tag-expression-corpus.py [--ref main]
"""

from __future__ import annotations

import argparse
import pathlib
import shutil
import subprocess
import tempfile

import yaml

REPO = "https://github.com/cucumber/tag-expressions"
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
    / "tag"
    / "TagExpressionCorpus.kt"
)


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


def render(parsing, evaluations, errors, ref: str) -> str:
    out: list[str] = []
    out.append("// GENERATED FILE — DO NOT EDIT BY HAND.")
    out.append("//")
    out.append("// Regenerate with:  python3 tools/update-tag-expression-corpus.py")
    out.append(f"// Source: {REPO} @ {ref}")
    out.append(
        f"// {len(parsing)} parsing, {len(evaluations)} evaluation, {len(errors)} error cases."
    )
    out.append("")
    out.append("package io.github.menjoo.cucumberkmp.core.tag")
    out.append("")
    out.append("internal data class TagParsingCase(val expression: String, val formatted: String)")
    out.append("")
    out.append("internal data class TagEvaluationCase(")
    out.append("    val expression: String,")
    out.append("    val variables: List<String>,")
    out.append("    val result: Boolean,")
    out.append(")")
    out.append("")
    out.append("internal data class TagErrorCase(val expression: String, val error: String)")
    out.append("")

    out.append("internal val TAG_PARSING_CORPUS: List<TagParsingCase> = listOf(")
    for case in parsing:
        out.append(
            f"    TagParsingCase({kotlin_string(case['expression'])}, {kotlin_string(case['formatted'])}),"
        )
    out.append(")")
    out.append("")

    out.append("internal val TAG_EVALUATION_CORPUS: List<TagEvaluationCase> = listOf(")
    for case in evaluations:
        variables = ", ".join(kotlin_string(v) for v in case["variables"])
        result = "true" if case["result"] else "false"
        out.append(
            f"    TagEvaluationCase({kotlin_string(case['expression'])}, listOf({variables}), {result}),"
        )
    out.append(")")
    out.append("")

    out.append("internal val TAG_ERROR_CORPUS: List<TagErrorCase> = listOf(")
    for case in errors:
        out.append(
            f"    TagErrorCase({kotlin_string(case['expression'])}, {kotlin_string(case['error'])}),"
        )
    out.append(")")
    out.append("")

    return "\n".join(out)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--ref", default="main")
    args = parser.parse_args()

    workdir = pathlib.Path(tempfile.mkdtemp(prefix="tag-expression-corpus-"))
    try:
        checkout = workdir / "tag-expressions"
        subprocess.run(
            ["git", "clone", "--depth", "1", "--branch", args.ref, "-q", REPO, str(checkout)],
            check=True,
        )
        ref = subprocess.run(
            ["git", "-C", str(checkout), "rev-parse", "HEAD"],
            check=True, capture_output=True, text=True,
        ).stdout.strip()

        testdata = checkout / "testdata"
        parsing = yaml.safe_load((testdata / "parsing.yml").read_text(encoding="utf-8"))
        errors = yaml.safe_load((testdata / "errors.yml").read_text(encoding="utf-8"))

        # evaluations.yml groups several tag sets under one expression; flatten so each
        # assertion is its own case and a failure names exactly one.
        evaluations = []
        for group in yaml.safe_load((testdata / "evaluations.yml").read_text(encoding="utf-8")):
            for test in group["tests"]:
                evaluations.append(
                    {
                        "expression": group["expression"],
                        "variables": test["variables"] or [],
                        "result": test["result"],
                    }
                )

        OUTPUT.parent.mkdir(parents=True, exist_ok=True)
        OUTPUT.write_text(render(parsing, evaluations, errors, ref) + "\n", encoding="utf-8")
        print(f"wrote {OUTPUT.relative_to(ROOT)}")
        print(f"  {len(parsing)} parsing, {len(evaluations)} evaluation, {len(errors)} error cases")
        return 0
    finally:
        shutil.rmtree(workdir, ignore_errors=True)


if __name__ == "__main__":
    raise SystemExit(main())
