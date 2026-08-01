#!/usr/bin/env bash
# Rewrites the single source of truth for the published version.
#
# Kept as a script rather than inline in the workflow so it can be run and tested locally:
#
#     .github/scripts/set-version.sh 1.0.0
#
set -euo pipefail

if [[ $# -ne 1 ]]; then
  echo "usage: $0 <version>" >&2
  exit 2
fi

version="$1"
file="gradle.properties"
key="cucumberkmp.version"

if [[ ! -f "$file" ]]; then
  echo "error: $file not found (run from the repository root)" >&2
  exit 1
fi

# Fail rather than append: a missing key means the build no longer reads this file, and silently
# adding it back would produce a release nobody's build actually used.
if ! grep -q "^${key}=" "$file"; then
  echo "error: $key is not defined in $file" >&2
  exit 1
fi

# A temporary file instead of sed -i, whose syntax differs between GNU and BSD.
awk -v key="$key" -v value="$version" \
  'BEGIN { FS = "=" } $1 == key { print key "=" value; next } { print }' \
  "$file" > "$file.tmp"
mv "$file.tmp" "$file"

echo "$key=$version"
