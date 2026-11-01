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
RELEASE_WORKFLOW = ROOT / ".github/workflows/release-signing.yml"
PUBLIC_KEY = "0123456789abcdef0123456789abcdef"
PROJECT = "4512353932279888"
DSN = f"https://{PUBLIC_KEY}@o451235.ingest.de.sentry.io/{PROJECT}"


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

    def check(self, dsn: str | None = DSN):
        environment = {name: value for name, value in os.environ.items() if name != "SENTRY_DSN"}
        if dsn is not None:
            environment["SENTRY_DSN"] = dsn
        return subprocess.run(
            [sys.executable, str(CHECK), str(self.apk)],
            env=environment, capture_output=True, text=True, timeout=10,
        )

    def test_an_artifact_carrying_the_configured_dsn_passes(self):
        self.write_apk(DSN.encode() + b"\x00")
        result = self.check()
        self.assertEqual(result.returncode, 0, result.stderr)
        self.assertIn(PROJECT, result.stdout)

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
        self.write_apk(DSN.replace(f"/{PROJECT}", "/4500000000000000").encode() + b"\x00")
        result = self.check()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("does not carry", result.stderr)

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
        self.assertIn("not a readable APK archive", result.stderr)


class ReleaseWorkflowWiringTest(unittest.TestCase):
    def setUp(self):
        self.workflow = RELEASE_WORKFLOW.read_text()

    def test_the_build_supplies_the_repository_variable_exactly_once(self):
        self.assertEqual(self.workflow.count("SENTRY_DSN: ${{ vars.MERCURIO_SENTRY_DSN }}"), 1)
        self.assertEqual(self.workflow.count('-Pmercurio.sentryDsn="$SENTRY_DSN"'), 1)

    def test_the_artifact_is_verified_before_it_is_checksummed_or_signed(self):
        verification = self.workflow.index("verify-release-dsn.py")
        self.assertLess(verification, self.workflow.index("Bind the unsigned APK checksum"))
        self.assertLess(verification, self.workflow.index("Sign and verify the pinned release identity"))

    def test_no_dsn_value_is_committed_to_the_workflow(self):
        self.assertNotRegex(self.workflow, r"https?://[0-9A-Za-z_]+@")


if __name__ == "__main__":
    unittest.main()
