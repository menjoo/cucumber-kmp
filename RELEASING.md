# Releasing

`main` is protected: changes go through pull requests, and nothing in CI is allowed to write to it.
The release process is shaped by that — the version bump is an ordinary pull request, and the
workflow only reads the result, publishes it, and pushes a tag. Tags are not covered by the branch
ruleset, so no bypass actor and no long-lived token are needed.

## Cutting a release

Three steps, two of them ordinary pull requests.

### 1. Open a pull request setting the version

```bash
git switch -c release-1.0.0
.github/scripts/set-version.sh 1.0.0
git commit -am 'Release 1.0.0'
git push -u origin release-1.0.0
gh pr create --title 'Release 1.0.0' --body 'Sets the version for the 1.0.0 release.'
```

Merge it once CI is green. Reviewing this pull request *is* the release approval — it is the last
point where a human decides the version is right.

### 2. Run the Release workflow

Actions → **Release** → *Run workflow*, entering the same version.

The input is a confirmation, not the source of truth: the workflow refuses to run unless it matches
`cucumberkmp.version` on `main`. That makes it impossible to release "whatever happened to be on
main" by mistake.

### 3. Open a pull request for the next development version

The workflow's summary prints the command. By default:

```bash
git switch -c open-1.0.1-snapshot
.github/scripts/set-version.sh 1.0.1-SNAPSHOT
git commit -am 'Open 1.0.1-SNAPSHOT for development'
```

## What the workflow does, and why in that order

1. **Check** the requested version matches `main`, is not a `-SNAPSHOT`, is semantic, and has no
   existing tag. Published versions are immutable, so re-releasing one is worth catching first.
2. **Verify** — `./gradlew build`, the whole matrix.
3. **Verify what a consumer gets** — publish to `mavenLocal` and build `examples/calculator`
   against those real artifacts, not substituted projects. The last chance to catch a packaging or
   source-set problem while the version is still reversible.
4. **Tag** and push the tag.
5. **Publish** to GitHub Packages.

Steps 4 and 5 are in that order deliberately. Publishing is the irreversible half — a Maven Central
artifact cannot be deleted — so a published artifact with no tag is a permanent inconsistency. A tag
with no artifact is not: delete it, fix the problem, run again.

## If it fails part way

| Failed at | State | What to do |
|---|---|---|
| Steps 1–3 | Nothing tagged, nothing published | Fix and re-run. |
| Step 4 (tag) | Nothing published | Re-run. |
| Step 5 (publish) | Tagged but not published | Delete the tag, fix, run again: `git push --delete origin v1.0.0` |

## Why not one button

An earlier version of this workflow did everything in one run: bump the version, commit, tag,
publish, then open the next snapshot. That needs CI to push to `main`, which the branch ruleset
blocks. Unblocking it would mean either adding *Repository admin* to the ruleset's bypass list and
storing a personal access token as a secret — a long-lived credential that defeats the protection —
or moving the repository to an organisation so a GitHub App could be used instead.

Two pull requests per release is the cost of not doing that. The bump commits are trivial to review,
and the version is visible in the repository rather than only in a tag.

## Where the version lives

`gradle.properties`:

```properties
cucumberkmp.version=0.1.0-SNAPSHOT
```

Everything reads it — all published modules, and `examples/calculator`, which loads this same file
rather than hardcoding a version it would forget to update. Override it for one invocation with
`-Pcucumberkmp.version=1.2.3`.

The scripts the workflow uses are runnable locally:

```bash
.github/scripts/get-version.sh           # prints the current version
.github/scripts/set-version.sh 1.0.0     # rewrites gradle.properties
.github/scripts/next-patch.sh 1.0.0      # prints 1.0.1
```

## Not yet set up: Maven Central

Releases go to GitHub Packages, which needs nothing but the automatic `GITHUB_TOKEN`. Note that the
GitHub Packages Maven registry **requires authentication even for public repositories**, so a
consumer cannot pull these artifacts without a token — Packages is fine for your own projects, but
it is not a public release in the sense most people mean.

Maven Central additionally requires a verified `io.github.menjoo` namespace on Sonatype, a GPG key
as the `SIGNING_KEY` secret with its passphrase as `SIGNING_PASSWORD`, and one more repository in
the convention plugin. Signing is already wired and activates as soon as `SIGNING_KEY` exists.
See ARCHITECTURE.md §15.
