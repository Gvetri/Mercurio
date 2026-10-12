# Hermes Native Client user guide

Hermes Native Client is an independent Android application. It is a native shell for connecting to
one pre-existing, compatible Hermes Gateway that you already operate. It does not embed a local
Hermes runtime, an agent execution engine, a desktop web interface, an Electron application, or a
WebView wrapper.

## What you need before installing

- An Android phone or tablet running Android 8.0 or later. The application declares `minSdk 24`,
  and both phones and tablets are supported.
- An ARM64 device. The normal device APK target is `arm64-v8a`; the x86_64 architecture exists only
  for emulator and continuous-integration validation and is not the published device artifact.
- One pre-existing compatible Hermes Gateway. The client requires a Gateway that already serves the
  client contract; it cannot add a missing Gateway feature.
- One profile-specific HTTPS endpoint for that Gateway.
- A bearer Gateway Credential that you supply. The client never issues, derives, or discovers one.

If the Gateway answers without the required capability manifest, the client reports **Required
feature unavailable. This Gateway does not support the client contract.**

## Download and install a signed APK

Stable releases are published as GitHub **prereleases** named **Stable Public Beta vX.Y.Z**. To
install one:

1. Open the repository's [releases page](https://github.com/Gvetri/hermes-native-client/releases).
2. Pick the newest release named **Stable Public Beta vX.Y.Z**. A release named **Nightly** is a
   development snapshot, not a stable release.
3. Download `hermes-native-client.apk` and `SHA256SUMS` from that release.
4. Verify the APK before installing it:

   ```text
   sha256sum --check SHA256SUMS
   apksigner verify --print-certs hermes-native-client.apk
   ```

   The reported signer certificate digest must equal the digest published in the release notes and
   in `.github/release-certificate.sha256`. Never install an APK whose checksum or certificate does
   not match.
5. Copy the APK to the device and open it. Android asks you to allow installing from this source;
   confirm it, then finish the installation.

The application is not distributed through an application store. The published artifact is the
signed APK attached to the release; a locally built unsigned APK is not a distribution artifact.

## Add one Gateway Connection

The connection form asks for exactly two values:

- **Gateway HTTPS endpoint** — the profile-specific HTTPS endpoint of your Gateway. The field shows
  `https://gateway.example` as an illustration only; it is not a working address. The client rejects
  a value that is not a single HTTPS address.
- **Bearer credential** — the bearer Gateway Credential you were given for that endpoint.

Press **Verify Gateway Connection**. The client verifies authentication and the required capability
manifest first. While it works, it shows **Verifying Gateway connection…**. The credential is
saved securely on this device after verification: it is encrypted with an AndroidKeyStore key and
stored in application-private storage that is excluded from Android backup, so you can reopen the
connection without retyping the credential, and **Change credential** replaces it. The client shows
**Gateway verified.** / **Saving credential securely…** while the secure save finishes and then
reports **Connected** / **Gateway connected**. If the secure save fails, it keeps your inputs,
reports **Could not save the Gateway credential securely. Try again.** and offers **Try again**,
which verifies and saves the connection again. The credential is never stored in plain text.

**Remove Gateway Connection** removes the saved endpoint and credential from this device. It is a
local removal only: the client does not delete or cancel remote Sessions and Runs.

## What the client does not do

- It does not create, host, configure networking for, or publish a Gateway.
- It does not run Hermes on your device, and it does not replace your Gateway.
- It does not discover Gateway profiles or choose a provider, a model, or a credential for you.
- It does not persist conversation content as an offline history.

## Supported core flow

**Sessions.** After connecting, the client shows the Gateway-derived Session list. You can search the
loaded titles and previews, load further pages server-side, and create, rename, pin, unpin, and
delete Sessions. Creating a Session takes an optional title; leaving it blank produces an
**Untitled Session**. Deleting a Session asks for confirmation and states that remote deletion
cannot be undone by this client.

**Message and Run.** Open a Session to read its Gateway history, type into the **Message** composer,
and press **Send**. Sending creates a Hermes Run on the Gateway. **Send** is disabled while a
submission or an active Run is in progress, and drafts stay in memory for the open Session. A failed
submission preserves your draft so you can **Try again**.

**Observation.** The client reports the Run state (**Queued**, **Running**, **Completed**,
**Failed**, **Cancelled**) and streams the response as it arrives. An interrupted stream reports
**Stream interrupted. Run result is uncertain.** rather than inventing a result.

**Recovery and retry.** A failed Run keeps your conversation and offers recovery
(**This Run failed. Your conversation is preserved.**). When the reported state is uncertain, use
**Refresh history to resolve** to read the authoritative Gateway state. A retry is always an explicit
action you take; the client never retries silently, and it refuses a response that reuses a failed
Run identity (**Retry response reused the failed Run identity.**).

**Notifications (optional).** **Run status notifications** report terminal Run status only. If
Android has blocked them, the client states that notifications are blocked and points you to the
Android settings.

## Privacy boundary

- There is no network analytics, no product telemetry, and no third-party crash reporting.
- Conversation content is not persisted on the device as an offline history. The open Session's
  content is held for display and re-read from the Gateway.
- A Run status notification carries terminal status only; it does not carry conversation content.
- A verified credential is authenticated-encrypted with an AndroidKeyStore key in
  application-private, backup-excluded storage. It is never written in plain text, and
  **Remove Gateway Connection** deletes the saved endpoint and credential from this device.

## Local diagnostics

**Local diagnostics** is a private, on-device buffer of redacted operational metadata only: it holds
no credentials, endpoints, prompts, responses, or identifiers. It records that a connection
verification, a Session-list load, or a Run submission succeeded, failed, or was uncertain.

- **Export diagnostics** writes a snapshot and offers it through the platform Sharesheet. It does
  not clear the buffer, and it stays disabled while the buffer is empty, so no empty or placeholder
  snapshot is produced.
- **Clear diagnostics** discards the retained records after an explicit confirmation. Exported
  copies are not affected.
- Nothing is uploaded automatically. There is no automatic upload, no background transfer, and no
  remote diagnostics endpoint.

## Nightly and Stable Public Beta releases

- A **Nightly** is a development snapshot for testing. It can contain regressions; it is not a
  stable-support promise.
- A **Stable Public Beta** is a human-approved promotion of one existing signed Nightly. It has
  community support only: there is no uptime or response-time commitment, no service-level agreement
  (SLA), and no compatibility promise beyond the compatibility statement published in that release's
  notes.
- Both are GitHub prereleases, and neither implies support beyond the published statement.

## Troubleshooting

| What the client shows | What it means | What to do |
| --- | --- | --- |
| **Invalid Gateway address. Enter one HTTPS Gateway endpoint.** | The endpoint is not a single valid HTTPS address. | Correct the endpoint. Only an `https://` address is accepted. |
| **Secure connection failed. Check the Gateway certificate and hostname.** | The TLS connection could not be trusted, or the hostname does not match the certificate. | Use the certificate the Gateway actually serves, and check that the endpoint host matches it. |
| **Authentication failed. Check the Gateway credential.** | The Gateway rejected the bearer credential. | Retype the credential carefully; if it was saved, use **Change credential** to replace it. |
| **Required feature unavailable. This Gateway does not support the client contract.** | The Gateway does not expose the capability manifest the client requires. | Connect to a compatible Gateway. This client cannot add a Gateway feature. |
| **Gateway request failed. Try again.** | A request failed after the connection was established. | Retry the action. Use **Refresh** to reload the Session list, and verify the Gateway if it persists. |

## Tested revision and compatibility

Hermes Native Client is validated against the immutable Hermes revision
`d9833c5615b80e199a174cd67d90ab430695a972`, the repository's pinned fixture provenance. Each
Stable Public Beta release states its own compatibility limits in its release notes, and the client
claims no compatibility beyond that published statement. The artifact package, the published
verification, and the per-release compatibility statement are recorded in
[Release artifacts](release-artifacts.md).
