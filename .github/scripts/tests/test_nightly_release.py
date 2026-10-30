"""Provider-free Nightly policy and workflow regression tests."""
from pathlib import Path
import json
import os
import shutil
import subprocess
import tempfile
import unittest
from urllib.parse import parse_qs, urlsplit
import zipfile

from github_api_fixture import GitHubApiFixture
from test_protected_main import job_block, job_key
from test_release_signing import REQUIRED_JOBS
from signing_fixture import make_signing_fixture

ROOT = Path(__file__).resolve().parents[3]


class NightlyWorkflowTest(unittest.TestCase):
    def test_main_push_compiles_without_packaging_or_device_jobs(self):
        workflow = (ROOT / ".github/workflows/quality-gate.yml").read_text()
        build = job_block(workflow, "android_build")
        self.assertIn("if: ${{ github.event_name == 'push' }}", build)
        self.assertIn("./gradlew :app:compileDebugKotlin :app:compileReleaseKotlin --no-daemon", build)
        self.assertIn("if: ${{ github.event_name != 'push' }}", build)
        for job in ("api24_instrumentation", "maestro_journeys"):
            condition = job_key(job_block(workflow, job), "if") or ""
            self.assertNotIn("'push'", condition)
            self.assertIn("'schedule'", condition)
            self.assertIn("'workflow_dispatch'", condition)

    def test_validation_report_is_failure_only_separate_and_cannot_publish(self):
        workflow = (ROOT / ".github/workflows/nightly-release.yml").read_text()
        reporter = job_block(workflow, "report_validation_failure")
        self.assertIn("\n  report_validation_failure:\n", workflow)
        self.assertEqual("prepare", job_key(reporter, "needs"))
        self.assertIn("always() && needs.prepare.result == 'failure'", reporter)
        self.assertIn("needs.prepare.outputs.validation_failed == 'true'", reporter)
        self.assertIn("github.ref == 'refs/heads/main' && github.ref_protected", reporter)
        self.assertIn("    permissions:\n      actions: read\n      contents: read\n      issues: write\n", reporter)
        self.assertNotIn("secrets.", reporter)
        self.assertNotIn("contents: write", reporter)
        self.assertIn("python3 .github/scripts/nightly-release.py report-validation", reporter)
        for name in ("run_id", "run_attempt", "sha"):
            self.assertIn(f"FAILED_VALIDATION_{name.upper()}: ${{{{ needs.prepare.outputs.failed_validation_{name} }}}}", reporter)
            self.assertIn(f"failed_validation_{name}: ${{{{ steps.prepare.outputs.failed_validation_{name} }}}}", workflow)
        self.assertNotIn("continue-on-error", job_block(workflow, "prepare"))
        self.assertNotIn("always()", job_block(workflow, "sign"))
        self.assertNotIn("always()", job_key(job_block(workflow, "publish"), "if"))

    def test_release_failure_reporter_is_separate_and_cannot_write_contents(self):
        workflow = (ROOT / ".github/workflows/nightly-release.yml").read_text()
        self.assertIn("\n  report_release_failure:\n", workflow)
        reporter = job_block(workflow, "report_release_failure")
        self.assertIn("needs: [prepare, sign, publish]", reporter)
        for name in ("source_sha", "version_code"):
            self.assertIn(f"RELEASE_{name.upper()}: ${{{{ needs.prepare.outputs.{name} }}}}", reporter)
        for condition in ("always()", "needs.sign.result == 'failure'", "needs.publish.result == 'failure'"):
            self.assertIn(condition, job_key(reporter, "if"))
        self.assertIn("contents: read", reporter)
        self.assertIn("actions: read", reporter)
        self.assertIn("issues: write", reporter)
        self.assertNotIn("contents: write", reporter)
        self.assertNotIn("secrets.", reporter)
        self.assertNotIn("environment:", reporter)
        self.assertIn("create-nightly-release-failure-issue.py", reporter)
        publisher = job_block(workflow, "publish")
        self.assertIn("NIGHTLY_FAILURE_OBSERVATION:", publisher)
        self.assertIn("nightly-publication-state-${{ github.run_id }}-${{ github.run_attempt }}", publisher)
        self.assertIn("always() && steps.publication.outcome == 'failure'", publisher)

    def test_nightly_scopes_dispatch_and_only_publisher_can_write_contents(self):
        workflow = (ROOT / ".github/workflows/nightly-release.yml").read_text()
        header = workflow.split("jobs:", 1)[0]
        self.assertIn("schedule:\n    - cron:", header)
        self.assertIn("workflow_dispatch:", header)
        self.assertNotIn("push:", header)
        self.assertNotIn("pull_request", header)
        self.assertNotIn("inputs:", header)
        self.assertIn("cancel-in-progress: false", header)
        self.assertIn("contents: read", header)
        self.assertNotIn("secrets: inherit", workflow)
        prepare = job_block(workflow, "prepare")
        self.assertNotIn("secrets.", prepare)
        self.assertIn("    permissions:\n      contents: read\n      actions: write\n", prepare)
        self.assertEqual("190", job_key(prepare, "timeout-minutes"))
        self.assertIn("NIGHTLY_VALIDATION_TIMEOUT_SECONDS: '10800'", prepare)
        self.assertNotIn("actions: write", header)
        self.assertIn("github.ref_protected", prepare)
        self.assertIn("python3 .github/scripts/nightly-release.py prepare", prepare)
        signer = job_block(workflow, "sign")
        self.assertIn("    secrets:\n"
                      "      ANDROID_RELEASE_KEYSTORE_BASE64: ${{ secrets.ANDROID_RELEASE_KEYSTORE_BASE64 }}\n"
                      "      ANDROID_RELEASE_KEYSTORE_PASSWORD: ${{ secrets.ANDROID_RELEASE_KEYSTORE_PASSWORD }}\n", signer)
        self.assertEqual(2, workflow.count("${{ secrets."))
        self.assertIn("needs.prepare.outputs.publish == 'true'", signer)
        self.assertIn("uses: ./.github/workflows/release-signing.yml", signer)
        self.assertNotIn("actions: write", signer)
        for name in ("validation_run_id", "source_sha", "validation_run_attempt", "nightly_version_code", "generation_attempt"):
            self.assertIn(f"{name}:", signer)
        publisher = job_block(workflow, "publish")
        self.assertNotIn("secrets.", publisher)
        self.assertIn("needs: [prepare, sign]", publisher)
        self.assertIn("contents: write", publisher)
        self.assertNotIn("environment:", publisher)
        self.assertNotIn("contents: write", workflow.split("  publish:", 1)[0])
        self.assertIn("artifact-ids: ${{ needs.sign.outputs.signed_artifact_id }}", publisher)
        self.assertIn("python3 .github/scripts/nightly-release.py publish", publisher)
        self.assertNotIn("./gradlew", publisher)
        signing_workflow = (ROOT / ".github/workflows/release-signing.yml").read_text()
        signing_prepare = job_block(signing_workflow, "prepare")
        self.assertIn("RELEASE_SOURCE_SHA: ${{ inputs.source_sha }}", signing_prepare)
        self.assertIn("RELEASE_VALIDATION_RUN_ATTEMPT: ${{ inputs.validation_run_attempt }}", signing_prepare)


class NightlyPreparationTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.old, self.selected, self.new = "a" * 40, "b" * 40, "c" * 40
        self.repository = "Gvetri/Mercurio"
        prefix = f"/repos/{self.repository}"
        self.selected_run = {
            "id": 123, "run_attempt": 1, "workflow_id": 45,
            "path": ".github/workflows/quality-gate.yml", "event": "workflow_dispatch",
            "head_branch": "main", "head_sha": self.selected,
            "head_repository": {"full_name": self.repository},
            "repository": {"full_name": self.repository},
            "status": "completed", "conclusion": "success",
        }
        self.runs = []
        self.jobs = [
            {"name": name, "head_sha": self.selected, "status": "completed", "conclusion": "success"}
            for name in REQUIRED_JOBS
        ] + [
            {"name": name, "head_sha": self.selected, "status": "completed", "conclusion": "skipped"}
            for name in ("fork-guard", "draft-validation", "commit-message", "ui-paths")
        ]
        self.releases = []
        self.routes = {
            f"{prefix}/branches/main": {"commit": {"sha": self.selected}, "protected": True},
            ("POST", f"{prefix}/actions/workflows/quality-gate.yml/dispatches"): self.dispatch,
            f"{prefix}/actions/workflows/quality-gate.yml": {"id": 45},
            f"{prefix}/actions/workflows/quality-gate.yml/runs": lambda: {"total_count": len(self.runs), "workflow_runs": self.runs},
            f"{prefix}/actions/runs/123": self.selected_run,
            f"{prefix}/actions/runs/123/attempts/1": self.selected_run,
            f"{prefix}/actions/runs/123/attempts/2": self.selected_run,
            f"{prefix}/actions/runs/123/attempts/1/jobs": {"total_count": len(self.jobs), "jobs": self.jobs},
            f"{prefix}/compare/{self.selected}...main": {"status": "ahead", "merge_base_commit": {"sha": self.selected}},
            f"{prefix}/releases": self.releases,
        }
        self.api = GitHubApiFixture(self.routes)
        self.api.start()
        self.addCleanup(self.api.stop)
        self.environment = {
            "PATH": os.environ["PATH"], "GITHUB_REPOSITORY": self.repository,
            "GITHUB_REF": "refs/heads/main", "GITHUB_REF_PROTECTED": "true",
            "GITHUB_EVENT_NAME": "schedule", "GITHUB_TOKEN": "synthetic-token",
            "GITHUB_WORKFLOW_REF": f"{self.repository}/.github/workflows/nightly-release.yml@refs/heads/main",
            "GITHUB_API_URL": self.api.url, "GITHUB_RUN_NUMBER": "7", "GITHUB_RUN_ATTEMPT": "2",
            "GITHUB_OUTPUT": str(Path(self.directory.name) / "output"),
            "NIGHTLY_VALIDATION_TIMEOUT_SECONDS": "2", "NIGHTLY_VALIDATION_POLL_SECONDS": "1",
        }

    def dispatch(self, payload):
        self.selected_run["created_at"] = "2026-01-01T00:00:00Z"
        self.runs[:] = [self.selected_run]
        return 204

    def prepare(self):
        output = Path(self.environment["GITHUB_OUTPUT"])
        output.unlink(missing_ok=True)
        result = subprocess.run(
            ["python3", str(ROOT / ".github/scripts/tests/fixtures/run-nightly-prepare.py"), "prepare"],
            cwd=ROOT, env=self.environment, text=True, capture_output=True, check=False,
        )
        values = dict(line.split("=", 1) for line in output.read_text().splitlines()) if output.exists() else {}
        return result, values

    def test_unknown_terminal_conclusion_is_not_printed_or_accepted(self):
        self.selected_run["conclusion"] = "untrusted-api-value-must-not-appear"
        result, values = self.prepare()
        self.assertNotEqual(0, result.returncode)
        self.assertNotIn(self.selected_run["conclusion"], result.stdout + result.stderr)
        self.assertIn("Dispatched validation 123 did not succeed; no Nightly was published", result.stderr)
        self.assertEqual({}, values)
        self.assertEqual([], self.releases)

    def test_failed_dispatch_preserves_reporting_identity_without_release_authority(self):
        self.selected_run["conclusion"] = "failure"
        result, values = self.prepare()
        self.assertNotEqual(0, result.returncode)
        self.assertEqual({
            "validation_failed": "true", "failed_validation_run_id": "123",
            "failed_validation_run_attempt": "1", "failed_validation_sha": self.selected,
        }, values)
        self.assertEqual([], self.releases)
        self.assertEqual(1, len(self.api.posts))

    def report_validation(self, values):
        root = Path(self.directory.name)
        binary = root / "issue-bin"
        binary.mkdir(exist_ok=True)
        shutil.copyfile(ROOT / ".github/scripts/tests/fixtures/fake-gh-validation-issue.py", binary / "gh")
        shutil.copyfile(ROOT / ".github/scripts/tests/fixtures/issue_store.py", binary / "issue_store.py")
        (binary / "gh").chmod(0o755)
        environment = {
            **self.environment, "PATH": str(binary) + os.pathsep + self.environment["PATH"],
            "GITHUB_RUN_ID": "456", "GITHUB_SHA": self.old,
            "GITHUB_SERVER_URL": "https://github.com",
            "FAILED_VALIDATION_RUN_ID": values["failed_validation_run_id"],
            "FAILED_VALIDATION_RUN_ATTEMPT": values["failed_validation_run_attempt"],
            "FAILED_VALIDATION_SHA": values["failed_validation_sha"],
        }
        return subprocess.run(
            ["python3", str(ROOT / ".github/scripts/nightly-release.py"), "report-validation"],
            cwd=root, env=environment, text=True, capture_output=True, check=False,
        )

    def test_issue_store_refuses_duplicate_creation_under_optimization(self):
        root = Path(self.directory.name)
        fixture = ROOT / ".github/scripts/tests/fixtures"
        result = subprocess.run(
            ["python3", "-O", "-c",
             "from issue_store import IssueStore; s = IssueStore('optimized-issue.json', 'owner/repo', 1); "
             "s.create({'body': 'original'}); s.create({'body': 'replacement'})"],
            cwd=root, env={**self.environment, "PYTHONPATH": str(fixture)}, capture_output=True, text=True,
        )
        self.assertNotEqual(0, result.returncode)
        self.assertEqual("original", json.loads((root / "optimized-issue.json").read_text())["body"])

    def direct_reporter(self, additional, overrides=None):
        root = Path(self.directory.name)
        binary = root / "direct-bin"
        binary.mkdir(exist_ok=True)
        for source, target in (("fake-gh-validation-issue.py", "gh"), ("issue_store.py", "issue_store.py")):
            shutil.copyfile(ROOT / ".github/scripts/tests/fixtures" / source, binary / target)
        (binary / "gh").chmod(0o755)
        self.routes[f"/repos/{self.repository}/actions/runs/123/artifacts"] = {"artifacts": []}
        return subprocess.run(
            ["bash", str(ROOT / ".github/scripts/create-nightly-failure-issue.sh")], cwd=root,
            env={**self.environment, "PATH": str(binary) + os.pathsep + self.environment["PATH"],
                 "GH_TOKEN": "synthetic-token", "GITHUB_RUN_ID": "123", "GITHUB_RUN_ATTEMPT": "1",
                 "GITHUB_SHA": self.selected, "GITHUB_SERVER_URL": "https://github.com",
                 "TEST_JOB_RESULTS": json.dumps(dict.fromkeys(
                     ("unit_tests", "compose_test", "api24_instrumentation", "maestro_journeys",
                      "coverage_mutation"), "success")),
                 "ADDITIONAL_FAILED_STAGES": json.dumps(additional), **(overrides or {})},
            capture_output=True, text=True, check=False,
        )

    def assert_clean_reporter_refusal(self, result):
        self.assertNotEqual(0, result.returncode, result.stdout)
        self.assertEqual(1, len(result.stderr.splitlines()), result.stderr)
        self.assertLess(len(result.stderr), 180)
        self.assertNotIn("Traceback", result.stderr)
        self.assertFalse((Path(self.directory.name) / "issue.json").exists())
        self.assertFalse((Path(self.directory.name) / "writes").exists())
        self.assertEqual([], self.api.posts)
        self.assertEqual([], self.api.patches)
        self.assertEqual([], self.api.deletes)

    def test_direct_reporter_cleanly_refuses_non_string_run_and_job_conclusions(self):
        stage = next(job for job in self.jobs if job["name"] == "formatting")
        for target in (self.selected_run, stage):
            for conclusion in ([], {}, None, 7, True):
                with self.subTest(target=target.get("name", "run"), conclusion=conclusion):
                    self.selected_run["conclusion"] = stage["conclusion"] = "failure"
                    target["conclusion"] = conclusion
                    self.assert_clean_reporter_refusal(self.direct_reporter(["formatting"]))

    def test_direct_reporter_cleanly_refuses_malformed_additional_json(self):
        for value in ("[", "not-json", '{"stage":'):
            with self.subTest(value=value):
                self.assert_clean_reporter_refusal(self.direct_reporter(
                    [], {"ADDITIONAL_FAILED_STAGES": value}))

    def test_direct_reporter_cleanly_refuses_malformed_test_results(self):
        results = dict.fromkeys(
            ("unit_tests", "compose_test", "api24_instrumentation", "maestro_journeys"), "success")
        for value in ("[", "null", "[]", "7", json.dumps(list(results)),
                      *[json.dumps({**results, "unit_tests": value}) for value in ([], {}, None, 7)]):
            with self.subTest(value=value):
                self.assert_clean_reporter_refusal(self.direct_reporter([], {"TEST_JOB_RESULTS": value}))

    def test_direct_reporter_refuses_unproved_additional_failures(self):
        stage = next(job for job in self.jobs if job["name"] == "formatting")
        for case in ("invented", "succeeded", "green", "unfinished", "missing", "skipped", "unknown"):
            with self.subTest(case=case):
                for name in ("issue.json", "writes"):
                    (Path(self.directory.name) / name).unlink(missing_ok=True)
                self.selected_run.update(status="completed", conclusion="failure")
                stage.update(name="formatting", conclusion="failure")
                additional = ["formatting"]
                if case == "invented":
                    additional = ["invented-check"]
                elif case == "succeeded":
                    stage["conclusion"] = "success"
                elif case == "green":
                    self.selected_run["conclusion"] = "success"
                elif case == "unfinished":
                    self.selected_run["status"] = "in_progress"
                elif case == "missing":
                    stage["name"] = "other"
                elif case in {"skipped", "unknown"}:
                    stage["conclusion"] = case
                result = self.direct_reporter(additional)
                self.assertNotEqual(0, result.returncode, result.stdout)
                self.assertTrue(result.stderr)
                self.assertFalse((Path(self.directory.name) / "issue.json").exists())
                self.assertFalse((Path(self.directory.name) / "writes").exists())

    def test_direct_reporter_accepts_only_terminal_failed_conclusions(self):
        self.selected_run["conclusion"] = "failure"
        stage = next(job for job in self.jobs if job["name"] == "formatting")
        root = Path(self.directory.name)
        for conclusion in ("failure", "cancelled", "timed_out", "startup_failure", "action_required", "neutral", "stale"):
            with self.subTest(conclusion=conclusion):
                stage["conclusion"] = conclusion
                result = self.direct_reporter(["formatting"])
                self.assertEqual(0, result.returncode, result.stderr)
                self.assertIn("- `formatting`", json.loads((root / "issue.json").read_text())["body"])
                self.assertEqual(["POST"], (root / "writes").read_text().splitlines())
                (root / "issue.json").unlink()
                (root / "writes").unlink()

    def test_failed_validation_files_one_issue_with_exact_evidence_and_updates_its_attempt(self):
        self.selected_run["conclusion"] = "failure"
        for identity, job in enumerate(self.jobs, 1):
            job["id"] = identity
            if job["name"] == "maestro-journeys":
                job["conclusion"] = "failure"
        artifacts = [{"id": 99, "name": "journey-evidence-123-1", "expired": False}]
        self.routes[f"/repos/{self.repository}/actions/runs/123/artifacts"] = {"artifacts": artifacts}
        result, values = self.prepare()
        self.assertNotEqual(0, result.returncode)
        result = self.report_validation(values)
        self.assertEqual(0, result.returncode, result.stderr)
        root = Path(self.directory.name)
        issue = json.loads((root / "issue.json").read_text())
        self.assertEqual([{"name": "ready-for-agent"}], issue["labels"])
        for expected in ("<!-- nightly-run:123 -->", "Attempt: `1`", self.selected,
                         "`maestro-journeys`: `failure`", "`unit-tests`: `success`",
                         "`compose-jvm-tests`: `success`", "`api24-instrumentation`: `success`",
                         "/actions/runs/123/artifacts/99"):
            self.assertIn(expected, issue["body"])
        self.assertNotIn(self.old, issue["body"])
        self.assertNotIn("nightly-run:456", issue["body"])
        self.selected_run["run_attempt"] = 2
        self.routes[f"/repos/{self.repository}/actions/runs/123/attempts/2/jobs"] = {
            "total_count": len(self.jobs), "jobs": self.jobs,
        }
        values["failed_validation_run_attempt"] = "2"
        issue["state"] = "closed"
        (root / "issue.json").write_text(json.dumps(issue))
        result = self.report_validation(values)
        self.assertEqual(0, result.returncode, result.stderr)
        issue = json.loads((root / "issue.json").read_text())
        self.assertEqual("open", issue["state"])
        self.assertEqual(60, issue["number"])
        self.assertIn("Attempt: `2`", issue["body"])
        self.assertIn("Not uploaded. Inspect the failed job logs in the workflow run.", issue["body"])
        self.assertNotIn("/artifacts/99", issue["body"])
        self.assertEqual(["POST", "PATCH"], (root / "writes").read_text().splitlines())
        self.assertEqual([], self.releases)
        self.assertEqual(1, len(self.api.posts))
        self.assertTrue(any("/attempts/2/jobs?" in path for path in self.api.requests))

    def test_terminal_stage_failures_file_exactly_one_issue(self):
        self.selected_run["conclusion"] = "failure"
        for identity, job in enumerate(self.jobs, 1):
            job["id"] = identity
        stage = next(job for job in self.jobs if job["name"] == "api24-instrumentation")
        self.routes[f"/repos/{self.repository}/actions/runs/123/artifacts"] = {"artifacts": []}
        result, values = self.prepare()
        self.assertNotEqual(0, result.returncode)
        root = Path(self.directory.name)
        for conclusion in ("failure", "timed_out", "action_required", "startup_failure", "neutral", "stale"):
            with self.subTest(conclusion=conclusion):
                stage["conclusion"] = conclusion
                (root / "issue.json").unlink(missing_ok=True)
                (root / "writes").unlink(missing_ok=True)
                for expected_writes in (["POST"], ["POST", "PATCH"]):
                    result = self.report_validation(values)
                    self.assertEqual(0, result.returncode, result.stderr)
                    issue = json.loads((root / "issue.json").read_text())
                    self.assertIn("`api24-instrumentation`: `failure`", issue["body"])
                    self.assertEqual(expected_writes, (root / "writes").read_text().splitlines())

    def test_unsupported_terminal_validation_stage_refuses_without_writing_an_issue(self):
        self.selected_run["conclusion"] = "failure"
        for identity, job in enumerate(self.jobs, 1):
            job["id"] = identity
        stage = next(job for job in self.jobs if job["name"] == "api24-instrumentation")
        stage["conclusion"] = "unexpected_terminal"
        self.routes[f"/repos/{self.repository}/actions/runs/123/artifacts"] = {"artifacts": []}
        result, values = self.prepare()
        self.assertNotEqual(0, result.returncode)
        result = self.report_validation(values)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Invalid terminal validation stage: api24-instrumentation", result.stderr)
        root = Path(self.directory.name)
        self.assertFalse((root / "issue.json").exists())
        self.assertFalse((root / "writes").exists())

    def test_stale_completed_run_preserves_identity_and_reports_failed_stage(self):
        self.selected_run["conclusion"] = "stale"
        for identity, job in enumerate(self.jobs, 1):
            job["id"] = identity
            if job["name"] == "maestro-journeys":
                job["conclusion"] = "stale"
        self.routes[f"/repos/{self.repository}/actions/runs/123/artifacts"] = {"artifacts": []}
        result, values = self.prepare()
        self.assertNotEqual(0, result.returncode)
        self.assertEqual("true", values.get("validation_failed"))
        result = self.report_validation(values)
        self.assertEqual(0, result.returncode, result.stderr)
        root = Path(self.directory.name)
        self.assertIn("`maestro-journeys`: `failure`", json.loads((root / "issue.json").read_text())["body"])
        self.assertEqual(["POST"], (root / "writes").read_text().splitlines())

    def test_formatting_failure_files_one_issue_without_changing_test_results(self):
        self.selected_run["conclusion"] = "failure"
        for identity, job in enumerate(self.jobs, 1):
            job["id"] = identity
            if job["name"] == "formatting":
                job["conclusion"] = "failure"
        self.routes[f"/repos/{self.repository}/actions/runs/123/artifacts"] = {"artifacts": []}
        result, values = self.prepare()
        self.assertNotEqual(0, result.returncode)
        result = self.report_validation(values)
        self.assertEqual(0, result.returncode, result.stderr)
        root = Path(self.directory.name)
        issue = json.loads((root / "issue.json").read_text())
        self.assertIn("## Additional failed required jobs\n\n- `formatting`", issue["body"])
        self.assertIn("## Required test jobs\n\n- `unit-tests`: `success`\n"
                      "- `compose-jvm-tests`: `success`\n- `api24-instrumentation`: `success`\n"
                      "- `maestro-journeys`: `success`\n- `coverage-mutation`: `success`\n", issue["body"])
        self.assertEqual(["POST"], (root / "writes").read_text().splitlines())

    def test_green_validation_and_all_success_stages_cannot_create_an_issue(self):
        values = {"failed_validation_run_id": "123", "failed_validation_run_attempt": "1",
                  "failed_validation_sha": self.selected}
        result = self.report_validation(values)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Cannot report a successful", result.stderr)
        self.selected_run["conclusion"] = "failure"
        for identity, job in enumerate(self.jobs, 1):
            job["id"] = identity
        self.routes[f"/repos/{self.repository}/actions/runs/123/artifacts"] = {"artifacts": []}
        result = self.report_validation(values)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("cannot publish a successful test run", result.stderr)
        self.assertFalse((Path(self.directory.name) / "writes").exists())

    def test_reporting_refuses_missing_duplicate_nonterminal_and_wrong_source_stages(self):
        self.selected_run["conclusion"] = "failure"
        jobs = [{**job, "id": identity} for identity, job in enumerate(self.jobs, 1)]
        stage = next(job for job in jobs if job["name"] == "unit-tests")
        values = {"failed_validation_run_id": "123", "failed_validation_run_attempt": "1",
                  "failed_validation_sha": self.selected}
        endpoint = f"/repos/{self.repository}/actions/runs/123/attempts/1/jobs"
        others = [job for job in jobs if job != stage]
        cases = [others, jobs + [{**stage, "id": 999}],
                 others + [{**stage, "status": "in_progress", "conclusion": None}],
                 others + [{**stage, "status": "in_progress", "conclusion": "failure"}],
                 others + [{key: value for key, value in stage.items() if key != "conclusion"}],
                 *[others + [{**stage, "conclusion": value}] for value in (None, "", 7)],
                 others + [{**stage, "head_sha": self.old}]]
        for case in cases:
            with self.subTest(jobs=case):
                self.routes[endpoint] = {"total_count": len(case), "jobs": case}
                result = self.report_validation(values)
                self.assertNotEqual(0, result.returncode)
                self.assertFalse((Path(self.directory.name) / "writes").exists())
        self.routes[endpoint] = {"total_count": len(jobs) + 1, "jobs": jobs}
        result = self.report_validation(values)
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Truncated", result.stderr)

    def test_reporting_refuses_changed_validation_identity(self):
        self.selected_run["conclusion"] = "failure"
        values = {"failed_validation_run_id": "123", "failed_validation_run_attempt": "1",
                  "failed_validation_sha": self.selected}
        endpoint = f"/repos/{self.repository}/actions/runs/123/attempts/1"
        for field, value in (("id", 124), ("run_attempt", 2), ("head_sha", self.old),
                             ("event", "schedule"), ("workflow_id", 46), ("head_branch", "topic"),
                             ("path", ".github/workflows/other.yml"),
                             ("head_repository", {"full_name": "someone/fork"})):
            with self.subTest(field=field):
                self.routes[endpoint] = {**self.selected_run, field: value}
                result = self.report_validation(values)
                self.assertNotEqual(0, result.returncode)
                self.assertIn("identity changed", result.stderr)
                self.assertFalse((Path(self.directory.name) / "writes").exists())

    def test_dispatches_full_validation_for_the_tip_and_uses_its_exact_evidence(self):
        result, values = self.prepare()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("true", values["publish"])
        self.assertEqual(self.selected, values["source_sha"])
        self.assertEqual("123", values["validation_run_id"])
        self.assertEqual("1", values["validation_run_attempt"])
        self.assertEqual([(f"/repos/{self.repository}/actions/workflows/quality-gate.yml/dispatches", {"ref": "main"})], self.api.posts)
        self.assertFalse(any("/commits/" in path for path in self.api.requests))
        queries = [parse_qs(urlsplit(path).query) for path in self.api.requests if "quality-gate.yml/runs?" in path]
        self.assertTrue(queries)
        for query in queries:
            self.assertEqual(["workflow_dispatch"], query["event"])
            self.assertEqual(["main"], query["branch"])
            self.assertEqual([self.selected], query["head_sha"])
            self.assertEqual([">=2026-01-01T00:00:00Z"], query["created"])
        self.assertEqual("7002", values["version_code"])
        self.assertEqual("nightly-7002-bbbbbbbbbbbb", values["version_name"])
        self.assertEqual(f"nightly-7002-{self.selected}", values["tag"])

    def test_does_not_build_or_publish_the_same_source_twice(self):
        self.releases.append({
            "id": 1, "tag_name": f"nightly-6001-{self.selected}", "name": "Nightly",
            "prerelease": True, "draft": False, "target_commitish": self.selected,
        })
        result, values = self.prepare()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("false", values["publish"])
        self.assertNotIn("version_code", values)
        self.assertEqual([], self.api.posts)
        self.assertEqual([], self.api.patches)
        self.assertEqual([], self.api.deletes)
        self.releases[0]["draft"] = True
        result, values = self.prepare()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("true", values["publish"])
        self.assertEqual(f"nightly-7002-{self.selected}", values["tag"])
        self.assertEqual([], self.api.deletes)

    def test_a_malformed_retained_draft_still_needs_operator_recovery(self):
        self.releases.append({"id": 1, "tag_name": f"nightly-6001-{self.selected}", "draft": True})
        result, values = self.prepare()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Existing Nightly identity is inconsistent", result.stderr)
        self.assertEqual({}, values)
        self.assertEqual([], self.api.posts)
        self.assertEqual([], self.api.deletes)

    def test_a_draft_for_another_source_does_not_block_the_nightly(self):
        self.releases.append({
            "id": 1, "tag_name": f"nightly-6001-{self.old}", "name": "Nightly",
            "prerelease": True, "draft": True, "target_commitish": self.old,
        })
        result, values = self.prepare()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("true", values["publish"])
        self.assertEqual([], self.api.deletes)

    def test_codes_increase_across_full_reruns_and_new_runs(self):
        for number, attempt, expected in (("7", "2", "7002"), ("7", "3", "7003"), ("8", "1", "8001")):
            with self.subTest(number=number, attempt=attempt):
                self.runs.clear()
                self.environment.update(GITHUB_RUN_NUMBER=number, GITHUB_RUN_ATTEMPT=attempt)
                result, values = self.prepare()
                self.assertEqual(0, result.returncode, result.stderr)
                self.assertEqual(expected, values["version_code"])

    def test_rejects_old_or_reused_codes_and_out_of_range_attempts(self):
        self.releases.append({"id": 1, "tag_name": f"nightly-8001-{self.old}"})
        result, values = self.prepare()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("not newer", result.stderr)
        self.assertEqual({}, values)
        self.releases.clear()
        for name, value in (("GITHUB_RUN_ATTEMPT", "1000"), ("GITHUB_RUN_NUMBER", "2100001"), ("GITHUB_RUN_NUMBER", "0")):
            with self.subTest(name=name, value=value):
                original = self.environment[name]
                self.environment[name] = value
                self.assertNotEqual(0, self.prepare()[0].returncode)
                self.environment[name] = original

    def test_missing_validation_evidence_fails_even_when_the_run_reports_success(self):
        self.jobs[0]["conclusion"] = "skipped"
        result, values = self.prepare()
        self.assertNotEqual(0, result.returncode)
        self.assertEqual({}, values)
        self.assertIn("Required validation evidence failed", result.stderr)

    def test_waits_for_the_started_run_to_appear_and_complete(self):
        pending = {**self.selected_run, "status": "in_progress", "conclusion": None}
        states = [pending, self.selected_run, self.selected_run]
        self.routes[f"/repos/{self.repository}/actions/runs/123"] = lambda: states.pop(0)
        result, values = self.prepare()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("123", values["validation_run_id"])

    def test_never_appearing_validation_fails_closed_without_historical_fallback(self):
        self.routes[("POST", f"/repos/{self.repository}/actions/workflows/quality-gate.yml/dispatches")] = 204
        result, values = self.prepare()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("did not appear", result.stderr)
        self.assertEqual({}, values)

    def test_validation_that_exceeds_the_bound_fails_closed(self):
        self.selected_run.update(status="in_progress", conclusion=None)
        result, values = self.prepare()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("did not finish within", result.stderr)
        self.assertEqual({}, values)

    def test_cancelled_failed_and_unknown_validation_never_publish(self):
        for conclusion in ("cancelled", "failure", "timed_out", None, "unknown"):
            with self.subTest(conclusion=conclusion):
                self.runs.clear()
                self.selected_run["conclusion"] = conclusion
                result, values = self.prepare()
                self.assertNotEqual(0, result.returncode)
                self.assertIn("Dispatched validation", result.stderr)
                if conclusion in {"cancelled", "failure", "timed_out"}:
                    self.assertEqual({
                        "validation_failed": "true", "failed_validation_run_id": "123",
                        "failed_validation_run_attempt": "1", "failed_validation_sha": self.selected,
                    }, values)
                else:
                    self.assertEqual({}, values)
                self.assertNotIn("publish", values)
                self.assertNotIn("version_code", values)
        self.runs.clear()
        self.selected_run.update(status="unknown", conclusion="success")
        result, values = self.prepare()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Unknown validation state", result.stderr)
        self.assertEqual({}, values)

    def test_push_validation_and_fork_runs_cannot_be_nightly_evidence(self):
        self.selected_run["event"] = "push"
        result, values = self.prepare()
        self.assertNotEqual(0, result.returncode)
        self.assertEqual({}, values)
        self.runs.clear()
        self.selected_run["event"] = "workflow_dispatch"
        self.selected_run["head_repository"] = {"full_name": "someone/fork"}
        self.assertNotEqual(0, self.prepare()[0].returncode)

    def test_wrong_tip_and_changed_dispatch_identity_fail_closed(self):
        endpoint = f"/repos/{self.repository}/actions/runs/123"
        for field, value in (("id", 999), ("head_sha", self.old), ("run_attempt", 2),
                             ("event", "push"), ("path", ".github/workflows/other.yml"),
                             ("workflow_id", 999), ("head_branch", "topic")):
            with self.subTest(field=field):
                self.runs.clear()
                self.routes[endpoint] = lambda: {**self.selected_run, field: value}
                result, values = self.prepare()
                self.assertNotEqual(0, result.returncode)
                self.assertEqual({}, values)
        self.routes[f"/repos/{self.repository}/branches/main"]["commit"]["sha"] = "bad-tip"
        self.api.posts.clear()
        result, values = self.prepare()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Invalid main tip", result.stderr)
        self.assertEqual([], self.api.posts)
        self.assertEqual([], self.api.patches)
        self.assertEqual([], self.api.deletes)

    def test_dispatch_failure_is_not_publication_recovery(self):
        self.routes[("POST", f"/repos/{self.repository}/actions/workflows/quality-gate.yml/dispatches")] = 403
        result, values = self.prepare()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Validation dispatch failed", result.stderr)
        self.assertNotIn("retained draft", result.stderr)
        self.assertEqual({}, values)

    def test_waits_for_delayed_discovery(self):
        endpoint = f"/repos/{self.repository}/actions/workflows/quality-gate.yml/runs"
        responses = [[], [], [self.selected_run]]
        self.routes[endpoint] = lambda: {"total_count": len(responses[0]), "workflow_runs": responses.pop(0) if len(responses) > 1 else responses[0]}
        result, values = self.prepare()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("123", values["validation_run_id"])

    def test_historical_runs_are_not_substitutes_for_the_dispatch(self):
        historical = {**self.selected_run, "created_at": "2026-01-01T00:00:00Z"}
        self.runs.append(historical)
        result, values = self.prepare()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("did not appear", result.stderr)
        self.assertEqual({}, values)

    def twin_dispatch(self, twin):
        prefix = f"/repos/{self.repository}"
        self.routes.update({
            f"{prefix}/actions/runs/124": twin,
            f"{prefix}/actions/runs/124/attempts/1": twin,
            f"{prefix}/actions/runs/124/attempts/1/jobs": {"total_count": len(self.jobs), "jobs": self.jobs},
        })

        def dispatch(payload):
            self.selected_run["created_at"] = "2026-01-01T00:00:00Z"
            twin.setdefault("created_at", "2026-01-01T00:00:00Z")
            self.runs[:] = [self.selected_run, twin]
            return 204
        self.runs.clear()
        self.routes[("POST", f"{prefix}/actions/workflows/quality-gate.yml/dispatches")] = dispatch

    def test_twin_dispatches_select_the_newest_successful_completed_run(self):
        for conclusions, expected in ((("success", "cancelled"), "123"), (("cancelled", "success"), "124"),
                                      (("success", "success"), "124")):
            with self.subTest(conclusions=conclusions):
                twin = {**self.selected_run, "id": 124, "conclusion": conclusions[1]}
                self.selected_run["conclusion"] = conclusions[0]
                self.twin_dispatch(twin)
                result, values = self.prepare()
                self.assertEqual(0, result.returncode, result.stderr)
                self.assertEqual("true", values["publish"])
                self.assertEqual(expected, values["validation_run_id"])

    def test_the_newest_successful_twin_wins_even_with_a_later_created_at(self):
        twin = {**self.selected_run, "id": 124, "conclusion": "success", "created_at": "2026-01-01T00:01:00Z"}
        self.twin_dispatch(twin)
        result, values = self.prepare()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("124", values["validation_run_id"])

    def test_unsuccessful_twin_dispatches_fail_closed_on_the_newest_completed_run(self):
        twin = {**self.selected_run, "id": 124, "conclusion": "cancelled"}
        self.selected_run["conclusion"] = "failure"
        self.twin_dispatch(twin)
        result, values = self.prepare()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Dispatched validation 124 did not succeed", result.stderr)
        self.assertEqual({
            "validation_failed": "true", "failed_validation_run_id": "124",
            "failed_validation_run_attempt": "1", "failed_validation_sha": self.selected,
        }, values)

    def test_old_creation_time_or_moved_dispatch_source_cannot_match(self):
        endpoint = f"/repos/{self.repository}/actions/workflows/quality-gate.yml/runs"
        for changed in ({"created_at": "2025-12-31T23:59:59Z"}, {"head_sha": self.old}):
            with self.subTest(changed=changed):
                responses = [[], [{**self.selected_run, "created_at": "2026-01-01T00:00:00Z", **changed}]]
                self.routes[endpoint] = lambda: {"total_count": len(responses[0]), "workflow_runs": responses.pop(0) if len(responses) > 1 else responses[0]}
                result, values = self.prepare()
                self.assertNotEqual(0, result.returncode)
                self.assertIn("did not appear", result.stderr)
                self.assertEqual({}, values)

    def test_a_newer_main_tip_during_validation_does_not_change_the_release_source(self):
        def dispatch(payload):
            self.routes[f"/repos/{self.repository}/branches/main"]["commit"]["sha"] = "c" * 40
            return self.dispatch(payload)
        self.routes[("POST", f"/repos/{self.repository}/actions/workflows/quality-gate.yml/dispatches")] = dispatch
        result, values = self.prepare()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(self.selected, values["source_sha"])

    def test_truncated_run_discovery_and_duplicate_release_history_fail_closed(self):
        endpoint = f"/repos/{self.repository}/actions/workflows/quality-gate.yml/runs"
        self.routes[endpoint] = {"total_count": 1, "workflow_runs": []}
        result, values = self.prepare()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Truncated Nightly history", result.stderr)
        self.assertEqual([], self.api.posts)
        self.assertEqual([], self.api.patches)
        self.assertEqual([], self.api.deletes)
        self.releases.extend([
            {"id": identity, "tag_name": f"nightly-{code}-{self.selected}"}
            for identity, code in ((1, 6001), (2, 6002))
        ])
        result, values = self.prepare()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Multiple Nightlies", result.stderr)
        self.assertEqual([], self.api.posts)
        self.assertEqual([], self.api.patches)
        self.assertEqual([], self.api.deletes)

    def test_duplicate_check_reads_all_release_pages_before_dispatch(self):
        endpoint = f"/repos/{self.repository}/releases"
        self.routes[endpoint + "?per_page=100&page=1"] = [
            {"id": identity, "tag_name": f"unrelated-{identity}"} for identity in range(1, 101)
        ]
        self.routes[endpoint + "?per_page=100&page=2"] = [{
            "id": 101, "tag_name": f"nightly-6001-{self.selected}", "name": "Nightly",
            "prerelease": True, "draft": False, "target_commitish": self.selected,
        }]
        result, values = self.prepare()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("false", values["publish"])
        self.assertEqual([], self.api.posts)
        self.assertEqual([], self.api.patches)
        self.assertEqual([], self.api.deletes)

    def signed_publication_fixture(self):
        root = Path(self.directory.name)
        fixture = make_signing_fixture(root / "signing", 7002, "nightly-7002-bbbbbbbbbbbb")
        signing_environment = {
            **fixture.environment, **self.environment, "HOME": str(root),
            "RELEASE_SOURCE_SHA": self.selected, "RELEASE_VERSION_CODE": "7002",
            "RELEASE_GENERATION_ATTEMPT": "2", "RELEASE_VALIDATION_RUN_ID": "123",
            "RELEASE_VALIDATION_RUN_ATTEMPT": "1",
        }
        self.signed = root / "signed"
        result = subprocess.run([
            "python3", str(ROOT / ".github/scripts/sign-release-apk.py"), str(fixture.apk),
            str(self.signed), str(fixture.certificate_file),
        ], env=signing_environment, capture_output=True, text=True, check=False)
        self.assertEqual(0, result.returncode, result.stderr)
        self.environment = {key: value for key, value in signing_environment.items() if not key.startswith("ANDROID_RELEASE_KEYSTORE_")}
        self.certificate = fixture.certificate_file
        binary = root / "bin"
        binary.mkdir()
        shutil.copyfile(ROOT / ".github/scripts/tests/fixtures/fake-gh-release.py", binary / "gh")
        (binary / "gh").chmod(0o755)
        self.environment["PATH"] = str(binary) + os.pathsep + self.environment["PATH"]
        self.environment["NIGHTLY_FAILURE_OBSERVATION"] = str(root / "publication-state.json")
        self.environment["GITHUB_SHA"] = self.new
        self.provider = root / "release-fixture"
        self.provider.mkdir()
        self.release_file = self.provider / "release.json"
        prefix = f"/repos/{self.repository}"
        self.tag = f"nightly-7002-{self.selected}"
        self.routes.update({
            f"{prefix}/releases": lambda: [json.loads(self.release_file.read_text())] if self.release_file.exists() else [],
            f"{prefix}/releases/1": lambda: json.loads(self.release_file.read_text()),
            f"{prefix}/git/matching-refs/tags/{self.tag}": [],
            f"{prefix}/git/ref/tags/{self.tag}": {"object": {"type": "commit", "sha": self.selected}},
            f"{prefix}/environments/nightly": {
                "id": 10, "name": "nightly", "can_admins_bypass": False,
                "protection_rules": [{"type": "branch_policy"}],
                "deployment_branch_policy": {"protected_branches": False, "custom_branch_policies": True},
            },
            f"{prefix}/environments/nightly/deployment-branch-policies": {"total_count": 1, "branch_policies": [{"name": "main", "type": "branch"}]},
            f"{prefix}/actions/runs/456/approvals": [],
        })

    def publish(self):
        return subprocess.run([
            "python3", str(ROOT / ".github/scripts/nightly-release.py"), "publish",
            str(self.signed), str(self.certificate),
        ], env=self.environment, capture_output=True, text=True, check=False)

    def report_fixture(self, failure="publish", observation=True):
        root = Path(self.directory.name)
        self.report_store = root / "report-fixture"
        self.report_store.mkdir(exist_ok=True)
        binary = root / "report-bin"
        binary.mkdir(exist_ok=True)
        shutil.copyfile(ROOT / ".github/scripts/tests/fixtures/fake-gh-nightly-report.py", binary / "gh")
        shutil.copyfile(ROOT / ".github/scripts/tests/fixtures/issue_store.py", binary / "issue_store.py")
        (binary / "gh").chmod(0o755)
        self.report_environment = {
            **self.environment, "PATH": str(binary) + os.pathsep + self.environment["PATH"],
            "HOME": str(root), "GH_TOKEN": "synthetic-token", "GITHUB_RUN_ID": "456",
            "GITHUB_SHA": self.new,
        }
        self.report_jobs = [
            {"id": 20, "name": "sign / prepare", "head_sha": self.new, "status": "completed", "conclusion": "failure" if failure == "sign" else "success"},
            {"id": 21, "name": "publish", "head_sha": self.new, "status": "completed", "conclusion": "failure" if failure == "publish" else "skipped"},
        ]
        prefix = f"/repos/{self.repository}"
        self.report_run = {
            "id": 456, "run_attempt": 2, "head_sha": self.new, "head_branch": "main",
            "repository": {"full_name": self.repository}, "head_repository": {"full_name": self.repository},
            "path": ".github/workflows/nightly-release.yml", "event": "schedule", "status": "in_progress",
        }
        self.report_artifacts = []
        if observation:
            with zipfile.ZipFile(self.report_store / "observation.zip", "w") as archive:
                archive.write(self.environment["NIGHTLY_FAILURE_OBSERVATION"], "publication-state.json")
            self.report_artifacts.append({"id": 31, "name": "nightly-publication-state-456-2", "expired": False})
        self.routes.update({
            f"{prefix}/actions/runs/456/attempts/2": self.report_run,
            f"{prefix}/actions/runs/456/attempts/2/jobs": {"total_count": len(self.report_jobs), "jobs": self.report_jobs},
            f"{prefix}/actions/runs/456/artifacts": {"total_count": len(self.report_artifacts), "artifacts": self.report_artifacts},
        })

    def report(self):
        return subprocess.run(["python3", str(ROOT / ".github/scripts/create-nightly-release-failure-issue.py")],
                              env=self.report_environment, capture_output=True, text=True, check=False)

    def test_refused_publication_reports_readback_identity_without_changing_release_state(self):
        self.signed_publication_fixture()
        (self.provider / "fail-upload").touch()
        self.assertNotEqual(0, self.publish().returncode)
        before = {path.relative_to(self.provider): path.read_bytes() for path in self.provider.rglob("*") if path.is_file()}
        self.report_fixture()
        result = self.report()
        self.assertEqual(0, result.returncode, result.stderr)
        issue = json.loads((self.report_store / "issue.json").read_text())
        for text in ("<!-- nightly-release-run:456 -->", "Attempt: `2`", self.new, self.selected,
                     "Version code: `7002`", self.tag, "Release id: `1`", "Incomplete draft: `yes`",
                     "Orphan tag: `no`", "nightly-publication-state-456-2", "signed-release-456-2: missing"):
            self.assertIn(text, issue["body"])
        self.assertEqual([{"name": "ready-for-agent"}], issue["labels"])
        repeated = self.report()
        self.assertEqual(0, repeated.returncode, repeated.stderr)
        self.assertEqual(["POST", "PATCH"], (self.report_store / "writes").read_text().splitlines())
        expected = json.loads((ROOT / ".github/scripts/tests/fixtures/nightly-publication-failure-issue.json").read_text())
        self.assertEqual(expected, json.loads((self.report_store / "issue.json").read_text()))
        after = {path.relative_to(self.provider): path.read_bytes() for path in self.provider.rglob("*") if path.is_file()}
        self.assertEqual(before, after)

    def test_known_terminal_release_stage_failures_file_exactly_one_issue(self):
        self.report_fixture(observation=False)
        for name in ("publish", "sign / prepare"):
            for conclusion in ("failure", "timed_out", "startup_failure", "action_required", "stale", "neutral"):
                with self.subTest(stage=name, conclusion=conclusion):
                    for job in self.report_jobs:
                        job["conclusion"] = conclusion if job["name"] == name else "skipped"
                    (self.report_store / "issue.json").unlink(missing_ok=True)
                    (self.report_store / "writes").unlink(missing_ok=True)
                    result = self.report()
                    self.assertEqual(0, result.returncode, result.stderr)
                    issue = json.loads((self.report_store / "issue.json").read_text())
                    self.assertIn(f"- `{name}`: `failure`", issue["body"])
                    self.assertEqual(["POST"], (self.report_store / "writes").read_text().splitlines())

    def test_completed_unsuccessful_release_runs_report_verified_failed_stages(self):
        self.report_fixture(failure="sign", observation=False)
        for conclusion in ("failure", "cancelled", "timed_out", "startup_failure", "action_required", "stale", "neutral"):
            with self.subTest(conclusion=conclusion):
                self.report_run.update(status="completed", conclusion=conclusion)
                (self.report_store / "issue.json").unlink(missing_ok=True)
                (self.report_store / "writes").unlink(missing_ok=True)
                result = self.report()
                self.assertEqual(0, result.returncode, result.stderr)
                self.assertIn("`sign / prepare`: `failure`", json.loads((self.report_store / "issue.json").read_text())["body"])
                self.assertEqual(["POST"], (self.report_store / "writes").read_text().splitlines())

    def test_signer_failure_before_outputs_reports_unknown_identity_and_missing_artifacts(self):
        self.report_fixture(failure="sign", observation=False)
        result = self.report()
        self.assertEqual(0, result.returncode, result.stderr)
        body = json.loads((self.report_store / "issue.json").read_text())["body"]
        for text in ("sign / prepare", "Source SHA: `unknown`", "Version code: `unknown`", "Tag: `unknown`",
                     "Release id: `unknown`", "Incomplete draft: `unknown`", "Orphan tag: `unknown`",
                     "Publication state artifact: missing", "reusable signer", "nightly-publication-state-456-2: missing"):
            self.assertIn(text, body)
        self.assertNotIn("/artifacts/", body)
        self.assertTrue(all("/actions/" in line or "/issues" in line
                            for line in (self.report_store / "requests").read_text().splitlines()))

    def test_signer_failure_reports_prepared_identity_without_claiming_remote_state(self):
        self.report_fixture(failure="sign", observation=False)
        self.report_environment.update(RELEASE_SOURCE_SHA=self.selected, RELEASE_VERSION_CODE="7002")
        result = self.report()
        self.assertEqual(0, result.returncode, result.stderr)
        body = json.loads((self.report_store / "issue.json").read_text())["body"]
        for text in (f"Source SHA: `{self.selected}`", "Version code: `7002`",
                     f"Tag: `nightly-7002-{self.selected}`", "Release id: `unknown`",
                     "Release state: `unknown`", "Incomplete draft: `unknown`", "Tag exists: `unknown`",
                     "Orphan tag: `unknown`", "intended identity, not proof a release exists",
                     "Publication state artifact: missing"):
            self.assertIn(text, body)
        self.assertEqual(["POST"], (self.report_store / "writes").read_text().splitlines())

    def test_later_attempt_reopens_and_updates_the_same_run_issue(self):
        self.report_fixture(failure="sign", observation=False)
        self.assertEqual(0, self.report().returncode)
        issue_file = self.report_store / "issue.json"
        issue = json.loads(issue_file.read_text())
        issue["state"] = "closed"
        issue_file.write_text(json.dumps(issue))
        self.report_environment["GITHUB_RUN_ATTEMPT"] = "3"
        self.report_run["run_attempt"] = 3
        prefix = f"/repos/{self.repository}/actions/runs/456/attempts"
        self.routes[f"{prefix}/3"] = self.report_run
        self.routes[f"{prefix}/3/jobs"] = self.routes[f"{prefix}/2/jobs"]
        result = self.report()
        self.assertEqual(0, result.returncode, result.stderr)
        issue = json.loads(issue_file.read_text())
        self.assertEqual("open", issue["state"])
        self.assertEqual(17, issue["number"])
        self.assertIn("Attempt: `3`", issue["body"])
        self.assertEqual(["POST", "PATCH"], (self.report_store / "writes").read_text().splitlines())

    def test_reporter_refuses_success_skips_cancellation_and_foreign_run_evidence(self):
        self.report_fixture(failure="none", observation=False)
        for conclusion in ("success", "skipped", "cancelled", "unexpected_terminal", None):
            self.report_jobs[0]["conclusion"] = conclusion
            result = self.report()
            self.assertNotEqual(0, result.returncode)
            self.assertIn("No failed signing or publication", result.stderr)
            self.assertFalse((self.report_store / "issue.json").exists())
        self.report_jobs[0]["conclusion"] = "failure"
        self.report_run["path"] = ".github/workflows/quality-gate.yml"
        self.assertNotEqual(0, self.report().returncode)
        self.assertFalse((self.report_store / "issue.json").exists())

    def test_reporter_refuses_a_successful_run_even_with_inconsistent_failed_jobs(self):
        self.report_fixture(failure="sign", observation=False)
        for conclusion in ("success", "skipped", "unexpected_terminal", None):
            with self.subTest(conclusion=conclusion):
                self.report_run.update(status="completed", conclusion=conclusion)
                self.assertNotEqual(0, self.report().returncode)
                self.assertFalse((self.report_store / "issue.json").exists())
                self.assertFalse((self.report_store / "writes").exists())

    def test_reporter_refuses_ambiguous_issues_and_verifies_the_issue_readback(self):
        self.report_fixture(failure="sign", observation=False)
        (self.report_store / "wrong-readback").touch()
        result = self.report()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Published issue does not match", result.stderr)
        (self.report_store / "wrong-readback").unlink()
        (self.report_store / "duplicate").touch()
        result = self.report()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Multiple issues", result.stderr)
        self.assertEqual(["POST"], (self.report_store / "writes").read_text().splitlines())

    def test_reporter_rejects_truncated_jobs_and_never_links_expired_artifacts(self):
        self.report_fixture(failure="sign", observation=False)
        endpoint = f"/repos/{self.repository}/actions/runs/456/attempts/2/jobs"
        self.routes[endpoint]["total_count"] += 1
        self.assertNotEqual(0, self.report().returncode)
        self.assertFalse((self.report_store / "issue.json").exists())
        self.routes[endpoint]["total_count"] -= 1
        self.report_artifacts.extend([
            {"id": 32, "name": "signed-release-456-2", "expired": True},
            {"id": 33, "name": "unsigned-release-456-1", "expired": False},
        ])
        self.routes[f"/repos/{self.repository}/actions/runs/456/artifacts"]["total_count"] = 2
        self.assertEqual(0, self.report().returncode)
        body = json.loads((self.report_store / "issue.json").read_text())["body"]
        self.assertNotIn("/artifacts/", body)
        self.assertIn("signed-release-456-2: missing or expired", body)

    def test_retained_draft_and_tag_are_read_back_even_for_an_older_version(self):
        self.signed_publication_fixture()
        tag = f"nightly-6001-{self.selected}"
        retained = {
            "id": 1, "tag_name": tag, "name": "Nightly", "prerelease": True,
            "draft": True, "target_commitish": self.selected, "assets": [],
        }
        self.release_file.write_text(json.dumps(retained))
        self.routes[f"/repos/{self.repository}/git/matching-refs/tags/{tag}"] = [{"ref": f"refs/tags/{tag}"}]
        self.routes[f"/repos/{self.repository}/git/matching-refs/tags/{self.tag}"] = [
            {"ref": f"refs/tags/{self.tag}", "object": {"sha": self.selected, "type": "commit"}},
        ]
        before = self.release_file.read_bytes()
        result = self.publish()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Nightly tag already exists; operator recovery is required", result.stderr)
        self.assertEqual([], self.api.deletes)
        self.assertEqual(before, self.release_file.read_bytes())
        self.assertFalse((self.provider / "operations").exists())
        self.report_fixture()
        result = self.report()
        self.assertEqual(0, result.returncode, result.stderr)
        body = json.loads((self.report_store / "issue.json").read_text())["body"]
        for text in ("Version code: `6001`", tag, "Release id: `1`", "Incomplete draft: `yes`", "Tag exists: `yes`", "Orphan tag: `no`"):
            self.assertIn(text, body)
        self.assertEqual(before, self.release_file.read_bytes())

    def test_a_published_release_for_the_source_refuses_the_retry_without_any_delete(self):
        self.signed_publication_fixture()
        published = {
            "id": 1, "tag_name": f"nightly-6001-{self.selected}", "name": "Nightly",
            "prerelease": True, "draft": False, "target_commitish": self.selected,
        }
        self.release_file.write_text(json.dumps(published))
        self.routes[f"/repos/{self.repository}/git/matching-refs/tags/{published['tag_name']}"] = [
            {"ref": f"refs/tags/{published['tag_name']}", "object": {"sha": self.selected, "type": "commit"}},
        ]
        before = self.release_file.read_bytes()
        result = self.publish()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("This source already has a published Nightly", result.stderr)
        self.assertEqual([], self.api.deletes)
        self.assertFalse((self.provider / "operations").exists())
        self.assertEqual(before, self.release_file.read_bytes())

    def test_failure_observation_uses_created_id_even_when_release_listing_is_unavailable(self):
        self.signed_publication_fixture()
        (self.provider / "fail-upload").touch()
        listings = iter([[], {"message": "listing unavailable"}])
        self.routes[f"/repos/{self.repository}/releases"] = lambda: next(listings)
        self.assertNotEqual(0, self.publish().returncode)
        observation = json.loads(Path(self.environment["NIGHTLY_FAILURE_OBSERVATION"]).read_text())
        self.assertEqual(1, observation["release_id"])
        self.assertEqual("yes", observation["incomplete_draft"])

    def test_missing_release_readback_is_unknown_not_absent_and_stale_artifact_is_refused(self):
        self.signed_publication_fixture()
        (self.provider / "fail-upload").touch()
        del self.routes[f"/repos/{self.repository}/releases/1"]
        self.assertNotEqual(0, self.publish().returncode)
        self.report_fixture()
        result = self.report()
        self.assertEqual(0, result.returncode, result.stderr)
        body = json.loads((self.report_store / "issue.json").read_text())["body"]
        for text in ("Release id: `unknown`", "Incomplete draft: `unknown`", "Orphan tag: `unknown`", "Read-back incomplete"):
            self.assertIn(text, body)
        observation = json.loads(Path(self.environment["NIGHTLY_FAILURE_OBSERVATION"]).read_text())
        observation["attempt"] = "1"
        with zipfile.ZipFile(self.report_store / "observation.zip", "w") as archive:
            archive.writestr("publication-state.json", json.dumps(observation))
        result = self.report()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("another run or attempt", result.stderr)
        self.assertEqual(["POST"], (self.report_store / "writes").read_text().splitlines())

    def test_publishes_only_after_real_apk_and_remote_asset_readback_then_deduplicates(self):
        result, values = self.prepare()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(self.selected, values["source_sha"])
        self.assertEqual("123", values["validation_run_id"])
        self.signed_publication_fixture()
        result = self.publish()
        self.assertEqual(0, result.returncode, result.stderr)
        release = json.loads(self.release_file.read_text())
        self.assertFalse(any("/approvals" in path for path in self.api.requests))
        self.assertEqual("Nightly", release["name"])
        self.assertIs(False, release["draft"])
        self.assertIs(True, release["prerelease"])
        self.assertEqual(self.selected, release["target_commitish"])
        self.assertIn("not a stable-support promise", release["body"])
        self.assertIn("nightly-7002-bbbbbbbbbbbb", release["body"])
        self.assertEqual(["create", "upload", "download", "edit"], (self.provider / "operations").read_text().splitlines())
        self.assertEqual({"mercurio.apk", "SHA256SUMS", "signing-metadata.json"}, {item["name"] for item in release["assets"]})
        result, values = self.prepare()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("false", values["publish"])
        self.assertEqual(["create", "upload", "download", "edit"], (self.provider / "operations").read_text().splitlines())

    def test_draft_listing_can_omit_a_created_release_without_blocking_exact_id_readback(self):
        self.signed_publication_fixture()
        self.routes[f"/repos/{self.repository}/releases"] = []
        result = self.publish()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertFalse(json.loads(self.release_file.read_text())["draft"])
        self.assertEqual(["create", "upload", "download", "edit"], (self.provider / "operations").read_text().splitlines())

    def test_a_creation_response_with_a_mismatched_release_id_fails_closed(self):
        self.signed_publication_fixture()
        (self.provider / "wrong-create-id").touch()
        self.routes[f"/repos/{self.repository}/releases/2"] = lambda: json.loads(self.release_file.read_text())
        result = self.publish()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Release identity changed", result.stderr)
        self.assertEqual(["create"], (self.provider / "operations").read_text().splitlines())
        self.assertIs(True, json.loads(self.release_file.read_text())["draft"])

    def test_partial_upload_leaves_a_draft_that_a_retry_replaces(self):
        self.signed_publication_fixture()
        (self.provider / "fail-upload").touch()
        result = self.publish()
        self.assertNotEqual(0, result.returncode)
        self.assertIs(True, json.loads(self.release_file.read_text())["draft"])
        self.assertEqual(["create", "upload"], (self.provider / "operations").read_text().splitlines())
        observation = json.loads(Path(self.environment["NIGHTLY_FAILURE_OBSERVATION"]).read_text())
        self.assertEqual("456", observation["run_id"])
        self.assertEqual("2", observation["attempt"])
        self.assertEqual(self.new, observation["commit"])
        self.assertEqual(1, observation["release_id"])
        self.assertEqual(self.selected, observation["source_sha"])
        self.assertEqual("7002", observation["version_code"])
        self.assertEqual(self.tag, observation["tag"])
        self.assertEqual("yes", observation["incomplete_draft"])
        self.assertEqual("no", observation["tag_exists"])
        self.assertEqual("no", observation["orphan_tag"])
        self.assertIn(f"/repos/{self.repository}/releases/1", self.api.requests)
        (self.provider / "fail-upload").unlink()
        root = Path(self.directory.name)
        retry = make_signing_fixture(root / "signing-retry", 7003, f"nightly-7003-{self.selected[:12]}")
        retry_environment = {
            **self.environment, **{k: v for k, v in retry.environment.items() if k != "PATH"},
            "GITHUB_RUN_ATTEMPT": "3", "HOME": str(root),
            "RELEASE_SOURCE_SHA": self.selected, "RELEASE_VERSION_CODE": "7003",
            "RELEASE_GENERATION_ATTEMPT": "3", "RELEASE_VALIDATION_RUN_ID": "123",
            "RELEASE_VALIDATION_RUN_ATTEMPT": "1",
        }
        self.signed = root / "signed-retry"
        result = subprocess.run([
            "python3", str(ROOT / ".github/scripts/sign-release-apk.py"), str(retry.apk),
            str(self.signed), str(retry.certificate_file),
        ], env=retry_environment, capture_output=True, text=True, check=False)
        self.assertEqual(0, result.returncode, result.stderr)
        self.environment = {key: value for key, value in retry_environment.items() if not key.startswith("ANDROID_RELEASE_KEYSTORE_")}
        self.certificate = retry.certificate_file
        retry_tag = f"nightly-7003-{self.selected}"
        self.routes[f"/repos/{self.repository}/git/matching-refs/tags/{retry_tag}"] = []
        self.routes[f"/repos/{self.repository}/git/ref/tags/{retry_tag}"] = {"object": {"type": "commit", "sha": self.selected}}
        result = self.publish()
        self.assertEqual(0, result.returncode, result.stderr)
        release = json.loads(self.release_file.read_text())
        self.assertIs(False, release["draft"])
        self.assertEqual(retry_tag, release["tag_name"])
        self.assertEqual(["create", "upload", "delete", "create", "upload", "download", "edit"],
                         (self.provider / "operations").read_text().splitlines())
        self.assertEqual({"mercurio.apk", "SHA256SUMS", "signing-metadata.json"},
                         {item["name"] for item in release["assets"]})

    def test_download_mismatch_never_publishes_the_draft(self):
        self.signed_publication_fixture()
        (self.provider / "corrupt-download").touch()
        result = self.publish()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("checksum mismatch", result.stderr)
        self.assertIs(True, json.loads(self.release_file.read_text())["draft"])
        self.assertEqual(["create", "upload", "download"], (self.provider / "operations").read_text().splitlines())

    def test_a_retained_draft_from_an_older_version_is_replaced_on_retry(self):
        self.signed_publication_fixture()
        older = f"nightly-6001-{self.selected}"
        self.release_file.write_text(json.dumps({
            "id": 1, "tag_name": older, "target_commitish": self.selected,
            "name": "Nightly", "prerelease": True, "draft": True, "assets": [],
        }))
        result = self.publish()
        self.assertEqual(0, result.returncode, result.stderr)
        release = json.loads(self.release_file.read_text())
        self.assertIs(False, release["draft"])
        self.assertEqual(self.tag, release["tag_name"])
        self.assertEqual(["delete", "create", "upload", "download", "edit"],
                         (self.provider / "operations").read_text().splitlines())

    def test_required_evidence_and_metadata_are_checked_before_any_write(self):
        self.signed_publication_fixture()
        metadata_file = self.signed / "signing-metadata.json"
        original = metadata_file.read_text()
        cases = {
            "source_sha": self.old, "version_code": 7001, "version_name": "stable",
            "apk_sha256": "0" * 64, "certificate_sha256": "0" * 64,
            "validation_run_attempt": 2, "signing_run_attempt": 1, "private_key": "must-not-be-published",
        }
        for key, value in cases.items():
            with self.subTest(field=key):
                metadata_file.write_text(json.dumps({**json.loads(original), key: value}))
                self.assertNotEqual(0, self.publish().returncode)
                self.assertFalse((self.provider / "operations").exists())
        metadata_file.write_text(original)
        self.jobs[0]["conclusion"] = "failure"
        self.assertNotEqual(0, self.publish().returncode)
        self.assertFalse((self.provider / "operations").exists())
        self.jobs[0]["conclusion"] = "success"
        self.routes[f"/repos/{self.repository}/environments/nightly"]["can_admins_bypass"] = True
        self.assertNotEqual(0, self.publish().returncode)
        self.assertFalse((self.provider / "operations").exists())

    def test_an_existing_tag_blocks_publication_before_creating_a_draft(self):
        self.signed_publication_fixture()
        self.routes[f"/repos/{self.repository}/git/matching-refs/tags/{self.tag}"] = [
            {"ref": f"refs/tags/{self.tag}", "object": {"sha": self.old, "type": "commit"}},
        ]
        result = self.publish()
        self.assertNotEqual(0, result.returncode)
        self.assertFalse((self.provider / "operations").exists())
        observation = json.loads(Path(self.environment["NIGHTLY_FAILURE_OBSERVATION"]).read_text())
        self.assertEqual("yes", observation["tag_exists"])
        self.assertEqual("unknown", observation["incomplete_draft"])
        self.assertEqual("unknown", observation["orphan_tag"])
        self.assertEqual("not found in release listing", observation["release_state"])
        self.report_fixture()
        result = self.report()
        self.assertEqual(0, result.returncode, result.stderr)
        body = json.loads((self.report_store / "issue.json").read_text())["body"]
        self.assertIn("Tag exists: `yes`", body)
        self.assertIn("Orphan tag: `unknown`", body)
        self.assertFalse((self.provider / "operations").exists())


class NightlyGenerationTest(unittest.TestCase):
    def test_partial_reruns_cannot_reuse_the_prepared_version_code(self):
        environment = {
            "PATH": os.environ["PATH"], "GITHUB_REPOSITORY": "Gvetri/Mercurio",
            "GITHUB_REF": "refs/heads/main", "GITHUB_REF_PROTECTED": "true",
            "GITHUB_EVENT_NAME": "schedule", "GITHUB_RUN_NUMBER": "7", "GITHUB_RUN_ATTEMPT": "2",
            "RELEASE_VERSION_CODE": "7002", "RELEASE_GENERATION_ATTEMPT": "2",
        }
        command = ["python3", str(ROOT / ".github/scripts/release-signing.py"), "generation"]
        result = subprocess.run(command, env=environment, capture_output=True, text=True, check=False)
        self.assertEqual(0, result.returncode, result.stderr)
        for field, value in (("GITHUB_RUN_ATTEMPT", "3"), ("RELEASE_VERSION_CODE", "7001"), ("RELEASE_GENERATION_ATTEMPT", "1")):
            with self.subTest(field=field):
                result = subprocess.run(command, env={**environment, field: value}, capture_output=True, text=True, check=False)
                self.assertNotEqual(0, result.returncode)


if __name__ == "__main__":
    unittest.main()
