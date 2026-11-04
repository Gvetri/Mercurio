# Quality gates

The quality gate is fail-closed. Every check either executes in the current invocation and meets
its declared scope and thresholds, or the build fails. A report is evidence for the check, never a
substitute for it: a missing, empty, stale, narrowed, or unparsable report fails the verification
that reads it, and no check is advisory.

Run the whole gate locally:

```text
./gradlew qualityGate
```

To run the same checks for a single module, see
[Local quality gate for one module](agents/local-quality-gate.md).

Neither Gradle cache is enabled for the root build, and both stay scoped to the standalone `buildSrc`
build: the verification tasks read the project and the task graph at execution time, and the evidence
contracts reject a report older than the newest compiled class, so a cache-restored class file would
trip the check before it measured anything (issue #238).

## Required checks

A pull request merges only when both required contexts pass: `quality-gate`, the aggregate of the
checks below, and `pr-template`, the body check.
[What a pull request must satisfy](agents/pull-request-checks.md) names what each verifies.
`quality-gate` fails unless every declared check below executed and passed for the event that
triggered the run. A check the event routes out of the run must report `skipped`, and any other
outcome fails the aggregate; see
[The protected main branch and the pull-request workflow](protected-main-workflow.md) for the routing.

| GitHub status | Gradle entry point | Fails when |
| --- | --- | --- |
| `formatting` | `formatCheck commentPolicyVerify` | A Kotlin file is not formatted, or a hand-maintained file carries a line or block comment where KDoc is the only allowed comment form |
| `static-analysis` | `:app:lintDebug` | Android lint reports an error |
| `detekt` | `detekt detektVerify` | detekt reports a finding, a baseline file could absorb it, or the analysis tasks did not execute |
| `unit-tests` | `verifyRequiredUnitTests` | A declared unit-test scope is missing, empty, has zero executed tests, or contains a skipped test |
| `fixture-descriptor` | `fixtureDescriptorTests verifyFixtureDescriptor` | The pinned fixture provenance or its descriptor contract changed |
| `fixture-lifecycle` | `fixtureLifecycleTests journeyScenarioTests` | An executed fixture lifecycle or journey-scenario test fails, or the runner task did not execute |
| `fixture-contract` | `fixtureContractTests` | A client-owned Gateway JSON or SSE contract fixture test fails |
| `android-build` | `:app:compileDebugKotlin :app:compileReleaseKotlin` on main pushes; `:app:assembleDebug :app:assembleRelease` otherwise | A debug/release compilation fails, or a non-push application APK does not build |
| `architecture-check` | `architectureRuleTests verifyNoMocks verifyDeterministicFakes` | An inward-dependency, untrusted-content, app-external file, unidirectional-data-flow, mock, or boundary-double rule is violated |
| `compose-jvm-tests` | `:feature:entry:presentation:verifyRoborazziBaselineManifest :feature:entry:presentation:verifyRoborazziDebug` | A pinned Compose surface drifts from its approved baseline, or the baseline manifest and the snapshots disagree |
| `coverage-mutation` | `coverageVerify mutationVerify` | Coverage or mutation evidence is missing, empty, stale, narrowed, below a declared threshold, or outside the declared production scope. Required on every `push` to `main`, on the nightly schedule, and on manual dispatch; skipped on every pull request, so a covered production change must regenerate and verify its evidence locally before it is reviewed |
| `conformance` | `.github/scripts/verify-repository-conformance.py`, `.github/scripts/verify-public-documentation.py`, and `.github/scripts/release-signing.py` (`environment` and `nightly-environment`) | The protected-main contract drifts, the pull-request template job stops running unconditionally or stops handing the author and body to `.github/scripts/verify-pr-template.py`, that check stops exempting Dependabot or starts exempting another bot while still rejecting a body that misses the template sections, the published documentation has a broken link or anchor, carries a credential, a real-looking endpoint example, a private infrastructure reference, or an instruction outside the supported product scope, or no longer states the approved user scope, standalone signing loses its required human reviewer, Nightly signing gains an approval/wait gate, or either signing environment enables administrator bypass, permits a ref other than the `main` branch, or cannot be verified |
| `commit-message` | `.github/scripts/verify-conventional-commits.py` | The squash-merge title or a commit of the pull request is not a Conventional Commit, or the commit list of the pull request is unavailable, empty, or truncated |
| `draft-validation` | `formatCheck :app:lintDebug` | The head of a draft pull request is unformatted or fails Android lint |
| `fork-guard` | `.github/workflows/quality-gate.yml` | Never on its own: it reports the handling of an external-fork pull request, and the aggregate requires it for every pull request, then fails closed for an external fork |
| `ui-paths` | `dorny/paths-filter` (pinned revision) in `.github/workflows/quality-gate.yml` | The changed-file answer for the pull request is unavailable or the detector reports an unreadable decision: the emulator-lane routing fails closed. On a main push an unreadable changed-file answer instead runs `buildsrc-contracts`, so the pushed range is still checked. The detector declares the UI surface as `app/src/main/**`, `feature/entry/presentation/**`, `feature/entry/wiring/src/main/res/**`, and `fixtures/mercurio/journey/**`. The same job classifies a documentation-only head against the declared documentation surface, and an unreadable pull-request changed-file answer fails closed to the full gate |
| `api24-instrumentation` | `:app:verifyConnectedAndroidTests` | Instrumentation produced no tests, skipped a test, or reported a failure. Required on scheduled and manual runs and on a ready in-repository pull request that carries the `run-maestro` label or modifies a declared UI path; skipped on main pushes, drafts, external forks, and pull requests that carry neither |
| `maestro-journeys` | `.github/scripts/journey-run.sh`, one partition per shard | A deterministic emulator journey fails against the controlled fake Gateway. The lane runs as three parallel shard jobs, each invoking `journey-run.sh <shard> 3` on its own partition of the pinned journeys, and a shard aggregate fails the lane when any shard fails; failure evidence is uploaded per shard. The shards start in parallel with `api24-instrumentation`, and the aggregate requires that device check to pass. Required on scheduled and manual runs and on a ready in-repository pull request that carries the `run-maestro` label; skipped on main pushes, drafts, external forks, and every pull request without the label, including one that modifies a declared UI path. [The journey suite](deterministic-emulator-journeys.md#requesting-the-journey-lane) lists the key areas that need the label |

A documentation-only pull request takes a fast path: when the decision job reports that every
changed file of the head is a Markdown file outside `.github/` or a file under `docs/**`, the heavy
lanes (`formatting`, `static-analysis`, `detekt`, `unit-tests`, `fixture-descriptor`,
`fixture-lifecycle`, `fixture-contract`, `android-build`, `architecture-check`,
`compose-jvm-tests`) report `skipped` from the first pull-request run and the aggregate requires
exactly that; the `run-maestro` label keeps precedence and starts the heavy lanes on a
documentation-only head too. A dependency-only head takes the sibling fast path: when the decision
job reports that every changed file is a build or dependency file (`gradle/libs.versions.toml`, any
`*.gradle.kts` including `settings.gradle.kts`, `gradle.properties`, or any file under `gradle/**`;
the declared class is deliberately generic over dependency and build files, and `.github/**` and
`buildSrc/**` are excluded before the match so control-plane and build-logic heads can never take
the trimmed path by accident), the aggregate skips `api24-instrumentation`, `maestro-journeys`,
and `buildsrc-contracts` while every core lane still runs, and the `run-maestro` label keeps
precedence over the emulator lanes there too. On every pull request the aggregate also expects
`coverage-mutation` to report `skipped`: the lane is a main-line and nightly signal, and the label
does not revive it.
Every classifier answer must be exactly `true` or `false`: on a pull request, an unreadable
changed-file answer fails closed to the full gate, and on a push, an unreadable changed-file answer
runs the `buildsrc-contracts` check; the aggregate re-validates each decision guard before trusting
the routing. The classification is deliberately conservative: code, resources, and especially
`.github/**` route the full gate. A push consults the decision only for the `buildsrc-contracts`
lane: a push that changes a `.github/**` or `buildSrc/**` file runs the buildSrc self-test, and an
app-only push skips it. A nightly schedule and a manual dispatch never consult the decision, so the
release chain always re-verifies the exact `main` tip with the complete gate.

The aggregate job compares `.github/quality-gate/required-checks.txt` (plus the `buildsrc_contracts`
support job) with its own `needs` list, so a check can neither be dropped from the declaration
silently nor added without updating the declaration. Support jobs such as `buildsrc_contracts` are
not declared GitHub statuses; the required statuses are `quality-gate` and `pr-template`. The
workflow contracts are self-tested in
`buildSrc/src/test/kotlin/org/mercurio/client/buildlogic/QualityGateConfigurationTest.kt`, the
verifications themselves in `CoverageEvidenceTest`, `MutationEvidenceTest`, `BoundaryDoubleVerifierTest`,
and `ReportEvidenceTest`, the fail-closed behaviour of the real tasks in
`QualityVerificationFailClosedTest`, and the architecture rules in `ArchitectureCheckTest`. The
documentation check is covered by
`.github/scripts/tests/test_public_documentation.py`, and the published-release artifact check by
`.github/scripts/tests/test_release_artifacts.py`; `QualityGateConfigurationTest` runs both. The six
workflow-contract classes (`QualityGateConfigurationTest`, `QualityVerificationFailClosedTest`,
`CoverageEvidenceTest`, `MutationEvidenceTest`, `BoundaryDoubleVerifierTest`, and
`ReportEvidenceTest`) run as three parallel shards behind the `buildsrc-contracts` umbrella job.
Shard 1 runs the four most expensive `QualityGateConfigurationTest` suites; shard 2 runs the
remaining `QualityGateConfigurationTest` tests and the mutation, coverage, and committed-baseline
tests of `QualityVerificationFailClosedTest`; shard 3 runs the remaining fail-closed test groups
plus the four verification classes. Every test is selected exactly once, no
assertion is removed, and the shards run on exactly the events where the umbrella runs. The
umbrella keeps the `buildsrc-contracts` key and name, remains the only result the aggregate reads,
and fails when any shard fails. A cold measurement before the split took 25 minutes for the six
classes in one invocation (29m51s for the CI step); the delivering pull request records the shard
timings, and the critical-path target is 12 minutes or less. The architecture rules in
`ArchitectureCheckTest` run in the `architecture-check` job, so a rule only exists if a check
enforces it and CI executes that check.

## Declared scopes and thresholds

`QualityPolicy` in `buildSrc/src/main/kotlin/org/mercurio/client/buildlogic/QualityPolicy.kt`
is the single declaration of the enforced scope. The Gradle tasks read their thresholds from it, and
the tables below mirror those declared values; the "(measured …)" figures record what each module
measured when its scope was declared.

The Gradle wiring is declared once as well. `buildSrc/src/main/kotlin/mercurio-quality-gate.gradle.kts`
is a convention plugin: each modified module applies `id("mercurio-quality-gate")` and keeps no Kover or
PIT block of its own. `buildSrc` resolves the Kover and PIT plugin versions from the main build's
version catalog and keeps both plugins on its build-logic classpath, so each version has one
declaration. The plugin reads the module scope from `QualityPolicy` and fails on a module that
has no declared scope, so no module can run the tools with thresholds of its own. It also gives each
test task and the PIT test minion the `fixture.repositoryRoot` property that the fixture-driven tests
resolve their fixtures with.

### Coverage

Kover measures the declared Kotlin/JVM production modules. Presentation is out of the covered
scope: it is a Compose surface whose behavior is pinned by the `compose-jvm-tests` baselines —
which render the committed synthetic state matrix, never a live Gateway — and
the wiring module is a thin Android bridge covered by instrumentation.

| Module | Line coverage | Branch coverage | Reported classes |
| --- | --- | --- | --- |
| `:feature:entry:domain` | 62% (measured 64.2%) | 66% (measured 68.2%) | 42 (measured 44) |
| `:feature:entry:application` | 86% (measured 87.9%) | 74% (measured 76.7%) | 18 (measured 19) |
| `:feature:entry:data` | 90% (measured 92.0%) | 61% (measured 63.5%) | 37 (measured 39) |

`./gradlew coverageVerify` runs `koverXmlReport` and Kover's own `koverVerify` for each module, then
re-reads each report and fails unless the report exists, is at least as new as the compiled classes it
measures, holds at least the declared number of reported classes, reports measured lines, and meets the
declared line and branch thresholds. Kover's verification uses the same declared values, so no
single entry point can pass with insufficient coverage.

### Mutation

PIT mutates the declared Kotlin/JVM production modules. The mutators are the default PIT set; the
Arcmutate Kotlin plugin is not part of the build, so the surfaced mutations are the ones PIT can
apply to plain JVM bytecode.

The measured values use PIT's own definitions, so the declared threshold and the tool's threshold
judge the same quantity: the mutation score is the detected mutants out of every analysed mutant,
and the test strength is the detected mutants out of the mutants the tests reached at all. A mutant
counts as detected when the tests killed it or it timed out, which is why the report records the
detected count next to the status breakdown.

| Module | Mutants | Mutated classes | Mutation score | Test strength |
| --- | --- | --- | --- | --- |
| `:feature:entry:domain` | 185 (measured 197) | 32 (measured 34) | 46% (measured 48.2%) | 82% (measured 85.6%) |
| `:feature:entry:application` | 115 (measured 121) | 10 (measured 11) | 78% (measured 81.0%) | 82% (measured 85.2%) |
| `:feature:entry:data` | 480 (measured 503) | 31 (measured 33) | 70% (measured 76.1%) | 82% (measured 86.7%) |

`./gradlew mutationVerify` runs the mutation task for each module, then re-reads each report and
fails unless the report exists, is at least as new as the compiled classes it measures, analyses at
least the declared number of mutants over at least the declared number of classes, keeps every
mutated class inside the declared target package, states for each mutant whether the tests detected
it, and meets the declared mutation score and test strength. It then cross-checks the two report
kinds: every class the mutation run mutated must appear
in the coverage report, so a class that silently left the covered scope fails the gate even when the
remaining coverage percentage stays above its threshold.

State transitions are inside the mutated scope: the domain state transitions, the application
orchestration and state rules, and the data mapping and storage logic are all plain JVM bytecode.
Presentation state holders (`EntryStateHolder` and the UI state it produces) are out of the mutated
scope for the same reason as the covered scope above: they are a Compose surface whose behavior is
pinned by the `compose-jvm-tests` baselines rather than by JVM unit tests, and moving that logic into
a JVM module is a separate change.

### Boundary doubles and mocks

`./gradlew verifyNoMocks` fails on a mocking framework reference in production, unit-test, or
instrumentation source, and on a mocking framework declared as a dependency in any module build
script. `./gradlew verifyDeterministicFakes` requires an in-repository test double for every
declared datasource and repository boundary, and rejects a double whose body reads randomness or the
wall clock. `QualityPolicy.allowedDoubleReferences` holds the declared deviations, each with its
reason; an allowance that no double uses is itself a violation.

The boundary list is declared, not discovered: adding a datasource or repository port means adding it
to `QualityPolicy.boundaryDoubles`, and the verification then fails until that boundary has a
production declaration and a deterministic double in the repository. A port that is never declared
keeps no double check, so a new port belongs in the same change as its declaration.

### Architecture rules

`./gradlew architectureRuleTests` runs the architecture rule suite from
`buildSrc/src/test/kotlin/org/mercurio/client/buildlogic/ArchitectureCheckTest.kt` in an isolated
build directory, and fails unless the focused source exists, its report exists, is non-empty, and
holds at least one executed, unskipped, passing test. The rules are Konsist checks: the layer graph
runs through the Konsist architecture assertion, and the reference and unidirectional-data-flow rules
read the declared scopes through Konsist's declaration model. The declared rules are `architecture/layer-boundaries`,
`architecture/domain-application-references`, `architecture/presentation-untrusted-content`,
`architecture/app-external-file-boundary`, `architecture/module-dependency-declarations`,
`udf/composables-take-state-and-events`, `udf/one-state-writer`, `udf/immutable-state-types`, and
`udf/one-event-entry`; see [Architecture boundaries](architecture.md) for what each one rejects.

Every rule also runs against a synthetic scope that carries one deliberate violation, and the suite
asserts that the rule names itself and reports the violation, so a rule that stopped rejecting a
violation fails instead of passing. A scope that resolves to no Kotlin source, and a module scope
that resolves to no build script, fail the rule that owns it, so a source set that moved out of the
declared roots cannot pass by scanning nothing; the layer graph fails the same way when a declared
layer holds no file. Konsist is pinned at `0.17.3` in `gradle/libs.versions.toml`, the
release whose Kotlin compiler matches the build's embedded Kotlin, and `buildSrc` keeps it on the
rule suite's test classpath. Konsist parses Kotlin sources, not Gradle Kotlin scripts, so the module
build scripts are read directly by the one rule that owns them.

`QualityGateConfigurationTest` pins the wiring: the pinned Konsist version, the test-classpath
declaration, the entry point the `architecture-check` job runs, the nine rule names, and the
deliberate-violation evidence of every rule.

### Detekt

detekt is pinned in `gradle/libs.versions.toml` and every Kotlin module applies it. The root build
script declares the strict policy once: `config/detekt/detekt.yml` is layered on detekt's default
config (`buildUponDefaultConfig`), the analysis fails on any reported finding, and the rules the gate
relies on are enabled explicitly, so a detekt upgrade cannot quietly turn one of them off —
`ReturnCount`, `ElseCaseInsteadOfExhaustiveWhen`, `DocumentationOverPrivateFunction`, and
`DocumentationOverPrivateProperty`. The configuration file carries no comments; its exemptions are
deliberate, each with its reason here:

- `FunctionNaming` keeps `functionPattern: '[a-zA-Z][a-zA-Z0-9]*'`, because Compose functions are
  PascalCase by convention and the pinned rule cannot exempt annotated functions, so the pattern
  admits both casings; the rule still reports a name that does not start with a letter or that
  contains a non-alphanumeric character.
- `ReturnCount` keeps `excludeGuardClauses: false`: an early `return` on a guard is still an early
  return, so guard clauses count toward the limit.
- `IteratorNotThrowingNoSuchElementException` excludes test sources only, because a blocking test
  double deliberately never exhausts its stream, so its `next` is unreachable after `hasNext`
  returns false; the iterator contract stays enforced in production sources.
- `InjectDispatcher` excludes test sources only, because a test chooses the dispatcher it drives the
  subject with and dispatcher injection is a production-wiring concern; the rule stays enforced in
  production sources.

`./gradlew detekt` runs the type-resolution tasks (`detektMain` and `detektTest` for every module),
so a rule that needs binding executes instead of silently skipping. The plain, non-type-resolving
per-module tasks exist but stay disabled, and the aggregate never runs them.

`./gradlew detektVerify` verifies the evidence:

- the analysis tasks must execute and be enabled in this invocation, so a run that skips or disables
  them cannot claim a verified analysis;
- no baseline file may exist under `config/detekt` — the committed `baseline.xml` and the
  source-set-specific `baseline-*.xml` files are gone, so a finding can never be absorbed silently
  again.

A finding must be fixed, not baselined: there is no committed baseline, and the analysis fails the
build on every reported finding.

The detekt contracts are self-tested as well: `QualityGateConfigurationTest` pins the workflow job,
the declaration, and the configuration, and `QualityVerificationFailClosedTest` runs the real tasks
against a deliberate violation of each of the four rules, a clean tree, a committed baseline file,
and a skipped detekt verification.

### Comments

`./gradlew commentPolicyVerify` runs `.github/scripts/verify-comments.py` over every tracked file.
Each file gets a language-aware reading — a Kotlin lexer that separates KDoc from block and line
comments, a YAML reader that treats a block-scalar body as content and reads a `run` or `script`
scalar as shell, a heredoc-aware shell reader, and a quote-aware Python reader — so a marker inside a
string, a URL, a heredoc, or a block scalar is not a comment. KDoc is the only allowed comment form; a
tool directive that starts the comment (`# noqa`, `# shellcheck`, `# syntax=`), a license header above
the first code, and the generated or vendored files the class table excludes pass.

A violation reports the file, the line, the comment kind, and the remedy: delete the comment, or move
its constraint into a test, a check, or `docs/` first. The verifier fails closed on its own scope too:
a tracked file whose class it does not know fails instead of passing, so a new file class is decided in
[Coding guidelines](coding-guidelines.md#scope) in the same change that adds it, and a sweep of the
whole repository that resolves to no in-scope file fails rather than reporting a clean tree. A caller
that names its own scope with `--files` is judged on exactly that scope, so the optional pre-commit
hook names the staged files and passes when none of them is in scope; `--staged` reads those files as
the index holds them, which is what the hook judges.

`QualityGateConfigurationTest` pins the wiring, and `.github/scripts/tests/test_verify_comments.py`
pins the tricky readings: comment markers inside strings, URLs, YAML block scalars, heredocs,
shebangs, license headers, and the generated files.

## Design evidence

The design flow in [Design delivery](design-delivery.md) adds no check and no status. Its evidence
rides the checks that already exist:

- The **code-versus-code** half is the `compose-jvm-tests` gate: `verifyRoborazziBaselineManifest`
  and `verifyRoborazziDebug` compare every capture with the repository's own approved image, so the
  screenshots in a design change are the baselines the gate reads.
- The **design-versus-code** half is the reviewer checklist over the captures and the approved design
  page. It is judgement over roles, the type scale, the spacing steps, the states, and both themes,
  never a pixel diff across renderers, so it stays out of CI.
- The **value check** compares the design package's token values with `HermesDesignTokens.kt` on the
  build host before the pull request. It reads a private package, so it stays out of CI and its
  result is recorded in the code issue.
- The contract document itself is checked by the `conformance` job, which resolves its links and
  anchors and applies the public documentation safety rules like every other page under `docs/`.

## Changing a declared value

A declared threshold, scope entry, or allowance is a deliberate decision that belongs in the same
change as the code that needs it:

1. Measure the module with `./gradlew coverageVerify mutationVerify` before changing anything.
2. Set the declared value to the measured value rounded down, keeping a small margin for the next
   legitimate change.
3. Update this document's tables in the same commit, including the new measured value.

Raising a threshold is never a side effect of an unrelated change, and lowering one requires the
same review as raising it.
