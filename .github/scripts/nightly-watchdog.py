#!/usr/bin/env python3
import json
import os
from pathlib import Path
import re
import runpy
import sys
import urllib.error
import urllib.request

POLICY = runpy.run_path(str(Path(__file__).with_name("release-signing.py")))
api_get = POLICY["api_get"]
pages = POLICY["pages"]
require = POLICY["require"]
ReleaseError = POLICY["ReleaseError"]
REPOSITORY = POLICY["REPOSITORY"]
TAG = re.compile(r"nightly-([1-9][0-9]*)-([0-9a-f]{40})")
MARKER = "<!-- nightly-watchdog -->"


def select_source():
    main = head = api_get("branches/main")["commit"]["sha"]
    visited = set()
    while head:
        require(isinstance(head, str) and re.fullmatch(r"[0-9a-f]{40}", head)
                and head not in visited, "Incomplete main ancestry")
        visited.add(head)
        runs = pages(f"actions/workflows/quality-gate.yml/runs?branch=main&status=success&head_sha={head}", "workflow_runs")
        eligible = [run for run in runs if (
            run.get("head_sha") == head and run.get("head_branch") == "main"
            and run.get("event") in {"schedule", "workflow_dispatch"}
            and run.get("status") == "completed" and run.get("conclusion") == "success"
            and run.get("head_repository", {}).get("full_name") == REPOSITORY
        )]
        if eligible:
            selected = max(eligible, key=lambda run: run["id"])
            evidence = POLICY["verify_validation_run"](selected["id"])
            require(evidence["source_sha"] == head, "Validation source changed during observation")
            run = api_get(f"actions/runs/{selected['id']}")
            require(run.get("event") in {"schedule", "workflow_dispatch"}, "Validation event changed")
            return {**evidence, "main_sha": main, "main_first_parent_commits_ahead": len(visited) - 1}
        commit = api_get(f"commits/{head}")
        require(commit.get("sha") == head, "Main ancestry response has the wrong commit")
        parents = commit["parents"]
        head = parents[0]["sha"] if parents else None
    raise ReleaseError("No fully validated main commit is available for a Nightly")


def report():
    evidence = select_source()
    history = []
    for release in pages("releases"):
        tag = release["tag_name"]
        if tag.startswith("nightly-") or release.get("name") == "Nightly":
            match = TAG.fullmatch(tag)
            require(match is not None, "Unrecognized Nightly release history")
            if release.get("draft") is True:
                continue
            require(release.get("draft") is False and release.get("prerelease") is True
                    and release.get("name") == "Nightly" and release.get("target_commitish") == match[2]
                    and isinstance(release.get("published_at"), str), "Inconsistent published Nightly")
            history.append((match[2], release))
    existing = [release for sha, release in history if sha == evidence["source_sha"]]
    require(len(existing) <= 1, "Multiple Nightlies identify this source")
    return {
        **evidence, "verdict": "released" if existing else "starved",
        "nightly": existing[0]["tag_name"] if existing else None,
        "newest_nightly_published_at": max((release["published_at"] for _, release in history), default=None),
    }


def write_issue(number, payload):
    require(number is None or type(number) is int and number > 0, "Invalid watchdog issue identity")
    base = os.environ.get("GITHUB_API_URL", "https://api.github.com").rstrip("/")
    path = "issues" if number is None else f"issues/{number}"
    request = urllib.request.Request(f"{base}/repos/{REPOSITORY}/{path}",
                                     data=json.dumps(payload).encode(),
                                     method="POST" if number is None else "PATCH", headers={
        "Accept": "application/vnd.github+json", "Content-Type": "application/json",
        "X-GitHub-Api-Version": "2022-11-28", "Authorization": f"Bearer {os.environ['GITHUB_TOKEN']}",
    })
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.load(response)
    except (urllib.error.URLError, TimeoutError, ValueError) as error:
        raise ReleaseError("Watchdog issue write failed") from error


def alert(facts):
    matches = [issue for issue in pages("issues?state=all")
               if "pull_request" not in issue and MARKER in (issue.get("body") or "")]
    require(len(matches) <= 1, "Multiple watchdog issues require inspection")
    number = matches[0]["number"] if matches else None
    labels = {label["name"] for label in matches[0].get("labels", [])} if matches else set()
    payload = {
        "title": "Nightly stopped: fully validated main source has no release",
        "body": f"{MARKER}\n\nThe newest fully validated main source has no published Nightly.\n\n"
                f"```json\n{json.dumps(facts, indent=2, sort_keys=True)}\n```\n\n"
                "This alarm does not publish, repair or retry a release.\n",
        "labels": sorted(labels | {"ready-for-agent"}), "state": "open",
    }
    written = write_issue(number, payload)
    actual_number = written.get("number")
    require(type(actual_number) is int and actual_number > 0
            and (number is None or actual_number == number), "Watchdog issue identity changed")
    issue = api_get(f"issues/{actual_number}")
    require(issue.get("number") == actual_number and "pull_request" not in issue
            and all(issue.get(key) == payload[key] for key in ("title", "body", "state"))
            and {label["name"] for label in issue.get("labels", [])} == set(payload["labels"]),
            "Watchdog issue read-back did not match")


def main():
    try:
        POLICY["verify_context"]()
        facts = report()
        print(json.dumps(facts, sort_keys=True), flush=True)
        if facts["verdict"] == "starved":
            alert(facts)
        return 0
    except (ReleaseError, KeyError, TypeError, AttributeError, ValueError, OSError) as error:
        message = str(error) if isinstance(error, ReleaseError) else "Missing or malformed watchdog evidence"
        print(f"Nightly watchdog refused: {message}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
