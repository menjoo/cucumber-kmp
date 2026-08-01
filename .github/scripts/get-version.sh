#!/usr/bin/env bash
# Prints the version the build is currently set to.
#
#     .github/scripts/get-version.sh   ->  0.1.0-SNAPSHOT
#
set -euo pipefail

file="gradle.properties"
key="cucumberkmp.version"

if [[ ! -f "$file" ]]; then
  echo "error: $file not found (run from the repository root)" >&2
  exit 1
fi

line=$(grep "^${key}=" "$file" || true)
if [[ -z "$line" ]]; then
  echo "error: $key is not defined in $file" >&2
  exit 1
fi

echo "${line#*=}"
