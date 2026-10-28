# The protected main branch and the pull-request workflow

`main` is protected by the repository ruleset `Main`. Every change lands through an issue-scoped
branch and a pull request, and the protected branch accepts a merge only when the current head of
that pull request passed the aggregate `quality-gate` status.

## Rules enforced on `main`

| Rule | Value |
| --- | --- |
| Pull request before merging | Required; direct pushes are refused for every account, administrators included |
| Merge strategy | Squash only. Merge commits and rebase merges are disabled on the repository as well |
| Required status | Exactly one: `quality-gate`, the aggregate check of the workflow below |
| Required approvals | Zero, because one operator maintains the repository |
| Stale reviews | Dismissed when a new commit arrives |
| Up-to-date branch | Required, so the status describes the head that is merged and not an older result |
| Branch deletion and force push | Prohibited |
| Bypass actors | None, so no account or role can skip a rule |

A merged pull request deletes its branch. Auto-merge may be enabled on a ready pull request, and the
merge then happens only once the required status is green for the exact current head. GitHub refuses
to merge a draft pull request at all.

## Branch names

Use one issue-scoped branch per change, in the form `<type>/<issue>-<short-description>`, for example
`feat/25-protected-main-workflow`. The type is the Conventional Commit type of the change.

## What the workflow runs

`.github/workflows/quality-gate.yml` runs on a pull request, a push to `main`, a nightly schedule at
02:00 UTC, and a manual dispatch. A pull request triggers on `opened`, `synchronize`, `reopened`,
`ready_for_review`, and `labeled`: each listed activity except a non-`run-maestro` label starts a run
that re-evaluates the head as it is at that moment, so a result from an earlier state of a pull
request never stands for the current one, and adding the `run-maestro` label starts the labeled
emulator suites on an open pull request. A title edit and a draft transition change no code, so they
start no run; adding any other label starts only a no-op run whose validation jobs report `skipped`
while its aggregate re-reports the decision of the newest completed `quality-gate` check run on that
same head, so the last completed gate stays authoritative and a label can neither clear a failed
gate nor block a green one.

With `cancel-in-progress: true`, pull-request runs group per pull-request number and push runs group
per ref: a newer run supersedes the obsolete run of the same pull request or ref. Scheduled runs
keep sharing the per-ref group. A hand-started run instead groups on its own run id, so a push cannot
cancel a validation the Nightly started, and separate hand starts cannot cancel one another; a
`labeled` run without the `run-maestro` label groups on its own run id too, so its no-op result never
cancels an in-flight gate.
Results are published per check, and only the aggregate job named
`quality-gate` is a required status. On a run that validates the head, the aggregate fails unless
every check declared in `.github/quality-gate/required-checks.txt` reports the outcome the event
expects; on a non-maestro label run it instead fails unless the newest completed `quality-gate`
check run on the same head carries a pass conclusion, so a no-op result is never more permissive
than the gate it mirrors.

| Run | Checks that execute | Checks that stay skipped |
| --- | --- | --- |
| Ready in-repository pull request | The complete gate: `formatting`, `static-analysis`, `detekt`, `unit-tests`, `fixture-descriptor`, `fixture-lifecycle`, `fixture-contract`, `android-build`, `architecture-check`, `coverage-mutation`, `compose-jvm-tests`, `conformance`, `commit-message`, `draft-validation`, `fork-guard`, `ui-paths` | `api24-instrumentation` unless the pull request carries the `run-maestro` label or modifies a declared UI path, and `maestro-journeys` unless it carries the label (next row); `buildsrc-contracts` unless the head changes `.github/**` or `buildSrc/**` files |
| Ready in-repository pull request labeled `run-maestro`, or modifying a declared UI path | The complete gate plus `api24-instrumentation`; `maestro-journeys` only when the label is present, and only after `api24-instrumentation` passes; `buildsrc-contracts` when the head changes `.github/**` or `buildSrc/**` files | `buildsrc-contracts` otherwise, and `maestro-journeys` on a head that modifies a declared UI path without the label |
| Ready in-repository pull request whose every changed file is documentation | `draft-validation`, `conformance`, `commit-message`, `fork-guard`, `ui-paths` | Every heavy check, both emulator suites, and `buildsrc-contracts` (a documentation head changes no CI or build-logic file), unless the pull request carries the `run-maestro` label, which starts the heavy checks and the emulator suites but not `buildsrc-contracts` |
| Ready in-repository pull request whose every changed file is a build or dependency file (`gradle/**`, any `*.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`) | `formatting`, `static-analysis`, `detekt`, `unit-tests`, `fixture-descriptor`, `fixture-lifecycle`, `fixture-contract`, `android-build`, `architecture-check`, `compose-jvm-tests`, `conformance`, `commit-message`, `draft-validation`, `fork-guard`, `ui-paths` | `coverage-mutation`, `buildsrc-contracts`, and both emulator suites, unless the pull request carries the `run-maestro` label, which starts the emulator suites and `coverage-mutation` but never `buildsrc-contracts` on a dependency-only head |
| Draft in-repository pull request | `draft-validation` (`formatCheck` and `:app:lintDebug` only), `fork-guard`, `ui-paths` | Every other check |
| External-fork pull request | `fork-guard`, `ui-paths` | Every other check |
| Push to `main` | JVM, static detekt, fixture, architecture, coverage/mutation and conformance validation; `buildsrc-contracts` runs the buildSrc self-test contracts; `android-build` compiles debug/release Kotlin without packaging the application | `api24-instrumentation`, `maestro-journeys`, `draft-validation`, `commit-message`, `fork-guard` |
| Nightly validation, manual validation | The complete gate plus `api24-instrumentation` and, only after it passes, `maestro-journeys` | `draft-validation`, `commit-message`, `fork-guard` |

The emulator suites are opt-in on pull requests, and the journey suite is opt-in by label alone. A
ready in-repository pull request runs `api24-instrumentation` when the `run-maestro` label is present
or when the `ui-paths` detector reports that the head modifies a declared UI path
(`app/src/main/**`, `feature/entry/presentation/**`, `feature/entry/wiring/src/main/res/**`,
`fixtures/mercurio/journey/**`). `maestro-journeys` starts only when the label is present, and only
after `api24-instrumentation` passes, so a build or device-suite failure never reaches the journey
lane. A pull request without the label reports `maestro-journeys` as `skipped` even when it modifies a
declared UI path, and the aggregate requires exactly that routing; the label on a draft or an
external-fork pull request starts no validation, and its aggregate still reports the decision that
exact head holds. [The journey suite](deterministic-emulator-journeys.md#requesting-the-journey-lane)
lists the key areas that need the label.

A documentation-only pull request takes a fast path from the opened event. When the `ui-paths` job's
changed-file answer shows that every changed file of the head is a Markdown file outside `.github/`
or a file under `docs/**`, the heavy lanes report `skipped` and the aggregate requires exactly that
routing; the `run-maestro` label keeps precedence and starts the heavy and emulator lanes on a
documentation-only head too. The classification is deliberately conservative — code, resources,
build files, and especially `.github/**` route the full gate — and a push, a nightly schedule, and a
manual dispatch never consult the decision, so the Nightly signing chain always validates the exact
`main` tip with the complete gate. An unreadable changed-file answer fails closed: the detector job
fails, the heavy lanes run, and the aggregate reports the failure.

On the nightly schedule, when `unit-tests`, `compose-jvm-tests`, `api24-instrumentation`, or
`maestro-journeys` does not succeed, the `create-nightly-failure-issue` job creates or updates one
issue from run metadata only: it never downloads or copies test logs into public issue content, and
it reads the exact issue back after the write. The lane is deliberately not a check. It is the only
job that requests a write scope, and it runs on the schedule alone, so no write token ever reaches
pull-request code.

Failure evidence uploaded by the test and device lanes is redacted by
`.github/scripts/redact-test-reports.py` before upload; the image artifacts uploaded alongside it,
such as the Compose lane's Roborazzi PNG comparison files and the journey screenshots, do not pass
through that script. The Android instrumentation lane publishes its failure artifact only after
its sanitization step completes, and uploaded evidence is retained for 14 days.

A merge does not package or publish an application APK or generate a product version.
Signing-policy tests may create minimal disposable APK fixtures; those are not application
builds or distribution artifacts. [The separate Nightly workflow](nightly-releases.md)
captures the current `main` tip, skips an already published source, and dispatches full
validation on `main`. Only that exact successful validation permits signing and publication.

`draft-validation` runs for every in-repository pull request: on a draft it is the whole validation,
and on a ready pull request it adds a fast formatting and lint result beside the complete gate. The
aggregate also requires that a pull request targets `main`, so a pull request against another branch
fails even when its checks pass. For an external-fork pull request the aggregate fails closed: the
head never ran the complete gate, so it must never hold a passing required status.

## Merging

A pull request may be merged or auto-merged only when all of the following hold:

- it is ready for review, not a draft;
- it targets `main`;
- its exact current head passed the required `quality-gate` status, and the branch is up to date
  with `main`;
- it comes from the repository itself, because an external-fork head never holds a passing required
  status;
- it is merged with a squash merge, which is the only enabled strategy.

The squash merge writes the pull-request title onto `main`, so the title and every commit the pull
request carries must be a Conventional Commit. `.github/scripts/verify-conventional-commits.py`
checks both and accepts the declared types `build`, `chore`, `ci`, `docs`, `feat`, `fix`, `perf`,
`refactor`, `revert`, and `test`, with an optional lower-case scope, an optional `!`, a non-empty
description, no trailing period, and a subject within 100 characters. Merge, revert, `fixup!`, and
`squash!` subjects are exempt.

## External forks

An external-fork pull request is untrusted input, so it never runs the complete gate:

- its run is read-only and secret-free, and the workflow declares no secret for any event;
- the repository requires maintainer approval before a run from an external contributor starts, so no
  external change consumes runner time on its own;
- only `fork-guard` executes, and it reports which handling applies;
- the aggregate fails closed, so an external head never holds a passing required status and neither
  an automatic nor a manual merge of the fork head is possible;
- external-fork artifacts are not release or compatibility inputs;
- a maintainer validates the change by carrying it onto an in-repository branch, where the complete
  gate runs against a commit-verified head, and merges that pull request explicitly. That explicit
  merge is the only merge an external change can lead to.

## Verifying the configuration

The rules above are configuration rather than code, so `.github/scripts/verify-repository-conformance.py`
reads the enforced rules, the active rulesets and their bypass actors, and the workflow declaration
back from the GitHub API, and fails when a rule is missing, weakened, or replaced by a bypass. The
`conformance` check runs it on every push, ready pull request, nightly run, and manual run.

The same check also runs `.github/scripts/release-signing.py environment` and
`nightly-environment` with read-only `actions` access. It verifies the `release`
environment's required human reviewer and the absence of approval/wait gates in
`nightly`. Both require disabled administrator bypass, a main-only branch policy,
and protected `main`. Missing, changed, or unreadable settings fail the check; no signing
secret is exposed to validation.
See [Protected Android release signing](release-signing.md) for the separate signing workflow.

The repository Actions settings need the Administration permission, which a workflow token cannot
hold, so the owner verifies them explicitly:

```text
GITHUB_REPOSITORY=Gvetri/hermes-native-client GITHUB_TOKEN="$(gh auth token)" \
  python3 .github/scripts/verify-repository-conformance.py --owner
```

Without `--owner` the check verifies everything a read-only credential can read: the enforced rules
and the rulesets' bypass actors, the required status, the required-check declaration, the declared
read-only token scope, and the absence of secrets.

## The contracts are themselves tested

`buildSrc/src/test/kotlin/org/mercurio/client/buildlogic/QualityGateConfigurationTest.kt` pins
the workflow contracts, and `.github/scripts/tests/test_protected_main.py` and
`.github/scripts/tests/test_test_jobs.py` pin the routing, the aggregate gate, the commit-message
check, and the conformance check. The workflow-contract suite runs in the `buildsrc-contracts` job,
so a change to the workflow that contradicts a documented rule fails the gate. See
[Quality gates](quality-gates.md) for the declared checks and thresholds.
