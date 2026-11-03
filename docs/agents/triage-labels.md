# Triage Labels

The skills use five canonical triage roles. This table maps each role to its label in this repo's GitHub issue tracker, together with the two labels that carry their own rules.

| Canonical role | GitHub label | Current status | GitHub description |
|---|---|---|---|
| `needs-triage` | `needs-triage` | Present | Needs initial triage — determine validity, priority, and next step |
| `needs-info` | `needs-info` | Present | Waiting on more information from the reporter before triage can finish |
| `ready-for-agent` | `ready-for-agent` | Present | Ready for an agent to implement |
| `ready-for-human` | `ready-for-human` | Present | Recurring PR error patterns — human decision needed |
| `wontfix` | `wontfix` | Present | This will not be worked on |
| `design` | `design` | Present | Design issue: authorizes a design change |
| `run-maestro` | `run-maestro` | Present | Run the API 24 and Maestro emulator suites on this ready pull request |

When a skill mentions a triage role, use its corresponding label from this table. These seven labels are the complete label set for this repository: add a row here before you create any other label. If one is missing or renamed, recreate it with the name and description above rather than inventing a new label.

## The design label

A `design` issue is always worked with the operator. An agent may draft or fill a design issue, but it must not approve a design. A change covered by a design issue is never merged before the operator approves the design. The operator approves a page revision by hash, and the issue records that hash. Until the approval exists, the design issue stays open and every code issue it blocks stays blocked. [Design delivery](../design-delivery.md) is the full contract.

## The run-maestro label

`run-maestro` applies to pull requests only; it has no meaning on an issue. It starts the API 24 instrumentation suite and the Maestro journey suite for that pull-request head. Add the label to a ready pull request before the push that starts the gate. See [the protected main workflow](../protected-main-workflow.md) for the lane rules.
