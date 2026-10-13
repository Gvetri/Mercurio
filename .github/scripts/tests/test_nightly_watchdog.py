import json
import subprocess
import unittest

import test_release_signing as signing

REPOSITORY, ROOT, SOURCE = signing.REPOSITORY, signing.ROOT, signing.SOURCE

SCRIPT = ROOT / ".github/scripts/nightly-watchdog.py"
PREFIX = f"/repos/{REPOSITORY}"
MARKER = "<!-- nightly-watchdog -->"


class NightlyWatchdogTest(unittest.TestCase):
    def setUp(self):
        fixture = signing.ReleaseSourceTest()
        fixture.setUp()
        self.addCleanup(fixture.doCleanups)
        self.routes, self.api = fixture.routes, fixture.api
        self.environment, self.workflow_run = fixture.environment, fixture.workflow_run
        self.jobs = fixture.jobs
        self.workflow_run["event"] = "schedule"
        self.head = "b" * 40
        self.routes.update({
            f"{PREFIX}/branches/main": {"commit": {"sha": self.head}},
            f"{PREFIX}/commits/{self.head}": {"sha": self.head, "parents": [{"sha": SOURCE}]},
            self.run_page(self.head): {"total_count": 0, "workflow_runs": []},
            self.run_page(SOURCE): {"total_count": 1, "workflow_runs": [self.workflow_run]},
        })
        self.releases = [{
            "id": 7, "tag_name": f"nightly-7001-{'c' * 40}", "name": "Nightly",
            "target_commitish": "c" * 40, "draft": False, "prerelease": True,
            "published_at": "2026-10-11T08:00:00Z",
        }]
        self.issues = []
        self.writes = []
        self.routes.update({
            f"{PREFIX}/releases": self.releases,
            f"{PREFIX}/issues": self.issues,
            ("POST", f"{PREFIX}/issues"): self.create_issue,
            ("PATCH", f"{PREFIX}/issues/9"): self.update_issue,
            f"{PREFIX}/issues/9": lambda: self.issues[0],
        })

    def run_page(self, sha, page=1):
        return (f"{PREFIX}/actions/workflows/quality-gate.yml/runs?branch=main&status=success"
                f"&head_sha={sha}&per_page=100&page={page}")

    def create_issue(self, payload):
        self.writes.append(("POST", payload))
        issue = {"id": 90, "number": 9, "state": "open", **payload}
        issue["labels"] = [{"name": label} for label in payload["labels"]]
        self.issues.append(issue)
        return 201, issue

    def update_issue(self, payload):
        self.writes.append(("PATCH", payload))
        self.issues[0].update(payload)
        self.issues[0]["labels"] = [{"name": label} for label in payload["labels"]]
        return 200, self.issues[0]

    def check(self):
        return subprocess.run(["python3", str(SCRIPT)], cwd=ROOT, env=self.environment,
                              capture_output=True, text=True, check=False, timeout=20)

    def test_repeated_starvation_updates_the_same_issue_and_reopens_it(self):
        self.assertEqual(0, self.check().returncode)
        self.assertEqual(0, self.check().returncode)
        self.assertEqual(["POST", "PATCH"], [method for method, _ in self.writes])
        self.assertEqual(1, len(self.issues))
        self.issues[0]["state"] = "closed"
        self.issues[0]["labels"].append({"name": "investigating"})
        self.assertEqual(0, self.check().returncode)
        self.assertEqual(["POST", "PATCH", "PATCH"], [method for method, _ in self.writes])
        self.assertEqual(1, len(self.issues))
        self.assertEqual("open", self.issues[0]["state"])
        self.assertIn({"name": "investigating"}, self.issues[0]["labels"])
        self.assertEqual(f"{PREFIX}/issues/9", self.api.requests[-1])

    def test_released_tip_has_zero_distance_and_does_not_update_an_existing_alarm(self):
        self.assertEqual(0, self.check().returncode)
        before = json.dumps(self.issues)
        self.routes[f"{PREFIX}/branches/main"]["commit"]["sha"] = SOURCE
        self.releases[0].update(tag_name=f"nightly-7001-{SOURCE}", target_commitish=SOURCE)
        result = self.check()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(0, json.loads(result.stdout)["main_first_parent_commits_ahead"])
        self.assertEqual(before, json.dumps(self.issues))
        self.assertEqual(["POST"], [method for method, _ in self.writes])
        self.assertEqual(1, len(self.api.posts))
        self.assertEqual([], self.api.patches)
        self.assertEqual([], self.api.deletes)

    def test_no_published_nightly_and_drafts_are_reported_as_none(self):
        self.releases[0].update(draft=True, published_at=None)
        result = self.check()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertIsNone(json.loads(result.stdout)["newest_nightly_published_at"])
        self.assertEqual("starved", json.loads(result.stdout)["verdict"])

    def test_push_fork_and_failed_runs_are_not_full_validation(self):
        for field, value in (("event", "push"), ("head_repository", {"full_name": "other/fork"}),
                             ("conclusion", "failure"), ("status", "in_progress")):
            with self.subTest(field=field):
                original = self.workflow_run[field]
                self.workflow_run[field] = value
                self.routes[f"{PREFIX}/commits/{SOURCE}"] = {"sha": SOURCE, "parents": []}
                result = self.check()
                self.assertNotEqual(0, result.returncode)
                self.assertIn("No fully validated", result.stderr)
                self.assertEqual([], self.writes)
                self.assertEqual([], self.api.posts)
                self.assertEqual([], self.api.patches)
                self.assertEqual([], self.api.deletes)
                self.workflow_run[field] = original

    def test_skipped_device_evidence_fails_closed_without_an_alarm(self):
        next(job for job in self.jobs if job["name"] == "maestro-journeys")["conclusion"] = "skipped"
        result = self.check()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Required validation evidence failed", result.stderr)
        self.assertEqual([], self.writes)
        self.assertEqual([], self.api.posts)
        self.assertEqual([], self.api.patches)
        self.assertEqual([], self.api.deletes)

    def test_paginated_runs_use_ancestry_not_older_rerun_completion(self):
        push_runs = [{**self.workflow_run, "id": number, "event": "push"} for number in range(1000, 1100)]
        self.routes[self.run_page(SOURCE)] = {"total_count": 101, "workflow_runs": push_runs}
        self.routes[self.run_page(SOURCE, 2)] = {"total_count": 101, "workflow_runs": [self.workflow_run]}
        self.routes[f"{PREFIX}/commits/{SOURCE}"] = {"sha": SOURCE, "parents": [{"sha": "c" * 40}]}
        self.workflow_run["event"] = "workflow_dispatch"
        result = self.check()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(SOURCE, json.loads(result.stdout)["source_sha"])
        self.assertNotIn(f"{PREFIX}/commits/{SOURCE}", self.api.requests)

    def test_truncated_and_repeated_history_fail_without_writing(self):
        self.routes[self.run_page(SOURCE)]["total_count"] = 2
        result = self.check()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Truncated Nightly history", result.stderr)
        self.routes[self.run_page(SOURCE)].update(total_count=2, workflow_runs=[self.workflow_run] * 2)
        result = self.check()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Duplicate Nightly history", result.stderr)
        self.assertEqual([], self.writes)
        self.assertEqual([], self.api.posts)
        self.assertEqual([], self.api.patches)
        self.assertEqual([], self.api.deletes)

    def test_wrong_commit_and_ancestry_cycle_fail_without_writing(self):
        commit = self.routes[f"{PREFIX}/commits/{self.head}"]
        commit["sha"] = SOURCE
        self.assertNotEqual(0, self.check().returncode)
        commit.update(sha=self.head, parents=[{"sha": self.head}])
        self.assertNotEqual(0, self.check().returncode)
        self.assertEqual([], self.writes)
        self.assertEqual([], self.api.posts)
        self.assertEqual([], self.api.patches)
        self.assertEqual([], self.api.deletes)

    def test_issue_read_back_must_confirm_body_and_agent_label(self):
        self.routes[f"{PREFIX}/issues/9"] = lambda: {**self.issues[0], "labels": []}
        result = self.check()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("read-back did not match", result.stderr)
        self.assertEqual(1, len(self.writes))

    def test_duplicate_alarm_markers_fail_closed(self):
        self.issues.extend([{"id": number, "number": number, "body": MARKER} for number in (1, 2)])
        result = self.check()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Multiple watchdog issues", result.stderr)
        self.assertEqual([], self.writes)
        self.assertEqual([], self.api.posts)
        self.assertEqual([], self.api.patches)
        self.assertEqual([], self.api.deletes)

    def test_released_source_reports_facts_without_filing_an_alarm(self):
        self.releases[0].update(tag_name=f"nightly-7001-{SOURCE}", target_commitish=SOURCE)
        result = self.check()
        self.assertEqual(0, result.returncode, result.stderr)
        facts = json.loads(result.stdout)
        self.assertEqual({
            "verdict": "released", "source_sha": SOURCE, "main_sha": self.head,
            "main_first_parent_commits_ahead": 1, "nightly": f"nightly-7001-{SOURCE}",
            "newest_nightly_published_at": "2026-10-11T08:00:00Z",
            "validation_run_id": 123, "validation_run_attempt": 1,
        }, facts)
        self.assertEqual([], self.issues)
        self.assertEqual([], self.writes)
        self.assertEqual([], self.api.posts)
        self.assertEqual([], self.api.patches)
        self.assertEqual([], self.api.deletes)
        self.assertFalse(any("/issues" in path for path in self.api.requests))

    def test_starved_source_reports_facts_and_files_one_agent_issue(self):
        result = self.check()
        self.assertEqual(0, result.returncode, result.stderr)
        facts = json.loads(result.stdout)
        self.assertEqual({
            "verdict": "starved", "source_sha": SOURCE, "main_sha": self.head,
            "main_first_parent_commits_ahead": 1, "nightly": None,
            "newest_nightly_published_at": "2026-10-11T08:00:00Z",
            "validation_run_id": 123, "validation_run_attempt": 1,
        }, facts)
        self.assertEqual(1, len(self.issues))
        self.assertEqual(["POST"], [method for method, _ in self.writes])
        issue = self.issues[0]
        self.assertIn({"name": "ready-for-agent"}, issue["labels"])
        self.assertIn(MARKER, issue["body"])
        for key, value in facts.items():
            self.assertIn(f'"{key}": {json.dumps(value)}', issue["body"])
        self.assertEqual(f"{PREFIX}/issues/9", self.api.requests[-1])


class NightlyWatchdogWorkflowTest(unittest.TestCase):
    def test_alarm_is_scheduled_manual_serialized_and_has_only_issue_write_access(self):
        import re
        from test_protected_main import jobs_of

        workflow = (ROOT / ".github/workflows/nightly-watchdog.yml").read_text()
        header = workflow.split("\njobs:\n")[0]
        self.assertIn("  schedule:\n    - cron:", header)
        self.assertIn("  workflow_dispatch:", header)
        self.assertNotIn("push:", header)
        self.assertNotIn("pull_request", workflow)
        self.assertIn("group: nightly-watchdog-${{ github.repository }}", workflow)
        self.assertIn("cancel-in-progress: false", workflow)
        self.assertEqual(["issues"], re.findall(r"^\s+([a-z-]+): write$", workflow, re.MULTILINE))
        self.assertIn("      actions: read", workflow)
        self.assertIn("      contents: read", workflow)
        self.assertIn("github.ref == 'refs/heads/main' && github.ref_protected", workflow)
        self.assertIn("github.repository == 'Gvetri/hermes-native-client'", workflow)
        self.assertIn("persist-credentials: false", workflow)
        self.assertIn("ref: ${{ github.sha }}", workflow)
        self.assertIn("GITHUB_TOKEN: ${{ github.token }}", workflow)
        self.assertIn("run: python3 .github/scripts/nightly-watchdog.py", workflow)
        self.assertNotIn("secrets.", workflow)
        declared = (ROOT / ".github/quality-gate/required-checks.txt").read_text().split()
        self.assertTrue(set(jobs_of(workflow)).isdisjoint(declared + ["quality-gate"]))


if __name__ == "__main__":
    unittest.main()
