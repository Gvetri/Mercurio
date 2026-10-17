<!--
Title this pull request as a Conventional Commit (`<type>[(scope)][!]: <description>`); the squash merge
uses the title verbatim as the subject on `main`.
-->

## Summary

<!-- What changes and why? Reference the issue this pull request delivers ("Closes #123"). -->

## Testing

<!-- How was this verified? Name the suites and gates that prove the change.
     Run the full gate (`./gradlew qualityGate`) before marking the pull request ready.
     UI change? Compare first (`:feature:entry:presentation:compareRoborazziDebug`), review the
     report, then re-record the affected baselines; update
     `feature/entry/presentation/src/test/roborazzi-baselines.txt` only when the state matrix changes.
     Covered code changed? Regenerate the coverage/mutation evidence in the same invocation
     (procedure in `docs/agents/local-quality-gate.md`). -->

## Risk

<!-- low, medium, or high — and why. -->

## How to revert

<!-- What undoes this change safely: a straight revert of the squash commit, or a follow-up pull request? -->
