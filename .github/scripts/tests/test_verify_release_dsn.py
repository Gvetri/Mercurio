"""Deterministic tests for the release DSN check."""
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import zipfile

ROOT = Path(__file__).resolve().parents[3]
CHECK = ROOT / ".github/scripts/verify-release-dsn.py"
DSN = "https://0123456789abcdef0123456789abcdef@o451235.ingest.de.sentry.io/4512353932279888"
TOKEN = "sntrys_eyJhbGciOiJIUzI1NiJ9_release_signing_placeholder"


class ReleaseDsnTest(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = Path(temporary.name)
        self.apk = self.root / "app-release-unsigned.apk"

    def write_apk(self, dex_payload):
        with zipfile.ZipFile(self.apk, "w") as archive:
            archive.writestr("AndroidManifest.xml", b"\x03\x00\x08\x00")
            archive.writestr("classes.dex", b"dex\n035\x00" + dex_payload)

    def check(self, dsn: str | None = DSN, token: str | None = None):
        environment = {
            name: value for name, value in os.environ.items()
            if name not in ("SENTRY_DSN", "SENTRY_AUTH_TOKEN")
        }
        if dsn is not None:
            environment["SENTRY_DSN"] = dsn
        if token is not None:
            environment["SENTRY_AUTH_TOKEN"] = token
        return subprocess.run(
            [sys.executable, str(CHECK), str(self.apk)],
            env=environment, capture_output=True, text=True, timeout=10,
        )

    def test_an_artifact_carrying_the_configured_dsn_passes(self):
        self.write_apk(DSN.encode() + b"\x00")
        result = self.check()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn("4512353932279888", result.stdout)

    def test_an_unset_dsn_fails(self):
        self.write_apk(DSN.encode() + b"\x00")
        result = self.check(dsn=None)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("SENTRY_DSN", result.stderr)

    def test_a_blank_dsn_fails(self):
        self.write_apk(DSN.encode() + b"\x00")
        result = self.check(dsn="  ")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("SENTRY_DSN", result.stderr)

    def test_a_malformed_dsn_fails(self):
        self.write_apk(b"not-a-dsn\x00")
        result = self.check(dsn="not-a-dsn")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Sentry DSN", result.stderr)

    def test_a_dsn_without_a_project_id_fails(self):
        self.write_apk(DSN.encode() + b"\x00")
        result = self.check(dsn=DSN.rsplit("/", 1)[0] + "/")
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("Sentry DSN", result.stderr)

    def test_an_artifact_that_does_not_carry_the_dsn_fails(self):
        self.write_apk(b"BuildConfig without the reporting value\x00")
        result = self.check()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("does not carry", result.stderr)

    def test_an_artifact_carrying_another_projects_dsn_fails(self):
        self.write_apk(DSN.replace("/4512353932279888", "/4500000000000000").encode() + b"\x00")
        result = self.check()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("does not carry", result.stderr)

    def test_an_artifact_carrying_the_auth_token_fails(self):
        self.write_apk(DSN.encode() + b"\x00" + TOKEN.encode() + b"\x00")
        result = self.check(token=TOKEN)
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("auth token", result.stderr)

    def test_an_artifact_without_a_dex_entry_fails(self):
        with zipfile.ZipFile(self.apk, "w") as archive:
            archive.writestr("AndroidManifest.xml", b"\x03\x00\x08\x00")
        result = self.check()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("dex", result.stderr)

    def test_a_missing_artifact_fails(self):
        result = self.check()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("artifact", result.stderr)

    def test_a_non_archive_artifact_fails(self):
        self.apk.write_bytes(b"not an apk")
        result = self.check()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("error:", result.stderr)


if __name__ == "__main__":
    unittest.main()
