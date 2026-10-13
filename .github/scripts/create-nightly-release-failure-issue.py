#!/usr/bin/env python3
import io
import json
import os
from pathlib import Path
import re
import runpy
import subprocess
import sys
from typing import Any
import zipfile

POLICY = runpy.run_path(str(Path(__file__).with_name("release-signing.py")))
REPOSITORY = POLICY["REPOSITORY"]
require = POLICY["require"]
ReleaseError = POLICY["ReleaseError"]


def gh(endpoint, payload=None, paginated=False, binary=False) -> Any:
    arguments = ["gh", "api"]
    if paginated:
        arguments.extend(["--paginate", "--slurp"])
    if payload is not None:
        arguments.extend(["--method", "PATCH" if re.fullmatch(r"issues/[1-9][0-9]*", endpoint) else "POST", "--input", "-"])
    arguments.append(f"repos/{REPOSITORY}/{endpoint}")
    response = subprocess.run(arguments, input=json.dumps(payload).encode() if payload else None,
                              capture_output=True, timeout=180, check=False)
    require(response.returncode == 0, "GitHub failure-report operation failed")
    return response.stdout if binary else json.loads(response.stdout)


def records(endpoint, key=None):
    pages = gh(endpoint, paginated=True)
    require(isinstance(pages, list) and pages, "Missing paginated evidence")
    result = []
    for page in pages:
        batch = page.get(key) if key else page
        require(isinstance(batch, list), "Malformed paginated evidence")
        result.extend(batch)
    identifiers = [item.get("id", item.get("number")) for item in result]
    require(all(type(identity) is int and identity > 0 for identity in identifiers)
            and len(set(identifiers)) == len(identifiers), "Duplicate or malformed evidence")
    if key:
        require(all(page.get("total_count") == len(result) for page in pages), "Truncated paginated evidence")
    return result


def observed_state(artifacts, run, attempt, commit):
    name = f"nightly-publication-state-{run}-{attempt}"
    matches = [artifact for artifact in artifacts if artifact.get("name") == name and artifact.get("expired") is False]
    require(len(matches) <= 1, "Multiple publication state artifacts")
    if not matches:
        return None
    archive = gh(f"actions/artifacts/{matches[0]['id']}/zip", binary=True)
    with zipfile.ZipFile(io.BytesIO(archive)) as zipped:
        require(zipped.namelist() == ["publication-state.json"], "Unexpected publication state artifact")
        require(zipped.getinfo("publication-state.json").file_size <= 16384, "Publication state artifact too large")
        state = json.loads(zipped.read("publication-state.json"))
    require(state.get("run_id") == run and state.get("attempt") == attempt
            and state.get("commit") == commit, "Publication state belongs to another run or attempt")
    for key in ("incomplete_draft", "tag_exists", "orphan_tag"):
        require(state.get(key) in {"yes", "no", "unknown"}, "Malformed publication state")
    require(state.get("release_state") in {"unknown", "draft", "published", "not found in release listing"}, "Malformed release state")
    for key, pattern in (("source_sha", r"[0-9a-f]{40}"), ("version_code", r"[1-9][0-9]*"),
                         ("tag", r"nightly-[1-9][0-9]*-[0-9a-f]{40}")):
        require(state.get(key) is None or isinstance(state[key], str) and re.fullmatch(pattern, state[key]), "Malformed release identity")
    for key in ("release_id", "created_release_id"):
        require(state.get(key) is None or type(state[key]) is int and state[key] > 0, "Malformed release id")
    if state.get("tag"):
        require(state["tag"] == f"nightly-{state['version_code']}-{state['source_sha']}", "Inconsistent release identity")
    return state


def report():
    require(os.environ.get("GITHUB_REPOSITORY") == REPOSITORY, "Unexpected repository")
    run, attempt, commit = (os.environ.get(key, "") for key in ("GITHUB_RUN_ID", "GITHUB_RUN_ATTEMPT", "GITHUB_SHA"))
    require(re.fullmatch(r"[1-9][0-9]*", run) and re.fullmatch(r"[1-9][0-9]*", attempt)
            and re.fullmatch(r"[0-9a-f]{40}", commit), "Invalid run identity")
    require(os.environ.get("GH_TOKEN"), "Missing issue token")
    actual = gh(f"actions/runs/{run}/attempts/{attempt}")
    require(actual.get("id") == int(run) and actual.get("run_attempt") == int(attempt)
            and actual.get("head_sha") == commit and actual.get("head_branch") == "main"
            and actual.get("repository", {}).get("full_name") == REPOSITORY
            and actual.get("head_repository", {}).get("full_name") == REPOSITORY
            and actual.get("path") == ".github/workflows/nightly-release.yml"
            and actual.get("event") in {"schedule", "workflow_dispatch"}
            and (actual.get("status") == "in_progress" and actual.get("conclusion") is None
                 or actual.get("status") == "completed" and actual.get("conclusion") in {"failure", "cancelled"}),
            "Unexpected Nightly run evidence")
    jobs = records(f"actions/runs/{run}/attempts/{attempt}/jobs?per_page=100", "jobs")
    failed = [job["name"] for job in jobs if (job.get("name") == "publish" or job.get("name", "").startswith("sign / "))
              and job.get("status") == "completed" and job.get("conclusion") == "failure" and job.get("head_sha") == commit]
    require(failed, "No failed signing or publication stage; refusing to file an issue")
    artifacts = records(f"actions/runs/{run}/artifacts?per_page=100", "artifacts")
    state = observed_state(artifacts, run, attempt, commit)
    intended = {}
    for key, pattern in (("source_sha", r"[0-9a-f]{40}"), ("version_code", r"[1-9][0-9]*")):
        value = os.environ.get(f"RELEASE_{key.upper()}")
        if value:
            require(re.fullmatch(pattern, value), "Malformed intended release identity")
            intended[key] = value
    if "source_sha" in intended and "version_code" in intended:
        intended["tag"] = f"nightly-{intended['version_code']}-{intended['source_sha']}"
    url = f"https://github.com/{REPOSITORY}/actions/runs/{run}"
    marker = f"<!-- nightly-release-run:{run} -->"
    lines = [marker, "", "# Nightly signing or publication failure", "", f"- Run: [{run}]({url})",
             f"- Attempt: `{attempt}`", f"- Commit: `{commit}`", "", "## Failed stages", ""]
    lines.extend(f"- `{name}`: `failure`" for name in failed)
    lines.extend(["", "## Retained release state", ""])
    for title, key in (("Source SHA", "source_sha"), ("Version code", "version_code"), ("Tag", "tag"),
                       ("Release id", "release_id"), ("Created release id (creation response only)", "created_release_id"),
                       ("Release state", "release_state"), ("Incomplete draft", "incomplete_draft"),
                       ("Tag exists", "tag_exists"), ("Orphan tag", "orphan_tag")):
        value = state.get(key) if state else None
        if value is None:
            value = intended.get(key)
        lines.append(f"- {title}: `{value if value is not None else 'unknown'}`")
    if state is None:
        lines.append("- Publication state artifact: missing or expired. No release state was read back by this reporter. "
                     "A failure inside the reusable signer can precede all signer outputs. "
                     "Source/version/tag from preparation are the intended identity, not proof a release exists.")
    else:
        lines.append("- Snapshot read back by the publication job after refusal; not a live recovery authorization. "
                     "Source/version/tag without a release id are the intended identity, not proof a release exists.")
        if state.get("observation_error"):
            lines.append("- Read-back incomplete; unknown fields are not evidence of absence.")
    lines.extend(["", "An incomplete draft means the read-back found a draft with an incomplete asset inventory. "
                  "A draft with all asset names has unknown completeness: bytes have not been verified here. "
                  "A tag attached to a read-back draft is not orphaned yet; removing that draft may orphan it. "
                  "A tag with no listed release has unknown orphan status because listings may omit drafts.",
                  "", "## Available diagnostic artifacts", ""])
    for prefix in ("nightly-publication-state", "unsigned-release", "signed-release"):
        name = f"{prefix}-{run}-{attempt}"
        available = [artifact for artifact in artifacts if artifact.get("name") == name and artifact.get("expired") is False]
        if available:
            lines.extend(f"- [{name}]({url}/artifacts/{artifact['id']})" for artifact in available)
        else:
            lines.append(f"- {name}: missing or expired; inspect the failed job logs.")
    lines.extend(["", "No workflow repairs, retries, deletes, overwrites or re-publishes release state. "
                  "Follow the agent recovery rule in [Development Nightly releases]"
                  f"(https://github.com/{REPOSITORY}/blob/main/docs/nightly-releases.md). "
                  "Never touch a published release or an asset; unknown state requires inspection."])
    payload = {"title": f"Nightly release failure: {', '.join(failed)} (run {run})", "body": "\n".join(lines) + "\n",
               "labels": ["ready-for-agent"]}
    issues = [issue for issue in records("issues?state=all&per_page=100")
              if issue.get("pull_request") is None and marker in (issue.get("body") or "")]
    require(len(issues) <= 1, "Multiple issues identify this run; refusing an ambiguous update")
    if issues:
        number = issues[0].get("number")
        require(type(number) is int and number > 0, "Invalid issue number")
        payload["state"] = "open"
        gh(f"issues/{number}", payload)
    else:
        number = gh("issues", payload).get("number")
        require(type(number) is int and number > 0, "Invalid issue number")
    verified = gh(f"issues/{number}")
    require(verified.get("number") == number and verified.get("state") == "open"
            and all(verified.get(key) == payload[key] for key in ("title", "body"))
            and "ready-for-agent" in {label.get("name") for label in verified.get("labels", [])},
            "Published issue does not match the required failure report")
    print(f"Verified Nightly release failure issue #{number}")


if __name__ == "__main__":
    try:
        report()
    except (ReleaseError, KeyError, ValueError, TypeError, AttributeError, OSError, subprocess.TimeoutExpired, zipfile.BadZipFile) as error:
        print(str(error) if isinstance(error, ReleaseError) else "Missing or malformed failure evidence", file=sys.stderr)
        raise SystemExit(1)
