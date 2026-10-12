# Stable Public Beta release artifacts

A **Stable Public Beta** release is a published GitHub prerelease that promotes one existing signed
Nightly. The release process is described in [the milestone-based Stable Public Beta
process](stable-public-beta.md); this document describes what the published package contains, how to
verify it, and what must be checked before the release is announced.

## What the published release contains

- A prerelease named **Stable Public Beta vX.Y.Z** on the tag `stable-beta-vX.Y.Z`, whose target is
  the exact source commit. The tag identifies the same commit the selected signed Nightly was built
  from.
- Release notes that state the released **Semantic Version**, the source commit, the **Milestone**
  association, the **APK SHA-256** checksum, the Android version code and version name, the signing
  certificate SHA-256, the release's compatibility statement, the deterministic validation and signed
  Nightly run links, the exact-APK Local Live Smoke result with the hash of the reviewed redacted
  evidence, the public user changes, and the community-support-only policy.
- Exactly three assets:
  - `hermes-native-client.apk` — the signed ARM64 device APK. The bytes are the ones the protected
    signer produced for the selected Nightly; the Android version name and version code embedded in
    the APK are retained. The stable Semantic Version is release metadata and a tag, not a modified
    or re-signed artifact.
  - `SHA256SUMS` — a single line, `<apk-sha256>  hermes-native-client.apk`.
  - `signing-metadata.json` — the source commit, the deterministic validation run identity and
    attempt, the signing run identity and attempt, the Android version code and version name, the
    actual native ABI list, the APK SHA-256, and the signing certificate SHA-256.

The release notes and assets never contain raw smoke logs, video, local filesystem paths, device or
provider identities, Gateway Session or Run identifiers, credentials, or arbitrary commit bodies.
The reviewed redacted smoke evidence is source-controlled in this repository; it is not a release
asset.

A **Nightly** release carries the same three asset names but is a development snapshot: it is
unattended, it is not human-approved, and it makes no compatibility or support statement. Nightly
release artifacts are documented in [Development Nightly releases](nightly-releases.md).

## Tested Hermes revision and compatibility

The published compatibility statement of a release is the one recorded in that release's notes. The
client is validated against the repository's pinned, immutable Hermes revision
`d9833c5615b80e199a174cd67d90ab430695a972`, whose descriptor is
[`fixtures/hermes/pinned-fixture.properties`](../fixtures/hermes/pinned-fixture.properties). See
[Deterministic Hermes fixture](deterministic-fixture.md) for the pinned provenance and the
compatibility-change rules. A mutable reference such as `main` or `latest` is never release
evidence.

## Verify a downloaded release

Download the assets and check them with Android Build Tools 35.0.0:

```text
sha256sum --check SHA256SUMS
apksigner verify --print-certs hermes-native-client.apk
aapt2 dump badging hermes-native-client.apk
```

Compare the reported certificate digest with the release notes and with the pinned
`.github/release-certificate.sha256`. Compare the APK's Android version with the release notes and
`signing-metadata.json`, and confirm the tag's source commit matches the notes.

## Verify the published release before announcing it

Announcement requires verifying the **published** release, not the promotion job's own read-back: the
download links a user will follow must resolve and the published checksum instructions must
reproduce. Run the operator check against the release tag:

```text
GH_TOKEN=<token> python3 .github/scripts/verify-release-artifacts.py stable-beta-vX.Y.Z
```

It requires JDK 21, Android SDK Platform 35, and Build Tools 35.0.0, with `JAVA_HOME` and
`ANDROID_HOME` set, because it reads the APK's signature and version back with `apksigner` and
`aapt2`.

The check reads the release back and fails closed on any of the following:

- the release is a draft, is not a prerelease, or is not named **Stable Public Beta vX.Y.Z** for its
  tag;
- the tag does not target a full source commit, or the notes omit the Semantic Version, source
  commit, milestone, APK SHA-256, compatibility statement, or the community-support-only policy;
- the asset set is not exactly `hermes-native-client.apk`, `SHA256SUMS`, and
  `signing-metadata.json`, an asset is not uploaded, or an asset download link does not belong to
  this repository's release tag;
- `SHA256SUMS` does not state the SHA-256 of the downloaded `hermes-native-client.apk`;
- `signing-metadata.json` does not bind the source commit, the APK checksum, and the pinned signing
  certificate, or its reported Android version and native ABI list disagree with the APK;
- the downloaded APK is not signed by the pinned certificate identity, or contains a native
  architecture other than ARM64;
- the notes' APK SHA-256 does not equal the bytes the user actually receives.

Only after the check passes is the release announced. A failure is not repaired by editing the
release: inspect the published release, and treat a mismatch as a release incident. The check uses
the GitHub API and the `gh` CLI, downloads only the three declared assets, and prints no secret.

## Verification of this document's own checks

The documentation that users read is itself checked, and the artifact check above has deterministic
coverage:

```text
python3 .github/scripts/verify-public-documentation.py
python3 .github/scripts/tests/test_public_documentation.py
python3 .github/scripts/tests/test_release_artifacts.py
```

The `conformance` quality check runs the documentation check, so a broken link, a credential or a
real-looking endpoint in an example, a private infrastructure reference, or an instruction outside
the supported product scope fails the gate.
