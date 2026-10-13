#!/usr/bin/env python3
import copy
import json
import os
from pathlib import Path
import subprocess
import unittest

from test_protected_main import ApiStub

ROOT = Path(__file__).resolve().parents[3]
SCRIPT = ROOT / ".github/scripts/clear-nightly-draft.py"
PREFIX = "/repos/Gvetri/hermes-native-client"
SOURCE = "a" * 40
TAG = f"nightly-7002-{SOURCE}"


class ClearNightlyDraftTest(unittest.TestCase):
    def setUp(self):
        self.release_path = f"{PREFIX}/releases/71"
        self.tag_path = f"{PREFIX}/git/ref/tags/{TAG}"
        self.release = {
            "id": 71, "tag_name": TAG, "target_commitish": SOURCE,
            "draft": True, "published_at": None, "prerelease": True, "name": "Nightly",
            "assets": [{"id": 10, "name": "hermes-native-client.apk", "state": "uploaded"}],
        }
        self.tag = {"ref": f"refs/tags/{TAG}", "object": {"type": "commit", "sha": SOURCE}}
        self.routes = {
            self.release_path: self.release,
            self.tag_path: self.tag,
            f"{PREFIX}/releases": lambda: [self.release] if self.release_path in self.routes else [],
            f"{self.release_path}/assets": self.release["assets"],
        }
        self.api = ApiStub(self.routes)
        self.api.start()
        self.addCleanup(self.api.stop)
        self.environment = {
            "PATH": os.environ["PATH"], "GITHUB_TOKEN": "synthetic-token",
            "GITHUB_API_URL": self.api.url,
        }

    def run_command(self, *arguments):
        return subprocess.run(
            ["python3", str(SCRIPT), *(arguments or ("--release-id", "71", "--tag", TAG, "--source-sha", SOURCE))],
            cwd=ROOT, env=self.environment, capture_output=True, text=True, timeout=15,
        )

    def assert_refusal(self, message):
        before = copy.deepcopy({key: value for key, value in self.routes.items() if not callable(value)})
        result = self.run_command()
        self.assertEqual(1, result.returncode, result.stdout + result.stderr)
        self.assertIn(message, result.stderr)
        self.assertNotIn("Traceback", result.stderr)
        evidence = [json.loads(line) for line in result.stdout.splitlines()]
        self.assertEqual(["before", "after"], [item.pop("phase") for item in evidence])
        self.assertEqual(evidence[0], evidence[1])
        self.assertEqual(before, {key: value for key, value in self.routes.items() if not callable(value)})
        self.assertEqual(2, self.api.requests.count(self.release_path))
        self.assertEqual([], self.api.posts)
        self.assertEqual([], self.api.deletes)
        return result

    def enable_deletion(self):
        def remove_release():
            del self.routes[self.release_path]
            return 204

        def remove_tag():
            del self.routes[self.tag_path]
            return 204

        self.routes[("DELETE", self.release_path)] = remove_release
        self.routes[("DELETE", f"{PREFIX}/git/refs/tags/{TAG}")] = remove_tag

    def test_clears_only_named_incomplete_draft_and_orphan_tag_with_read_back(self):
        self.enable_deletion()
        unrelated = {"id": 72, "draft": False, "tag_name": "unrelated"}
        self.routes[f"{PREFIX}/releases/72"] = unrelated
        result = self.run_command()
        self.assertEqual(0, result.returncode, result.stderr)
        evidence = [json.loads(line) for line in result.stdout.splitlines()]
        self.assertEqual("before", evidence[0]["phase"])
        self.assertEqual(71, evidence[0]["release"]["id"])
        self.assertEqual({"phase": "after", "release": None, "tag": None}, evidence[-1])
        self.assertEqual([self.release_path, f"{PREFIX}/git/refs/tags/{TAG}"], self.api.deletes)
        self.assertGreaterEqual(self.api.requests.count(self.release_path), 2)
        self.assertEqual(unrelated, self.routes[f"{PREFIX}/releases/72"])
        self.assertEqual([], self.api.posts)

    def test_refuses_workflow_execution_without_remote_access(self):
        self.environment["GITHUB_ACTIONS"] = "true"
        result = self.run_command()
        self.assertEqual(1, result.returncode)
        self.assertIn("Local recovery only", result.stderr)
        self.assertEqual([], self.api.requests)
        self.assertEqual([], self.api.deletes)

    def test_refuses_inconsistent_named_tag_source_before_remote_access(self):
        result = self.run_command("--release-id", "71", "--tag", TAG, "--source-sha", "b" * 40)
        self.assertEqual(1, result.returncode)
        self.assertIn("Named tag and source disagree", result.stderr)
        self.assertEqual([], self.api.requests)

    def test_refuses_published_release_with_unchanged_before_after_evidence(self):
        self.release.update(draft=False, published_at="2026-10-13T00:00:00Z")
        self.assert_refusal("Published release")

    def test_null_api_response_is_unknown_not_absence(self):
        self.routes[self.tag_path] = None
        self.enable_deletion()
        result = self.run_command()
        self.assertEqual(1, result.returncode)
        self.assertIn("unavailable", result.stderr)
        evidence = [json.loads(line) for line in result.stdout.splitlines()]
        self.assertEqual(["before", "after"], [item["phase"] for item in evidence])
        self.assertTrue(all(item["tag"] == {"state": "unknown"} for item in evidence))
        self.assertEqual([], self.api.deletes)
        self.assertIn(self.release_path, self.routes)

    def test_unavailable_evidence_is_printed_as_unknown_before_and_after_without_writes(self):
        self.routes[self.release_path] = (403, {"message": "Forbidden"})
        result = self.run_command()
        self.assertEqual(1, result.returncode)
        self.assertIn("unavailable", result.stderr)
        evidence = [json.loads(line) for line in result.stdout.splitlines()]
        self.assertEqual(["before", "after"], [item["phase"] for item in evidence])
        self.assertTrue(all(item["release"] == {"state": "unknown"} for item in evidence))
        self.assertEqual([], self.api.deletes)

    def test_refuses_unknown_publication_state_with_unchanged_before_after_evidence(self):
        del self.release["published_at"]
        self.assert_refusal("Unknown publication state")

    def test_changed_draft_before_deletion_is_not_removed(self):
        calls = []

        def changing_release():
            calls.append(True)
            return self.release if len(calls) == 1 else {**self.release, "draft": False}

        self.routes[self.release_path] = changing_release
        result = self.run_command()
        self.assertEqual(1, result.returncode)
        self.assertIn("Recovery state changed", result.stderr)
        self.assertEqual([], self.api.deletes)
        self.assertFalse(json.loads(result.stdout.splitlines()[-1])["release"]["draft"])

    def test_new_tag_owner_after_draft_deletion_prevents_tag_deletion(self):
        self.enable_deletion()
        self.routes[f"{PREFIX}/releases"] = lambda: [self.release] if self.release_path in self.routes else [
            {"id": 72, "tag_name": TAG},
        ]
        result = self.run_command()
        self.assertEqual(1, result.returncode)
        self.assertIn("Tag is not orphaned", result.stderr)
        self.assertEqual([self.release_path], self.api.deletes)
        self.assertEqual(self.tag, json.loads(result.stdout.splitlines()[-1])["tag"])

    def test_refuses_non_draft_with_unchanged_before_after_evidence(self):
        self.release["draft"] = False
        self.assert_refusal("Release is not a draft")

    def test_refuses_missing_draft_with_unchanged_before_after_evidence(self):
        del self.routes[self.release_path]
        self.assert_refusal("Draft does not exist")

    def test_refuses_wrong_release_id_with_unchanged_before_after_evidence(self):
        self.release["id"] = 72
        self.assert_refusal("Release id mismatch")

    def test_refuses_wrong_release_tag_with_unchanged_before_after_evidence(self):
        self.release["tag_name"] = f"nightly-8001-{SOURCE}"
        self.assert_refusal("Release tag mismatch")

    def test_refuses_wrong_release_source_with_unchanged_before_after_evidence(self):
        self.release["target_commitish"] = "b" * 40
        self.assert_refusal("Release source mismatch")

    def test_refuses_foreign_assets_with_unchanged_before_after_evidence(self):
        self.release["assets"].append({"id": 11, "name": "operator-notes.txt"})
        self.assert_refusal("Foreign or duplicate assets")

    def test_refuses_complete_draft_with_unchanged_before_after_evidence(self):
        self.release["assets"].extend([
            {"id": 11, "name": "SHA256SUMS"}, {"id": 12, "name": "signing-metadata.json"},
        ])
        self.assert_refusal("Complete-looking draft")

    def test_refuses_wrong_tag_source_with_unchanged_before_after_evidence(self):
        self.tag["object"]["sha"] = "b" * 40
        self.assert_refusal("Tag source mismatch")

    def test_refuses_annotated_tag_with_unchanged_before_after_evidence(self):
        self.tag["object"]["type"] = "tag"
        self.assert_refusal("Tag source mismatch")

    def test_refuses_wrong_tag_reference_with_unchanged_before_after_evidence(self):
        self.tag["ref"] += "-other"
        self.assert_refusal("Tag reference mismatch")

    def test_refuses_ambiguous_tag_ownership_with_unchanged_before_after_evidence(self):
        self.routes[f"{PREFIX}/releases"] = [self.release, {"id": 72, "tag_name": TAG}]
        self.assert_refusal("Tag ownership is ambiguous")

    def test_refuses_non_nightly_with_unchanged_before_after_evidence(self):
        self.release["prerelease"] = False
        self.assert_refusal("Release is not a Nightly prerelease")

    def test_refuses_inconsistent_asset_listing_with_unchanged_before_after_evidence(self):
        self.routes[f"{self.release_path}/assets"] = []
        self.assert_refusal("Asset inventory changed")

    def test_clears_empty_draft_without_deleting_an_absent_tag(self):
        self.release["assets"].clear()
        del self.routes[self.tag_path]
        self.enable_deletion()
        result = self.run_command()
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual([self.release_path], self.api.deletes)
        self.assertEqual({"phase": "after", "release": None, "tag": None},
                         json.loads(result.stdout.splitlines()[-1]))

    def test_failed_release_deletion_keeps_tag_and_reports_state(self):
        self.routes[("DELETE", self.release_path)] = 403
        result = self.run_command()
        self.assertEqual(1, result.returncode)
        self.assertIn("Deletion failed", result.stderr)
        self.assertEqual([self.release_path], self.api.deletes)
        evidence = [json.loads(line) for line in result.stdout.splitlines()]
        self.assertEqual(evidence[0]["release"], evidence[-1]["release"])
        self.assertEqual(evidence[0]["tag"], evidence[-1]["tag"])

    def test_failed_tag_deletion_reports_partial_state_without_retry(self):
        self.enable_deletion()
        self.routes[("DELETE", f"{PREFIX}/git/refs/tags/{TAG}")] = 403
        result = self.run_command()
        self.assertEqual(1, result.returncode)
        self.assertIn("Deletion failed", result.stderr)
        self.assertEqual(2, len(self.api.deletes))
        after = json.loads(result.stdout.splitlines()[-1])
        self.assertIsNone(after["release"])
        self.assertEqual(self.tag, after["tag"])

    def test_success_response_without_release_deletion_fails_read_back(self):
        self.routes[("DELETE", self.release_path)] = 204
        result = self.run_command()
        self.assertEqual(1, result.returncode)
        self.assertIn("Draft deletion read-back failed", result.stderr)
        self.assertEqual([self.release_path], self.api.deletes)
        self.assertEqual(self.release, json.loads(result.stdout.splitlines()[-1])["release"])

    def test_success_response_without_tag_deletion_fails_read_back(self):
        self.enable_deletion()
        self.routes[("DELETE", f"{PREFIX}/git/refs/tags/{TAG}")] = 204
        result = self.run_command()
        self.assertEqual(1, result.returncode)
        self.assertIn("Deletion read-back failed", result.stderr)
        self.assertEqual(self.tag, json.loads(result.stdout.splitlines()[-1])["tag"])

    def test_local_command_is_not_referenced_by_workflows_or_other_scripts(self):
        for path in (ROOT / ".github/workflows").glob("*.yml"):
            self.assertNotIn(SCRIPT.name, path.read_text(), str(path))
        for path in (ROOT / ".github/scripts").iterdir():
            if path.is_file() and path != SCRIPT:
                self.assertNotIn(SCRIPT.name, path.read_text(), str(path))

    def test_requires_each_identity_argument_and_rejects_malformed_tokens(self):
        arguments = ["--release-id", "71", "--tag", TAG, "--source-sha", SOURCE]
        for index in (0, 2, 4):
            with self.subTest(missing=arguments[index]):
                result = self.run_command(*(arguments[:index] + arguments[index + 2:]))
                self.assertEqual(2, result.returncode)
                self.assertIn("required", result.stderr)
        for index, value in ((1, "071"), (1, "../72"), (3, TAG + "-other"), (5, "A" * 40)):
            with self.subTest(malformed=value):
                malformed = arguments.copy()
                malformed[index] = value
                result = self.run_command(*malformed)
                self.assertEqual(1, result.returncode)
                self.assertIn("Invalid", result.stderr)
        self.assertEqual([], self.api.requests)
        self.assertEqual([], self.api.deletes)


if __name__ == "__main__":
    unittest.main()
