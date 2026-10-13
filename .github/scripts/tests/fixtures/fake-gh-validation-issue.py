#!/usr/bin/env python3
import json
import os
from pathlib import Path
import sys
import urllib.request

args = sys.argv[1:]
repository = "repos/Gvetri/hermes-native-client"
state = Path("issue.json")
if args[:3] in (["api", "--method", "POST"], ["api", "--method", "PATCH"]):
    method = args[2]
    assert args[3] == repository + ("/issues" if method == "POST" else "/issues/60")
    assert state.exists() == (method == "PATCH")
    payload = json.loads(Path(args[args.index("--input") + 1]).read_text())
    issue = {**payload, "number": 60, "html_url": "https://github.com/Gvetri/hermes-native-client/issues/60",
             "state": "open", "labels": [{"name": name} for name in payload["labels"]]}
    state.write_text(json.dumps(issue))
    with Path("writes").open("a") as output:
        output.write(method + "\n")
    print(json.dumps(issue))
elif args == ["api", "--paginate", "--slurp", repository + "/issues?state=all&per_page=100"]:
    print(json.dumps([[json.loads(state.read_text())] if state.exists() else []]))
elif args == ["api", repository + "/issues/60"]:
    print(state.read_text())
elif args == ["api", "--paginate", "--slurp", repository + "/actions/runs/123/artifacts?per_page=100"]:
    with urllib.request.urlopen(os.environ["GITHUB_API_URL"] + "/" + args[-1]) as response:
        print(json.dumps([json.load(response)]))
else:
    raise SystemExit("Unexpected fixture API request: " + repr(args))
