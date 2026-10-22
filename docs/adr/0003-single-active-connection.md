# Single active connection

Status: accepted (2026-10-22)

A multi-server registry was requested. The client provides one explicit Gateway connection flow, and its device-local storage is endpoint-scoped. The posture review deferred the decision instead of changing the posture.

Decision: the client keeps exactly one active Gateway connection. Saved connections (one active at a time) and a full multi-server registry are revisited at the next posture round, after the quick-win features. The variant choice — saved connections with one active connection versus a full concurrent registry — is made at that revisit.

Considered options: a full concurrent registry now (rejected: concurrent streams, per-server credential isolation, and push pairing are a large surface with no current demand); saved connections with one active connection (the leading candidate for the revisit).
