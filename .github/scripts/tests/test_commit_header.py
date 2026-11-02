"""Deterministic tests for the commit-header rule and its command line."""

import pathlib
import subprocess
import sys
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[3]
SCRIPT = ROOT / ".github/scripts/commit_header.py"
sys.path.insert(0, str(SCRIPT.parent))

from commit_header import MessageError, validate_message


class CommitHeaderRuleTest(unittest.TestCase):
    def test_a_declared_type_with_a_scope_passes(self):
        self.assertEqual([], validate_message("feat(entry): render the stored state", "The commit"))

    def test_the_scope_and_the_breaking_marker_are_optional(self):
        self.assertEqual([], validate_message("fix: render the stored state", "The commit"))
        self.assertEqual([], validate_message("feat(api)!: drop the legacy field", "The commit"))

    def test_a_merge_subject_passes_untouched(self):
        self.assertEqual([], validate_message("Merge branch 'main' into feat/25", "The commit"))
        self.assertEqual([], validate_message("Revert \"Merge branch 'main'\"", "The commit"))

    def test_an_undeclared_type_is_rejected(self):
        violations = validate_message("feature: render the state", "The commit")
        self.assertEqual(1, len(violations))
        self.assertIn("'feature' is not a declared commit type", violations[0])

    def test_an_upper_case_scope_is_rejected(self):
        violations = validate_message("fix(CI): pin the group", "The commit")
        self.assertIn("the scope 'CI' must be lower case", violations[0])

    def test_a_trailing_period_is_rejected(self):
        violations = validate_message("fix: pin the group.", "The commit")
        self.assertIn("must not end with a period", violations[0])

    def test_a_subject_longer_than_the_bound_is_rejected(self):
        violations = validate_message(f"fix: {'a' * 120}", "The commit")
        self.assertIn("characters; keep it within 100", violations[0])

    def test_a_missing_description_is_rejected(self):
        violations = validate_message("fix(ci): ", "The commit")
        self.assertIn("is not a Conventional Commit", violations[0])

    def test_a_comment_only_message_cannot_be_judged(self):
        with self.assertRaises(MessageError):
            validate_message("# Please enter the commit message\n#\n", "The commit")

    def test_the_leading_git_comment_block_is_skipped(self):
        self.assertEqual([], validate_message("# On branch main\n\nfeat: render the state\n", "The commit"))


class CommitHeaderCommandLineTest(unittest.TestCase):
    def run_command(self, content):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        message_file = pathlib.Path(temporary.name) / "COMMIT_EDITMSG"
        message_file.write_text(content, encoding="utf-8")
        return subprocess.run(
            [sys.executable, str(SCRIPT), str(message_file)],
            capture_output=True,
            text=True,
            timeout=60,
        )

    def test_an_accepted_message_passes(self):
        completed = self.run_command("docs: record the wiring command\n")
        self.assertEqual(0, completed.returncode, completed.stderr)
        self.assertIn("Commit-message verification passed.", completed.stdout)

    def test_a_rejected_message_fails_with_the_violation(self):
        completed = self.run_command("feature: record the wiring command\n")
        self.assertEqual(1, completed.returncode)
        self.assertIn("'feature' is not a declared commit type", completed.stderr)
        self.assertIn("--no-verify", completed.stderr)

    def test_an_unreadable_message_file_fails_closed(self):
        completed = subprocess.run(
            [sys.executable, str(SCRIPT), str(ROOT / "docs/missing-message-file")],
            capture_output=True,
            text=True,
            timeout=60,
        )
        self.assertEqual(1, completed.returncode)
        self.assertIn("failed closed", completed.stderr)


if __name__ == "__main__":
    unittest.main()
