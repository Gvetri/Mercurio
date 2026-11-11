# Architecture boundaries

## Feature-first layout

The initial `entry` vertical slice is intentionally small:

```text
feature/entry/
├── domain/
├── application/
├── data/
├── presentation/
└── wiring/
```

The dependency direction is inward:

```text
app → wiring → presentation → application → domain
             → data implementation → domain
```

The app module only owns Android startup and composition. It does not own Gateway requests, repository decisions, or screen state transitions.

## Layer rules

### Domain

Domain source is plain Kotlin/JVM. It does not import Android, Compose, HTTP, SSE, serialization transport types, dependency-injection frameworks, or concrete data implementations. Domain ports describe what the application needs without selecting an implementation.

### Application

Application source is plain Kotlin/JVM. It coordinates domain ports and returns application state. It does not know about Android, Compose, transport libraries, or data implementations.

### Data

Data owns transport DTO parsing, the authenticated HTTP and SSE adapter, concrete repository implementations, the authenticated-encryption record format for the Gateway Credential, and Run recovery registry behavior. `HttpMessageImagePort` enforces the image fetch guardrails: an absolute HTTPS destination, HTTPS-only redirects, a public unicast literal or resolved address on every hop, an image media type checked before decode, a bounded body, and a memory-only result. The pre-flight resolution refuses a host that maps to a private, loopback, link-local, or reserved address, and the transport refuses the connection itself when the acquired socket is not public unicast: it cancels before the request is written and reports the same refusal. `DefaultGatewayClient` uses one configured HTTPS Gateway endpoint and the versioned Public Beta capability manifest. It does not discover profiles, use desktop or dashboard routes, append a server extension, or move transport types into domain/application. A deterministic in-memory datasource remains available for tests, while wiring provides the Android persistence bridge for the endpoint and the encrypted credential record, the AndroidKeyStore key that protects it, and the Android storage bridge for the JVM Run recovery registry.

### Presentation

Presentation owns immutable UI state, UI events, state transitions, the Compose theme, and rendering. Events enter `EntryStateHolder`; state flows down to `EntryScreen`. A composable does not call a repository, an HTTP client, or a transport adapter. The one composition-scoped loader is `MessageImageServices`: the image renderer starts it, it calls the `MessageImagePort` domain port, and it cancels with the composition.

Untrusted Gateway message content is rendered by a presentation-local Markdown subset: paragraphs, lists, emphasis, HTTPS links, fenced code blocks, pipe tables, and math (`SafeMarkdown.kt`, `MarkdownContent.kt`, `MarkdownTable.kt`, `MarkdownMath.kt`). A table becomes the approved table card; inline `$…$` math joins the inline text path and `$$…$$` display math gets its own surface, both laid out by the JLatexMath engine behind a single seam (`LatexFormula.of`); an expression the engine refuses renders its exact literal source. Every unsupported construct stays literal text, no WebView or executable content is involved, and only a destination that `allowedExternalLinkDestination` accepts can leave the client through the external-browser intent in `MessageIntents.kt`. Copy and Share are explicit user selections over the chosen message content.

An inline HTTPS image is fetched through the `MessageImagePort` domain port, implemented in data, and decoded by Coil's bitmap decoder inside presentation; that decoder holds no network component and no disk cache. The renderer shows a loading placeholder with the alt text, the loaded image, or a typed failure with an explicit retry, bounds concurrent loads, and cancels a load when the image leaves the viewport. A destination that is not an absolute HTTPS URL stays literal text and is never resolved.

### Wiring

Wiring selects concrete datasource and repository implementations and creates the presentation state holder. This is the only feature module that assembles the bootstrap data path. The module enables core-library desugaring for `java.time`, because the diagnostics exporter records UTC instants and the minimum supported API level is 24.

## Unidirectional data flow

State flows down and events flow up, and the shape is enforced by rules rather than by review. The state holder exposes `uiState` and one event entry, `onEvent`; a composable receives immutable state and one event callback.

- **One writer.** The rule reads the declared `MutableStateFlow` properties — `EntryStateHolder.mutableUiState` today — and fails on an assignment, an `update { }`, a `getAndSet`, or a `compareAndSet` on one of them outside the state-holder files (`EntryStateHolder*.kt`). Every other file reads state or raises an event. A scope that declares no `MutableStateFlow` fails rather than passing vacuously, so renaming the flow cannot retire the rule.
- **No public mutable property.** The state holder keeps no public `var`. A capability such as the notification-permission request arrives through `EntryStateHolderDependencies` at construction, so no caller can swap a callback or set a field after the holder exists.
- **Immutable state types.** A `*UiState` type declares `val` properties and carries no mutable collection anywhere in a property type — `List`, never `MutableList`, at any nesting depth.
- **One event entry.** Exactly one function that is not private takes `EntryUiEvent` itself, and it is `onEvent`. The dispatch functions take the event's subtypes and stay private or internal, so they are not entries. A settable function-typed property of the event type — the shape the removed permission callback had — is a second entry and fails; a read-only callback bundle that a composable receives is not.
- **Composables take state and events.** A `@Composable` takes immutable state, one event callback, and styling inputs. It fails when it takes a state holder or a port, and a port is an interface declared outside presentation, whatever it is named.

The four rules share one deliberate blind spot: they read declarations and the source text of presentation's main sources, not dataflow. A write through a local alias of the state flow, and an event entry declared in another module, are outside what they can see; the shape they pin is the one this feature has.

## State-holder confinement

The state holder has one state-change lane, and the lane is background work:

- **One entry, one queue.** `onEvent` enqueues a command through `submitStateChange`; `applyStateChange` enqueues one from a suspend caller. `consumeStateChanges` is the only consumer and applies the commands in FIFO order, and a command re-derives its guards from the current state because it runs after an unbounded number of earlier commands.
- **One command at a time.** Production wiring launches the consumer on `Dispatchers.IO.limitedParallelism(1)`, so the lane applies one command at a time on the IO pool — a concurrency limit, not a pinned thread. Producers keep the holder's IO scope, so a slow Gateway call never runs on the event thread and never holds up the lane.
- **No locks.** `state-holder/no-monitor-locks` rejects `synchronized`/`@Synchronized` in `EntryStateHolder*.kt`: a monitor there guards nothing the lane already serializes, and a path that writes outside the lane needs routing through the lane, not a lock.
- **Blind spots, stated rather than implied.** The lane is not yet the only writer of `mutableUiState`. These paths write it directly from their IO coroutines: the uncertain-send retry path (`prepareRunSubmission`), connection verification, the model-catalog fetch, the inbound-link open-failure frame, the diagnostics surface (record-count refresh, export and clear completion frames) and the app-update path. Those writes are thread-safe on `MutableStateFlow` and put no storage on the event thread, but the lane does not order them; routing them through the consumer is outstanding. The rule's scope is the `EntryStateHolder*.kt` filename prefix, so `UncertaintyStoreState` and its process-wide `ProcessRunSubmissionUncertaintyStore` keep their own monitors outside the rule's reach.
- **Blocking commits.** An applied state change may read or commit a small `SharedPreferences` record while the lane holds it. Those calls run on the background lane, never on the event thread, and delay later state changes rather than the event loop.

## Architecture checks

The architecture rules are Konsist checks. `./gradlew architectureRuleTests` runs the rule suite and fails unless its executed evidence is complete, and every rule also runs against a synthetic scope that carries one deliberate violation, so a rule that stopped rejecting a violation fails the suite instead of passing it. The rules live in `buildSrc/src/test/kotlin/org/mercurio/client/buildlogic/ArchitectureCheckTest.kt` and are declared by name:

| Rule | Rejects |
| --- | --- |
| `architecture/layer-boundaries` | a domain or application declaration that reaches an outer layer |
| `architecture/domain-application-references` | a framework, transport, serialization, injection, or concrete data, presentation, or wiring reference in domain or application source, including a fully qualified reference |
| `architecture/presentation-untrusted-content` | executable web content or a network client in presentation, which renders untrusted Gateway content, so the renderer cannot reach a web view or a link destination's server |
| `architecture/app-external-file-boundary` | a file path or a writable grant in any main source, so a file handed to another app stays a read-only content URI from the declared, non-exported file provider |
| `architecture/module-dependency-declarations` | a forbidden dependency declaration in the domain or application module build script |
| `udf/composables-take-state-and-events` | a `@Composable` that takes a state holder or an interface declared outside presentation (a port) instead of immutable state and one event callback |
| `udf/one-state-writer` | a write to a declared state flow outside the state-holder files, a scope that declares no state flow, or a public mutable property on a state holder |
| `udf/immutable-state-types` | a `*UiState` type with a mutable property or a mutable collection at any nesting depth |
| `udf/one-event-entry` | a second function that is not private and takes the feature's UI event, or a settable function-typed property of that event |
| `state-holder/no-monitor-locks` | a monitor lock (`synchronized`, `@Synchronized`) in a source named `EntryStateHolder*.kt`; state changes submitted to the lane are serialized there, so a monitor lock guards nothing |

The layer graph runs through the Konsist architecture assertion: domain depends on nothing, application and data depend only inward, and presentation and wiring stay inside the declared direction. The reference rules read the same scopes through Konsist's declaration model, because a fully qualified reference that carries no import is invisible to an import-based dependency assertion. The UDF rules read presentation's declarations the same way: the `@Composable` signatures, the state-holder and state-flow properties, the `*UiState` properties, and the functions and settable properties that carry the event type. The composable rule resolves over the whole production scope, because a port is only recognisable as an interface declared in another package, and narrows this judgement to presentation functions. The one-state-writer rule reads the source text as well, because an assignment inside a function body is not a declaration. Konsist parses Kotlin sources, not Gradle Kotlin scripts, so the module build scripts are the one medium the rule suite reads directly.

`./gradlew verifyNoMocks` rejects mock framework names, mock framework dependencies, and mock construction in main, unit-test, and instrumentation source. `./gradlew verifyDeterministicFakes` requires an in-repository test double for every declared datasource and repository boundary and rejects a double that reads randomness or the wall clock. `./gradlew coverageVerify` and `./gradlew mutationVerify` enforce the declared coverage and mutation scopes; see [Quality gates](quality-gates.md).

The typed Gateway contract adapter is verified by deterministic data-layer tests against the checked-in JSON and SSE fixtures. These tests assert the versioned capability manifest, exact profile routes, bearer authentication, all supported Session/Run operations, additive fields and events, malformed responses, and safe transport error categories.

These checks are intentionally simple and visible. A future feature must extend the declared checks when it introduces a new boundary.
