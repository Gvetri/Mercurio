# Local quality gate for one module

Run the module-scoped checks and the repository-wide checks a module change can break, in the order
the checks must judge the sources, with one command:

```text
.github/scripts/local-quality-gate.sh feature/entry/domain
```

The script accepts the module path with or without the leading colon. For the given module it runs
six Gradle invocations:

1. `ktlintFormat` — the formatter rewrites what it can fix, and the formatter runs before the ktlint checks.
2. the module `test` task — this compiles the main and test sources and runs the unit tests.
3. `ktlintMainSourceSetCheck` and `ktlintTestSourceSetCheck` — the module's main and test source-set checks behind the `formatting` required status.
4. `commentPolicyVerify` — the repository-wide comment check of the same `formatting` status. KDoc is the only allowed comment form in a hand-maintained file, and the policy is not module-scoped, so the module's ktlint tasks cannot see it.
5. `detektMain` and `detektTest` for the module, then the fail-closed `detektVerify`, which pulls the root `detekt` aggregate — the check behind the `detekt` required status. Detekt analyzes main and test sources with a line limit of 120, import statements exempt. `detektVerify` fails closed unless the analysis executed in the same invocation and no baseline file exists, so a skipped or narrowed analysis cannot report a pass.
6. `architectureRuleTests verifyNoMocks verifyDeterministicFakes` — the `architecture-check` lane, the command CI runs. The architecture rules read the production sources of the repository, so this invocation judges more than the module you passed; `verifyNoMocks` rejects mock-based test code and `verifyDeterministicFakes` rejects a boundary double that reads randomness or the wall clock. `architectureRuleTests` fails closed unless the focused rule suite executed a passing test in that invocation.

Two modules face a lane no other module runs, so the script adds it only for them:

- `feature/entry/presentation` also runs `verifyRoborazziBaselineManifest verifyRoborazziDebug` — the `compose-jvm-tests` lane — after its tests.
- `app` also runs `lintDebug assembleDebug assembleRelease` — the `static-analysis` and `android-build` lanes.

Every other module faces no lane of its own, so the six invocations above are its whole bar.

## Ordering rules

- Format before check. The checks must judge the formatted state; run in the other order, they
  report failures the formatter would have fixed.
- Detekt after any reformat. Mechanical wrapping can re-trip detekt rules such as `LongMethod`, so
  detekt must observe the sources exactly as they are committed.
- The architecture-check lane after both. The rules read the same sources, so they must judge the
  formatted, committed state as well.

The root `detekt` task is the aggregate of the per-module type-resolution tasks, and `detektVerify`
is the verification that makes the analysis fail closed. Run both, as the script does and as the
`detekt` CI job does.

## One test class

While iterating, run one class instead of the module aggregate:

```text
./gradlew :feature:entry:presentation:testDebugUnitTest --tests '*RunObservationRecoveryTest*'
```

In an Android module, `testDebugUnitTest` is the variant task that accepts `--tests`; the module
`test` task is a lifecycle aggregate that rejects `--tests` with `Unknown command-line option`. In a
Kotlin JVM module, `test` is the test task itself and accepts the filter directly:

```text
./gradlew :feature:entry:domain:test --tests '*HistoryCacheNoOpPortTest*'
```

The variant runs the debug unit tests only, the aggregate runs every variant, and the `unit-tests`
CI job runs the repository-wide `verifyRequiredUnitTests` aggregate.

## What a gate run costs

Wall-clock time from run creation to the last job, on the 47 successful pull-request and push runs
created between 2026-11-05 and 2026-11-10:

| Head | Runs | Median | Observed |
| --- | --- | --- | --- |
| An ordinary pull request, full gate | 6 | 7.8 minutes | 5.4 to 10.4 minutes |
| A control-plane head | 8 | 19.0 minutes | 15.5 to 45.3 minutes |
| A push to the protected branch | 13 | 12.8 minutes | 7.7 to 25.4 minutes |

The classification reads the changed files and the lanes that ran: a control-plane head changes a
`.github/**` or `buildSrc/**` file, which starts the self-test lane and makes it the critical path.
From the same window, a documentation-only head takes the fast path and reports in 3.0 to 3.8
minutes, a draft head runs only `draft-validation` and reports in about 3 minutes, and a head that
carries `run-maestro` runs the journey shards and reports in 20.2 to 38.9 minutes.

The loop does not run the `coverage-mutation` lane, which no pull request runs, or the device
journeys (`api24-instrumentation` and the `run-maestro` shards), which need an emulator.
`./gradlew qualityGate` is the broad local aggregate; it does not run the presentation Roborazzi
verification or a device journey either. The loop covers the Roborazzi verification in the
presentation module's own run, and it does not run the journeys, which need an emulator;
[Running locally](../deterministic-emulator-journeys.md#running-locally) documents that procedure.

## The staleness convergence recipe

`coverageVerify` and `mutationVerify` fail closed when the kover XML report or the PIT report is
older than the compiled classes it measures. After changing production code in a covered module,
regenerate both reports against one class set, then run the verifications:

```text
./gradlew koverXmlReport pitest --rerun-tasks
./gradlew coverageVerify mutationVerify
```

The pull-request gate does not run this lane, so this local evidence is the only pre-merge check for
a covered production change: run both commands and keep them green before you open or update the pull
request. The `main` push, the nightly schedule, and manual dispatch still verify it centrally.

The first invocation produces both reports against one class set; the second verifies them. See
[Quality gates](../quality-gates.md) for the declared scopes and thresholds and for
[how to change a declared value](../quality-gates.md#changing-a-declared-value).
