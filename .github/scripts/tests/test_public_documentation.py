"""Deterministic tests for the published-documentation check."""

import os
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile
import unittest

ROOT = Path(__file__).resolve().parents[3]
SCRIPT = ROOT / ".github/scripts/verify-public-documentation.py"
README = Path("README.md")
USER_GUIDE = Path("docs/user-guide.md")
RELEASE_ARTIFACTS = Path("docs/release-artifacts.md")
PINNED_FIXTURE = Path("fixtures/mercurio/pinned-fixture.properties")
DIAGNOSTICS_SURFACE = Path(
    "feature/entry/presentation/src/main/kotlin/org/mercurio/client/feature/entry/presentation"
    "/LocalDiagnosticsScreen.kt"
)
IGNORED = shutil.ignore_patterns(".git", "build", ".gradle", ".kotlin", "__pycache__")


def run_check(root=None):
    command = ["python3", str(SCRIPT)]
    if root is not None:
        command.extend(["--root", str(root)])
    return subprocess.run(command, cwd=ROOT, capture_output=True, text=True, check=False,
                          env=dict(os.environ))


class PublishedDocumentationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.pristine = Path(tempfile.mkdtemp(prefix="published-documentation-"))
        cls.addClassCleanup(shutil.rmtree, cls.pristine, True)
        shutil.copytree(ROOT, cls.pristine, ignore=IGNORED, dirs_exist_ok=True)

    def setUp(self):
        self.temporary = Path(tempfile.mkdtemp(prefix="published-documentation-case-"))
        self.addCleanup(shutil.rmtree, self.temporary, True)
        self.root = self.temporary / "repository"
        shutil.copytree(self.pristine, self.root, ignore=IGNORED)

    def document(self, relative):
        return self.root / relative

    def replace_once(self, relative, old, new):
        path = self.document(relative)
        text = path.read_text(encoding="utf-8")
        self.assertIn(old, text, f"{relative} must contain {old!r} for this case")
        path.write_text(text.replace(old, new, 1), encoding="utf-8")

    def append(self, relative, text):
        path = self.document(relative)
        path.write_text(path.read_text(encoding="utf-8") + text, encoding="utf-8")

    def assert_rejected(self, completed, message):
        self.assertNotEqual(0, completed.returncode, completed.stdout)
        self.assertIn(message, completed.stderr)

    def test_the_repository_documentation_passes(self):
        completed = run_check()
        self.assertEqual(0, completed.returncode, completed.stderr)
        self.assertIn("Verified", completed.stdout)
        self.assertIn("published document(s)", completed.stdout)

    def test_the_documentation_copy_passes_before_a_mutation(self):
        completed = run_check(self.root)
        self.assertEqual(0, completed.returncode, completed.stderr)

    def test_a_broken_relative_link_is_rejected(self):
        self.append(USER_GUIDE, "\nSee [the missing page](missing-page.md).\n")
        self.assert_rejected(run_check(self.root), "links to a missing target: missing-page.md")

    def test_a_broken_in_page_anchor_is_rejected(self):
        self.append(USER_GUIDE, "\nSee [a missing section](#no-such-section).\n")
        self.assert_rejected(run_check(self.root), "links to a missing anchor: #no-such-section")

    def test_a_link_to_an_existing_anchor_is_accepted(self):
        self.append(USER_GUIDE, "\nSee [the privacy boundary](#privacy-boundary).\n")
        completed = run_check(self.root)
        self.assertEqual(0, completed.returncode, completed.stderr)

    def test_a_private_filesystem_path_is_rejected(self):
        self.append(RELEASE_ARTIFACTS, "\nEvidence lives at /home/example/evidence.json.\n")
        self.assert_rejected(run_check(self.root), "contains a private filesystem path")

    def test_a_credential_value_is_rejected(self):
        self.append(README, "\nSet OPENCODE_GO_API_KEY=sk-abcdefghijklmnop to reproduce it.\n")
        self.assert_rejected(run_check(self.root), "contains a credential value")

    def test_a_real_looking_endpoint_example_is_rejected(self):
        self.append(USER_GUIDE, "\nFor example, connect to https://gateway.internal.example.corp today.\n")
        self.assert_rejected(run_check(self.root), "uses a real-looking endpoint example")

    def test_an_out_of_scope_instruction_is_rejected(self):
        self.append(USER_GUIDE, "\nThen run docker run hermes/gateway to start the Gateway.\n")
        self.assert_rejected(run_check(self.root), "outside the supported product scope: 'docker run'")

    def test_an_internal_smoke_parameter_is_rejected(self):
        self.append(RELEASE_ARTIFACTS, "\nPass --device-serial to the operator tool.\n")
        self.assert_rejected(run_check(self.root), "a local smoke or operator identity parameter")

    def test_a_missing_required_statement_is_rejected(self):
        self.replace_once(USER_GUIDE, "arm64-v8a", "arm64")
        self.assert_rejected(run_check(self.root), "must state: arm64-v8a")

    def test_a_feature_outside_the_release_scope_is_not_required_in_the_guide(self):
        text = self.document(USER_GUIDE).read_text(encoding="utf-8")
        for statement in ("Local diagnostics", "Export diagnostics", "Clear diagnostics"):
            text = text.replace(statement, "diagnostics")
        self.document(USER_GUIDE).write_text(text, encoding="utf-8")
        self.document(DIAGNOSTICS_SURFACE).unlink()
        completed = run_check(self.root)
        self.assertEqual(0, completed.returncode, completed.stderr)

    def test_an_in_scope_local_diagnostics_surface_must_be_documented(self):
        self.replace_once(USER_GUIDE, "Export diagnostics", "diagnostics")
        self.assert_rejected(run_check(self.root), "must state: Export diagnostics")

    def test_a_stale_tested_revision_is_rejected(self):
        revision = None
        for line in self.document(PINNED_FIXTURE).read_text(encoding="utf-8").splitlines():
            if line.startswith("hermes_revision="):
                revision = line.split("=", 1)[1]
        self.assertIsNotNone(revision)
        self.replace_once(PINNED_FIXTURE, f"hermes_revision={revision}", f"hermes_revision={'1' * 40}")
        self.assert_rejected(run_check(self.root), "must identify the tested Hermes revision")

    def test_a_dropped_verification_procedure_is_rejected(self):
        self.replace_once(
            RELEASE_ARTIFACTS,
            ".github/scripts/verify-release-artifacts.py",
            ".github/scripts/verify-something-else.py",
        )
        self.assert_rejected(
            run_check(self.root),
            "must state: .github/scripts/verify-release-artifacts.py",
        )

    def test_an_unlinked_published_document_is_rejected(self):
        text = self.document(README).read_text(encoding="utf-8")
        self.document(README).write_text(text.replace("docs/user-guide.md", "docs/elsewhere.md"), encoding="utf-8")
        self.assert_rejected(run_check(self.root), "must link the published document docs/user-guide.md")

    def test_a_missing_published_document_fails_closed(self):
        self.document(RELEASE_ARTIFACTS).unlink()
        self.assert_rejected(run_check(self.root), "failed closed")

    def test_a_directory_that_is_not_a_checkout_fails_closed(self):
        shutil.rmtree(self.root)
        self.root.mkdir()
        self.assert_rejected(run_check(self.root), "failed closed")

    def test_an_unsupported_argument_fails_closed(self):
        completed = subprocess.run(["python3", str(SCRIPT), "--unknown"], cwd=ROOT, capture_output=True,
                                   text=True, check=False, env=dict(os.environ))
        self.assert_rejected(completed, "failed closed")


if __name__ == "__main__":
    unittest.main()
