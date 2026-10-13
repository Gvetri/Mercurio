#!/usr/bin/env python3
"""Issue-only write adapter over the synthetic API and artifact store."""
import json
import os
from pathlib import Path
import sys
import urllib.request

arguments = sys.argv[1:]
assert arguments[0] == "api"
root = Path.home() / "report-fixture"
root.mkdir(exist_ok=True)
issue_file = root / "issue.json"
endpoint = next(item for item in arguments if item.startswith("repos/"))
with (root / "requests").open("a") as log:
    log.write(" ".join(arguments) + "\n")
if "--method" in arguments:
    method = arguments[arguments.index("--method") + 1]
    assert method in {"POST", "PATCH"}
    assert endpoint in {"repos/Gvetri/hermes-native-client/issues", "repos/Gvetri/hermes-native-client/issues/17"}
    assert (method == "PATCH") == issue_file.exists()
    payload = json.load(sys.stdin)
    issue = {**payload, "number": 17, "state": payload.get("state", "open"),
             "labels": [{"name": label} for label in payload["labels"]],
             "html_url": "https://github.com/Gvetri/hermes-native-client/issues/17"}
    issue_file.write_text(json.dumps(issue))
    with (root / "writes").open("a") as log:
        log.write(method + "\n")
    print(json.dumps(issue))
elif endpoint.endswith("/issues/17"):
    issue = json.loads(issue_file.read_text())
    if (root / "wrong-readback").exists():
        issue["state"] = "closed"
    print(json.dumps(issue))
elif "/issues?" in endpoint:
    issue = json.loads(issue_file.read_text()) if issue_file.exists() else None
    issues = [issue] if issue else []
    if (root / "duplicate").exists() and issue:
        issues.append({**issue, "number": 18})
    print(json.dumps([issues]))
elif endpoint.endswith("/zip"):
    sys.stdout.buffer.write((root / "observation.zip").read_bytes())
else:
    assert "/actions/" in endpoint
    with urllib.request.urlopen(os.environ["GITHUB_API_URL"] + "/" + endpoint) as response:
        payload = json.load(response)
    print(json.dumps([payload] if "--slurp" in arguments else payload))
