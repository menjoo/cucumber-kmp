#!/usr/bin/env python3
"""Regenerate the Gherkin dialect table from upstream cucumber/gherkin.

ARCHITECTURE.md §6 requires the i18n keyword table to be generated from upstream's
`gherkin-languages.json` rather than hand-typed. This script vendors that file and emits
`GherkinDialects.kt`.

Usage:
    python3 tools/update-gherkin-dialects.py            # use the vendored JSON
    python3 tools/update-gherkin-dialects.py --download  # refresh the JSON first

The generated map is split into chunked functions on purpose: one 80-language initializer
exceeds the JVM's 64 KB per-method bytecode limit.
"""

from __future__ import annotations

import argparse
import json
import pathlib
import sys
import urllib.request

UPSTREAM = "https://raw.githubusercontent.com/cucumber/gherkin/main/gherkin-languages.json"

ROOT = pathlib.Path(__file__).resolve().parent.parent
VENDORED = ROOT / "tools" / "gherkin-languages.json"
OUTPUT = (
    ROOT
    / "cucumber-kmp-core"
    / "src"
    / "commonMain"
    / "kotlin"
    / "io"
    / "github"
    / "menjoo"
    / "cucumberkmp"
    / "core"
    / "gherkin"
    / "GherkinDialects.kt"
)

CHUNK_SIZE = 10

# JSON key -> Kotlin constructor parameter name
KEYWORD_FIELDS = [
    ("feature", "featureKeywords"),
    ("rule", "ruleKeywords"),
    ("background", "backgroundKeywords"),
    ("scenario", "scenarioKeywords"),
    ("scenarioOutline", "scenarioOutlineKeywords"),
    ("examples", "examplesKeywords"),
    ("given", "givenKeywords"),
    ("when", "whenKeywords"),
    ("then", "thenKeywords"),
    ("and", "andKeywords"),
    ("but", "butKeywords"),
]


def kotlin_string(value: str) -> str:
    """Escape a Python str as a Kotlin string literal."""
    out = value.replace("\\", "\\\\").replace('"', '\\"').replace("$", "\\$")
    out = out.replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t")
    return f'"{out}"'


def kotlin_list(values: list[str]) -> str:
    return "listOf(" + ", ".join(kotlin_string(v) for v in values) + ")"


def render_dialect(tag: str, data: dict) -> str:
    lines = [f"    {kotlin_string(tag)} to GherkinDialect("]
    lines.append(f"        language = {kotlin_string(tag)},")
    lines.append(f"        name = {kotlin_string(data['name'])},")
    lines.append(f"        nativeName = {kotlin_string(data['native'])},")
    for json_key, kotlin_name in KEYWORD_FIELDS:
        lines.append(f"        {kotlin_name} = {kotlin_list(data.get(json_key, []))},")
    lines.append("    ),")
    return "\n".join(lines)


def render(languages: dict) -> str:
    tags = sorted(languages)
    chunks = [tags[i : i + CHUNK_SIZE] for i in range(0, len(tags), CHUNK_SIZE)]

    out: list[str] = []
    out.append("// GENERATED FILE — DO NOT EDIT BY HAND.")
    out.append("//")
    out.append("// Regenerate with:  python3 tools/update-gherkin-dialects.py --download")
    out.append(f"// Source: {UPSTREAM}")
    out.append(f"// Languages: {len(tags)}")
    out.append("")
    out.append("package io.github.menjoo.cucumberkmp.core.gherkin")
    out.append("")
    out.append("/**")
    out.append(" * Every Gherkin dialect published upstream, keyed by its language tag.")
    out.append(" *")
    out.append(" * Split into chunked functions because a single initializer for all")
    out.append(f" * {len(tags)} languages exceeds the JVM's 64 KB per-method bytecode limit.")
    out.append(" */")
    out.append("internal val GHERKIN_DIALECTS: Map<String, GherkinDialect> = buildMap {")
    for index in range(len(chunks)):
        out.append(f"    putAll(gherkinDialectsChunk{index}())")
    out.append("}")
    out.append("")

    for index, chunk in enumerate(chunks):
        out.append(
            f"private fun gherkinDialectsChunk{index}(): Map<String, GherkinDialect> = mapOf("
        )
        for tag in chunk:
            out.append(render_dialect(tag, languages[tag]))
        out.append(")")
        out.append("")

    return "\n".join(out)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--download",
        action="store_true",
        help="refresh the vendored gherkin-languages.json from upstream first",
    )
    args = parser.parse_args()

    if args.download:
        print(f"downloading {UPSTREAM}")
        with urllib.request.urlopen(UPSTREAM, timeout=60) as response:
            VENDORED.write_bytes(response.read())

    if not VENDORED.exists():
        print(f"missing {VENDORED}; run with --download", file=sys.stderr)
        return 1

    languages = json.loads(VENDORED.read_text(encoding="utf-8"))
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    OUTPUT.write_text(render(languages) + "\n", encoding="utf-8")
    print(f"wrote {OUTPUT.relative_to(ROOT)} ({len(languages)} languages)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
