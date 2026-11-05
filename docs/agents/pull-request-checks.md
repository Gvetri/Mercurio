# What a pull request must satisfy

Two required contexts stand between a pull request and `main`, and both must pass on the exact head
that is merged:

| Required context | What it verifies |
| --- | --- |
| `quality-gate` | The aggregate gate: every check declared in `.github/quality-gate/required-checks.txt` reports the outcome its event expects, so one failing, missing, or misrouted check fails it. [Quality gates](../quality-gates.md) lists the checks and the routing. |
| `pr-template` | The body carries the four template headings, each as a line of its own, verbatim; a pull request authored by `dependabot[bot]` is exempt, because Dependabot writes its own body. |

`.github/scripts/verify-repository-conformance.py` reads the ruleset back and fails when either
context stops being required, and it runs the body check for a Dependabot author, another bot, and a
human, so an edit that widens or drops the exemption fails the gate.

## The body check

`pr-template` runs on `opened`, `edited`, `reopened`, `ready_for_review`, and `synchronize`. An edit
re-runs it alone: `quality-gate` has no `edited` trigger, so editing the body never re-runs the
aggregate and never replaces its verdict on the head. The body check declares no concurrency block, so
a re-run never cancels the earlier one: every run ends with a verdict for the body it read.

The four headings, verbatim:

- `## Summary`
- `## Testing`
- `## Risk`
- `## How to revert`

The check reads those four headings and nothing else in the body, so content under a heading is the
reviewer's and a heading written verbatim passes before its section is filled in.

A Dependabot pull request is exempt: Dependabot writes its own release-note body and cannot author
the headings, so the required context would keep a green dependency update blocked. The exemption is
the exact `dependabot[bot]` identity and nothing else, so a body authored by anyone else, or an
author the event does not name, must still carry every heading. The job always runs, because a
skipped job does not satisfy a required check.

## Closing keywords and issue hygiene

The tracker reads the body as well as the reviewer does. When the body contains a closing keyword
directly followed by an issue number, GitHub closes that issue the moment the pull request merges.
The keywords are `close`, `closes`, `closed`, `fix`, `fixes`, `fixed`, `resolve`, `resolves`, and
`resolved`, case-insensitive, and several issues separate with commas or spaces (`Closes #399, fixes
#401`).

The match is literal and scans the whole body: it fires from ordinary prose, from a table cell, and
from inside a code span, so `` `close #399` `` written in backticks still closes #399. Write a
keyword beside a number only when the merge should close the issue; quoting the phrase to discuss it
does not protect the issue. The same scan reads the commit that lands on `main`, and this repository
merges by squash, so a keyword and a number in the pull-request title close the issue just the same.

When the merge leaves one manual step behind, do not point a closing keyword at that issue. Name the
step with a word that is not a keyword, such as `Refs #399`, and spell the step out in the issue
itself, so the issue stays open until the step is done. If an issue has already closed too early,
reopen it with `gh issue reopen <number>`, then comment the merge commit SHA and the step that
remains, so the record shows why it reopened.

## The design section is reviewer material

The template's `## Design evidence` section is not one of the four headings, and no check reads it.
It is evidence for the reviewer; [Design delivery](../design-delivery.md) states what it carries.

## Blocked, behind, and mergeable

A pull request that reads green but cannot merge is in one of these `mergeStateStatus` states. Each
has one first fix:

| State | What it means | First fix |
| --- | --- | --- |
| Blocked (`BLOCKED`) | A required context has not passed on this head | Open that context's own log; a body failure is fixed by editing the body, and the edit re-runs `pr-template` alone |
| Behind (`BEHIND`) | The head is not up to date with `main`, which the ruleset requires | Update the branch from `main`, push, then wait for the gate to re-run on the new head |
| Dirty (`DIRTY`) | The head conflicts with `main` | Resolve the conflict, push, then wait for the gate to re-run |
| Mergeable (`CLEAN`) | Both contexts passed on this head and it is up to date | Squash-merge, and let the merged branch be deleted |

The `mergeable` field answers a narrower question: `true` means no conflict, and `null` means GitHub
is still computing the merge state, so read it again rather than treating it as a failure. A failing
check the ruleset does not require does not block the merge; only the two contexts above do.
