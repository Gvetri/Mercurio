#!/usr/bin/env python3
import json
import os
from pathlib import Path
import sys
import urllib.request

from issue_store import IssueStore

args = sys.argv[1:]
repository = "repos/Gvetri/hermes-native-client"
state = Path("issue.json")
store = IssueStore(state, "Gvetri/hermes-native-client", 60)
if args[:3] in (["api", "--method", "POST"], ["api", "--method", "PATCH"]):
    method = args[2]
    assert args[3] == repository + ("/issues" if method == "POST" else "/issues/60")
    assert state.exists() == (method == "PATCH")
    payload = json.loads(Path(args[args.index("--input") + 1]).read_text())
    issue = store.patch(payload) if method == "PATCH" else store.create(payload)
    with Path("writes").open("a") as output:
        output.write(method + "\n")
    print(json.dumps(issue))
elif args == ["api", "--paginate", "--slurp", repository + "/issues?state=all&per_page=100"]:
    print(json.dumps([store.list()]))
elif args == ["api", repository + "/issues/60"]:
    print(json.dumps(store.get()))
elif args == ["api", "--paginate", "--slurp", repository + "/actions/runs/123/artifacts?per_page=100"]:
    with urllib.request.urlopen(os.environ["GITHUB_API_URL"] + "/" + args[-1]) as response:
        print(json.dumps([json.load(response)]))
else:
    raise SystemExit("Unexpected fixture API request: " + repr(args))
