#!/usr/bin/env bash
# Prints the next patch version, used as the default development version after a release.
#
#     .github/scripts/next-patch.sh 1.2.3   ->  1.2.4
#     .github/scripts/next-patch.sh 1.2.3-rc1 -> 1.2.4
#
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "usage: $0 <version>" >&2
  exit 2
fi

# Drop any pre-release suffix: the version after 1.2.3-rc1 is 1.2.4, not 1.2.3-rc2.
base="${1%%-*}"

if [[ ! "$base" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
  echo "error: '$1' is not a semantic version" >&2
  exit 1
fi

IFS=. read -r major minor patch <<< "$base"
echo "$major.$minor.$((patch + 1))"
