#!/usr/bin/env python3
"""Verify a published Stable Public Beta release before it is announced.

Announcement requires the published release, not the promotion job's own read-back: the artifact links
a user will follow must resolve, the published checksum must describe the bytes they receive, and the
signature identity must match the pinned certificate. This check reads the release back, downloads
only the declared assets, and fails closed on any contradiction.
"""

import json
import os
from pathlib import Path
import re
import runpy
import subprocess
import sys
import tempfile

POLICY = runpy.run_path(str(Path(__file__).with_name("release-signing.py")))
NIGHTLY = runpy.run_path(str(Path(__file__).with_name("nightly-release.py")))
SIGNER = NIGHTLY["SIGNER"]
api_get = POLICY["api_get"]
require = POLICY["require"]
ReleaseError = POLICY["ReleaseError"]
REPOSITORY = POLICY["REPOSITORY"]

ROOT = Path(__file__).resolve().parents[2]
PINNED_CERTIFICATE = ROOT / ".github/release-certificate.sha256"
STABLE = re.compile(r"stable-beta-v((?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*))\Z")
SHA = re.compile(r"[0-9a-f]{40}\Z")
HEX256 = re.compile(r"[0-9a-f]{64}\Z")
NOTES_FIELDS = {
    "semantic version": re.compile(r"^- Semantic Version: `([^`]+)`$", re.MULTILINE),
    "source commit": re.compile(r"^- Source commit: `([0-9a-f]{40})`$", re.MULTILINE),
    "milestone": re.compile(r"^- Milestone: #([1-9][0-9]*)$", re.MULTILINE),
    "APK checksum": re.compile(r"^- APK SHA-256: `([0-9a-f]{64})`$", re.MULTILINE),
    "compatibility statement": re.compile(r"^- Compatibility: (\S.*)$", re.MULTILINE),
}
SIGNING_METADATA_FIELDS = {
    "source_sha", "validation_run_id", "validation_run_attempt", "signing_run_id",
    "signing_run_attempt", "version_code", "version_name", "native_abis", "apk_sha256",
    "certificate_sha256",
}


def digest(path):
    return SIGNER["digest"](path)


def pinned_certificate(path):
    require(path.is_file() and not path.is_symlink(), "Missing or unsafe pinned certificate")
    fingerprint = path.read_text().strip()
    require(HEX256.fullmatch(fingerprint) is not None, "Invalid pinned certificate identity")
    return fingerprint


def release_notes_fields(body, version):
    require(isinstance(body, str) and body, "Missing release notes")
    fields = {}
    for description, pattern in NOTES_FIELDS.items():
        match = pattern.search(body)
        require(match is not None, f"The release notes state no {description}")
        fields[description] = match.group(1).strip()
    require(fields["semantic version"] == version, "The release notes state another Semantic Version")
    require("Community support only" in body, "The release notes omit the community-support-only policy")
    require("not a release asset" in body, "The release notes claim the redacted smoke evidence is an asset")
    return fields


def published_assets(release, tag):
    assets = release.get("assets")
    require(type(assets) is list and len(assets) == len(NIGHTLY["ASSETS"])
            and {asset.get("name") for asset in assets} == set(NIGHTLY["ASSETS"])
            and len({asset.get("id") for asset in assets}) == len(assets),
            "The release does not publish exactly the three declared assets")
    for asset in assets:
        name = asset["name"]
        require(asset.get("state") == "uploaded", f"The release asset {name} was not uploaded")
        require(type(asset.get("id")) is int and asset["id"] > 0 and type(asset.get("size")) is int
                and asset["size"] > 0, f"The release asset {name} has no identity or size")
        expected = f"https://github.com/{REPOSITORY}/releases/download/{tag}/{name}"
        require(asset.get("browser_download_url") == expected,
                f"The release asset {name} does not publish the repository's download link")
        require(isinstance(asset.get("url"), str)
                and asset["url"].endswith(f"/releases/assets/{asset['id']}"),
                f"The release asset {name} has no API download link")
    return {asset["name"]: asset for asset in assets}


def download(tag, directory):
    NIGHTLY["gh_release"](["download", tag, "--dir", str(directory),
                           *(option for name in NIGHTLY["ASSETS"] for option in ("--pattern", name))])
    require({entry.name for entry in directory.iterdir()} == set(NIGHTLY["ASSETS"]),
            "The published download links did not deliver exactly the declared assets")


def verify_checksum_file(directory, apk_sha256, notes):
    require((directory / "SHA256SUMS").read_text() == f"{apk_sha256}  hermes-native-client.apk\n",
            "The published SHA256SUMS does not state the downloaded APK checksum")
    require(notes["APK checksum"] == apk_sha256,
            "The release notes state a checksum the published APK does not have")


def verify_signing_metadata(directory, source, apk_sha256, certificate):
    metadata = json.loads((directory / "signing-metadata.json").read_text())
    require(type(metadata) is dict and set(metadata) == SIGNING_METADATA_FIELDS,
            "The published signing metadata has unexpected fields")
    require(metadata["source_sha"] == source, "The published signing metadata names another source")
    require(metadata["apk_sha256"] == apk_sha256, "The published signing metadata names another APK")
    require(metadata["certificate_sha256"] == certificate,
            "The published signing metadata names another certificate")
    for key in ("validation_run_id", "validation_run_attempt", "signing_run_id", "signing_run_attempt"):
        require(type(metadata[key]) is int and metadata[key] > 0,
                "The published signing metadata names no validation or signing run")
    require(type(metadata["version_code"]) is int and metadata["version_code"] > 0
            and isinstance(metadata["version_name"], str) and metadata["version_name"],
            "The published signing metadata states no Android version")
    return metadata


def verify_apk(directory, metadata, certificate):
    tools = Path(os.environ["ANDROID_HOME"]) / "build-tools/35.0.0"
    environment = {name: os.environ[name] for name in ("PATH", "JAVA_HOME")}
    apk = directory / "hermes-native-client.apk"
    actual = SIGNER["apk_metadata"](apk, tools, environment)
    require(actual["version_code"] == metadata["version_code"]
            and actual["version_name"] == metadata["version_name"],
            "The published APK version does not match the published signing metadata")
    require(set(actual["native_abis"]) <= {"arm64-v8a"},
            "The published APK carries a native architecture other than ARM64")
    require(list(actual["native_abis"]) == list(metadata["native_abis"]),
            "The published signing metadata and APK disagree on the native architecture")
    verification = SIGNER["run_tool"]([str(tools / "apksigner"), "verify", "--print-certs", str(apk)],
                                      environment)
    signers = re.findall(r"^Signer #[0-9]+ certificate SHA-256 digest: ([0-9a-f]{64})$",
                         verification, re.MULTILINE)
    require(signers == [certificate],
            "The published APK is not signed by the pinned release certificate")


def verify(tag, certificate_path):
    match = STABLE.fullmatch(tag)
    require(match is not None, "Expected a stable-beta-vX.Y.Z release tag")
    version = match.group(1)
    certificate = pinned_certificate(certificate_path)
    release = api_get(f"releases/tags/{tag}")
    require(type(release) is dict and release.get("tag_name") == tag
            and release.get("draft") is False and release.get("prerelease") is True
            and release.get("name") == f"Stable Public Beta v{version}"
            and isinstance(release.get("html_url"), str),
            "The tag does not identify a published Stable Public Beta prerelease")
    source = release.get("target_commitish")
    require(isinstance(source, str) and SHA.fullmatch(source) is not None,
            "The published release does not target an exact source commit")
    notes = release_notes_fields(release.get("body"), version)
    require(notes["source commit"] == source, "The release notes name another source commit")
    assets = published_assets(release, tag)
    temporary = os.environ.get("RUNNER_TEMP") or tempfile.gettempdir()
    with tempfile.TemporaryDirectory(prefix="verify-release-", dir=temporary) as directory:
        downloaded = Path(directory)
        download(tag, downloaded)
        apk_sha256 = digest(downloaded / "hermes-native-client.apk")
        verify_checksum_file(downloaded, apk_sha256, notes)
        metadata = verify_signing_metadata(downloaded, source, apk_sha256, certificate)
        verify_apk(downloaded, metadata, certificate)
        for name, asset in assets.items():
            require(asset["size"] == (downloaded / name).stat().st_size,
                    f"The published size of {name} does not match the downloaded bytes")
    return release, version, source, apk_sha256


def main(arguments):
    try:
        require(len(arguments) in (1, 2), "Expected a release tag and an optional certificate file")
        token = os.environ.get("GITHUB_TOKEN") or os.environ.get("GH_TOKEN") or ""
        require(token != "", "Set GITHUB_TOKEN or GH_TOKEN to read the published release")
        os.environ["GITHUB_TOKEN"] = token
        require(os.environ.get("ANDROID_HOME", "") != "",
                "Set ANDROID_HOME to the Android SDK with Build Tools 35.0.0")
        require(os.environ.get("JAVA_HOME", "") != "", "Set JAVA_HOME to JDK 21")
        certificate = Path(arguments[1]) if len(arguments) == 2 else PINNED_CERTIFICATE
        release, version, source, apk_sha256 = verify(arguments[0], certificate)
        print(f"Stable Public Beta v{version} verified: {release['html_url']}")
        print(f"Source commit: {source}")
        print(f"APK SHA-256: {apk_sha256}")
        return 0
    except (ReleaseError, SIGNER["SigningError"], KeyError, TypeError, ValueError, AttributeError,
            OSError, IndexError, json.JSONDecodeError, subprocess.TimeoutExpired) as error:
        message = str(error) if isinstance(error, (ReleaseError, SIGNER["SigningError"])) \
            else "Missing or malformed release artifacts"
        print(f"Published release refused: {message}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
