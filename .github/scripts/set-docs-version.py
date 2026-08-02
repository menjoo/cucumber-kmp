#!/usr/bin/env python3
"""Point the documented install snippets at a released version.

`set-version.sh` moves the version the build *produces*. This moves the version the documentation
tells people to *consume*, which is a different number: it may only change once a release has
actually landed on Central, so it lags the build by one step. See RELEASING.md.

Only dependency coordinates are rewritten:

    id("io.github.menjoo.cucumberkmp") version "X"
    io.github.menjoo.cucumberkmp:<module>:X

Anchoring on the coordinate rather than on the version is the whole point. This repository
deliberately documents the 0.1.0 Central failure in several places -- "0.1.0 was lost to precisely
this", "0.1.0 does not exist on Central", "the `v0.1.0` tag remains as a record" -- and a
substitution that matched bare version numbers would quietly erase that history. Prose is left
alone by construction, including ARCHITECTURE.md's status lines, which need a human anyway.

Usage:
    .github/scripts/set-docs-version.sh 1.2.3
    .github/scripts/set-docs-version.sh 1.2.3 --check    # report, change nothing
"""

from __future__ import annotations

import argparse
import pathlib
import re
import sys

# Files carrying install snippets. A file listed here is expected to contain at least one
# coordinate; see the "anchors matched nothing" check below.
DOCUMENTED = ("README.md",)

GROUP = re.escape("io.github.menjoo.cucumberkmp")

# `id("io.github.menjoo.cucumberkmp") version "0.1.1"` — the Gradle plugin marker. Deliberately
# not a general `version "..."` match, which would also rewrite the KSP plugin on the line above.
PLUGIN = re.compile(rf'(id\("{GROUP}"\)\s+version\s+")([^"]+)(")')

# `io.github.menjoo.cucumberkmp:cucumber-kmp-core:0.1.1` — a Maven coordinate in any position.
COORDINATE = re.compile(rf"({GROUP}:[A-Za-z0-9._-]+:)([0-9][A-Za-z0-9.+-]*)")

SEMVER = re.compile(r"^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?$")


class Change:
    def __init__(self, path: str, line: int, old: str, new: str) -> None:
        self.path, self.line, self.old, self.new = path, line, old, new

    def __str__(self) -> str:
        return f"{self.path}:{self.line}  {self.old} -> {self.new}"


def retarget(text: str, path: str, version: str) -> tuple[str, list[Change], int]:
    """Returns the rewritten text, the changes made, and how many coordinates were seen."""
    changes: list[Change] = []
    seen = 0
    out = []

    for number, line in enumerate(text.splitlines(keepends=True), start=1):
        def replace(match: re.Match[str]) -> str:
            nonlocal seen
            seen += 1
            groups = match.groups()
            # PLUGIN captures (prefix, version, suffix); COORDINATE captures (prefix, version).
            current = groups[1]
            if current != version:
                changes.append(Change(path, number, current, version))
            return groups[0] + version + (groups[2] if len(groups) > 2 else "")

        line = PLUGIN.sub(replace, line)
        line = COORDINATE.sub(replace, line)
        out.append(line)

    return "".join(out), changes, seen


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("version", help="the released version the docs should point at")
    parser.add_argument(
        "--check",
        action="store_true",
        help="report what would change and exit non-zero if anything would, writing nothing",
    )
    parser.add_argument("files", nargs="*", default=None, help=f"defaults to {' '.join(DOCUMENTED)}")
    args = parser.parse_args()

    if not SEMVER.match(args.version):
        sys.exit(f"error: '{args.version}' is not a semantic version")
    if args.version.endswith("-SNAPSHOT"):
        sys.exit(
            f"error: {args.version} is a development version. The documentation points at what "
            "consumers can resolve, which a snapshot is not."
        )

    paths = [pathlib.Path(p) for p in (args.files or DOCUMENTED)]
    changes: list[Change] = []
    total_seen = 0

    for path in paths:
        if not path.is_file():
            sys.exit(f"error: {path} not found (run from the repository root)")
        original = path.read_text(encoding="utf-8")
        updated, found, seen = retarget(original, str(path), args.version)
        total_seen += seen
        changes += found
        if found and not args.check:
            path.write_text(updated, encoding="utf-8")

    # Silence here would mean "documentation is current" and "the anchors no longer match" look
    # identical, which is how a script like this rots unnoticed after a docs restructure.
    if total_seen == 0:
        sys.exit(
            f"error: no cucumber-kmp coordinates found in {', '.join(str(p) for p in paths)}. "
            "The install snippets moved or changed shape — update the patterns in this script."
        )

    for change in changes:
        print(change)

    if not changes:
        print(f"{total_seen} coordinate(s) already at {args.version}, nothing to do")
        return 0

    files = len({change.path for change in changes})
    verb = "would update" if args.check else "updated"
    print(f"{len(changes)} coordinate(s) {verb} in {files} file(s), of {total_seen} found")
    print("ARCHITECTURE.md is prose and is not touched — update its status lines by hand.")

    return 1 if args.check else 0


if __name__ == "__main__":
    raise SystemExit(main())
