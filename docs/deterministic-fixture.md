# Deterministic Hermes fixture

The repository-owned descriptor at [`fixtures/hermes/pinned-fixture.properties`](../fixtures/hermes/pinned-fixture.properties) is the compatibility evidence for the deterministic Hermes fixture.

## Descriptor rules

- The descriptor has `schema_version=1` and exactly one immutable provenance field.
- `hermes_revision` is a full 40-character Git revision.
- `image_digest` is a full `sha256:` container digest.
- Mutable references such as `main`, `latest`, version-only tags, and branch names are invalid.
- The descriptor contains no Gateway credential, provider credential, endpoint secret, private host detail, or environment-specific setting.

The descriptor uses a small properties format so the build can validate it without adding a runtime or Android dependency. Lifecycle values such as `build-and-run-pinned-hermes` are runner action identifiers. They are not shell commands and must not be replaced with environment-specific command lines.

## Lifecycle contract

The deterministic runner added by later integration work must honor these declarations:

1. Resolve the descriptor's immutable provenance and execute its startup action in an ephemeral, isolated fixture with no restart supervisor. The public startup action uses the repository-owned synthetic fixture and does not fetch or invoke a live provider.
2. Require `GET /health` to return HTTP 200 before tests start.
3. Require `GET /v1/capabilities` to succeed and satisfy the client capability manifest.
4. Create only client-owned synthetic state. Reset it before and after each test.
5. Require teardown after both success and failure. Verify process exit and state reset.

The descriptor validator and focused unit tests run as `fixtureDescriptorTests verifyFixtureDescriptor`. They are dependencies of the local `qualityGate`, and hosted CI runs the same validation as a required quality check.

## Local fixture runner

The deterministic runner is a plain JVM module at [`fixtures/hermes/runner`](../fixtures/hermes/runner). It consumes the pinned descriptor, resolves its `build-and-run-pinned-hermes` action and immutable provenance identity, starts a loopback-only synthetic Gateway, and rejects a process whose pinned identity does not match the descriptor. It never reads provider credentials or contacts a public endpoint. The public action is intentionally repository-owned and local-only: it provides the pinned compatibility boundary without downloading or invoking a live Hermes runtime. Its HTTP behavior includes deterministic `/health`, the `/v1/capabilities` endpoints table, and the pinned synthetic Session (`/api/sessions`, `/api/sessions/{session_id}/messages`) and Run (`/v1/runs`, `/v1/runs/{run_id}`, `/v1/runs/{run_id}/events`) routes used by the integration suite. It remains loopback-only and never contacts a provider.

Use the explicit lifecycle hooks when an integration test needs more than one test case:

1. `setup()` validates the descriptor and starts the local fixture.
2. `awaitReady()` performs the health and capability checks. A failed check throws and cannot be skipped.
3. `runTest { ... }` resets synthetic state before and after the test.
4. `teardown()` resets state, stops the process, and verifies process exit.

`execute { ... }` runs these hooks as one failure-safe operation. Teardown runs after setup, readiness, or test failure. A cleanup problem is reported as `FixtureCleanupException`; when the test itself fails, that cleanup exception is attached as a suppressed failure so the two causes remain distinct.

The local validation command is:

```text
./gradlew fixtureLifecycleTests
```

The hosted `fixture-lifecycle` quality check runs this same command. It is deterministic lifecycle validation, not a live-provider smoke test.

The deterministic emulator journeys ([`docs/deterministic-emulator-journeys.md`](deterministic-emulator-journeys.md)) reuse this pinned provenance as the identity for every journey scenario and for the fake Gateway that serves the emulator layer.

## Live acceptance lane

The data module owns test-class acceptance lanes that drive the real OkHttp client against the real deterministic journey Gateway over TLS. Each lane starts `JourneyGatewayProcess` from a programmatic scenario, wraps it in an http-scheme view for the fixture's loopback guard, and points `DefaultGatewayClient` at the started process's own `https` endpoint with an `OkHttpClient` that trusts only the journey CA and keeps default hostname verification. Bearer authentication is proven over the wire: the scenarios require a bearer credential, so any request without the expected credential fails with `AUTHENTICATION_FAILED`.

The one command is:

```text
./gradlew :feature:entry:data:liveAcceptanceTest
```

The task runs the `LiveGatewaySessionAcceptanceTest` and `LiveGatewayRunAcceptanceTest` suites, which together cover the client capability manifest's eleven endpoint requirements. The session suite verifies capability discovery, the paged session list, create, open, rename, pin, unpin, history, and delete against the started process only, and verifies that a failing operation surfaces its `GatewayErrorCategory`. The run suite creates an ephemeral Session through the lane client and verifies run create (202 admission), run status (the `hermes.run` object with its identity check), and run SSE observation against that Session: the admitted run's delta text is delivered without loss, a `live_framing` stream proves tolerance for `id:` lines and comment lines, an event type outside the client's mapping is silently skipped, a held-open stream is closed by the client after its terminal event, and a refused creation surfaces `GATEWAY_REQUEST_FAILED`. A fake-transport test at the same boundary seam proves that a `message.delta` frame without its delta and a record mixing `data:` with other content both surface `INVALID_RESPONSE`. The same tests also run hermetically under the plain `test` task on an ephemeral loopback port.

Each operation emits one redacted JSON line with the operation name, method, path and query shape, expected and actual status, outcome, envelope key types, and the error category on failure. Run observation lines carry a per-stream summary: events seen by type, the delta count, the terminal event, skipped unmapped frames, the tolerated framing artifacts (`id:` lines and boundary comments), and whether the stream held open with keep-alive comments. Values, credentials, bearer tokens, delta text, run input, URLs, and workspace paths are redacted. Evidence lands under `feature/entry/data/build/live-session-matrix-evidence/` by default; a different directory can be supplied with the `live.session.matrix.evidenceDir` system property.

## Fixture parity step

The lane also proves that the envelopes the client actually exchanges with the started Gateway keep the shape of the checked-in contract fixtures under `fixtures/hermes/contracts/`, using the same operation-to-fixture mapping `DefaultGatewayClientTest` and `ContractFixtureCatalog` define. A tee transport wraps the lane's `OkHttpGatewayTransport` and records each wire exchange's method, path, query parameter names, request body, status, response body, and raw SSE lines; a recorder writes them redacted to `live-session-matrix-parity-captures.jsonl` / `live-run-matrix-parity-captures.jsonl` next to the evidence. After the run, `FixtureParity.compare` loads the captures, reads the fixtures, and emits one parity artifact with one entry per operation and fixture pair: operation, fixture, method, path shape, expected and observed status, verdict, and the observed difference on a mismatch.

Normalization makes the comparison deterministic and shape-only: opaque identifiers become `<id>`, timestamps become `<timestamp>`, numeric and boolean leaves that carry server state become `<count>` and `<bool>`, string-valued server-state fields — titles, previews, message content, delta text, run input and output, roles, statuses — are compared for presence and JSON type only, and path parameters collapse to their `{param}` shape. Provenance by design: the SSE `fixture.metadata` event, the capability manifest's descriptive fields outside the `endpoints` table, and the session's `last_active` field are normalized away, not reported as drift. Key-set differences are reported in both directions: a live-only key is reported unless the fixture tolerates it (string-valued or null content fields, identity and timestamp fields), and a fixture-declared key that the live envelope lacks is reported unless its name is in the declared exemption set — `last_active` (the session projection's by-design omission), `created_at`, `output`, and `usage` (the double's reduced `hermes.run` status and `run.completed` projection), and `session_id` (the double's message projection carries no run identity metadata). Outside those tolerance rules, value and type differences, including nullability, are still reported.

The step is report-only: it never writes fixtures, parsers, or the manifest, and a test proves those source hashes are unchanged even when a mismatch is seeded. The artifact lands under `build/fixture-parity/` by default; a different directory can be supplied with the `live.fixture.parity.artifactDir` system property, so the same one command as the acceptance lane produces both the evidence and the parity artifact. The step also reconciles the contract drift protocol's issue ledger from the same entries.

## Contract drift protocol

Every parity mismatch is reconciled against an issue ledger so an agent can pick up contract-alignment work. The protocol is report-only like the parity step itself: it never writes fixtures, parsers, or the manifest, and re-baselining a fixture always happens through a reviewed change that cites the filed issue.

The shipped issue sink is a local issue ledger, `contract-drift-issues.jsonl` in the parity artifact directory (the same `live.fixture.parity.artifactDir` override applies). Each ledger line is one issue record: the divergence identity (`operation :: fixture`), the `ready-for-agent` label, the open/resolved status, the title, the divergent operations, the parity artifact reference (`fixture-parity.jsonl`), the redacted parity evidence, an observation count, a proposed fixture re-baseline summary, and the manifest-version guidance. Lines carry references and redacted content only — the same redaction vocabulary as the parity captures (bearer values, URLs, workspace paths) — and a missing capture is stated as missing, never invented or linked.

The reconciliation rules:

- A parity mismatch files exactly one issue for the divergence. Divergence identity is the operation and fixture pair: a repeated mismatch on the same identity updates the existing open issue (its observation count increments), and a different identity files a new issue. The ledger holds at most one record per identity. A resolved identity that regresses reopens as a fresh open record whose observation count restarts at one; the resolved row it replaces is history.
- A green parity run files nothing: an issue can only exist with a real divergence behind it. When there is no mismatch to file and no open issue to resolve, the ledger is left byte-identical, and the only write a green run may perform is resolving an open issue whose divergence now matches; the resolved record stays in the ledger with no duplicate rows.
- The issue template guidance states the manifest-version rule: the manifest version advances only when a required endpoint's name, method or path changes.

The shipped sink is local to the test-scoped protocol. Wiring a repository tracker is an operational step outside the protocol: one adapter implementing the same two-method sink (list open issues, upsert by divergence identity), with no new mechanism.

A synthetic-divergence drill (`DriftProtocolDrillTest`) stages a fixture mismatch against a scratch copy of the contracts in a temporary directory: the staged divergence files the issue, restoring the scratch fixture resolves it without residue, a green rerun leaves the ledger byte-identical, and the real fixtures, parsers, and manifest stay hash-unchanged throughout.

## Compatibility changes

Changing `hermes_revision` or `image_digest` is a compatibility change. Every such change requires all of the following:

- verify the fixture startup, health, capability, synthetic-data, and teardown contract;
- verify the client contract used by deterministic integration tests;
- run deterministic integration tests against the new provenance value;

Do not use `main`, `latest`, or another mutable reference as compatibility or release evidence.
