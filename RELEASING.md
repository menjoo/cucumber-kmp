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

The job runs in the `release` environment, so it waits for a deployment approval before it starts.
That approval is what releases the signing key and the Central Portal credentials to the run —
they are environment secrets, not repository secrets, so no other workflow can reach them.

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
2. **Check the Central configuration** — before building, so a missing secret costs seconds rather
   than a full matrix.
3. **Verify** — `./gradlew build`, the whole matrix.
4. **Verify what a consumer gets** — publish to `mavenLocal` *signed*, and build
   `examples/calculator` against those real artifacts rather than substituted projects.
5. **Verify the deployment would pass Central** — `verify-publishable.py` applies Central's own
   rules to that local publication: sources jar, javadoc jar, a signature for every
   `.jar`/`.pom`/`.module`, and the six required POM elements.
6. **Tag** and push the tag.
7. **Publish** to GitHub Packages, then to Maven Central.
8. **Create the GitHub release** — last, so a release only appears once the artifacts behind it
   exist. Notes are the install snippet plus GitHub's auto-generated "What's Changed".

Steps 6 and 7 are in that order deliberately. Publishing is the irreversible half — a Central
artifact cannot be deleted — so a published artifact with no tag is a permanent inconsistency. A tag
with no artifact is not: delete it, fix the problem, run again.

Step 5 exists because 0.1.0 was lost without it. Central validates a deployment as a unit and
rejects it wholesale, but only *after* the tag has been pushed; two JVM-only modules were missing
sources, javadoc, signatures and POM metadata. GitHub Packages had accepted them without complaint,
which is the trap: **publishing successfully somewhere is not evidence that Central will take it.**
The check now runs while the release is still reversible.

Run it yourself any time:

```bash
./gradlew publishToMavenLocal          # add SIGNING_KEY to check signatures too
.github/scripts/verify-publishable.py  # --no-signatures for an unsigned build
```

## If it fails part way

| Failed at | State | What to do |
|---|---|---|
| Steps 1–5 | Nothing tagged, nothing published | Fix and re-run. |
| Step 6 (tag) | Nothing published | Re-run. |
| Step 7 (publish) | Tagged but not published | Delete the tag, fix, run again: `git push --delete origin v1.0.0`. Note GitHub Packages may already hold the version, and re-publishing a deleted Packages version can be refused — releasing the next patch is usually safer than reusing the number. |
| Step 8 (GitHub release) | Published, but no release entry | Nothing is wrong with the artifacts. Create it by hand: `gh release create v1.0.0 --generate-notes` |

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

## Where releases go

**GitHub Packages**, always. Needs nothing but the automatic `GITHUB_TOKEN`. Note the Packages
Maven registry requires authentication even for public repositories, so consumers cannot pull from
it without a token — useful for your own projects, not a public release.

**Maven Central**, once its secrets exist. The workflow skips it silently when they do not, so it
works before and after the setup without being edited.

### Setting up Maven Central

The `io.github.menjoo` namespace is verified. Two secrets remain:

| Secret | What it is |
|---|---|
| `CENTRAL_PORTAL_USERNAME` / `CENTRAL_PORTAL_PASSWORD` | A Central Portal **user token**, not your account password. central.sonatype.com → Account → Generate User Token. |
| `SIGNING_KEY` / `SIGNING_PASSWORD` | An ASCII-armoured GPG private key and its passphrase. Central rejects unsigned artifacts. |

Generate and export a signing key:

```bash
gpg --quick-generate-key "Your Name <you@example.com>" rsa4096 sign 2y
gpg --list-secret-keys --keyid-format=long          # note the key id
gpg --keyserver keyserver.ubuntu.com --send-keys <KEY_ID>   # Central verifies against a keyserver
gpg --armor --export-secret-keys <KEY_ID> | pbcopy          # paste into the SIGNING_KEY secret
```

The workflow fails early with a clear message if `CENTRAL_PORTAL_USERNAME` is set but `SIGNING_KEY`
is not, rather than letting the upload fail obscurely.

### Staged, not published

Uploads use `publishingType = USER_MANAGED`, so a release lands in the Portal as a **staged
deployment** and is not public until you review and release it at
<https://central.sonatype.com/publishing/deployments>. That is deliberate for the first few
releases, because a Central release is permanent and cannot be deleted or overwritten.

Once it is routine, switch to automatic by passing
`-Pcucumberkmp.centralPublishingType=AUTOMATIC`, or change the default in `settings.gradle.kts`.

### Why a third-party plugin

Sonatype publishes no official Gradle plugin for the Central Portal and points at community ones.
[nmcp](https://gradleup.com/nmcp/) is the smallest fit: it uploads the publications `maven-publish`
already produces rather than defining its own, so the POM, signing and Kotlin Multiplatform
variants configured in `build-logic` keep working untouched. The alternatives either replace the
whole publishing setup or rely on the OSSRH staging compatibility endpoint, which is a migration
aid and needs an extra API call from the same IP as the upload.
