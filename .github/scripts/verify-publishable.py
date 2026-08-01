#!/usr/bin/env python3
"""Check that every published module would pass Maven Central validation.

Central rejects a deployment wholesale if any module is missing a sources jar, a javadoc jar, a
signature, or POM metadata. That rejection arrives *after* the release has been tagged, so this
reproduces the same checks against a local publication while the release is still reversible.

The 0.1.0 release failed on exactly these rules: the two JVM-only modules had none of it, and
GitHub Packages had accepted them regardless — publishing successfully somewhere is not evidence
that Central will take it.

Usage:
    ./gradlew publishToMavenLocal            # ideally with SIGNING_KEY set
    .github/scripts/verify-publishable.py
    .github/scripts/verify-publishable.py --version 1.2.3 --no-signatures
"""

from __future__ import annotations

import argparse
import pathlib
import re
import sys

GROUP_PATH = "io/github/menjoo/cucumberkmp"

# Every element Central requires in a POM.
REQUIRED_POM_ELEMENTS = ("name", "description", "url", "licenses", "scm", "developers")

# Artifact kinds that each need a detached signature alongside them.
SIGNABLE_SUFFIXES = (".jar", ".pom", ".module")


def read_version() -> str:
    text = pathlib.Path("gradle.properties").read_text(encoding="utf-8")
    match = re.search(r"^cucumberkmp\.version=(.+)$", text, re.M)
    if not match:
        sys.exit("error: cucumberkmp.version is not defined in gradle.properties")
    return match.group(1).strip()


def check_module(directory: pathlib.Path, is_marker: bool, require_signatures: bool) -> list[str]:
    files = {path.name for path in directory.iterdir() if path.is_file()}
    problems: list[str] = []

    # A Gradle plugin marker is a POM with no artifacts, so Central asks it only for metadata.
    if not is_marker:
        if not any(name.endswith("-sources.jar") for name in files):
            problems.append("no sources jar")
        if not any(name.endswith("-javadoc.jar") for name in files):
            problems.append("no javadoc jar")

    if require_signatures:
        for name in sorted(files):
            if name.endswith(SIGNABLE_SUFFIXES) and f"{name}.asc" not in files:
                problems.append(f"unsigned {name}")

    poms = [name for name in files if name.endswith(".pom")]
    if not poms:
        problems.append("no pom")
    else:
        pom = (directory / poms[0]).read_text(encoding="utf-8")
        problems += [
            f"pom has no <{element}>"
            for element in REQUIRED_POM_ELEMENTS
            if f"<{element}>" not in pom
        ]

    return problems


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--version", help="defaults to cucumberkmp.version in gradle.properties")
    parser.add_argument(
        "--repository",
        default=str(pathlib.Path.home() / ".m2" / "repository"),
        help="local Maven repository to inspect",
    )
    parser.add_argument(
        "--no-signatures",
        action="store_true",
        help="skip signature checks, for a build published without SIGNING_KEY",
    )
    args = parser.parse_args()

    version = args.version or read_version()
    group = pathlib.Path(args.repository) / GROUP_PATH

    if not group.is_dir():
        sys.exit(f"error: {group} not found — run ./gradlew publishToMavenLocal first")

    modules = sorted(path for path in group.iterdir() if (path / version).is_dir())
    if not modules:
        sys.exit(f"error: nothing published at version {version} under {group}")

    failures = 0
    for module in modules:
        is_marker = module.name.endswith("gradle.plugin")
        problems = check_module(module / version, is_marker, not args.no_signatures)
        if problems:
            failures += 1
            print(f"FAIL  {module.name}")
            for problem in problems:
                print(f"        {problem}")
        else:
            print(f"ok    {module.name}")

    print()
    if failures:
        print(f"{failures} of {len(modules)} modules would be rejected by Maven Central.")
        return 1

    print(f"All {len(modules)} modules satisfy Maven Central's requirements at {version}.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
