#!/usr/bin/env python3
"""The commit-header rule, shared by the CI checker and the commit-msg hook.

The squash merge writes the pull-request title onto the protected branch, and every commit a pull
request carries is judged as well, so the header rule has one home: the declared types, the header
shape, the merge prefixes, and the subject bound below. `verify-conventional-commits.py` reads them
for the `commit-message` check and `.githooks/commit-msg` reads them for the local check, so the two
answers cannot drift.

The command line reads one message file, which is the file git writes the message to, and fails with
the same violations the check reports.
"""

import argparse
import re
import sys

COMMIT_TYPES = (
    "build",
    "chore",
    "ci",
    "docs",
    "feat",
    "fix",
    "perf",
    "refactor",
    "revert",
    "test",
)
HEADER = re.compile(
    r"^(?P<type>[a-z]+)"
    r"(?:\((?P<scope>[^()]+)\))?"
    r"(?P<breaking>!)?"
    r": (?P<subject>\S.*)$"
)
MERGE_PREFIXES = ("Merge ", "Revert \"Merge ", "Merged in ", "fixup! ", "squash! ")
MAX_SUBJECT_LENGTH = 100
GIT_COMMENT_PREFIX = "#"


class MessageError(Exception):
    """A message that cannot be judged, as opposed to a message that breaks the rule."""


def header_of(message):
    for line in message.splitlines():
        stripped = line.strip()
        if not stripped or stripped.startswith(GIT_COMMENT_PREFIX):
            continue
        return stripped
    raise MessageError("The message has no subject line")


def validate_message(message, label):
    """Return the violations of one message; an empty list means it is a valid Conventional Commit."""
    header = header_of(message)
    if header.startswith(MERGE_PREFIXES):
        return []
    match = HEADER.match(header)
    if match is None:
        return [
            f"{label}: '{header}' is not a Conventional Commit. "
            "Use '<type>[optional scope][!]: <description>'."
        ]
    violations = []
    commit_type = match.group("type")
    if commit_type not in COMMIT_TYPES:
        violations.append(
            f"{label}: '{commit_type}' is not a declared commit type. Use one of {', '.join(COMMIT_TYPES)}."
        )
    scope = match.group("scope")
    if scope is not None and scope != scope.lower():
        violations.append(f"{label}: the scope '{scope}' must be lower case.")
    subject = match.group("subject").strip()
    if subject.endswith("."):
        violations.append(f"{label}: the description must not end with a period.")
    if len(header) > MAX_SUBJECT_LENGTH:
        violations.append(
            f"{label}: the subject line is {len(header)} characters; keep it within {MAX_SUBJECT_LENGTH}."
        )
    return violations


def read_message(path):
    try:
        with open(path, encoding="utf-8") as handle:
            return handle.read()
    except (OSError, UnicodeDecodeError) as error:
        raise MessageError(f"The message file is unreadable: {error}") from error


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("message_file", help="the file git writes the commit message to")
    arguments = parser.parse_args(argv)
    try:
        violations = validate_message(read_message(arguments.message_file), "The commit message")
    except MessageError as error:
        print(f"Commit-message verification failed closed: {error}", file=sys.stderr)
        return 1
    if violations:
        for violation in violations:
            print(violation, file=sys.stderr)
        print(
            "Commit-message verification failed. Fix the subject line and commit again, or bypass the "
            "hook with --no-verify and let the commit-message check report it.",
            file=sys.stderr,
        )
        return 1
    print("Commit-message verification passed.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
