# Local quality gate for one module

Run what the `quality-gate` CI jobs run for a single module, in the order CI runs it, with one
command:

```text
.github/scripts/local-quality-gate.sh feature/entry/domain
```

The script accepts the module path with or without the leading colon. For the given module it runs
four Gradle invocations:

1. `ktlintFormat` — the formatter rewrites what it can fix, and the formatter runs before the ktlint checks.
2. the module `test` task — this compiles the main and test sources and runs the unit tests.
3. `ktlintMainSourceSetCheck` and `ktlintTestSourceSetCheck` — the check behind the `formatting` required status.
4. `detektMain` and `detektTest` for the module, then the aggregate `detektVerify` — the check behind the `detekt` required status. Detekt analyzes main and test sources with a line limit of 120, import statements exempt. `detektVerify` fails closed unless the analysis executed in the same invocation and no baseline file exists, so a skipped or narrowed analysis cannot report a pass.

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

The first invocation produces both reports against one class set; the second verifies them. See
[Quality gates](../quality-gates.md) for the declared scopes and thresholds and for
[how to change a declared value](../quality-gates.md#changing-a-declared-value).
