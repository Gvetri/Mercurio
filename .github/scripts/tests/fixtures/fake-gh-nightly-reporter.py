#!/usr/bin/env python3
import json, os, shutil, sys
from pathlib import Path
args = sys.argv[1:]
state = Path("issue.json")
if "--method" in args:
    method = args[args.index("--method") + 1]
    if os.environ.get("FIXTURE_WRITE_FAIL"):
        raise SystemExit(1)
    payload_path = args[args.index("--input") + 1]
    shutil.copyfile(payload_path, os.environ.get("FIXTURE_CAPTURE", "submitted-payload.json"))
    payload = json.loads(Path(payload_path).read_text())
    issue = {**(json.loads(state.read_text()) if state.exists() else {}), **payload}
    issue.update({"number": 60, "state": "open",
                  "html_url": "https://github.com/example/client/issues/60",
                  "labels": [{"name": n} for n in payload["labels"]]})
    state.write_text(json.dumps(issue))
    with Path("writes").open("a") as output:
        output.write(method + "\n")
    response = os.environ.get("FIXTURE_WRITE_RESPONSE")
    print(response if response is not None else json.dumps(issue), end="")
elif any("/artifacts?" in arg for arg in args):
    print(os.environ.get("FIXTURE_ARTIFACTS", '[{"artifacts": []}]'), end="")
elif any("/issues?" in arg for arg in args):
    default = json.dumps([[json.loads(state.read_text())] if state.exists() else []])
    print(os.environ.get("FIXTURE_ISSUES", default), end="")
elif args[-1] == "repos/example/client/issues/60":
    if os.environ.get("FIXTURE_READ_FAIL"):
        raise SystemExit(1)
    default = state.read_text()
    print(os.environ.get("FIXTURE_READ_RESPONSE", default), end="")
else:
    raise SystemExit("Unexpected fixture API request: " + repr(args))
