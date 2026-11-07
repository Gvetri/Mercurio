#!/usr/bin/env python3
"""Verify that a pull-request body carries the sections of the published pull-request template.

A Dependabot pull request is exempt: Dependabot writes its own release-note body and cannot author
the template headings, so judging it by the template would keep a green dependency update blocked.
The exemption is the exact bot identity and nothing else; a body authored by anyone else, or by an
author the event does not name, must still carry every heading.
"""

import os
import re
import sys

REQUIRED_HEADINGS = ("## Summary", "## Testing", "## Risk", "## How to revert")
EXEMPT_AUTHORS = frozenset({"dependabot[bot]"})
TEMPLATE = ".github/PULL_REQUEST_TEMPLATE.md"


def missing_headings(body):
    return [
        heading
        for heading in REQUIRED_HEADINGS
        if not re.search(rf"(?m)^{re.escape(heading)}[ \t]*$", body)
    ]


def main():
    author = os.environ.get("PR_AUTHOR") or ""
    if author in EXEMPT_AUTHORS:
        print(f"The pull request is authored by {author}, which writes its own body, so the template sections do not apply.")
        return 0

    missing = missing_headings(os.environ.get("PR_BODY") or "")
    if missing:
        print("Pull request body is missing required template sections:", file=sys.stderr)
        for heading in missing:
            print(f"  - {heading}", file=sys.stderr)
        print(f"Reuse the headings from {TEMPLATE} verbatim.", file=sys.stderr)
        return 1

    print("All required template sections are present.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
