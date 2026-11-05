# Gateway contract fixtures

The files under [`fixtures/mercurio/contracts`](../fixtures/mercurio/contracts) are
small, repository-owned evidence for the supported Public Beta Gateway boundary.
They are not generated from a running Gateway and they do not require a public
network, a provider, or a credential.

## Provenance

Each accepted JSON or SSE fixture contains exactly one `hermes_revision` field.
Its value must match the immutable provenance in
[`fixtures/mercurio/pinned-fixture.properties`](../fixtures/mercurio/pinned-fixture.properties).
The validator rejects a missing field, duplicate provenance metadata, an
`image_digest` field when `hermes_revision` is selected, or a value that does
not match the descriptor.

The provenance field is fixture metadata. It is outside the request or response
body that a future adapter sends to or reads from the Gateway.

## Supported boundary

The catalog defines the complete boundary for this issue. Future client
adapters must use these shapes and must not infer additional routes or fields.

| Area | Supported shape |
| --- | --- |
| Capabilities | `GET /v1/capabilities` with the `endpoints` table below |
| Connection authentication | Authenticated success and HTTP 401 authentication failure outcomes |
| Session list | `GET /api/sessions` with server `limit` and `offset`; the response is `{"object": "list", "data": [...], "limit", "offset", "has_more"}` |
| Session search | No general search parameter: the client filters the already-loaded rows locally by title and preview. The `title` query is an exact-title lookup, not a search surface. |
| Session last active | The list projection's `last_active` epoch-seconds value feeds the row's relative time label; a missing value renders the placeholder. |
| Session pagination | `offset` advances the window; `has_more` decides whether another page exists |
| Session create | `POST /api/sessions` with an optional title and an authoritative returned Session ID |
| Session open | The authoritative Session resource by Session ID (`GET /api/sessions/{session_id}`) |
| Session history | The authoritative history resource (`GET /api/sessions/{session_id}/messages`); the empty fixture contains no fabricated messages |
| Session rename | `PATCH /api/sessions/{session_id}` with a confirmed `title` |
| Session pin | `PATCH /api/sessions/{session_id}` with `pinned: true` |
| Session unpin | `PATCH /api/sessions/{session_id}` with `pinned: false` |
| Session delete | `DELETE /api/sessions/{session_id}` returning the `hermes.session.deleted` object |
| Run create | `POST /v1/runs` with `session_id` and `input`, plus the optional `model` and `effort` keys that are omitted when unset; the response carries the authoritative Run ID and status |
| Run status | `GET /v1/runs/{run_id}` |
| Run observation | `GET /v1/runs/{run_id}/events` as Server-Sent Events |
| Model catalog | `GET /api/models` (pinned) or `GET /api/model/options` (deployed), additive-optional, never a client-required endpoint; the supported response shapes are defined in [Run model catalog](#run-model-catalog) |

Session row responses (create, get, and patch) can omit the list-only `preview`
field. Missing preview remains unavailable; it is not fabricated from history.
The real pinned database emits integer message IDs and numeric Unix-second
timestamps. The client preserves those values as text in its domain model; it
also accepts string IDs and timestamps. Malformed scalar types still fail.

A Session history response contains `session_id`, an ordered `data` array, and a
`pagination` object. Each message must provide its Gateway `id`. The pinned
`_message_response` whitelist is `id`, `session_id`, `role`, `content`,
`tool_call_id`, `tool_calls`, `tool_name`, `timestamp`, `token_count`,
`finish_reason`, `reasoning`, `reasoning_content`, and `display_kind`. Pinned
messages carry no Run linkage (`run_id`, `run_status`, `run_result`); the client
preserves those fields if a future server returns them, but it never derives
IDs, results, statuses, or timestamps from other fields.

Because the pinned history carries no Run linkage, the client only reconciles
Runs whose IDs it already knows — from a `POST /v1/runs` response or the
persisted recovery registry — through `GET /v1/runs/{run_id}`. It cannot
discover, reconcile, or offer a retry for Runs that exist only in history. This
known-run-ID limitation is the supported boundary, not a gap to work around
with derived data.

The required capability endpoints are the pinned `endpoints` table entries the
client calls:

| Endpoint name | Method | Path |
| --- | --- | --- |
| `sessions` | `GET` | `/api/sessions` |
| `session_create` | `POST` | `/api/sessions` |
| `session` | `GET` | `/api/sessions/{session_id}` |
| `session_messages` | `GET` | `/api/sessions/{session_id}/messages` |
| `session_update` | `PATCH` | `/api/sessions/{session_id}` |
| `session_delete` | `DELETE` | `/api/sessions/{session_id}` |
| `runs` | `POST` | `/v1/runs` |
| `run_status` | `GET` | `/v1/runs/{run_id}` |
| `run_events` | `GET` | `/v1/runs/{run_id}/events` |

Capability detection is fail-closed: every required endpoint must be advertised
with the exact pinned method and path, and a missing or mismatched endpoint
fails connection verification with `REQUIRED_FEATURE_UNAVAILABLE`. Unknown
additive endpoints and unknown additive JSON fields are allowed when every
required endpoint is present. The pinned SSE stream uses `data:` records with
an `event` discriminator inside JSON, not named SSE `event:` or `id:` lines.
The client consumes `message.delta`, `run.completed`, `run.failed`,
`run.cancelled`, and `run.stopping`. Known tool, approval, reasoning, subagent,
and steering events indicate an active Run but do not expose tool or provider
administration. Other event types are ignored. The current adapter has no
server event cursor or event-ID deduplication claim; it prevents duplicate
observers locally. Unknown fields and events must not invent client behavior.

### Run model catalog

The Run model catalog is an additive-optional surface. The client never requires
it and connection verification never depends on it: the manifest version stays
`1` and the required endpoint table above is unchanged.

Two endpoint rows can carry the catalog. The client accepts the first one the
Gateway advertises, in this preference order:

| Preference | Endpoint name | Method | Path | Response shape |
| --- | --- | --- | --- | --- |
| 1 | `run_models` | `GET` | `/api/models` | pinned catalog |
| 2 | `model_options` | `GET` | `/api/model/options` | deployed provider-grouped inventory |

Detection uses the same case-insensitive exact method-and-path rule as required
endpoints, applied to these optional rows: a row is present with the exact
pinned method and path, and that surface is available; no row matches, and the
catalog is unavailable without any failure. The client calls the row it
accepted, so an accepted surface never resolves to a different route.

`GET /v1/models` (the `models` row) is deliberately not a catalog surface. It
means "the agent as a model", not a Run catalog: a live Hermes 0.22.5 Gateway
answers it with the single entry `hermes-agent`, which is not a selectable Run
model.

A bearer-authenticated `GET /api/models` (see the bare request in
[`fixtures/mercurio/contracts/models`](../fixtures/mercurio/contracts/models))
returns HTTP 200 with the pinned shape:

- `object` — the fixed discriminator `hermes.api_server.run_models`;
- `default_model` — string or null; the server's configured Run model, and the
  truthful baseline the client shows when it is non-null;
- `models[]` — a required, non-empty array; each entry carries `id` (a non-blank
  string and the exact value the client sends in Run creation), `display_name`
  (a non-blank UI string), and `provider` (string or null, informational only
  and never credential material);
- `effort` — an optional per-entry object; its presence means the server
  declares effort support for that model. `supported` is a non-empty ordered
  array of server-owned strings that the client never hardcodes, and `default`
  is a string or null that must be a member of `supported` when non-null. An
  entry without `effort` is selectable with no effort control.

A bearer-authenticated `GET /api/model/options` returns HTTP 200 with the
deployed shape (a captured sample is
[`fixtures/mercurio/contracts/models/options-success.json`](../fixtures/mercurio/contracts/models/options-success.json)):

- `providers[]` — a required array of provider rows. Each row carries `slug`
  (a non-blank string, the informational provider label) and `authenticated`
  (a boolean);
- `providers[].models[]` — a required array of model ID strings;
- `model` — string or null; the server's configured Run model, mapped to the
  catalog baseline.

The deployed mapping into the catalog domain follows four rules:

1. Only a provider row with `authenticated: true` contributes entries. An
   unauthenticated provider cannot run a model, so offering one would be a
   selectable-but-dead row.
2. Entries keep provider order, then model order. The first occurrence of a
   model ID wins, because a Run names only the model.
3. `display_name` is the model ID: the deployed inventory publishes no display
   names.
4. `effort` is null for every deployed entry: the deployed inventory publishes
   no effort vocabulary, so the effort control stays hidden and Run creation
   never sends the optional `effort` key.

Unknown additive keys at any level of either response are allowed and ignored,
exactly as on the rest of the boundary. Both catalog shapes, both rows, and the
two Run-create request shapes (unset and selected) are pinned by fixtures and
contract tests, not inferred from a live Gateway. The captured live
capabilities payload pins the drift that motivated the second row: a real
Gateway advertises `model_options` and no `run_models` at all.

Absence is fail-closed in a fixed ladder:

1. No accepted row in the discovered `endpoints` table — the catalog is never
   fetched and model selection stays hidden.
2. The row is advertised but the catalog request returns a non-200 status or a
   transport error — selection stays hidden and connection verification is
   unaffected.
3. A 200 response that is empty or malformed for its surface — for the pinned
   surface a foreign `object` discriminator, a missing or empty `models` array,
   a blank entry field, or an effort `default` outside `supported`; for the
   deployed surface a missing `providers` array, a provider row that is not an
   object, a blank `slug`, a non-boolean `authenticated`, a non-string or blank
   model ID, or no authenticated model at all. A provider row with
   `authenticated: false` is skipped, never invalid, and a provider may list no
   models. The parser fails closed to `INVALID_RESPONSE` internally, the
   catalog is unavailable, selection stays hidden, and the connection stays
   healthy.
4. A catalog entry without its optional `effort` object — that model is
   selectable with no effort control.

Run creation accepts the optional `model` and `effort` keys per Run. An unset
selection is omitted from the request body; a required contract field is never
sent as null. The pinned two-key body in
`fixtures/mercurio/contracts/runs/create-request.json` is the omitted case and
`fixtures/mercurio/contracts/runs/create-request-model-effort.json` pins the
selected case.

## Identity and data policy

Session and Run IDs in the fixtures are opaque, immutable Gateway IDs. The
client must not replace them with local IDs, derive them from text or position,
or use timestamps as identity. History and Run input fixtures contain no
fabricated transcript content. The fixture values are synthetic contract
samples only; they are not user data.

## Malformed cases

The `malformed` directory contains deterministic parser inputs for:

- invalid JSON;
- a missing required JSON field;
- an invalid required JSON field type;
- invalid SSE event framing;
- an incomplete Run admission response; and
- identity-mismatched responses for a Session, history, pin, Run status, or Run
  event, which must fail closed instead of being applied.

The `runs` directory also contains a valid Run observation stream that ends
before a terminal event, so the client must reconcile through the Run resource.

The tests also construct missing-provenance and duplicate-provenance inputs.
Failures use stable safe categories: `INVALID_JSON`,
`INVALID_SSE_FRAMING`, `MISSING_PROVENANCE`, `DUPLICATE_PROVENANCE`,
`INVALID_PROVENANCE`, `MISSING_REQUIRED_FIELD`, and
`INVALID_REQUIRED_FIELD_TYPE`. The parser does not expose raw server content in
these category messages.

## Validation

Run the focused contract check locally:

```text
./gradlew fixtureContractTests
```

The repository quality gate runs this command as the required
`fixture-contract` check. Any added, removed, or changed fixture must update the
catalog or matching contract tests. CI therefore makes fixture changes visible
to compatibility review. This check is deterministic validation, not a live
provider smoke test.

Changing `hermes_revision` or changing any supported request, response, or SSE
shape is a compatibility change. Re-run the contract and lifecycle checks
before changing the pinned descriptor or accepting a new Gateway boundary.

## Excluded behavior

This fixture set does not define dashboard-only routes, profile discovery,
archive or restore, attachments, unsupported interaction types, server search
endpoints, event-resume cursors, or production HTTP/SSE adapters. Ambiguous
behavior is excluded rather than inferred.
