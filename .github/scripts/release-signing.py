#!/usr/bin/env python3
"""Fail closed before a protected release build or signing operation."""
import json
import os
from pathlib import Path
import re
import sys
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
REPOSITORY = "Gvetri/hermes-native-client"


class ReleaseError(Exception):
    """The release input or its evidence cannot be trusted."""


def require(condition, message):
    if not condition:
        raise ReleaseError(message)


NIGHTLY_NOTES = (
    "Development snapshot for testing. It may contain regressions and is not a stable-support promise.\n\n"
    "This is a **Nightly** prerelease, not a Stable Public Beta release.\n\n"
    "- Source commit: `{source_sha}`\n- Android version code: `{version_code}`\n"
    "- Android version name: `{version_name}`\n- Device architecture: ARM64\n"
    "- APK SHA-256: `{apk_sha256}`\n"
    "- Signing certificate SHA-256: `{certificate_sha256}`\n"
    f"- Deterministic validation: https://github.com/{REPOSITORY}/actions/runs/"
    "{validation_run_id} (attempt {validation_run_attempt})\n"
)
SIGNING_METADATA_FIELDS = {
    "source_sha", "validation_run_id", "validation_run_attempt", "signing_run_id",
    "signing_run_attempt", "version_code", "version_name", "native_abis", "apk_sha256", "certificate_sha256",
}


def nightly_notes(metadata):
    return NIGHTLY_NOTES.format_map(metadata)


def parse_nightly_notes(body):
    fields = {
        "source_sha": r"[0-9a-f]{40}", "version_code": r"[1-9][0-9]*",
        "version_name": r"nightly-[1-9][0-9]*-[0-9a-f]{12}",
        "apk_sha256": r"[0-9a-f]{64}", "certificate_sha256": r"[0-9a-f]{64}",
        "validation_run_id": r"[1-9][0-9]*", "validation_run_attempt": r"[1-9][0-9]*",
    }
    pattern = re.escape(NIGHTLY_NOTES)
    for name, value in fields.items():
        pattern = pattern.replace(re.escape("{" + name + "}"), f"(?P<{name}>{value})")
    match = re.fullmatch(pattern, body if isinstance(body, str) else "")
    require(match is not None, "Publisher notes are missing or malformed")
    values = match.groupdict()
    for name in ("version_code", "validation_run_id", "validation_run_attempt"):
        values[name] = int(values[name])
    return values


def api_get(path):
    base = os.environ.get("GITHUB_API_URL", "https://api.github.com").rstrip("/")
    request = urllib.request.Request(f"{base}/repos/{REPOSITORY}/{path}", headers={
        "Accept": "application/vnd.github+json",
        "X-GitHub-Api-Version": "2022-11-28",
        "Authorization": f"Bearer {os.environ['GITHUB_TOKEN']}",
    })
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return json.load(response)
    except (urllib.error.URLError, TimeoutError, ValueError) as error:
        raise ReleaseError("Required GitHub evidence is unavailable") from error


def pages(path, key=None, deadline=None, clock=time):
    result, seen = [], set()
    page = 1
    while True:
        if deadline is not None:
            require(clock.monotonic() < deadline, "Validation lookup exceeded its time bound")
        separator = "&" if "?" in path else "?"
        response = api_get(f"{path}{separator}per_page=100&page={page}")
        batch = response[key] if key else response
        require(isinstance(batch, list), "Malformed Nightly history")
        for item in batch:
            identity = item.get("id", item.get("sha"))
            require(identity is not None and identity not in seen, "Duplicate Nightly history record")
            seen.add(identity)
        result.extend(batch)
        if len(batch) < 100:
            if key:
                require(response.get("total_count") == len(result), "Truncated Nightly history")
            return result
        page += 1


def positive_integer(value):
    require(isinstance(value, str) and re.fullmatch(r"[1-9][0-9]*", value), "Invalid run identity")
    return int(value)


def verify_context():
    require(os.environ.get("GITHUB_REPOSITORY") == REPOSITORY, "Untrusted repository")
    require(os.environ.get("GITHUB_REF") == "refs/heads/main", "Only main may request signing")
    require(os.environ.get("GITHUB_REF_PROTECTED") == "true", "The workflow ref must be protected")
    require(os.environ.get("GITHUB_EVENT_NAME") in {"workflow_dispatch", "schedule"}, "Unsafe release event")


def generation_code():
    """Reserve a distinct Android code for each Nightly run and full rerun."""
    number = positive_integer(os.environ.get("GITHUB_RUN_NUMBER"))
    attempt = positive_integer(os.environ.get("GITHUB_RUN_ATTEMPT"))
    require(attempt < 1000, "Nightly attempt range exhausted; start a new workflow run")
    code = number * 1000 + attempt
    require(code <= 2100000000, "Android version-code range exhausted")
    return code


def verify_generation():
    """Reject outputs retained from an earlier attempt or an arbitrary version selection."""
    verify_context()
    require(os.environ.get("RELEASE_VERSION_CODE") == str(generation_code()), "Nightly version does not belong to this run attempt")
    require(os.environ.get("RELEASE_GENERATION_ATTEMPT") == os.environ["GITHUB_RUN_ATTEMPT"], "Partial rerun cannot reuse a prepared version; rerun all jobs")


def required_check_names():
    required = (ROOT / ".github/quality-gate/required-checks.txt").read_text().split()
    return {job: "compose-jvm-tests" if job == "compose_test" else job.replace("_", "-")
            for job in required}


def verify_validation_run(run_id):
    """Check the full deterministic main validation without imposing a caller event."""
    require(type(run_id) is int and run_id > 0, "Invalid validation run identity")
    run = api_get(f"actions/runs/{run_id}")
    workflow = api_get("actions/workflows/quality-gate.yml")
    require(type(workflow.get("id")) is int and workflow["id"] > 0, "Missing workflow identity")
    require(run.get("id") == run_id and run.get("workflow_id") == workflow["id"], "Wrong validation workflow")
    require(run.get("path") == ".github/workflows/quality-gate.yml", "Wrong validation path")
    require(run.get("event") in {"push", "schedule", "workflow_dispatch"}, "Pull-request evidence is not release evidence")
    require(run.get("head_branch") == "main", "Validation did not run on main")
    for key in ("repository", "head_repository"):
        require(run.get(key, {}).get("full_name") == REPOSITORY, "External-fork evidence is not release evidence")
    require(run.get("status") == "completed" and run.get("conclusion") == "success", "Validation has not passed")
    sha = run.get("head_sha", "")
    require(isinstance(sha, str) and re.fullmatch(r"[0-9a-f]{40}", sha), "Invalid source commit")
    attempt = run.get("run_attempt")
    require(type(attempt) is int and attempt > 0, "Missing validation attempt")
    comparison = api_get(f"compare/{sha}...main")
    require(
        comparison.get("status") in {"ahead", "identical"}
        and comparison.get("merge_base_commit", {}).get("sha") == sha,
        "Source commit is not on main",
    )
    jobs = []
    page = 1
    while True:
        result = api_get(f"actions/runs/{run_id}/attempts/{attempt}/jobs?per_page=100&page={page}")
        batch = result["jobs"]
        require(isinstance(batch, list) and batch, "Missing validation jobs")
        jobs.extend(batch)
        total = result["total_count"]
        require(type(total) is int and len(jobs) <= total, "Contradictory validation job count")
        if len(jobs) == total:
            break
        require(len(batch) == 100, "Truncated validation jobs")
        page += 1
    expected = {
        name: "skipped" if job in {"fork_guard", "draft_validation", "commit_message", "ui_paths"} else "success"
        for job, name in required_check_names().items()
    }
    expected["quality-gate"] = "success"
    for name, conclusion in expected.items():
        matches = [job for job in jobs if job.get("name") == name]
        require(len(matches) == 1, f"Missing or duplicate validation job: {name}")
        job = matches[0]
        require(
            job.get("head_sha") == sha and job.get("status") == "completed"
            and job.get("conclusion") == conclusion,
            f"Required validation evidence failed: {name}",
        )
    return {"source_sha": sha, "validation_run_id": run_id, "validation_run_attempt": attempt}


def verify_source():
    verify_context()
    evidence = verify_validation_run(positive_integer(os.environ.get("RELEASE_VALIDATION_RUN_ID")))
    for name, actual in (("RELEASE_SOURCE_SHA", evidence["source_sha"]),
                         ("RELEASE_VALIDATION_RUN_ATTEMPT", str(evidence["validation_run_attempt"]))):
        if name in os.environ:
            require(os.environ[name] == actual, "Validation evidence changed after preparation")
    return evidence


def verify_environment(name="release"):
    require(os.environ.get("GITHUB_REPOSITORY") == REPOSITORY, "Untrusted repository")
    require(name in {"release", "nightly"}, "Unknown signing environment")
    environment = api_get(f"environments/{name}")
    require(environment.get("name") == name and type(environment.get("id")) is int, "Missing signing environment")
    require(environment.get("can_admins_bypass") is False, "Administrator bypass must be disabled")
    if name == "release":
        rules = [rule for rule in environment.get("protection_rules", []) if rule.get("type") == "required_reviewers"]
        require(len(rules) == 1, "Required human approval is missing")
        reviewers = rules[0].get("reviewers", [])
        require(
            len(reviewers) == 1 and reviewers[0].get("type") == "User"
            and reviewers[0].get("reviewer", {}).get("id") == 8773754
            and reviewers[0].get("reviewer", {}).get("login") == "Gvetri",
            "The signing environment must require the declared human reviewer",
        )
    else:
        require([rule.get("type") for rule in environment.get("protection_rules", [])] == ["branch_policy"],
                "Nightly signing must be main-only with no approval or wait gate")
    require(environment.get("deployment_branch_policy") == {
        "protected_branches": False, "custom_branch_policies": True,
    }, "Only the declared branch policy is allowed")
    policies = api_get(f"environments/{name}/deployment-branch-policies?per_page=100")
    branches = policies.get("branch_policies", [])
    require(
        policies.get("total_count") == 1 and len(branches) == 1
        and branches[0].get("name") == "main" and branches[0].get("type") == "branch",
        "Only the main branch may use the signing environment",
    )
    require(api_get("branches/main").get("protected") is True, "Main is not protected")
    return environment["id"]


def verify_run_approval(run_id, environment_id):
    """Check run-bound approval; GitHub does not return an approval timestamp."""
    reviews = api_get(f"actions/runs/{run_id}/approvals")
    require(isinstance(reviews, list), "Missing release approval history")
    approvals = [review for review in reviews if any(
        environment.get("id") == environment_id and environment.get("name") == "release"
        for environment in review.get("environments", [])
    )]
    require(approvals and all(
        review.get("state") == "approved" and review.get("user", {}).get("id") == 8773754
        and review.get("user", {}).get("login") == "Gvetri" and review.get("user", {}).get("type") == "User"
        for review in approvals
    ), "Explicit human approval for this signing run is missing or contradictory")


def verify_approval():
    verify_context()
    environment_id = verify_environment()
    run_id = positive_integer(os.environ.get("GITHUB_RUN_ID"))
    verify_run_approval(run_id, environment_id)


def verify_nightly_authorization():
    """Only the trusted Nightly caller can sign without a human approval."""
    verify_generation()
    require(os.environ.get("GITHUB_WORKFLOW_REF") ==
            f"{REPOSITORY}/.github/workflows/nightly-release.yml@refs/heads/main",
            "Only the protected Nightly workflow may use unattended signing")
    verify_environment("nightly")


def signing_environment():
    verify_context()
    if os.environ.get("RELEASE_VERSION_CODE"):
        verify_nightly_authorization()
        return "nightly"
    verify_environment()
    return "release"


def verify_authorization():
    if os.environ.get("RELEASE_VERSION_CODE"):
        verify_nightly_authorization()
    else:
        verify_approval()


def main():
    try:
        if sys.argv[1:] == ["source"]:
            evidence = verify_source()
            with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
                for key, value in evidence.items():
                    output.write(f"{key}={value}\n")
        elif sys.argv[1:] == ["environment"]:
            verify_environment()
        elif sys.argv[1:] == ["nightly-environment"]:
            verify_environment("nightly")
        elif sys.argv[1:] == ["signing-environment"]:
            name = signing_environment()
            with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
                output.write(f"signing_environment={name}\n")
        elif sys.argv[1:] == ["authorization"]:
            verify_authorization()
        elif sys.argv[1:] == ["approval"]:
            verify_approval()
        elif sys.argv[1:] == ["generation"]:
            verify_generation()
        else:
            raise ReleaseError("Unsupported release operation")
        print("Release evidence verified.")
        return 0
    except (ReleaseError, KeyError, TypeError, AttributeError, ValueError, OSError) as error:
        message = str(error) if isinstance(error, ReleaseError) else "Missing or malformed release evidence"
        print(f"Release signing refused: {message}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
