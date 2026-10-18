## Coding guidelines

Before writing or changing any code, follow `docs/coding-guidelines.md`; it records the repository's
coding rules and what enforces each one. The two that tooling will not catch for you:

- KDoc is the only allowed comment form in `.kt`/`.kts` files; there is no mechanical check yet.
- No mocking framework anywhere (enforced by `verifyNoMocks`; deterministic fakes only).

Keep the public scope of `CONTRIBUTING.md`: no credentials, no private-organization detail, no
WebView reuse, no local Hermes runtime.

## Verify your changes

Fast loop while working: `.github/scripts/local-quality-gate.sh feature/entry/<module>` —
see `docs/agents/local-quality-gate.md`. Full gate before marking a PR ready:
`./gradlew qualityGate`. After changing covered production code, regenerate evidence and
verify in two invocations:
`./gradlew koverXmlReport pitest --rerun-tasks` then `./gradlew coverageVerify mutationVerify`.

## Tests

Unit tests sit next to each feature module's sources (`feature/entry/*/src/test`); Gateway
contract/lifecycle/journey tests live in `fixtures/hermes:runner`. Compose UI changes require
re-recording Roborazzi baselines and updating
`feature/entry/presentation/src/test/roborazzi-baselines.txt` (procedure in CONTRIBUTING.md);
the `compose-jvm-tests` check fails on manifest or pixel drift.

## Agent skills

### Issue tracker

Issues live in this repo's GitHub Issues; use `gh`. See `docs/agents/issue-tracker.md`.

### Triage labels

Use the default triage labels listed in `docs/agents/triage-labels.md`.

### Domain docs

Use the single-context layout. See `docs/agents/domain.md`.
