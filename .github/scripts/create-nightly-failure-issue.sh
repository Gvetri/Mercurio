#!/usr/bin/env bash
set -Eeuo pipefail

: "${GITHUB_REPOSITORY:?GITHUB_REPOSITORY must identify the repository}"
: "${GITHUB_SERVER_URL:?GITHUB_SERVER_URL must identify the GitHub server}"
: "${GITHUB_RUN_ID:?GITHUB_RUN_ID must identify the workflow run}"
: "${GITHUB_RUN_ATTEMPT:?GITHUB_RUN_ATTEMPT must identify the workflow attempt}"
: "${GITHUB_SHA:?GITHUB_SHA must identify the commit}"
: "${TEST_JOB_RESULTS:?TEST_JOB_RESULTS must contain the required test job results}"
: "${GH_TOKEN:?GH_TOKEN must provide GitHub API access}"

if ! [[ "$GITHUB_RUN_ID" =~ ^[0-9]+$ && "$GITHUB_RUN_ATTEMPT" =~ ^[0-9]+$ && "$GITHUB_SHA" =~ ^[0-9a-f]{40}$ ]]; then
    printf '%s\n' 'Invalid workflow run, attempt, or commit identifier.' >&2
    exit 1
fi

issues_endpoint="repos/${GITHUB_REPOSITORY}/issues"
temporary_directory="$(mktemp -d)"
trap 'rm -rf "$temporary_directory"' EXIT

gh api --paginate --slurp "repos/${GITHUB_REPOSITORY}/actions/runs/${GITHUB_RUN_ID}/artifacts?per_page=100" > "$temporary_directory/artifacts.json"
gh api --paginate --slurp "${issues_endpoint}?state=all&per_page=100" > "$temporary_directory/issues.json"
python3 - "$temporary_directory" "$(dirname "${BASH_SOURCE[0]}")" <<'PY'
from pathlib import Path
import json
import os
import re
import runpy
import subprocess
import sys
from urllib.parse import urlparse

root = Path(sys.argv[1])
repository = os.environ["GITHUB_REPOSITORY"]
server = os.environ["GITHUB_SERVER_URL"].rstrip("/")
if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repository):
    raise SystemExit("Invalid repository identifier.")
try:
    parsed = urlparse(server)
except ValueError:
    raise SystemExit("Invalid GitHub server URL.")
if parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.password or parsed.path or parsed.query or parsed.fragment:
    raise SystemExit("Invalid GitHub server URL.")
run = os.environ["GITHUB_RUN_ID"]
attempt = os.environ["GITHUB_RUN_ATTEMPT"]
sha = os.environ["GITHUB_SHA"]
try:
    results = json.loads(os.environ["TEST_JOB_RESULTS"])
except ValueError:
    raise SystemExit("Expected valid JSON for required test job results.")
policy = runpy.run_path(str(Path(sys.argv[2]) / "release-signing.py"))
declared = policy["required_check_names"]()
job_names = {key: declared[key] for key in (
    "unit_tests", "compose_test", "api24_instrumentation", "maestro_journeys",
)}
if not isinstance(results, dict) or set(results) != set(job_names) or any(
    not isinstance(value, str) or value not in {"success", "failure", "cancelled", "skipped"}
    for value in results.values()
):
    raise SystemExit("Expected a terminal result for each required test job.")
failed = [job for job in job_names if results[job] != "success"]
try:
    additional = json.loads(os.environ.get("ADDITIONAL_FAILED_STAGES", "[]"))
except ValueError:
    raise SystemExit("Invalid additional failed required stages.")
if not isinstance(additional, list) or any(
    not isinstance(name, str) or not re.fullmatch(r"[a-z][a-z0-9-]*", name) or name in job_names.values()
    for name in additional
) or len(additional) != len(set(additional)):
    raise SystemExit("Invalid additional failed required stages.")
if additional:
    if not set(additional) <= set(declared.values()):
        raise SystemExit("Additional stage is not a declared required check.")
    endpoint = f"repos/{repository}/actions/runs/{run}/attempts/{attempt}"

    def evidence(path, paginate=False):
        command = ["gh", "api", *(["--paginate", "--slurp"] if paginate else []), path]
        result = subprocess.run(command, capture_output=True, text=True, check=False)
        if result.returncode:
            raise SystemExit("Validation evidence is unavailable.")
        try:
            return json.loads(result.stdout)
        except ValueError:
            raise SystemExit("Validation evidence is malformed.")

    failed_conclusions = {"failure", "cancelled", "timed_out", "startup_failure", "action_required", "neutral", "stale"}
    validation = evidence(endpoint)
    if not isinstance(validation, dict) or validation.get("id") != int(run) or validation.get("run_attempt") != int(attempt) or validation.get("head_sha") != sha:
        raise SystemExit("Validation run attempt identity does not match.")
    if validation.get("status") != "completed" or not isinstance(validation.get("conclusion"), str) or validation["conclusion"] not in failed_conclusions:
        raise SystemExit("Validation run must be completed with a terminal failure.")
    pages = evidence(endpoint + "/jobs?per_page=100", paginate=True)
    try:
        jobs = [job for page in pages for job in page["jobs"]]
        if any(page["total_count"] != len(jobs) for page in pages):
            raise ValueError()
        for name in additional:
            matching = [job for job in jobs if job.get("name") == name]
            if len(matching) != 1 or matching[0].get("status") != "completed" or not isinstance(matching[0].get("conclusion"), str) or matching[0]["conclusion"] not in failed_conclusions or matching[0].get("head_sha") != sha:
                raise SystemExit(f"Required stage has no unique terminal failure in this attempt: {name}")
    except (KeyError, TypeError, ValueError, AttributeError):
        raise SystemExit("Validation jobs are missing, malformed, or truncated.")
if not failed and not additional:
    raise SystemExit("The nightly issue script cannot publish a successful test run.")
run_url = f"{server}/{repository}/actions/runs/{run}"
marker = f"<!-- nightly-run:{run} -->"
lines = [marker, "", "# Nightly test failure", "",
         f"- Run: [{run}]({run_url})", f"- Attempt: `{attempt}`",
         f"- Commit: [`{sha}`]({server}/{repository}/commit/{sha})", "",
         "## Required test jobs", ""]
for job, name in job_names.items():
    lines.append(f"- `{name}`: `{results[job]}`")
if additional:
    lines.extend(["", "## Additional failed required jobs", ""])
    lines.extend(f"- `{name}`" for name in additional)
lines.extend(["", "## Available diagnostic reports", ""])
expected_names = {
    f"jvm-test-reports-{run}-{attempt}",
    f"compose-test-reports-{run}-{attempt}",
    f"android-test-failure-evidence-{run}-{attempt}",
    f"journey-evidence-{run}-{attempt}",
}
try:
    artifact_pages = json.loads((root / "artifacts.json").read_text())
except ValueError:
    raise SystemExit("Expected valid JSON for the run artifacts response.")
if not isinstance(artifact_pages, list) or any(
    not isinstance(page, dict) or not isinstance(page.get("artifacts"), list) or any(
        not isinstance(artifact, dict)
        or not (artifact.get("name") is None or isinstance(artifact.get("name"), str))
        for artifact in page["artifacts"]
    )
    for page in artifact_pages
):
    raise SystemExit("Malformed run artifacts response.")
artifacts = [artifact for page in artifact_pages for artifact in page["artifacts"]
             if artifact.get("name") in expected_names and artifact.get("expired") is False
             and type(artifact.get("id")) is int]
for artifact in artifacts:
    lines.append(f"- [{artifact['name']}]({run_url}/artifacts/{artifact['id']})")
if not artifacts:
    lines.append("- Not uploaded. Inspect the failed job logs in the workflow run.")
lines.extend(["", "Inspect the first failing command in each failed job. A dependency download failure still blocks the quality gate."])
payload = {"title": f"Nightly test failure: {', '.join([job_names[job] for job in failed] + additional)} (run {run})",
           "body": "\n".join(lines) + "\n", "labels": ["ready-for-agent"]}
try:
    issue_pages = json.loads((root / "issues.json").read_text())
except ValueError:
    raise SystemExit("Expected valid JSON for the issues response.")
if not isinstance(issue_pages, list) or any(
    not isinstance(page, list) or any(
        not isinstance(issue_entry, dict)
        or not (issue_entry.get("body") is None or isinstance(issue_entry.get("body"), str))
        for issue_entry in page
    )
    for page in issue_pages
):
    raise SystemExit("Malformed issues response.")
issues = [issue for page in issue_pages for issue in page
          if issue.get("pull_request") is None and marker in (issue.get("body") or "")]
if len(issues) > 1:
    raise SystemExit("Multiple issues identify this run; refusing an ambiguous update.")
if issues:
    number = issues[0].get("number")
    if type(number) is not int:
        raise SystemExit("Existing issue has no numeric identifier.")
    (root / "existing-number").write_text(str(number))
    payload["state"] = "open"
(root / "payload.json").write_text(json.dumps(payload))
PY

if [[ -s "$temporary_directory/existing-number" ]]; then
    issue_number="$(< "$temporary_directory/existing-number")"
    if ! gh api --method PATCH "${issues_endpoint}/${issue_number}" --input "$temporary_directory/payload.json" > "$temporary_directory/response.json"; then
        printf 'Nightly failure issue update failed; an issue may have been filed for run %s (issue %s).\n' "$GITHUB_RUN_ID" "$issue_number" >&2
        exit 1
    fi
else
    if ! gh api --method POST "$issues_endpoint" --input "$temporary_directory/payload.json" > "$temporary_directory/response.json"; then
        printf 'Nightly failure issue creation failed; an issue may have been filed for run %s.\n' "$GITHUB_RUN_ID" >&2
        exit 1
    fi
    python3 - "$temporary_directory" "$GITHUB_RUN_ID" <<'PY'
from pathlib import Path
import json
import sys

root = Path(sys.argv[1])
run = sys.argv[2]
try:
    number = json.loads((root / "response.json").read_text())["number"]
except (ValueError, KeyError, TypeError):
    raise SystemExit(f"Nightly failure issue response is unreadable; an issue may have been filed for run {run}.")
if type(number) is not int:
    raise SystemExit(f"Nightly failure issue response has no numeric identifier; an issue may have been filed for run {run}.")
(root / "issue-number").write_text(str(number))
PY
    issue_number="$(< "$temporary_directory/issue-number")"
fi

if ! gh api "${issues_endpoint}/${issue_number}" > "$temporary_directory/verified.json"; then
    printf 'Nightly failure issue read-back failed; an issue may have been filed for run %s (issue %s).\n' "$GITHUB_RUN_ID" "$issue_number" >&2
    exit 1
fi
python3 - "$temporary_directory" "$GITHUB_RUN_ID" "$issue_number" <<'PY'
from pathlib import Path
import json
import sys

root = Path(sys.argv[1])
run = sys.argv[2]
number = sys.argv[3]

def refusal(reason):
    raise SystemExit(f"{reason}; an issue may have been filed (run {run}, issue {number}).")

try:
    expected = json.loads((root / "payload.json").read_text())
    actual = json.loads((root / "verified.json").read_text())
except ValueError:
    refusal("Published issue read-back is unreadable")
if (not isinstance(actual, dict) or type(actual.get("number")) is not int
        or not isinstance(actual.get("html_url"), str)
        or not isinstance(actual.get("labels"), list)
        or any(not isinstance(label, dict) for label in actual["labels"])):
    refusal("Published issue read-back is malformed")
if actual.get("state") != "open" or any(actual.get(key) != expected[key] for key in ("title", "body")):
    refusal("Published issue does not match the required failure report")
if not any(label.get("name") == "ready-for-agent" for label in actual["labels"]):
    refusal("Published issue is missing the ready-for-agent label")
print(f"Verified nightly failure issue #{actual['number']}: {actual['html_url']}")
PY
