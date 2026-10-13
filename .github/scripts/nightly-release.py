#!/usr/bin/env python3
"""Prepare and publish one source-bound development Nightly through the protected signer."""
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import runpy
import subprocess
import sys
import tempfile
import time
import urllib.error
import urllib.parse
import urllib.request

POLICY = runpy.run_path(str(Path(__file__).with_name("release-signing.py")))
api_get = POLICY["api_get"]
pages = POLICY["pages"]
require = POLICY["require"]
ReleaseError = POLICY["ReleaseError"]
REPOSITORY = POLICY["REPOSITORY"]
TAG = re.compile(r"nightly-([1-9][0-9]*)-([0-9a-f]{40})")


def dispatch_validation():
    base = os.environ.get("GITHUB_API_URL", "https://api.github.com").rstrip("/")
    request = urllib.request.Request(
        f"{base}/repos/{REPOSITORY}/actions/workflows/quality-gate.yml/dispatches",
        data=json.dumps({"ref": "main"}).encode(), method="POST", headers={
            "Accept": "application/vnd.github+json", "Content-Type": "application/json",
            "X-GitHub-Api-Version": "2022-11-28",
            "Authorization": f"Bearer {os.environ['GITHUB_TOKEN']}",
        },
    )
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            require(response.status == 204, "Validation dispatch returned an unexpected response")
    except (urllib.error.URLError, TimeoutError) as error:
        raise ReleaseError("Validation dispatch failed; no Nightly was published") from error


def validate_tip(sha, clock=time):
    timeout = POLICY["positive_integer"](os.environ.get("NIGHTLY_VALIDATION_TIMEOUT_SECONDS", "10800"))
    interval = POLICY["positive_integer"](os.environ.get("NIGHTLY_VALIDATION_POLL_SECONDS", "15"))
    require(timeout <= 10800 and interval <= timeout, "Invalid validation wait bound")
    deadline = clock.monotonic() + timeout
    discovery_deadline = min(deadline, clock.monotonic() + 300)
    started = datetime.fromtimestamp(clock.time(), timezone.utc).replace(microsecond=0)
    query = urllib.parse.urlencode({
        "event": "workflow_dispatch", "branch": "main", "head_sha": sha,
        "created": ">=" + started.strftime("%Y-%m-%dT%H:%M:%SZ"),
    })
    path = f"actions/workflows/quality-gate.yml/runs?{query}"
    before = {run["id"] for run in pages(path, "workflow_runs", discovery_deadline, clock)}
    started = datetime.fromtimestamp(clock.time(), timezone.utc).replace(microsecond=0)
    dispatch_validation()
    selected = None
    while clock.monotonic() < discovery_deadline:
        runs = pages(path, "workflow_runs", discovery_deadline, clock)
        candidates = [run for run in runs if run["id"] not in before
                      and run.get("event") == "workflow_dispatch"
                      and run.get("head_branch") == "main" and run.get("head_sha") == sha
                      and datetime.fromisoformat(run["created_at"]) >= started]
        require(len(candidates) <= 1, "Dispatched validation is ambiguous; no Nightly was published")
        if candidates:
            selected = candidates[0]
            break
        clock.sleep(min(interval, max(0, discovery_deadline - clock.monotonic())))
    require(selected is not None, "Dispatched validation did not appear within the discovery bound")
    run_id, attempt = selected["id"], selected["run_attempt"]
    require(type(run_id) is int and run_id > 0 and type(attempt) is int and attempt > 0,
            "Invalid dispatched validation identity")
    os.environ.update(RELEASE_SOURCE_SHA=sha, RELEASE_VALIDATION_RUN_ID=str(run_id),
                      RELEASE_VALIDATION_RUN_ATTEMPT=str(attempt))
    print(f"Validating main tip {sha}: run {run_id}, attempt {attempt}", flush=True)
    while clock.monotonic() < deadline:
        run = api_get(f"actions/runs/{run_id}")
        require(run.get("id") == run_id and run.get("head_sha") == sha
                and run.get("run_attempt") == attempt and run.get("event") == "workflow_dispatch",
                "Dispatched validation identity changed")
        status = run.get("status")
        if status == "completed":
            if run.get("conclusion") in {"failure", "cancelled", "timed_out", "action_required", "startup_failure", "stale"}:
                verify_dispatched_identity(run, run_id, attempt, sha)
                write_outputs({
                    "validation_failed": "true", "failed_validation_run_id": run_id,
                    "failed_validation_run_attempt": attempt, "failed_validation_sha": sha,
                })
            require(run.get("conclusion") == "success",
                    f"Dispatched validation {run_id} did not succeed; no Nightly was published")
            evidence = POLICY["verify_source"]()
            require(clock.monotonic() < deadline, "Dispatched validation did not finish within the time bound")
            return evidence
        require(status in {"queued", "in_progress", "waiting", "pending", "requested"},
                "Unknown validation state; no Nightly was published")
        clock.sleep(min(interval, max(0, deadline - clock.monotonic())))
    raise ReleaseError("Dispatched validation did not finish within the time bound")


def write_outputs(values):
    with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
        for key, value in values.items():
            output.write(f"{key}={value}\n")


def verify_dispatched_identity(run, run_id, attempt, sha):
    workflow = api_get("actions/workflows/quality-gate.yml")
    require(run.get("id") == run_id and run.get("run_attempt") == attempt
            and run.get("head_sha") == sha and run.get("head_branch") == "main"
            and run.get("event") == "workflow_dispatch"
            and run.get("workflow_id") == workflow.get("id")
            and run.get("path") == ".github/workflows/quality-gate.yml"
            and run.get("repository", {}).get("full_name") == REPOSITORY
            and run.get("head_repository", {}).get("full_name") == REPOSITORY,
            "Dispatched validation identity changed")


def report_validation():
    POLICY["verify_context"]()
    run_id = POLICY["positive_integer"](os.environ.get("FAILED_VALIDATION_RUN_ID"))
    attempt = POLICY["positive_integer"](os.environ.get("FAILED_VALIDATION_RUN_ATTEMPT"))
    sha = os.environ["FAILED_VALIDATION_SHA"]
    require(re.fullmatch(r"[0-9a-f]{40}", sha), "Invalid failed validation source")
    run = api_get(f"actions/runs/{run_id}/attempts/{attempt}")
    verify_dispatched_identity(run, run_id, attempt, sha)
    require(run.get("status") == "completed" and run.get("conclusion") in {
        "failure", "cancelled", "timed_out", "action_required", "startup_failure", "stale",
    }, "Cannot report a successful or unfinished validation")
    jobs = pages(f"actions/runs/{run_id}/attempts/{attempt}/jobs", "jobs")
    stages = {"unit_tests", "compose_test", "api24_instrumentation", "maestro_journeys"}
    names = POLICY["required_check_names"]()
    results = {}
    additional = []
    for key, name in names.items():
        matching = [job for job in jobs if job.get("name") == name]
        require(len(matching) == 1, f"Missing or duplicate validation stage: {name}")
        job = matching[0]
        require(job.get("head_sha") == sha and job.get("status") == "completed"
                and isinstance(job.get("conclusion"), str) and job["conclusion"] in {
                    "success", "skipped", "cancelled", "failure", "timed_out",
                    "startup_failure", "action_required", "neutral", "stale",
                },
                f"Invalid terminal validation stage: {name}")
        conclusion = job["conclusion"]
        if key in stages:
            results[key] = conclusion if conclusion in {"success", "skipped", "cancelled"} else "failure"
        elif conclusion != "success" and not (
            key in {"fork_guard", "draft_validation", "commit_message"} and conclusion == "skipped"
        ):
            additional.append(name)
    environment = {
        **os.environ, "GITHUB_RUN_ID": str(run_id), "GITHUB_RUN_ATTEMPT": str(attempt),
        "GITHUB_SHA": sha, "TEST_JOB_RESULTS": json.dumps(results),
        "ADDITIONAL_FAILED_STAGES": json.dumps(additional),
        "GH_TOKEN": os.environ["GITHUB_TOKEN"],
    }
    result = subprocess.run(
        ["bash", str(Path(__file__).with_name("create-nightly-failure-issue.sh"))],
        env=environment, timeout=300, check=False,
    )
    require(result.returncode == 0, "Validation failure reporter refused; inspect the failed validation logs")


def release_history():
    """Keep the Nightly tag namespace auditable, including incomplete drafts."""
    history = []
    for release in pages("releases"):
        tag = release["tag_name"]
        if tag.startswith("nightly-") or release.get("name") == "Nightly":
            match = TAG.fullmatch(tag)
            if match is None:
                raise ReleaseError("Unrecognized Nightly release history")
            history.append((int(match[1]), match[2], release))
    return history


def prepare(clock=time):
    POLICY["verify_context"]()
    sha = api_get("branches/main")["commit"]["sha"]
    require(isinstance(sha, str) and re.fullmatch(r"[0-9a-f]{40}", sha), "Invalid main tip")
    history = release_history()
    existing = [release for _, source, release in history if source == sha]
    if existing:
        require(len(existing) == 1, "Multiple Nightlies already identify this source")
        release = existing[0]
        require(release.get("draft") is False, "Existing Nightly draft needs operator recovery")
        require(release.get("prerelease") is True and release.get("name") == "Nightly"
                and release.get("target_commitish") == sha, "Existing Nightly identity is inconsistent")
        return {"publish": "false", "source_sha": sha}
    code = POLICY["generation_code"]()
    require(code > max((code for code, _, _ in history), default=1), "Version code is not newer; start a new workflow run")
    evidence = validate_tip(sha, clock)
    return {
        "publish": "true", **evidence, "version_code": code,
        "version_name": f"nightly-{code}-{sha[:12]}", "tag": f"nightly-{code}-{sha}",
        "generation_attempt": os.environ["GITHUB_RUN_ATTEMPT"],
    }


SIGNER = runpy.run_path(str(Path(__file__).with_name("sign-release-apk.py")))
ASSETS = ("hermes-native-client.apk", "SHA256SUMS", "signing-metadata.json")


def verify_artifact(directory, certificate, evidence, code, signing_run_id=None, signing_run_attempt=None):
    """Bind the actual signed APK and its small metadata allowlist to the current run."""
    require({path.name for path in directory.iterdir()} == set(ASSETS), "Unexpected release asset set")
    require(all((directory / name).is_file() and not (directory / name).is_symlink() for name in ASSETS), "Unsafe release asset")
    metadata = json.loads((directory / "signing-metadata.json").read_text())
    require(set(metadata) == POLICY["SIGNING_METADATA_FIELDS"], "Unexpected signing metadata fields")
    for key, value in evidence.items():
        require(metadata.get(key) == value, "APK validation evidence does not match the selected source")
    require(metadata["signing_run_id"] == (signing_run_id if signing_run_id is not None else POLICY["positive_integer"](os.environ.get("GITHUB_RUN_ID"))), "APK belongs to another signing run")
    require(metadata["signing_run_attempt"] == (signing_run_attempt if signing_run_attempt is not None else POLICY["positive_integer"](os.environ.get("GITHUB_RUN_ATTEMPT"))), "APK belongs to an earlier signing attempt")
    tools = Path(os.environ["ANDROID_HOME"]) / "build-tools/35.0.0"
    environment = {name: os.environ[name] for name in ("PATH", "JAVA_HOME")}
    apk = directory / "hermes-native-client.apk"
    actual = SIGNER["apk_metadata"](apk, tools, environment)
    require(actual["version_code"] == code and actual["version_name"] == f"nightly-{code}-{evidence['source_sha'][:12]}", "APK version does not match the Nightly")
    require(set(actual["native_abis"]) <= {"arm64-v8a"}, "Nightly APK is not an ARM64 device build")
    require(all(metadata[key] == value for key, value in actual.items()), "APK metadata is inconsistent")
    fingerprint = certificate.read_text().strip()
    require(re.fullmatch(r"[0-9a-f]{64}", fingerprint), "Invalid pinned certificate")
    verification = SIGNER["run_tool"]([str(tools / "apksigner"), "verify", "--print-certs", str(apk)], environment)
    signers = re.findall(r"^Signer #[0-9]+ certificate SHA-256 digest: ([0-9a-f]{64})$", verification, re.MULTILINE)
    require(signers == [fingerprint] and metadata["certificate_sha256"] == fingerprint, "Nightly signing identity is inconsistent")
    checksum = SIGNER["digest"](apk)
    require(metadata["apk_sha256"] == checksum, "Nightly APK checksum changed")
    require((directory / "SHA256SUMS").read_text() == f"{checksum}  hermes-native-client.apk\n", "Nightly checksum file is inconsistent")
    return metadata


def gh_command(arguments, payload=None):
    """Use the native CLI for publication; retain neither token nor raw command errors."""
    environment = {name: os.environ[name] for name in ("PATH", "HOME")}
    environment.update(GH_TOKEN=os.environ["GITHUB_TOKEN"], GH_HOST="github.com")
    result = subprocess.run(["gh", *arguments], input=json.dumps(payload) if payload is not None else None,
                            text=True, env=environment, capture_output=True, timeout=180, check=False)
    require(result.returncode == 0, "GitHub release operation failed; inspect the retained draft before retrying")
    return result.stdout


def gh_release(arguments):
    return gh_command(["release", *arguments, "--repo", REPOSITORY])


def publish(directory, certificate, failure_context=None):
    POLICY["verify_generation"]()
    evidence = POLICY["verify_source"]()
    POLICY["verify_nightly_authorization"]()
    code = POLICY["generation_code"]()
    metadata = verify_artifact(directory, certificate, evidence, code)
    sha = evidence["source_sha"]
    history = release_history()
    require(not any(source == sha for _, source, _ in history), "This source already has a Nightly or a draft")
    require(code > max((value for value, _, _ in history), default=1), "Nightly version is not newer than release history")
    tag = f"nightly-{code}-{sha}"
    require(api_get(f"git/matching-refs/tags/{tag}") == [], "Nightly tag already exists; operator recovery is required")
    body = POLICY["nightly_notes"](metadata)

    def read_release(draft, expected_id):
        require(type(expected_id) is int and expected_id > 0, "Missing release identity")
        release = api_get(f"releases/{expected_id}")
        require(release.get("id") == expected_id, "Release identity changed")
        require(release.get("tag_name") == tag and release.get("target_commitish") == sha
                and release.get("name") == "Nightly" and release.get("prerelease") is True
                and release.get("draft") is draft and release.get("body") == body, "Release read-back does not match the requested Nightly")
        return release

    with tempfile.TemporaryDirectory(prefix="nightly-publication-", dir=os.environ["RUNNER_TEMP"]) as temporary:
        created = json.loads(gh_command(["api", "--method", "POST", f"repos/{REPOSITORY}/releases", "--input", "-"], {
            "tag_name": tag, "target_commitish": sha, "name": "Nightly", "body": body,
            "draft": True, "prerelease": True, "make_latest": "false",
        }))
        require(type(created) is dict, "Missing release creation response")
        release_id = created.get("id")
        if failure_context is not None:
            failure_context["created_release_id"] = release_id
        release = read_release(True, release_id)
        require(release.get("assets") == [], "New Nightly draft unexpectedly contains assets")
        gh_release(["upload", tag, *(str(directory / name) for name in ASSETS)])
        release = read_release(True, release_id)
        assets = release.get("assets", [])
        require(len(assets) == len(ASSETS) and {asset["name"] for asset in assets} == set(ASSETS), "Uploaded Nightly assets are incomplete")
        require(all(asset["size"] == (directory / asset["name"]).stat().st_size for asset in assets), "Uploaded Nightly asset size mismatch")
        downloaded = Path(temporary) / "downloaded"
        downloaded.mkdir()
        gh_release(["download", tag, "--dir", str(downloaded), *(argument for name in ASSETS for argument in ("--pattern", name))])
        require(all(SIGNER["digest"](downloaded / name) == SIGNER["digest"](directory / name) for name in ASSETS), "Uploaded Nightly asset checksum mismatch")
        verify_artifact(downloaded, certificate, evidence, code)
        gh_release(["edit", tag, "--draft=false", "--prerelease", "--latest=false", "--title", "Nightly"])
        release = read_release(False, release_id)
        reference = api_get(f"git/ref/tags/{tag}")
        require(reference.get("object", {}).get("type") == "commit" and reference["object"].get("sha") == sha,
                "Published Nightly tag does not identify its exact source")
        print(f"Nightly publication verified: {release['html_url']}")


def record_publication_failure(context):
    destination = os.environ.get("NIGHTLY_FAILURE_OBSERVATION")
    if not destination:
        return
    observation = {
        "run_id": os.environ.get("GITHUB_RUN_ID"), "attempt": os.environ.get("GITHUB_RUN_ATTEMPT"),
        "commit": os.environ.get("GITHUB_SHA"), "source_sha": os.environ.get("RELEASE_SOURCE_SHA"),
        "version_code": os.environ.get("RELEASE_VERSION_CODE"), "tag": None, "release_id": None,
        "created_release_id": context.get("created_release_id"),
        "incomplete_draft": "unknown", "tag_exists": "unknown", "orphan_tag": "unknown",
        "release_state": "unknown", "observation_error": None,
    }
    try:
        POLICY["verify_context"]()
        sha = observation["source_sha"]
        code = observation["version_code"]
        require(isinstance(sha, str) and re.fullmatch(r"[0-9a-f]{40}", sha)
                and isinstance(code, str) and re.fullmatch(r"[1-9][0-9]*", code), "Missing release identity")
        observation["tag"] = f"nightly-{code}-{sha}"
        identity = context.get("created_release_id")
        candidates = []
        if identity is None:
            for release in pages("releases"):
                match = TAG.fullmatch(release.get("tag_name", ""))
                if match and match[2] == sha:
                    candidates.append(release)
            if len(candidates) == 1:
                identity = candidates[0].get("id")
        if identity is not None:
            require(type(identity) is int and identity > 0, "Missing release identity")
            release = api_get(f"releases/{identity}")
            match = TAG.fullmatch(release.get("tag_name", ""))
            require(release.get("id") == identity and match and match[2] == sha
                    and release.get("target_commitish") == sha, "Release identity changed")
            observation.update(release_id=identity, tag=release["tag_name"], version_code=match[1])
            if release.get("draft") is True:
                observation["release_state"] = "draft"
                assets = release.get("assets")
                if isinstance(assets, list):
                    observation["incomplete_draft"] = "yes" if {asset.get("name") for asset in assets} != set(ASSETS) else "unknown"
            elif release.get("draft") is False:
                observation.update(release_state="published", incomplete_draft="no")
        elif not candidates:
            observation["release_state"] = "not found in release listing"
        references = api_get(f"git/matching-refs/tags/{observation['tag']}")
        require(isinstance(references, list), "Malformed tag evidence")
        exact = [ref for ref in references if ref.get("ref") == f"refs/tags/{observation['tag']}"]
        require(len(exact) <= 1, "Ambiguous tag evidence")
        observation["tag_exists"] = "yes" if exact else "no"
        if not exact or observation["release_state"] in {"draft", "published"}:
            observation["orphan_tag"] = "no"
        elif not candidates and identity is None:
            observation["orphan_tag"] = "unknown"
    except (ReleaseError, KeyError, TypeError, ValueError, AttributeError, OSError):
        observation["observation_error"] = "Read-back incomplete; unknown fields are not evidence of absence."
    Path(destination).write_text(json.dumps(observation) + "\n")


def main(clock=time):
    failure_context = {}
    try:
        if sys.argv[1:] == ["prepare"]:
            result = prepare(clock)
            write_outputs(result)
            print("Nightly source prepared." if result["publish"] == "true" else "Source already has a Nightly; nothing to publish.")
        elif sys.argv[1:] == ["report-validation"]:
            report_validation()
        elif len(sys.argv) == 4 and sys.argv[1] == "publish":
            publish(Path(sys.argv[2]), Path(sys.argv[3]), failure_context)
        else:
            raise ReleaseError("Unsupported Nightly operation")
        return 0
    except (ReleaseError, SIGNER["SigningError"], KeyError, TypeError, ValueError, AttributeError, OSError, subprocess.TimeoutExpired) as error:
        message = str(error) if isinstance(error, (ReleaseError, SIGNER["SigningError"])) else "Missing or malformed Nightly evidence"
        print(f"Nightly refused: {message}", file=sys.stderr)
        if len(sys.argv) == 4 and sys.argv[1] == "publish":
            try:
                record_publication_failure(failure_context)
            except OSError:
                print("Publication state artifact could not be written.", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
