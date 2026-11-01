# Error reporting operations runbook

The client reports crashes and unexpected failures through the error-reporting port to the
official Sentry Android SDK. This document is the operator procedure around it: how the DSN is
supplied, how the free-plan quota is watched, how a junk-event flood is stopped, and how the
backend is changed or left.

The posture itself — what an event carries, what it must never carry, and why Sentry was chosen
over Crashlytics — is recorded in
[the error-reporting ADR](adr/0007-error-reporting-posture.md). Only standard SDK options are
used: no session replay, no profiling, no mapping or symbol upload.

## Supplying the DSN

The DSN is one build configuration value and nothing else reads it. A build that does not supply
it stays safe: reporting is disabled, no backend is initialised, and no network call is attempted.

```text
./gradlew :app:assembleDebug -Pmercurio.sentryDsn=<project DSN>
```

Absent, empty, or blank, the value resolves to no DSN and the port falls back to its no-op sink.
The value is read only by the adapter in the wiring layer, and it never enters the application or
domain layers.

## Watching consumption

The free Developer plan allows 5,000 errors and 5,000,000 spans per month with 30-day retention.
Overage is hard-dropped at HTTP 429: there is no bill and no grace period, so a month that runs
hot silently loses the errors it could not accept, and the quota resets monthly.

Watch the [Stats](https://sentry.io/stats/) page for the organisation's project. The condition to
act on is not "the number looks large" but "real defects are being dropped": if accepted errors
approach the monthly allowance while field reports still arrive, the quota has started costing
visibility.

## Stopping a junk-event flood

The DSN is a public identifier by design — it ships inside the APK and Sentry documents extraction
as a quota question, not a security incident. Two controls exist, and neither ships a new build:

1. **Rate-limit the key.** In the project's client-key settings, set the per-key rate limit. The
   SDK keeps working and excess envelopes are rejected before they consume quota.
2. **Rotate the key.** Create a new key, point the next build at its DSN, and disable the old key.
   Rotation is the remedy; the previously extracted key then dies with the old client key.

Neither step requires an app-code change: the DSN is the single value the adapter reads.

## Alerting

The free Developer plan offers one seat and email alerts. Alert rules are built in the Sentry UI;
the client configures none and requires no push permission or notification dependency for them.
Channels behind the Team plan — Slack routing, third-party integrations, REST API access — are
deliberately not relied on.

## When quota binds

The documented escalation is Sentry's sponsored open-source plan, not a paid upgrade. Eligibility
criteria are already met by the repository's license, and the sponsored allowance is 5,000,000
errors and 1,000,000,000 spans per month. Apply only when the condition above is true, and record
the decision on the spec issue that carries this work.

Until then the free plan is the intended operating point.

## Changing the backend

Moving to an existing GlitchTip instance is a DSN change plus one toggle:

1. Set the build to the GlitchTip DSN with `-Pmercurio.sentryDsn=<glitchtip DSN>`.
2. Turn session tracking off, because GlitchTip does not implement it.
3. Rebuild the alert rules by hand; they do not migrate.
4. Accept that historical events do not move — the free plan retains 30 days and nothing is
   dual-shipped.

No application or domain code changes, and no second backend runs in parallel.
