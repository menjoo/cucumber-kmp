# Releasing

## How to cut a release

Actions → **Release** → *Run workflow*, and enter the version without a leading `v`:

| Input | Meaning |
|---|---|
| `version` | The version to publish, e.g. `1.0.0` |
| `nextVersion` | Optional. The version to open for development afterwards. Defaults to a patch bump, so `1.0.0` leaves `main` on `1.0.1-SNAPSHOT`. |

That is the whole process. Nothing is published by merging to `main`.

## Why merging does not publish

A merge cannot know whether it is a patch, a minor or a major. Projects that publish on every
merge have to encode that intent somewhere else — a changeset file in the pull request, or
conventional commit messages parsed by a bot. Both add process to every pull request, and neither
is common in the Kotlin ecosystem. Deciding a version is an explicit act here instead.

There is also no snapshot publishing. To try an unreleased change from another project, either
depend on the repository as a composite build (as [`examples/calculator`](examples/calculator)
does), or run `./gradlew publishToMavenLocal`.

## What the workflow does, and why in that order

1. **Validate** the version is semantic and its tag does not already exist. Published versions are
   immutable, so re-releasing one is a mistake worth catching before anything else runs.
2. **Set the version** in `gradle.properties`, *before* building, so every artifact, POM and test
   runs against the exact version being released rather than the snapshot.
3. **Verify** — `./gradlew build`, the whole matrix.
4. **Verify what a consumer gets** — publish to `mavenLocal` and build
   `examples/calculator` against those real artifacts, not substituted projects. This is the last
   chance to catch a packaging or source-set problem while the version is still reversible.
5. **Commit and tag**, then push.
6. **Publish** to GitHub Packages.
7. **Open the next development version** and push that.

Steps 5 and 6 are in that order deliberately. Publishing is the irreversible half — a Maven Central
artifact cannot be deleted — so a published artifact with no matching tag or commit is a permanent
inconsistency. A tag with no artifact is not: delete the tag, fix the problem, run the workflow
again.

## If it fails part way

| Failed at | State | What to do |
|---|---|---|
| Steps 1–4 | Nothing pushed, nothing published | Fix and re-run. The version change lives only on the runner. |
| Step 5 (commit/tag/push) | Nothing published | Re-run. If the tag was pushed but the commit was not, delete the tag first: `git push --delete origin vX.Y.Z`. |
| Step 6 (publish) | Tag and release commit are on `main`, artifacts are not published | Do **not** re-run the workflow — it will refuse, because the tag exists. Fix the cause and publish from that tag manually, or delete the tag and the release commit and start over. |
| Step 7 (next version) | Released successfully; `main` is still on the release version | Bump it by hand: `.github/scripts/set-version.sh 1.0.1-SNAPSHOT`, then commit and push. |

## Where the version lives

`gradle.properties`:

```properties
cucumberkmp.version=0.1.0-SNAPSHOT
```

Everything reads it — all published modules, and `examples/calculator`, which loads this same file
rather than hardcoding a version it would forget to update. Override it for one invocation with
`-Pcucumberkmp.version=1.2.3`.

The two scripts the workflow uses are runnable locally:

```bash
.github/scripts/set-version.sh 1.0.0     # rewrites gradle.properties
.github/scripts/next-patch.sh 1.0.0      # prints 1.0.1
```

## Not yet set up: Maven Central

Releases currently go to GitHub Packages, which needs nothing but the automatic `GITHUB_TOKEN`.
Maven Central additionally requires a verified `io.github.menjoo` namespace on Sonatype, a GPG key
as the `SIGNING_KEY` secret with its passphrase as `SIGNING_PASSWORD`, and one more repository in
the convention plugin. Signing is already wired and activates as soon as `SIGNING_KEY` exists.
See ARCHITECTURE.md §15.
