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

A DSN the SDK rejects is treated the same way: the backend does not install, no event is ever sent,
and the app runs normally. Only a working DSN turns reporting on.

### Release builds

The protected release workflow supplies the same property from the repository variable
`MERCURIO_SENTRY_DSN`. It is a variable rather than a secret because the DSN is a public identifier:
it permits submitting events and nothing else, and no auth token is involved in building or signing.

That variable is mandatory for a release. After assembling the APK the workflow reads the packaged
classes back and fails the build unless they carry exactly that DSN, so a Nightly cannot be signed or
published silent. A missing, blank, or malformed variable stops the release instead of producing an
artifact that reports nothing.

Rotating the key therefore means creating the new client key in the project, updating the variable,
and letting the next Nightly carry it; local builds and pull-request gates stay on their no-DSN
default and remain silent.

## Deliberate delivery check

Prove real delivery from a real build once per backend change; the deterministic journeys use a
recording sink and deliberately prove nothing about the network.

1. Build a debug APK with the DSN, as above, and install it.
2. Raise one deliberate report through the debug probe:

```text
adb shell am start -a android.intent.action.VIEW -d 'mercurio-journey://error-reporting?live=1'
```

3. The probe screen reports `Live error report raised` once the port has taken the report. Without
   the `live` argument the same deep link binds the recording sink and delivers nothing.
4. In Sentry, open the project's issues and confirm one `Unexpected client failure` event whose
   release is the installed build and whose environment is the build type. If it does not arrive,
   check the DSN value before assuming an app defect.

## What the scrubber does and does not remove

Before an event leaves the device, the adapter drops breadcrumbs, user identity, request data,
extras and the server name, and removes any tag whose name names a session, draft, credential,
token, password, authorization, cookie, gateway, host or DSN. What remains is the event's own
content: its release, its environment, the exception and its stack frames, plus the device and OS
context the SDK fills in.

It does not scan free text: an exception message or stack frame that embeds a Gateway endpoint is
reported as written, and `contexts` is left intact for device and OS detail. Generated failure
messages must therefore stay free of endpoints and of Session content — the reporting port's own
contract already carries nothing but release and environment.

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
