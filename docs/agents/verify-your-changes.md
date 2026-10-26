# Verify your changes

Fast loop while working: `.github/scripts/local-quality-gate.sh feature/entry/<module>` —
see `docs/agents/local-quality-gate.md`. Full gate before marking a PR ready:
`./gradlew qualityGate`. After changing covered production code, regenerate evidence and
verify in two invocations:
`./gradlew koverXmlReport pitest --rerun-tasks` then `./gradlew coverageVerify mutationVerify`.

## Tests

Unit tests sit next to each feature module's sources (`feature/entry/*/src/test`); Gateway
contract/lifecycle/journey tests live in `fixtures/mercurio:runner`. Compose UI changes require
re-recording Roborazzi baselines and updating
`feature/entry/presentation/src/test/roborazzi-baselines.txt` (procedure in CONTRIBUTING.md);
the `compose-jvm-tests` check fails on manifest or pixel drift.
