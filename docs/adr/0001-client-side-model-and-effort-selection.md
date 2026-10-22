# Client-side model and effort selection

Status: accepted (2026-10-22)

The README and the user guide excluded client-side provider, model, and credential choice. The posture review re-opened that exclusion for model and reasoning-effort selection: per-run control is a core mobile expectation, and the server already advertises its configured model through `capabilities.model`.

Decision: the client gains a per-run model + reasoning-effort picker that remembers the last choice. The model list comes from a server-provided catalog endpoint (an additive contract change: a new endpoint, a capability-table entry, and pinned fixtures), with favorites and recent choices kept client-side. Run creation gains optional `model` and `effort` fields, omitted when unset. Effort is optional and rides in the same control. Choosing a provider or a credential on the client stays excluded: model selection is not credential selection.

Considered options: keeping the full exclusion (rejected: per-run control is a core client expectation); a client-managed model list (rejected: free-typed model identifiers fail at run time, and the server stays the source of truth for what it can run).

Consequences: the non-goal wording in the README and the user guide is amended when the feature ships. A research step verifies real-Gateway catalog support before the contract work starts.
