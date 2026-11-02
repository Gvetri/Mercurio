"""Deterministic tests for the committed git hooks.

The formatter is the one part of a hook this suite cannot run: `ktlintFormat` needs a configured
Gradle build. The suite installs a `gradlew` that records the arguments it received and applies a
line-ending rule, so the hook's contract with the formatter - which files it names, and what happens
when the formatter refuses - is pinned without a Gradle run.
"""

import pathlib
import shutil
import subprocess
import tempfile
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[3]
HOOKS = ROOT / ".githooks"
COMMIT_HEADER = ROOT / ".github/scripts/commit_header.py"
COMMENTS = ROOT / ".github/scripts/verify-comments.py"

GRADLEW = """#!/usr/bin/env bash
set -euo pipefail
printf '%s\\n' "$@" >> gradle-arguments.txt
filter=''
for argument in "$@"; do
  case "$argument" in
    -PinternalKtlintGitFilter=*) filter="${argument#-PinternalKtlintGitFilter=}" ;;
  esac
done
status=0
while IFS= read -r path; do
  if [ -z "$path" ]; then
    continue
  fi
  printf '%s\\n' "$path" >> gradle-filtered-files.txt
  if grep -q UNFIXABLE "$path"; then
    printf 'the formatter cannot fix %s\\n' "$path" >&2
    status=1
  elif grep -q '[[:space:]]$' "$path"; then
    sed 's/[[:space:]]*$//' "$path" > "$path.formatted"
    mv "$path.formatted" "$path"
  fi
done <<< "$filter"
exit "$status"
"""


class HookTestCase(unittest.TestCase):
    def setUp(self):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        self.root = pathlib.Path(temporary.name)
        self.git("init", "-q", "-b", "main")
        self.git("config", "user.email", "hook@example.test")
        self.git("config", "user.name", "Hook test")
        self.git("config", "commit.gpgsign", "false")
        (self.root / ".github/scripts").mkdir(parents=True)
        (self.root / ".githooks").mkdir()
        shutil.copyfile(COMMIT_HEADER, self.root / ".github/scripts/commit_header.py")
        shutil.copyfile(COMMENTS, self.root / ".github/scripts/verify-comments.py")
        for name in ("commit-msg", "pre-commit"):
            target = self.root / ".githooks" / name
            shutil.copyfile(HOOKS / name, target)
            target.chmod(0o755)
        gradlew = self.root / "gradlew"
        gradlew.write_text(GRADLEW, encoding="utf-8")
        gradlew.chmod(0o755)

    def git(self, *arguments):
        return subprocess.run(
            ["git", *arguments],
            cwd=self.root,
            capture_output=True,
            text=True,
            timeout=120,
            check=False,
        )

    def write(self, relative, content):
        target = self.root / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(content, encoding="utf-8")
        return relative

    def stage(self, *relatives):
        completed = self.git("add", "--", *relatives)
        self.assertEqual(0, completed.returncode, completed.stderr)

    def run_hook(self, name, *arguments):
        return subprocess.run(
            [str(self.root / ".githooks" / name), *arguments],
            cwd=self.root,
            capture_output=True,
            text=True,
            timeout=120,
        )

    def filtered_files(self):
        record = self.root / "gradle-filtered-files.txt"
        if not record.is_file():
            return []
        return [line for line in record.read_text(encoding="utf-8").splitlines() if line]

    def gradle_arguments(self):
        record = self.root / "gradle-arguments.txt"
        if not record.is_file():
            return []
        return [line for line in record.read_text(encoding="utf-8").splitlines() if line]


class CommitMessageHookTest(HookTestCase):
    def message_file(self, content):
        path = self.root / "COMMIT_EDITMSG"
        path.write_text(content, encoding="utf-8")
        return str(path)

    def test_an_accepted_message_passes(self):
        completed = self.run_hook("commit-msg", self.message_file("feat(ci): commit the two hooks\n"))
        self.assertEqual(0, completed.returncode, completed.stderr)
        self.assertIn("Commit-message verification passed.", completed.stdout)

    def test_a_rejected_message_fails_with_the_violation(self):
        completed = self.run_hook("commit-msg", self.message_file("feature: commit the two hooks.\n"))
        self.assertEqual(1, completed.returncode)
        self.assertIn("'feature' is not a declared commit type", completed.stderr)
        self.assertIn("must not end with a period", completed.stderr)


class PreCommitHookTest(HookTestCase):
    def test_the_formatter_receives_only_the_staged_kotlin_files(self):
        self.write("app/src/main/Present.kt", "package app\n\ninternal fun present(): Int = 1\n")
        self.write("build.gradle.kts", "plugins {\n}\n")
        self.write("docs/notes.md", "Notes.\n")
        self.stage("app/src/main/Present.kt", "build.gradle.kts", "docs/notes.md")
        completed = self.run_hook("pre-commit")
        self.assertEqual(0, completed.returncode, completed.stderr)
        self.assertEqual(["app/src/main/Present.kt", "build.gradle.kts"], self.filtered_files())
        self.assertIn("--quiet", self.gradle_arguments())
        self.assertIn("ktlintFormat", self.gradle_arguments())

    def test_the_formatted_file_is_staged_again(self):
        self.write("app/src/main/Present.kt", "package app\n\ninternal fun present(): Int = 1 \n")
        self.stage("app/src/main/Present.kt")
        completed = self.run_hook("pre-commit")
        self.assertEqual(0, completed.returncode, completed.stderr)
        staged = self.git("show", ":app/src/main/Present.kt")
        self.assertEqual(0, staged.returncode, staged.stderr)
        self.assertNotIn("= 1 \n", staged.stdout)

    def test_an_unfixable_violation_refuses_the_commit(self):
        self.write("app/src/main/Present.kt", "package app\n\ninternal fun present(): Int = 1\nUNFIXABLE\n")
        self.stage("app/src/main/Present.kt")
        completed = self.run_hook("pre-commit")
        self.assertEqual(1, completed.returncode)
        self.assertIn("the formatter cannot fix", completed.stderr)

    def test_a_staged_file_that_carries_a_comment_is_rejected(self):
        self.write("app/src/main/Present.kt", "package app\n\n\ninternal fun present(): Int = 1\n")
        self.write("tools/run-live-smoke.sh", "#!/usr/bin/env bash\n# run the smoke\nset -euo pipefail\n")
        self.stage("app/src/main/Present.kt", "tools/run-live-smoke.sh")
        completed = self.run_hook("pre-commit")
        self.assertEqual(1, completed.returncode)
        self.assertIn("tools/run-live-smoke.sh:2: line comment is not allowed", completed.stderr)

    def test_an_unstaged_file_that_carries_a_comment_is_ignored(self):
        self.write("app/src/main/Present.kt", "package app\n\ninternal fun present(): Int = 1\n")
        self.write("tools/run-live-smoke.sh", "#!/usr/bin/env bash\n# run the smoke\nset -euo pipefail\n")
        self.stage("app/src/main/Present.kt")
        completed = self.run_hook("pre-commit")
        self.assertEqual(0, completed.returncode, completed.stderr)
        self.assertNotIn("run-live-smoke.sh", completed.stderr)

    def test_a_staged_kotlin_file_with_unstaged_changes_is_refused(self):
        self.write("app/src/main/Present.kt", "package app\n\ninternal fun present(): Int = 1\n")
        self.stage("app/src/main/Present.kt")
        self.write("app/src/main/Present.kt", "package app\n\ninternal fun present(): Int = 2\n")
        completed = self.run_hook("pre-commit")
        self.assertEqual(1, completed.returncode)
        self.assertIn("must carry no unstaged change", completed.stderr)
        self.assertIn("app/src/main/Present.kt", completed.stderr)

    def test_nothing_staged_passes_without_a_run(self):
        completed = self.run_hook("pre-commit")
        self.assertEqual(0, completed.returncode, completed.stderr)
        self.assertEqual([], self.gradle_arguments())


class WiringTest(HookTestCase):
    def test_the_wiring_command_installs_both_hooks(self):
        self.git("config", "core.hooksPath", ".githooks")
        self.write("app/src/main/Present.kt", "package app\n\ninternal fun present(): Int = 1\n")
        self.stage("app/src/main/Present.kt")
        rejected = self.git("commit", "-m", "protect main")
        self.assertNotEqual(0, rejected.returncode)
        self.assertIn("is not a Conventional Commit", rejected.stderr + rejected.stdout)
        accepted = self.git("commit", "-m", "feat(ci): commit the two hooks")
        self.assertEqual(0, accepted.returncode, accepted.stderr + accepted.stdout)
        log = self.git("log", "--format=%s", "-1")
        self.assertEqual("feat(ci): commit the two hooks", log.stdout.strip())


if __name__ == "__main__":
    unittest.main()
