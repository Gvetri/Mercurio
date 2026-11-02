# What a pull request must satisfy

Two required contexts stand between a pull request and `main`, and both must pass on the exact head
that is merged:

| Required context | What it verifies |
| --- | --- |
| `quality-gate` | The aggregate gate: every check declared in `.github/quality-gate/required-checks.txt` reports the outcome its event expects, so one failing, missing, or misrouted check fails it. [Quality gates](../quality-gates.md) lists the checks and the routing. |
| `pr-template` | The body carries the four template headings, each at the start of a line, verbatim. |

`.github/scripts/verify-repository-conformance.py` reads the ruleset back and fails when either
context stops being required.

## The body check

`pr-template` runs on `opened`, `edited`, `reopened`, `ready_for_review`, and `synchronize`. An edit
re-runs it alone: `quality-gate` has no `edited` trigger, so editing the body never re-runs the
aggregate and never replaces its verdict on the head.

The four headings, verbatim:

- `## Summary`
- `## Testing`
- `## Risk`
- `## How to revert`

The check reads those four headings and nothing else in the body, so content under a heading is the
reviewer's and a heading written verbatim passes before its section is filled in.

## The design section is reviewer material

The template's `## Design evidence` section is not one of the four headings, and no check reads it.
It is evidence for the reviewer; [Design delivery](../design-delivery.md) states what it carries.

## Blocked, behind, mergeable

A pull request that reads green but cannot merge is in one of these states. Each has one first fix:

| State | What it means | First fix |
| --- | --- | --- |
| Blocked (`BLOCKED`) | A required context has not passed on this head | Open that context's own log; a body failure is fixed by editing the body, and the edit re-runs `pr-template` alone |
| Behind (`BEHIND`) | The head is not up to date with `main`, which the ruleset requires | Update the branch from `main`, push, then wait for the gate to re-run on the new head |
| Dirty (`DIRTY`) | The head conflicts with `main` | Resolve the conflict, push, then wait for the gate to re-run |
| Mergeable (`CLEAN`, `mergeable: true`) | Both contexts passed on this head and it is up to date | Squash-merge, and let the merged branch be deleted |

`mergeable: null` means GitHub is still computing the merge state: read it again, and treat it as no
verdict rather than a failure. A failing check the ruleset does not require does not block the merge;
only the two contexts above do.
