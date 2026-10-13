#!/usr/bin/env python3
import json
import os
from pathlib import Path
import sys
import urllib.request

from issue_store import IssueStore

arguments = sys.argv[1:]
assert arguments[0] == "api"
root = Path.home() / "report-fixture"
root.mkdir(exist_ok=True)
issue_file = root / "issue.json"
store = IssueStore(issue_file, "Gvetri/hermes-native-client", 17)
endpoint = next(item for item in arguments if item.startswith("repos/"))
with (root / "requests").open("a") as log:
    log.write(" ".join(arguments) + "\n")
if "--method" in arguments:
    method = arguments[arguments.index("--method") + 1]
    assert method in {"POST", "PATCH"}
    assert endpoint in {"repos/Gvetri/hermes-native-client/issues", "repos/Gvetri/hermes-native-client/issues/17"}
    assert (method == "PATCH") == issue_file.exists()
    payload = json.load(sys.stdin)
    issue = store.patch(payload) if method == "PATCH" else store.create(payload)
    with (root / "writes").open("a") as log:
        log.write(method + "\n")
    print(json.dumps(issue))
elif endpoint.endswith("/issues/17"):
    issue = store.get()
    if (root / "wrong-readback").exists():
        issue["state"] = "closed"
    print(json.dumps(issue))
elif "/issues?" in endpoint:
    issues = store.list()
    if (root / "duplicate").exists() and issues:
        issues.append({**issues[0], "number": 18})
    print(json.dumps([issues]))
elif endpoint.endswith("/zip"):
    sys.stdout.buffer.write((root / "observation.zip").read_bytes())
else:
    assert "/actions/" in endpoint
    with urllib.request.urlopen(os.environ["GITHUB_API_URL"] + "/" + endpoint) as response:
        payload = json.load(response)
    print(json.dumps([payload] if "--slurp" in arguments else payload))
