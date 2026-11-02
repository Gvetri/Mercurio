"""Verify the pull-request template check and the workflow that runs it.

The check reports the required sections of `.github/PULL_REQUEST_TEMPLATE.md` on a pull request, and
it accepts a Dependabot pull request, whose body Dependabot writes itself and cannot carry those
sections. The workflow assertions pin the two properties the required context depends on: the job
always runs, and it hands the author to the check.
"""
import os
import re
import subprocess
import sys
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]
CHECK = ROOT / ".github/scripts/verify-pr-template.py"
WORKFLOW = ROOT / ".github/workflows/pr-template.yml"

HEADINGS = ("## Summary", "## Testing", "## Risk", "## How to revert")
TEMPLATE_BODY = "\n\n".join(f"{heading}\n\nA line." for heading in HEADINGS) + "\n"
HUMAN_AUTHOR = "octocat"
DEPENDABOT_BODY = "Bumps `io.sentry:sentry-android-core` from 8.16.0 to 8.61.1.\n"
DEPENDABOT_HEADLINE = "Bump io.sentry:sentry-android-core from 8.16.0 to 8.61.1"


def run_check(body=None, author=None):
    environment = dict(os.environ)
    environment.pop("PR_BODY", None)
    environment.pop("PR_AUTHOR", None)
    if body is not None:
        environment["PR_BODY"] = body
    if author is not None:
        environment["PR_AUTHOR"] = author
    return subprocess.run(
        [sys.executable, str(CHECK)],
        capture_output=True,
        text=True,
        env=environment,
        timeout=120,
        check=False,
    )


def job_block(workflow, job):
    body = workflow.split("\njobs:\n", 1)[1]
    match = re.search(rf"^  {re.escape(job)}:\n", body, flags=re.MULTILINE)
    assert match is not None, f"the workflow must declare the {job} job"
    remainder = body[match.end():]
    end = re.search(r"^  [a-z0-9_-]+:\n", remainder, flags=re.MULTILINE)
    return remainder[: end.start()] if end else remainder


class PullRequestTemplateCheckTest(unittest.TestCase):
    """What the check accepts and what it rejects."""

    def assert_accepted(self, completed):
        self.assertEqual(0, completed.returncode, completed.stdout + completed.stderr)

    def assert_rejected(self, completed, *expected):
        self.assertEqual(1, completed.returncode, completed.stdout + completed.stderr)
        for fragment in expected:
            self.assertIn(fragment, completed.stderr)

    def test_a_body_with_every_required_heading_passes(self):
        completed = run_check(body=TEMPLATE_BODY, author=HUMAN_AUTHOR)
        self.assert_accepted(completed)
        self.assertIn("All required template sections are present.", completed.stdout)

    def test_a_missing_heading_fails_and_names_it(self):
        body = TEMPLATE_BODY.replace("## Risk\n\nA line.\n", "")
        completed = run_check(body=body, author=HUMAN_AUTHOR)
        self.assert_rejected(
            completed,
            "Pull request body is missing required template sections:",
            "  - ## Risk",
            "Reuse the headings from .github/PULL_REQUEST_TEMPLATE.md verbatim.",
        )
        self.assertNotIn("## Summary", completed.stderr)

    def test_an_empty_body_names_every_required_heading(self):
        completed = run_check(body="", author=HUMAN_AUTHOR)
        self.assert_rejected(completed, *(f"  - {heading}" for heading in HEADINGS))

    def test_an_absent_body_fails_closed(self):
        completed = run_check(author=HUMAN_AUTHOR)
        self.assert_rejected(completed, "missing required template sections", "  - ## Summary")

    def test_an_absent_author_is_judged_as_a_human(self):
        completed = run_check(body="", author=None)
        self.assert_rejected(completed, "missing required template sections")

    def test_a_heading_at_another_level_does_not_satisfy_the_check(self):
        body = TEMPLATE_BODY.replace("## Summary", "### Summary")
        completed = run_check(body=body, author=HUMAN_AUTHOR)
        self.assert_rejected(completed, "  - ## Summary")

    def test_a_dependabot_body_without_the_headings_passes(self):
        completed = run_check(body=DEPENDABOT_BODY, author="dependabot[bot]")
        self.assert_accepted(completed)
        self.assertIn("dependabot[bot]", completed.stdout)

    def test_a_dependabot_pull_request_passes_without_any_body(self):
        completed = run_check(body=None, author="dependabot[bot]")
        self.assert_accepted(completed)

    def test_another_bot_still_needs_the_headings(self):
        completed = run_check(body=DEPENDABOT_BODY, author="renovate[bot]")
        self.assert_rejected(completed, *(f"  - {heading}" for heading in HEADINGS))

    def test_an_author_that_only_resembles_dependabot_still_needs_the_headings(self):
        completed = run_check(body=DEPENDABOT_BODY, author="dependabot")
        self.assert_rejected(completed, "  - ## Summary")


class PullRequestTemplateWorkflowTest(unittest.TestCase):
    """The declaration that keeps the required context on every pull request."""

    def setUp(self):
        self.workflow = WORKFLOW.read_text(encoding="utf-8")
        self.job = job_block(self.workflow, "pr_template")

    def test_the_required_context_is_the_documented_job_name(self):
        self.assertIn("    name: pr-template\n", self.job)

    def test_the_job_runs_unconditionally(self):
        self.assertIsNone(
            re.search(r"^    if:", self.job, flags=re.MULTILINE),
            "a skipped required check is not a pass, so the job must not carry an if:",
        )
        self.assertNotIn("continue-on-error", self.job)

    def test_the_job_hands_the_author_and_the_body_to_the_check(self):
        self.assertIn("          PR_AUTHOR: ${{ github.event.pull_request.user.login }}\n", self.job)
        self.assertIn("          PR_BODY: ${{ github.event.pull_request.body }}\n", self.job)

    def test_the_job_runs_the_pinned_check(self):
        self.assertIn("        run: python3 .github/scripts/verify-pr-template.py\n", self.job)

    def test_the_workflow_still_runs_on_a_dependabot_pull_request(self):
        triggers = self.workflow.split("\njobs:\n", 1)[0]
        for activity in ("opened", "edited", "reopened", "ready_for_review", "synchronize"):
            self.assertIn(f"      - {activity}\n", triggers)


class PullRequestTemplateHeadingParityTest(unittest.TestCase):
    """The check and the published template name the same sections."""

    def test_every_required_heading_appears_in_the_pull_request_template(self):
        template = (ROOT / ".github/PULL_REQUEST_TEMPLATE.md").read_text(encoding="utf-8")
        check = CHECK.read_text(encoding="utf-8")
        declared = re.search(r"REQUIRED_HEADINGS = \((.+?)\)", check, flags=re.DOTALL)
        if declared is None:
            self.fail("the check must declare its required headings")
        for heading in HEADINGS:
            self.assertIn(f'"{heading}"', declared.group(1))
            self.assertIn(f"\n{heading}\n", template)


if __name__ == "__main__":
    unittest.main()
