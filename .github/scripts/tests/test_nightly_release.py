"""Provider-free Nightly policy and workflow regression tests."""
from pathlib import Path
import json
import os
import shutil
import subprocess
import tempfile
import unittest
from urllib.parse import parse_qs, urlsplit

from test_protected_main import ApiStub, job_block, job_key
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
        self.old, self.selected = "a" * 40, "b" * 40
        self.repository = "Gvetri/hermes-native-client"
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
            for name in ("fork-guard", "draft-validation", "commit-message")
        ]
        self.releases = []
        self.routes = {
            f"{prefix}/branches/main": {"commit": {"sha": self.selected}, "protected": True},
            ("POST", f"{prefix}/actions/workflows/quality-gate.yml/dispatches"): self.dispatch,
            f"{prefix}/actions/workflows/quality-gate.yml": {"id": 45},
            f"{prefix}/actions/workflows/quality-gate.yml/runs": lambda: {"total_count": len(self.runs), "workflow_runs": self.runs},
            f"{prefix}/actions/runs/123": self.selected_run,
            f"{prefix}/actions/runs/123/attempts/1/jobs": {"total_count": len(self.jobs), "jobs": self.jobs},
            f"{prefix}/compare/{self.selected}...main": {"status": "ahead", "merge_base_commit": {"sha": self.selected}},
            f"{prefix}/releases": self.releases,
        }
        self.api = ApiStub(self.routes)
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
        self.releases[0]["draft"] = True
        result, values = self.prepare()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("draft", result.stderr)
        self.assertEqual({}, values)

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
                self.assertEqual({}, values)
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
        self.routes[endpoint] = lambda: {"total_count": len(responses[0]), "workflow_runs": responses.pop(0)}
        result, values = self.prepare()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual("123", values["validation_run_id"])

    def test_historical_and_ambiguous_runs_are_not_substitutes_for_the_dispatch(self):
        endpoint = f"/repos/{self.repository}/actions/workflows/quality-gate.yml/runs"
        historical = {**self.selected_run, "created_at": "2026-01-01T00:00:00Z"}
        self.runs.append(historical)
        result, values = self.prepare()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("did not appear", result.stderr)
        self.assertEqual({}, values)
        responses = [[], [historical, {**historical, "id": 124}]]
        self.routes[endpoint] = lambda: {"total_count": len(responses[0]), "workflow_runs": responses.pop(0)}
        result, values = self.prepare()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("ambiguous", result.stderr)
        self.assertEqual({}, values)

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
        self.releases.extend([
            {"id": identity, "tag_name": f"nightly-{code}-{self.selected}"}
            for identity, code in ((1, 6001), (2, 6002))
        ])
        result, values = self.prepare()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("Multiple Nightlies", result.stderr)
        self.assertEqual([], self.api.posts)

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
        self.assertEqual({"hermes-native-client.apk", "SHA256SUMS", "signing-metadata.json"}, {item["name"] for item in release["assets"]})
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

    def test_partial_upload_leaves_a_draft_and_retry_does_not_write_again(self):
        self.signed_publication_fixture()
        (self.provider / "fail-upload").touch()
        result = self.publish()
        self.assertNotEqual(0, result.returncode)
        self.assertIs(True, json.loads(self.release_file.read_text())["draft"])
        self.assertEqual(["create", "upload"], (self.provider / "operations").read_text().splitlines())
        (self.provider / "fail-upload").unlink()
        self.assertNotEqual(0, self.publish().returncode)
        self.assertEqual(["create", "upload"], (self.provider / "operations").read_text().splitlines())

    def test_download_mismatch_never_publishes_the_draft(self):
        self.signed_publication_fixture()
        (self.provider / "corrupt-download").touch()
        result = self.publish()
        self.assertNotEqual(0, result.returncode)
        self.assertIn("checksum mismatch", result.stderr)
        self.assertIs(True, json.loads(self.release_file.read_text())["draft"])
        self.assertEqual(["create", "upload", "download"], (self.provider / "operations").read_text().splitlines())

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


class NightlyGenerationTest(unittest.TestCase):
    def test_partial_reruns_cannot_reuse_the_prepared_version_code(self):
        environment = {
            "PATH": os.environ["PATH"], "GITHUB_REPOSITORY": "Gvetri/hermes-native-client",
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
