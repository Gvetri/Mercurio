# Error-reporting posture

Status: accepted (2026-10-29)

The client reported no crashes or handled errors, so a release regression reached maintainers only through user reports. Observability must not add a Google dependency, must work on devices without Google services, and must not turn conversation content into telemetry.

Decision: the client reports errors through one application-owned port, `ErrorReporter`. Its event contract is closed: it carries the release and environment tags and nothing else, so no Session content, draft text, credential, or Gateway host can travel with a report. The application layer declares the port and a recording fake for tests and journeys; the no-op sink is the default binding, so nothing leaves the device until a backend adapter is selected in wiring. Reporting is fire-and-forget: a report never blocks the app it observes. The selected backend is the official Sentry Android SDK on the free Developer plan: the DSN is one build configuration value read only by the adapter, only standard SDK options are used, and a scrubbing callback runs before an event leaves the device. Session replay, profiling, and other backend-only features are not adopted, and no Google, Firebase, or Crashlytics dependency enters the catalog.

Considered options: Crashlytics (rejected: a Google dependency and vendor lock-in); direct SDK calls from feature code (rejected: the backend would leak into the application layer and make the exit path a refactor); a self-hosted backend only (rejected: no managed quota or alerting today, while the same SDK keeps that migration a DSN change).

Consequences: reporting stays inert until an adapter is bound. The adapter owns release and environment tags, sampling, scrubbing, and noise filtering. Moving to a self-hosted Sentry-compatible backend changes one DSN value and turns session tracking off.
