#!/usr/bin/env python3
import argparse
import json
import os
import re
from pathlib import Path
import runpy
import sys
import urllib.error
import urllib.request

NIGHTLY = runpy.run_path(str(Path(__file__).with_name("nightly-release.py")))
api_get = NIGHTLY["api_get"]
require = NIGHTLY["require"]
ReleaseError = NIGHTLY["ReleaseError"]


def optional_get(path):
    try:
        response = api_get(path)
        require(isinstance(response, dict), "Malformed recovery evidence")
        return response
    except ReleaseError as error:
        if isinstance(error.__cause__, urllib.error.HTTPError) and error.__cause__.code == 404:
            return None
        raise


def snapshot(identity, tag, phase):
    state = {}
    errors = []
    for key, path in (("release", f"releases/{identity}"), ("tag", f"git/ref/tags/{tag}")):
        try:
            state[key] = optional_get(path)
        except ReleaseError as error:
            state[key] = {"state": "unknown"}
            errors.append(error)
    print(json.dumps({"phase": phase, **state}, sort_keys=True), flush=True)
    require(not errors, "Required GitHub evidence is unavailable; inspect retained state")
    return state


def delete(path):
    base = os.environ.get("GITHUB_API_URL", "https://api.github.com").rstrip("/")
    request = urllib.request.Request(f"{base}/repos/{NIGHTLY['REPOSITORY']}/{path}", method="DELETE", headers={
        "Accept": "application/vnd.github+json", "X-GitHub-Api-Version": "2022-11-28",
        "Authorization": f"Bearer {os.environ['GITHUB_TOKEN']}",
    })
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            require(response.status == 204, "Unexpected deletion response; inspect retained state")
    except (urllib.error.URLError, TimeoutError) as error:
        raise ReleaseError("Deletion failed; inspect retained state, do not retry automatically") from error


def clear(identity, tag, source):
    try:
        before = snapshot(identity, tag, "before")
        release = before["release"]
        require(release is not None, "Draft does not exist or is not visible to this credential")
        require("published_at" in release, "Unknown publication state")
        require(release.get("published_at") is None, "Published release requires inspection")
        require(release.get("draft") is True, "Release is not a draft")
        require(type(release.get("id")) is int and release["id"] == int(identity), "Release id mismatch")
        require(release.get("tag_name") == tag, "Release tag mismatch")
        require(release.get("target_commitish") == source, "Release source mismatch")
        require(release.get("name") == "Nightly" and release.get("prerelease") is True,
                "Release is not a Nightly prerelease")
        assets = NIGHTLY["pages"](f"releases/{identity}/assets")
        require(assets == release.get("assets"), "Asset inventory changed or is incomplete")
        names = [asset["name"] for asset in assets]
        require(len(names) == len(set(names)) and set(names) <= set(NIGHTLY["ASSETS"]),
                "Foreign or duplicate assets require inspection")
        require(set(names) < set(NIGHTLY["ASSETS"]), "Complete-looking draft requires inspection")
        code = NIGHTLY["TAG"].fullmatch(tag)[1]
        certificate = (NIGHTLY["POLICY"]["ROOT"] / ".github/release-certificate.sha256").read_text().strip()
        prefix = (
            "Development snapshot for testing. It may contain regressions and is not a stable-support promise.\n\n"
            "This is a **Nightly** prerelease, not a Stable Public Beta release.\n\n"
            f"- Source commit: `{source}`\n- Android version code: `{code}`\n"
            f"- Android version name: `nightly-{code}-{source[:12]}`\n- Device architecture: ARM64\n"
        )
        body = release.get("body")
        notes = re.fullmatch(
            re.escape(prefix) + r"- APK SHA-256: `([0-9a-f]{64})`\n"
            + re.escape(f"- Signing certificate SHA-256: `{certificate}`\n"
                        f"- Deterministic validation: https://github.com/{NIGHTLY['REPOSITORY']}/actions/runs/")
            + r"[1-9][0-9]* \(attempt [1-9][0-9]*\)\n",
            body if isinstance(body, str) else "",
        )
        require(re.fullmatch(r"[0-9a-f]{64}", certificate) and notes is not None,
                "Publisher notes are missing, malformed, or contradict the Nightly identity")
        for asset in assets:
            uploader = asset.get("uploader")
            require(isinstance(uploader, dict) and uploader.get("login") == "github-actions[bot]"
                    and type(uploader.get("id")) is int and uploader["id"] == 41898282,
                    "Asset uploader is missing, unreadable, or not the release bot")
            if asset["name"] == "hermes-native-client.apk":
                require(asset.get("digest") == f"sha256:{notes[1]}",
                        "APK digest is missing or disagrees with publisher notes")
        reference = before["tag"]
        if reference is not None:
            require(reference.get("ref") == f"refs/tags/{tag}", "Tag reference mismatch")
            require(reference.get("object", {}).get("type") == "commit"
                    and reference["object"].get("sha") == source, "Tag source mismatch")
        owners = [item for item in NIGHTLY["pages"]("releases") if item.get("tag_name") == tag]
        require(len(owners) == 1 and owners[0].get("id") == int(identity), "Tag ownership is ambiguous")
        require(api_get(f"releases/{identity}") == release
                and optional_get(f"git/ref/tags/{tag}") == reference, "Recovery state changed before deletion")
        delete(f"releases/{identity}")
        require(optional_get(f"releases/{identity}") is None, "Draft deletion read-back failed")
        require(not any(item.get("tag_name") == tag for item in NIGHTLY["pages"]("releases")),
                "Tag is not orphaned; inspect retained state")
        require(optional_get(f"git/ref/tags/{tag}") == reference, "Tag changed after draft deletion")
        if reference is not None:
            delete(f"git/refs/tags/{tag}")
    finally:
        after = snapshot(identity, tag, "after")
    require(after == {"release": None, "tag": None}, "Deletion read-back failed; inspect retained state")


def main():
    parser = argparse.ArgumentParser(description="Deliberately clear one issue-named incomplete Nightly draft, never from CI.")
    parser.add_argument("--release-id", required=True)
    parser.add_argument("--tag", required=True)
    parser.add_argument("--source-sha", required=True)
    arguments = parser.parse_args()
    try:
        require(not os.environ.get("GITHUB_ACTIONS") and not os.environ.get("GITHUB_EVENT_NAME"),
                "Local recovery only; workflows may not clear release state")
        require(re.fullmatch(r"[1-9][0-9]*", arguments.release_id), "Invalid release id")
        require(re.fullmatch(r"[0-9a-f]{40}", arguments.source_sha), "Invalid source SHA")
        match = NIGHTLY["TAG"].fullmatch(arguments.tag)
        require(match is not None, "Invalid Nightly tag")
        require(match[2] == arguments.source_sha, "Named tag and source disagree")
        require(bool(os.environ.get("GITHUB_TOKEN")), "Missing GITHUB_TOKEN")
        clear(arguments.release_id, arguments.tag, arguments.source_sha)
        return 0
    except (ReleaseError, KeyError, TypeError, ValueError, AttributeError, OSError) as error:
        message = str(error) if isinstance(error, ReleaseError) else "Missing or malformed recovery evidence"
        print(f"Nightly recovery refused: {message}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
