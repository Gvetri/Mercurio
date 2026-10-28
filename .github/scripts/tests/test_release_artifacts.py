"""Deterministic tests for the published-release artifact check."""

import hashlib
import json
import os
from pathlib import Path
import runpy
import shutil
import subprocess
import tempfile
import unittest

from signing_fixture import make_signing_fixture
from github_api_fixture import GitHubApiFixture

ROOT = Path(__file__).resolve().parents[3]
SCRIPT = ROOT / ".github/scripts/verify-release-artifacts.py"
SIGNING_SCRIPT = ROOT / ".github/scripts/sign-release-apk.py"
REPOSITORY = runpy.run_path(str(ROOT / ".github/scripts/release-signing.py"))["REPOSITORY"]
TAG = "stable-beta-v0.1.0"
VERSION = "0.1.0"
VERSION_CODE = 7002
SOURCE = "b" * 40
ASSETS = ("mercurio.apk", "SHA256SUMS", "signing-metadata.json")


def sha256(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


class PublishedReleaseArtifactsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.build = Path(tempfile.mkdtemp(prefix="published-release-"))
        cls.addClassCleanup(shutil.rmtree, cls.build, True)
        fixture = make_signing_fixture(
            cls.build / "signing", VERSION_CODE, f"nightly-{VERSION_CODE}-{SOURCE[:12]}"
        )
        environment = {
            **fixture.environment, "RELEASE_SOURCE_SHA": SOURCE,
            "GITHUB_RUN_ID": "456", "GITHUB_RUN_ATTEMPT": "2",
            "RELEASE_VERSION_CODE": str(VERSION_CODE),
        }
        signed = cls.build / "signed"
        result = subprocess.run(
            ["python3", str(SIGNING_SCRIPT), str(fixture.apk), str(signed), str(fixture.certificate_file)],
            env=environment, capture_output=True, text=True, check=False,
        )
        if result.returncode:
            raise AssertionError(result.stderr)
        cls.certificate = fixture.certificate_file
        cls.signed = signed
        cls.metadata = json.loads((signed / "signing-metadata.json").read_text())

    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        self.home = self.root / "home"
        self.store = self.home / "release-fixture" / "assets"
        self.store.mkdir(parents=True)
        self.release_file = self.home / "release-fixture" / "release.json"
        for name in ASSETS:
            shutil.copyfile(self.signed / name, self.store / name)
        self.release_file.write_text(json.dumps({"assets": []}))
        self.release = self.published_release()

        self.routes = {f"/repos/{REPOSITORY}/releases/tags/{TAG}": self.release}
        self.api = GitHubApiFixture(self.routes)
        self.api.start()
        self.addCleanup(self.api.stop)

        binary = self.root / "bin"
        binary.mkdir()
        shutil.copyfile(ROOT / ".github/scripts/tests/fixtures/fake-gh-release.py", binary / "gh")
        (binary / "gh").chmod(0o755)
        self.environment = {
            "PATH": str(binary) + os.pathsep + os.environ["PATH"],
            "HOME": str(self.home), "TMPDIR": str(self.root), "LANG": "C.UTF-8",
            "JAVA_HOME": os.environ["JAVA_HOME"], "ANDROID_HOME": os.environ["ANDROID_HOME"],
            "RUNNER_TEMP": str(self.root), "GITHUB_TOKEN": "synthetic-token",
            "GITHUB_API_URL": self.api.url,
        }

    def notes(self):
        return (
            f"# Stable Public Beta v{VERSION}\n\n"
            "Community support only. No uptime or response-time commitment; compatibility is "
            "limited to the published statement.\n\n"
            f"- Semantic Version: `{VERSION}`\n"
            f"- Source commit: `{SOURCE}`\n"
            "- Milestone: #42\n"
            f"- APK SHA-256: `{self.metadata['apk_sha256']}`\n"
            f"- Android version code: `{VERSION_CODE}`\n"
            f"- Android version name: `{self.metadata['version_name']}` (retained from the signed Nightly)\n"
            f"- Signing certificate SHA-256: `{self.metadata['certificate_sha256']}`\n"
            "- Compatibility: Android 8.0 and newer on ARM64.\n"
            f"- Deterministic validation: https://github.com/{REPOSITORY}/actions/runs/123 (attempt 1)\n"
            f"- Signed Nightly run: https://github.com/{REPOSITORY}/actions/runs/456 (attempt 2)\n"
            "- Exact-APK Local Live Smoke: passed (reviewed evidence SHA-256 `"
            + "0" * 64 + "`; redacted source evidence is not a release asset)\n\n"
            "## User changes\n- Added session list updates.\n"
        )

    def published_release(self):
        assets = [
            {
                "id": index, "name": name, "size": (self.store / name).stat().st_size,
                "state": "uploaded",
                "url": f"https://api.github.com/repos/{REPOSITORY}/releases/assets/{index}",
                "browser_download_url": f"https://github.com/{REPOSITORY}/releases/download/{TAG}/{name}",
            }
            for index, name in enumerate(ASSETS, 1)
        ]
        return {
            "id": 1, "tag_name": TAG, "name": f"Stable Public Beta v{VERSION}",
            "target_commitish": SOURCE, "prerelease": True, "draft": False,
            "body": self.notes(), "assets": assets,
            "html_url": f"https://github.com/{REPOSITORY}/releases/tag/{TAG}",
        }

    def command(self, *arguments, environment=None):
        return subprocess.run(
            ["python3", str(SCRIPT), *arguments], cwd=ROOT,
            env=environment or self.environment, capture_output=True, text=True, check=False,
        )

    def refresh(self, **changes):
        self.release.update(changes)
        self.routes[f"/repos/{REPOSITORY}/releases/tags/{TAG}"] = self.release

    def assert_rejected(self, completed, message):
        self.assertNotEqual(0, completed.returncode, completed.stdout)
        self.assertIn(message, completed.stderr)

    def test_a_published_stable_release_is_verified(self):
        completed = self.command(TAG, str(self.certificate))
        self.assertEqual(0, completed.returncode, completed.stderr)
        self.assertIn(f"Stable Public Beta v{VERSION} verified", completed.stdout)
        self.assertIn(f"APK SHA-256: {self.metadata['apk_sha256']}", completed.stdout)

    def test_the_pinned_certificate_is_the_default_identity(self):
        completed = self.command(TAG)
        self.assertNotEqual(0, completed.returncode)
        self.assertIn("The published signing metadata names another certificate", completed.stderr)

    def test_a_draft_release_is_rejected(self):
        self.refresh(draft=True)
        self.assert_rejected(self.command(TAG, str(self.certificate)),
                             "does not identify a published Stable Public Beta prerelease")

    def test_a_release_that_is_not_a_prerelease_is_rejected(self):
        self.refresh(prerelease=False)
        self.assert_rejected(self.command(TAG, str(self.certificate)),
                             "does not identify a published Stable Public Beta prerelease")

    def test_a_release_named_for_another_version_is_rejected(self):
        self.refresh(name="Stable Public Beta v0.2.0")
        self.assert_rejected(self.command(TAG, str(self.certificate)),
                             "does not identify a published Stable Public Beta prerelease")

    def test_a_source_that_is_not_a_full_commit_is_rejected(self):
        self.refresh(target_commitish="main")
        self.assert_rejected(self.command(TAG, str(self.certificate)),
                             "does not target an exact source commit")

    def test_release_notes_without_a_required_field_are_rejected(self):
        self.refresh(body=self.notes().replace("- Compatibility: Android 8.0 and newer on ARM64.\n", ""))
        self.assert_rejected(self.command(TAG, str(self.certificate)),
                             "The release notes state no compatibility statement")

    def test_release_notes_without_the_community_policy_are_rejected(self):
        self.refresh(body=self.notes().replace("Community support only. ", ""))
        self.assert_rejected(self.command(TAG, str(self.certificate)),
                             "omit the community-support-only policy")

    def test_a_checksum_file_that_does_not_match_the_apk_is_rejected(self):
        (self.store / "SHA256SUMS").write_text(f"{'0' * 64}  mercurio.apk\n")
        self.assert_rejected(self.command(TAG, str(self.certificate)),
                             "does not state the downloaded APK checksum")

    def test_release_notes_naming_another_checksum_are_rejected(self):
        self.refresh(body=self.notes().replace(self.metadata["apk_sha256"], "a" * 64))
        self.assert_rejected(self.command(TAG, str(self.certificate)),
                             "state a checksum the published APK does not have")

    def test_signing_metadata_naming_another_apk_is_rejected(self):
        metadata = json.loads((self.store / "signing-metadata.json").read_text())
        metadata["apk_sha256"] = "f" * 64
        (self.store / "signing-metadata.json").write_text(json.dumps(metadata))
        self.assert_rejected(self.command(TAG, str(self.certificate)),
                             "The published signing metadata names another APK")

    def test_an_unexpected_signing_metadata_field_is_rejected(self):
        metadata = json.loads((self.store / "signing-metadata.json").read_text())
        metadata["video_path"] = "/private/video.mp4"
        (self.store / "signing-metadata.json").write_text(json.dumps(metadata))
        self.assert_rejected(self.command(TAG, str(self.certificate)),
                             "unexpected fields")

    def test_a_release_signed_by_another_certificate_is_rejected(self):
        other = self.root / "other-certificate.sha256"
        other.write_text("1" * 64 + "\n")
        self.assert_rejected(self.command(TAG, str(other)),
                             "The published signing metadata names another certificate")

    def test_an_invalid_certificate_file_fails_closed(self):
        other = self.root / "invalid-certificate.sha256"
        other.write_text("not-a-digest\n")
        self.assert_rejected(self.command(TAG, str(other)), "Invalid pinned certificate identity")

    def test_an_unexpected_asset_set_is_rejected(self):
        self.refresh(assets=self.release["assets"] + [{
            "id": 9, "name": "extra.txt", "size": 1, "state": "uploaded",
            "url": f"https://api.github.com/repos/{REPOSITORY}/releases/assets/9",
            "browser_download_url": f"https://github.com/{REPOSITORY}/releases/download/{TAG}/extra.txt",
        }])
        self.assert_rejected(self.command(TAG, str(self.certificate)),
                             "does not publish exactly the three declared assets")

    def test_an_asset_that_is_not_uploaded_is_rejected(self):
        self.release["assets"][0]["state"] = "new"
        self.assert_rejected(self.command(TAG, str(self.certificate)), "was not uploaded")

    def test_a_foreign_download_link_is_rejected(self):
        self.release["assets"][0]["browser_download_url"] = (
            f"https://github.com/{REPOSITORY}/releases/download/stable-beta-v9.9.9/{ASSETS[0]}"
        )
        self.assert_rejected(self.command(TAG, str(self.certificate)),
                             "does not publish the repository's download link")

    def test_a_published_size_that_does_not_match_the_bytes_is_rejected(self):
        self.release["assets"][0]["size"] = 1
        self.assert_rejected(self.command(TAG, str(self.certificate)),
                             "does not match the downloaded bytes")

    def test_an_unknown_release_tag_shape_fails_closed(self):
        self.assert_rejected(self.command("nightly-7002-" + SOURCE, str(self.certificate)),
                             "Expected a stable-beta-vX.Y.Z release tag")

    def test_an_unsupported_argument_shape_fails_closed(self):
        self.assert_rejected(self.command(TAG, str(self.certificate), "extra"),
                             "Expected a release tag and an optional certificate file")

    def test_a_missing_token_fails_closed(self):
        environment = {**self.environment}
        del environment["GITHUB_TOKEN"]
        self.assert_rejected(self.command(TAG, str(self.certificate), environment=environment),
                             "Set GITHUB_TOKEN or GH_TOKEN")

    def test_an_unavailable_release_fails_closed(self):
        del self.routes[f"/repos/{REPOSITORY}/releases/tags/{TAG}"]
        self.assert_rejected(self.command(TAG, str(self.certificate)), "Published release refused")


if __name__ == "__main__":
    unittest.main()
