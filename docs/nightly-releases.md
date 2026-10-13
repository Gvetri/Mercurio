# Development Nightly releases

A **Nightly** is a development snapshot for testing. It can contain regressions and
is **not a stable-support promise**. It is not a Stable Public Beta. This workflow
does not publish Stable Public Beta releases or make a compatibility/support claim.
See [milestone-based Stable Public Beta](stable-public-beta.md) for the separate,
human-approved promotion of an existing signed Nightly.

## Daily path and source selection

`.github/workflows/nightly-release.yml` is scheduled daily at **04:37 UTC**. GitHub
may delay a scheduled run. Each Nightly captures the current tip of `main`, checks
release history, and starts `quality-gate.yml` on `main` through `workflow_dispatch`.
The independent 02:00 UTC quality-gate schedule and its failure reporting remain
unchanged. A merge to `main` validates and compiles debug/release Kotlin, but does
not package an application APK, create a release, or generate a product version.
Pull-request APK builds and scheduled/manual device tests keep their existing roles.

Before dispatch, a published Nightly for the captured SHA makes the run succeed
without validation, building, signing, or publishing another one. A visible draft
still stops preparation for recovery; duplicate or inconsistent source identities
fail closed. The history check uses the existing release listing, not workflow history.

For an unreleased tip, preparation dispatches the existing full gate and waits for
its result. It correlates new workflow-dispatch runs by `main`, the captured SHA,
and creation time, excludes runs present before dispatch, and refuses ambiguous
matches. Discovery is limited to five minutes; the entire validation wait is
limited to **three hours** (`10800` seconds), within a **190-minute** preparation
job. This leaves room for the full gate, including its sequential device stages,
without an unbounded wait. Dispatch errors, missing runs, cancellation, failed
validation, unknown states, unavailable evidence, and expired waits publish nothing.
The wait never retries a dispatch or falls back to an older successful source.

The exact dispatched run and attempt become release evidence only after the
unchanged signer verifies workflow identity, repository, main membership, source
SHA, and every declared deterministic job, including API 24 instrumentation and
Maestro journeys. Push-only, pull-request, fork, incomplete, or changed evidence
cannot authorize publication. Signing and publication stay bound to the captured
SHA and attempt. A newer commit merged during validation waits for the next Nightly:
the dispatched validation has its own run-id concurrency group, so a push cannot
cancel it. On success this Nightly releases the captured tip, not the newer commit.
Other cancellation still fails closed instead of substituting another run.
If `main` moves before dispatch and
the run starts on another SHA, it cannot satisfy discovery for the captured tip.

The old first-parent ancestry walk is removed: a broken current tip must not be
hidden by releasing historical validation. The Nightly validates its own source,
then signs and publishes only that source on success.

## Validation failure issues

When the validation started by the Nightly completes unsuccessfully, preparation
stays red and a separate job invokes the existing
`.github/scripts/create-nightly-failure-issue.sh` reporter. Nothing is signed or
published and no release state changes. The reporting job has `actions: read`,
`contents: read` for checkout, and `issues: write`, with no signing secrets or
release-write permission.

The issue is labelled `ready-for-agent` and identifies the **validation** run,
attempt and commit, not the parent Nightly run. Its four stage results come from
that exact attempt's jobs: `unit-tests`, `compose-jvm-tests`,
`api24-instrumentation`, and `maestro-journeys`. Other failed required jobs, such as
`formatting`, appear in a separate section, from the same exact run and attempt.
The expected skips of `fork-guard`, `draft-validation`, and `commit-message` are
not failures on this main validation. The `nightly-run` marker deduplicates
by validation run ID; reporting another attempt updates and reopens the same issue.
Only available, unexpired diagnostic artifacts for that attempt are linked. With
none, the issue says: “Not uploaded. Inspect the failed job logs in the workflow
run.” Missing evidence is never invented.

A hand-started quality gate that the Nightly did not start does **not** enter this
reporting path. The quality gate's independent nightly schedule keeps its existing
schedule-only failure-issue automation unchanged. The Nightly does not retry or
repair a failed validation. A green validation cannot produce an issue. The reporter
refuses when all four test stages succeeded and no additional required stage failed;
a non-test failure alone can now produce an issue without changing any test result.
The additional failed-stage input is supplied only by the Nightly caller. Stage conclusions of
`failure`, `timed_out`, `startup_failure`, `action_required`, `neutral`, and `stale`
are normalized to `failure`; `success`, `skipped`, and `cancelled` retain their values.
Missing, duplicate, nonterminal, wrong-source, or malformed stage results, and any
conclusion outside GitHub's closed conclusion enum, fail reporting closed rather
than inventing results. Discovery failures or expired waits without a completed identified run
leave the Nightly red without fabricating a validation issue.

## Versions and ARM64 APK

The workflow generates these values without editing a product version:

- Android `versionCode`: `GITHUB_RUN_NUMBER * 1000 + GITHUB_RUN_ATTEMPT`.
- Android `versionName`: `nightly-<versionCode>-<first-12-source-SHA-characters>`.
- Release tag: `nightly-<versionCode>-<full-source-SHA>`.
- GitHub release title: **Nightly**, always a prerelease and never marked latest.

For example, run 7 attempt 2 receives code `7002`; a full rerun gets `7003`, and
run 8 attempt 1 gets `8001`. Codes must exceed all recorded Nightly codes and stay
within Android's `2100000000` limit. Attempts must be below 1000. An old run cannot
publish over a newer release: start a new workflow run instead. Partial reruns
that retain a preparation output from an earlier attempt fail; use **Re-run all
jobs** so the generated version and signed artifact belong to the same attempt.
Do not reset the workflow run counter, delete published release history, or reuse
a version code as a recovery mechanism.

The isolated build receives the generated code and exact source SHA as Gradle
properties. It builds the normal release application with only `arm64-v8a` native
libraries; `x86_64` remains available to non-Nightly emulator validation. A build
with no native libraries is also device-compatible. The signer reads the actual APK
application ID, embedded versions, and ABIs instead of trusting caller metadata.
The Nightly now validates the current tip itself rather than bootstrapping from an
older successful source. The build-metadata check remains mandatory: a source that
does not honor the generated properties cannot produce a Nightly.

## Signing and publication boundaries

The daily workflow calls [the protected signer](release-signing.md), without
`secrets: inherit`. It explicitly maps only the two declared signing-secret names;
their values remain in the called job's environment. Its preparation and unsigned
build have no signing secrets.
The trusted Nightly caller uses the main-only `nightly` environment with
no human reviewer or approval wait. Signing and publication are unattended. A
daily schedule is not a promise of publication when validation or signing
material is unavailable. Standalone signing and Stable Public Beta promotion
retain their separate human approval in `release`.

The signing job has only the two environment-scoped Android signing secrets. It
has no provider credentials and performs no live-provider smoke test. It verifies
the pinned certificate and uploads an immutable, same-run signed artifact.

Preparation alone has `actions: write` to dispatch validation, while retaining
`contents: read` and no signing secrets. The signing stage's permissions are unchanged.
Only the separate publication job has `contents: write`; it has no signing
environment or signing secrets. Before writing it rechecks validation, trusted
Nightly caller and environment, run/attempt, version, source, certificate, APK checksum, and the exact
three-file artifact allowlist. Workflow concurrency serializes daily/manual runs,
and the publisher rechecks duplicate history.
It refuses an existing tag or source, including a draft visible to its write token.
The contents-read-only preparation token may not see drafts; the publication check remains
authoritative and refuses a second write.

Publication proceeds in this order:

1. Create a **draft prerelease** bound to the exact source SHA, with generated notes.
2. Read the draft back by the exact release ID returned by creation. Do not
   rediscover it through a release listing, which can omit the new draft;
   GitHub's by-tag endpoint also excludes drafts.
3. Upload `hermes-native-client.apk`, `SHA256SUMS`, and `signing-metadata.json`.
4. Read the asset inventory, download the uploaded files, compare every file's
   SHA-256 with the local verified artifact, and verify the APK again.
5. Publish the verified draft as the **Nightly** prerelease. Read back its release
   ID, flags, title, notes, and exact source tag before reporting success.

An upload or download-verification failure leaves a draft, not a public partial
release. A retry refuses the retained draft rather than overwriting assets or
creating another release for the source. The operator must inspect the failed run,
exact release ID/tag/source and uploaded assets before removing an incomplete,
unpublished draft (and an orphaned tag, if present), then start a new full run with
a new version. Never delete an already published release to force a retry. If the
final publication succeeded but its read-back failed, inspect and verify that
existing release instead of publishing again. No automatic cleanup hides failures.

A failed signing or publication stage files or updates one `ready-for-agent` issue
per Nightly run. The reporter reads the exact run attempt and failed jobs, lists
only available same-attempt artifacts, and verifies the issue after writing it.
It has `actions: read`, `issues: write`, and `contents: read` for checkout, never
release-write access or signing secrets. The publication job uses its existing
write token to read back retained state after refusal and uploads
`nightly-publication-state-<run>-<attempt>`. The reporter consumes that artifact,
not draft-release APIs. Missing artifacts, failed read-backs, and unavailable
identities are explicitly unknown, never proof that a release or tag is absent.
A failure inside the reusable signer can precede its outputs, leaving no recovered
release identity. A complete-looking draft still requires inspection; an asset
inventory alone does not prove that its bytes are valid. Reported state is a
snapshot, not permission to skip fresh recovery checks.

An agent may remove **one incomplete, unpublished draft and its orphan tag** only
when the failure issue names their exact release id, tag, and source. The agent
must verify that identity and the retained state again before and after removal.
It may not touch a published release or an asset, overwrite or re-publish release
state, or recover a partially published release. Unknown state requires inspection,
not guessed cleanup. After deliberate recovery, start a **new full Nightly run**
with a new version; never retry only signing or publication. This is an explicit
agent action, never a workflow step or automatic repair.

The supported command is [clear-nightly-draft.py](../.github/scripts/clear-nightly-draft.py),
run locally from the repository root after inspecting the failure issue. Supply its
exact values, not a tag discovered by guessing or by selecting the latest release:

```sh
python3 .github/scripts/clear-nightly-draft.py \
  --release-id "$RELEASE_ID" --tag "$RELEASE_TAG" --source-sha "$SOURCE_SHA"
```

Use a `GITHUB_TOKEN` authorized to read drafts and delete releases/tags in this
repository. It uses the same `GITHUB_API_URL` handling as the release scripts.
It refuses a GitHub Actions environment. Wait for the failed publisher to finish
and ensure no person or other agent is editing this release: GitHub does not offer
an atomic transaction spanning release and tag deletion. Fresh checks narrow that
race but cannot lock out a concurrent external writer.

The id, full lowercase source SHA, Nightly tag, API release identity, and any
existing lightweight tag's commit must agree. Recovery refuses published or
non-draft releases, a missing/invisible draft, non-Nightly state, unknown evidence,
ambiguous tag ownership, and changed state. A draft is provably incomplete only
when its full asset inventory is a **strict subset** of the publisher's declared
`hermes-native-client.apk`, `SHA256SUMS`, and `signing-metadata.json` allowlist.
An asset outside that set is interpreted as **foreign state**, not something the
publisher created, and requires human inspection. Every retained asset must identify
`github-actions[bot]` (id `41898282`) as its uploader; a different, missing, or
unreadable uploader is refused before deletion. The draft must carry the publisher's
structured notes matching the supplied source and tag-derived version code and name,
with a valid APK SHA-256, the pinned certificate SHA-256, and validation identity.
Missing, malformed, or contradictory notes are refused. A retained APK must have
a recorded `sha256:` digest matching the notes; a missing or mismatched digest is refused.

Duplicate names, inconsistent inventories, annotated tags, and complete-looking
drafts also require inspection. These checks cannot distinguish uploads under the
same bot identity: a byte-identical re-upload under the publisher's own identity
is not detectable. A partially published release remains an inspection task;
the command does not repair it.

The command prints JSON `before` and `after` API observations to stdout, including
retained assets and tag state. It never calls an individual asset deletion endpoint,
uploads, overwrites, retries, or publishes anything. Removing the allowed draft
also removes its attached incomplete uploads as part of GitHub's release deletion;
it does not separately edit or delete an asset. It deletes only the exact tag, if
present, after reading back the draft's absence and proving the tag is orphaned and
unchanged. A missing draft never authorizes standalone orphan-tag cleanup.

Exit `0` means both targets read back absent. Exit `1` reports a refusal or failed
operation on stderr; malformed CLI usage exits `2`. Invalid arguments or workflow
execution are rejected before any API access. For valid identities, refusals also
read state before and after without mutation; an unavailable read is printed as
unknown, never absence. If deletion fails midway, the command prints retained state
and stops without retry or rollback. Inspect it rather than guessing a second cleanup.

The workflow uses only `GITHUB_TOKEN`; permission or protected-tag failures stop it,
not a fallback to an administrator credential.

## Read-only Nightly watchdog

The separate [Nightly watchdog workflow](../.github/workflows/nightly-watchdog.yml)
runs daily at **12:17 UTC** and on a hand start on protected `main`. It is not a
required check and does not change the quality gate or Nightly schedule. GitHub
may delay either schedule; the watchdog evaluates the evidence it actually reads,
not an assumed ordering of scheduled runs.

It walks `main` first-parent ancestry, newest first, accepting only a completed,
successful `quality-gate.yml` run from a schedule or hand start in this repository.
It reuses the signer's full source/attempt/job verification, including both device
stages. This is an observation-only ancestry walk; the publisher still validates
and releases its captured current tip, never an older fallback.

Every successful observation prints JSON with the newest fully validated source,
validation run and attempt, captured `main` SHA, number of first-parent commits
between them, the published Nightly tag for that source or `null`, and the newest
Nightly publication timestamp or `null` if none exists. Drafts are not publications;
the read-only token may not see them. Missing or contradictory evidence fails the
watchdog without turning a test failure into a starvation issue.

Only when that validated source has **no published Nightly**, the watchdog files or
updates one `ready-for-agent` issue containing those same facts. A stable
`<!-- nightly-watchdog -->` body marker identifies it across observations and
sources; a closed alarm is reopened if starvation is seen again. Runs are serialized
and the issue is read back after every write. Duplicate markers fail closed rather
than producing another issue. When the source has a Nightly, the watchdog stays
silent as an alarm: it prints the observation but neither creates nor updates an
issue, even if newer unvalidated commits exist. It does not automatically close
an earlier issue.

The token has only `contents: read`, `actions: read`, and `issues: write`.
The watchdog publishes nothing, repairs nothing, retries nothing, dispatches no
validation, and cannot write repository contents or releases.

## Download verification

The release notes include the source SHA, embedded version code and name, APK
SHA-256, pinned signer certificate SHA-256, and validation run/attempt. The metadata
file also binds the signing run/attempt and actual native ABI list. These are public
identifiers; signing material and credentials are never release assets.

Download all three release files and use Android Build Tools 35.0.0:

```text
sha256sum --check SHA256SUMS
apksigner verify --print-certs hermes-native-client.apk
aapt2 dump badging hermes-native-client.apk
```

Compare the certificate digest with `.github/release-certificate.sha256` at the
protected workflow revision. Compare the APK's version with the release notes,
metadata, and full source SHA in the tag. A locally built unsigned APK is not a
Nightly distribution artifact.

## Deterministic verification

The API and publication fixtures are local test stand-ins. The tests use real
Android packaging/signing tools with disposable keys, never the production key.
They cover main-push routing, current-tip dispatch and exact-run correlation,
bounded discovery and completion, cancelled/failed/unknown validation, refusal of
historical fallback and changed evidence, duplicate prevention before dispatch,
increasing versions, stale partial reruns, metadata and certificate checks, missing
evidence, partial uploads, downloaded-byte corruption, and published-release read-back.
The [recorded failure issue payload](../.github/scripts/tests/fixtures/nightly-publication-failure-issue.json)
is synthetic evidence from the refusal/report test, not a live issue or run.
Failure-report tests also cover early signer failure, missing artifacts, unreadable
state, stale evidence, issue read-back, idempotent updates, and unchanged remote
release state after refusal. They do not claim a real GitHub release was signed or
published. The same synthetic API seam and a deterministic `gh` issue store execute
the existing shell reporter, proving validation identity, four stage results,
additional failed required jobs, artifact presence/absence, one issue with update-on-rerun,
and refusal of green or malformed evidence. These tests remain in `test_nightly_release.py`, already invoked
by `QualityGateConfigurationTest`.

Live evidence for validation-failure reporting remains unmet: after this change is
on protected `main`, record the first real failing Nightly's validation run, attempt,
commit and issue URL; no live failing night's issue was produced during local testing.

```text
python3 .github/scripts/tests/test_nightly_release.py
python3 .github/scripts/tests/test_clear_nightly_draft.py -v
python3 .github/scripts/tests/test_nightly_watchdog.py
python3 -m unittest discover -s .github/scripts/tests -p 'test_*sign*.py'
./gradlew -p buildSrc test --tests org.hermesnative.client.buildlogic.QualityGateConfigurationTest
```

Use JDK 21, Android SDK Platform 35, Build Tools 35.0.0, `JAVA_HOME`, and
`ANDROID_HOME`. The existing required `architecture-check` job runs these policy
and workflow tests. Production signing and publication still need successful
exact-source validation and the verified main-only Nightly signing boundary,
but no operator deployment approval.
