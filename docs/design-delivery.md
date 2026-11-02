# Design delivery

A designed surface is approved before code is written, and the code is verified against that
approval. This document is the contract: it names the issues, the design name, the evidence, and the
gates. It describes the flow the first implementation used, not a plan.

A **designed surface** is any screen or component a user can see. A change to internal structure that
a user cannot see is out of this flow and needs no design issue.

## The three issues

| Issue | Label | Authorizes | Closes when |
| --- | --- | --- | --- |
| Feature spec | `enhancement` | The scope of one screen or component change | Its design and code issues are closed |
| Design issue | `design` | A change to the named screen or component in the design source | The operator approves a page revision by hash |
| Code issue | `enhancement` | The implementation of the approved design | Its pull request merges |

The feature spec creates the design issue and the code issue, and records both links. The code issue
names its blocker as `Blocked by #<design>`.

**The blocking rule.** A code issue is not ready while its design issue is open. The design issue
closes on the operator's approval; only then does the code issue get `ready-for-agent`. An agent that
finds a code issue whose design issue is still open stops and reports the open blocker instead of
starting the work.

## The design name

Every public issue names the surface it covers as `mercurio/<screen>` — for example
`mercurio/session-list`. The design name is the only pointer a public issue carries. A public issue,
a pull request, or a commit never carries a design path, a design link, or a host name.

The private resolution table maps a design name to the design system, the master page, and the screen
page. It is not part of this repository. A resolution that finds no page for the name stops the agent
with a clear error; the agent does not guess a similar page.

## The design source

The design source is the private design tool. It is the only source of truth for colour, type,
spacing, shape, elevation, component rules, and screen composition. The repository holds no copy of
the design package, and a second working copy would compete with the source.

The design package carries three documents that a design issue reads before it answers anything:

- `DECISIONS.md` records the approved direction and its evidence. When a plan and `DESIGN.md`
  disagree, the plan wins and the conflict is recorded.
- `DESIGN.md` names the primitives with their slots, states, and token references. A feature
  composes the primitives and supplies the copy; it does not rename one.
- `components.manifest.json` lists atoms, components, states, and token references with stable ids.
  A code issue imports the components by id and does not invent a variant.

**Write access.** The agent writes to the design source only when an open design issue names the
screen or component. No issue, no edit. An invisible implementation detail is not a reason to edit
the design.

**Backup.** The design source is exported to a private archive, once a week and once when a design
issue closes. The archive is copied off-site and the last eight archives are kept. The agent never
edits an archive. A restore is approved by the operator and then run by the agent.

## What a design issue carries

- The **design name** and the screens and states it changes.
- The **page preview in light and dark**.
- A **states matrix**: state × light × dark, where each cell is either a frame or a named decision
  that the state does not exist. The states of a screen include empty, loading, unavailable,
  populated, and every menu, confirmation, or in-place edit the screen defines.
- The **component list**: the ids from `components.manifest.json` that the screen uses, plus every
  slot it fills.
- The **Impeccable record**: the review output with each finding classified as fixed, accepted, or a
  false positive. A false positive names the reason and the measurement that clears it.
- The **tradeoff note** in the issue body, or the word `none`. A tradeoff is a design value that
  departs from the platform guidance — a control below the 48 dp touch target, or an action that is
  reachable only by a hidden gesture — and the note says what the departure is and why it stays.
- The **approval hash**: the operator approves a page revision by hash, and the issue records the
  hash it approves.

## Layout scope

Portrait phone is the review target, in light and dark. Tablets and foldables are out of scope. A new
screen gets a wide capture generated as it renders; it is not compared with a design page. The
existing wide baselines stay, so a wide regression is still caught.

Material 3 is the only component source. Every value maps to a Material 3 role or token: no ad-hoc
hex value, no one-off spacing, and no bespoke component. When the design needs something Material 3
cannot express, the gap and the chosen mapping are recorded in the "Known gaps" section of the design
document and the operator decides. There is no code workaround without that record.

## The two screenshot checks

The two checks answer different questions, and neither replaces the other.

**The design-versus-code checklist** is a reviewer's judgement over the captures and the approved
page. It compares roles, the type scale, the spacing steps, the states, and both themes. It is never
a pixel diff across two renderers, because the design tool and Compose do not render the same pixels.

**The code-versus-code visual regression** is the `compose-jvm-tests` gate. It runs
`verifyRoborazziBaselineManifest` and then `verifyRoborazziDebug`, so a capture is compared with the
repository's own approved image, pixel-exact, and a baseline that the manifest does not list fails
the check. A new screen's first image becomes a baseline only after the design is approved, so a
wrong screen cannot be locked in. The procedure for recording a deliberate change is in
[Visual regression baselines](../CONTRIBUTING.md#visual-regression-baselines).

## The value check

The value check runs before the pull request, on the build host, and is not part of CI. It compares
the token values of the design package with `HermesDesignTokens.kt` and fails on a mismatch, so a
hand-copied value that drifted is caught before review. Its result is recorded in the code issue.

## The evidence checklist

A code change to a designed surface is not ready for review until the code issue carries every row:

| Evidence | What it proves |
| --- | --- |
| The design issue link and the design name | The code implements an approved design |
| Before and after captures for every state, light and dark | The reviewer can compare the app with the design page |
| The states matrix mapped to the capture files | Every approved state is implemented |
| The checklist result | The captures match the approved page |
| The value check result | The Kotlin values match the design values |
| The screenshot test report | The app matches its own previous images |
| The Maestro journey result, when `run-maestro` is set | The runtime behaviour holds |
| The Impeccable audit result | The implemented screen meets the quality floor |
| The tradeoff note, or `none` | A departure from guidance is deliberate |

A state that the previous implementation did not have has no before capture, and the approved design
frame is the reference for it. The pull request states which captures are in that position instead of
leaving the row empty.

The pull request's design evidence section is reviewer material: no check reads it, and the body
check requires only its four headings
([what a pull request must satisfy](agents/pull-request-checks.md)).

## The stop rule

When the code cannot match the approved design, stop. Do not improvise a value, do not approximate a
component, and do not merge the change. Open a design issue that names the screen and the state, and
wait for the operator's approval of the page revision. The code change resumes against the approved
design.

The same rule covers a discovered drift: a screen that the design source does not cover is a missing
design, not a licence to invent one.

## The Impeccable gates

The Impeccable review is a hard gate at two points.

- **At design approval.** The review and audit run on the design page. The findings and their fixes
  are recorded in the design issue. The design issue does not close with an open finding unless the
  operator accepts it in the tradeoff note.
- **At code merge.** The native audit runs on the implemented screen: Material 3 components, TalkBack
  semantics, font scaling, and touch targets. The findings and their fixes are recorded in the pull
  request, and an accepted departure is carried in the tradeoff note rather than silently inflated.

## Labels and templates

The `design` label marks a design issue and is the only label that authorizes a design change. The
issue templates are [the feature spec](../.github/ISSUE_TEMPLATE/feature-spec.md),
[the design issue](../.github/ISSUE_TEMPLATE/design-issue.md), and
[the code issue](../.github/ISSUE_TEMPLATE/code-issue.md). The pull-request template carries the
design evidence section, and a change that touches no designed surface writes `Not a design change`
there.

## Related documents

- [Visual regression baselines](../CONTRIBUTING.md#visual-regression-baselines) — how a baseline is
  recorded and how the manifest is kept in step.
- [Quality gates](quality-gates.md) — the checks that carry the design evidence.
- [Glossary](../GLOSSARY.md) — the shared domain vocabulary of this repository.
