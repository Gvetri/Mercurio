# Local quality gate for one module

Run what the `quality-gate` CI jobs run for a single module, in the order the checks must judge the
sources, with one command:

```text
.github/scripts/local-quality-gate.sh feature/entry/domain
```

The script accepts the module path with or without the leading colon. For the given module it runs
four Gradle invocations:

1. `ktlintFormat` — the formatter rewrites what it can fix, and the formatter runs before the ktlint checks.
2. the module `test` task — this compiles the main and test sources and runs the unit tests.
3. `ktlintMainSourceSetCheck` and `ktlintTestSourceSetCheck` — the module's main and test source-set checks behind the `formatting` required status.
4. `detektMain` and `detektTest` for the module, then the fail-closed `detektVerify`, which pulls the root `detekt` aggregate — the check behind the `detekt` required status. Detekt analyzes main and test sources with a line limit of 120, import statements exempt. `detektVerify` fails closed unless the analysis executed in the same invocation and no baseline file exists, so a skipped or narrowed analysis cannot report a pass.

## Ordering rules

- Format before check. The checks must judge the formatted state; run in the other order, they
  report failures the formatter would have fixed.
- Detekt after any reformat. Mechanical wrapping can re-trip detekt rules such as `LongMethod`, so
  detekt must observe the sources exactly as they are committed.

The root `detekt` task is the aggregate of the per-module type-resolution tasks, and `detektVerify`
is the verification that makes the analysis fail closed. Run both, as the script does and as the
`detekt` CI job does.

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
