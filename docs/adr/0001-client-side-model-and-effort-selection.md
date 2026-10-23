# Client-side model and effort selection

Status: accepted (2026-10-22)

The README and the user guide excluded client-side provider, model, and credential choice. The posture review re-opened that exclusion for model and reasoning-effort selection: per-run control is a core mobile expectation, and the server already advertises its configured model through `capabilities.model`.

Decision: the client gains a per-run model + reasoning-effort picker that remembers the last choice. The model list comes from a server-provided catalog endpoint (an additive contract change: a new endpoint, a capability-table entry, and pinned fixtures), with favorites and recent choices kept client-side. Run creation gains optional `model` and `effort` fields, omitted when unset. Effort is optional and rides in the same control. Choosing a provider or a credential on the client stays excluded: model selection is not credential selection.

Considered options: keeping the full exclusion (rejected: per-run control is a core client expectation); a client-managed model list (rejected: free-typed model identifiers fail at run time, and the server stays the source of truth for what it can run).

Consequences: the non-goal wording in the README and the user guide is amended when the feature ships. A research step verifies real-Gateway catalog support before the contract work starts.

## Contract design (spec #252 delivery)

## Context

The client cannot influence or see which model executes a Run. Run creation
accepts only `input` and `session_id`, and the server-side configuration
decides the model and its reasoning effort for every Run. Spec #252 opened a
posture review of where that choice should live. Three shapes were considered:

1. A dedicated additive catalog endpoint the client reads and sends per-Run
   selections to.
2. Embedding the catalog into the existing `GET /v1/capabilities` discovery
   response.
3. Reusing the existing OpenAI-compatible `GET /v1/models` listing.

A survey of a real pinned Gateway established the facts: `GET /v1/models`
already means "the agent as a model" and carries no display names or effort
metadata, so putting Run-catalog semantics there would change the meaning of an
existing surface; the `endpoints` capability table is the only discovery
mechanism the client trusts; and the Gateway tolerates unknown additive fields
on Run creation today. Provider and credential administration surfaces expose
credential material and environment-variable names and are out of bounds for
the client.

## Decision

Adopt client-side, per-Run model and effort selection driven by an
additive-optional Gateway catalog, the approved shape from the posture review:

- The catalog is a new endpoint, `GET /api/models`, advertised additively as
  the `run_models` row (`GET`, `/api/models`) of the `endpoints` capability
  table. The full response schema, the detection rule, and the fail-closed
  absence ladder are defined in
  [Gateway contract fixtures](../gateway-contract-fixtures.md#run-model-catalog).
- The capability entry is optional: it is not a client-required manifest row,
  it is absent from the pinned required-set fixture, and the manifest version
  stays 1. A Gateway without the catalog behaves exactly as before; connection
  verification is never blocked by the catalog.
- Run creation gains optional `model` and `effort` keys, omitted when unset; a
  required contract field is never sent as null.
- The server owns the vocabulary: model IDs and effort values come only from
  the catalog, and the client never hardcodes them and never renders them as
  provider or credential choices.
- Selection is per Run. The server's configured model from the catalog
  `default_model` is the visible baseline; clearing the override sends no
  selection keys.

## Consequences

- The catalog contract is a forward contract at the pinned Hermes revision: the
  upstream Gateway does not serve `GET /api/models` yet, so the picker stays
  hidden against real Gateways until the route exists, and the pinned fixtures
  carry the shape instead of live traffic.
- Two Run-create request shapes are now pinned by fixtures and contract tests
  (unset and selected), so any future change to the optional keys is a visible
  compatibility change.
- Client work is split across the campaign: catalog fetch and selection memory
  beside the existing ports, then composer plumbing. Each treats absence with
  the documented fail-closed ladder and adds no new required surface.
- Journey scenarios may advertise `run_models` additively but never require it;
  the required-set gate keeps its current set.
- Rejected model or effort values surface asynchronously (the Gateway admits
  unknown values today), so refusal copy must come from the Run-failure path,
  not only admission.

Verify the boundary with the documented fixture and unit gates, for example:

```text
./gradlew fixtureContractTests
./gradlew :feature:entry:domain:test
```
