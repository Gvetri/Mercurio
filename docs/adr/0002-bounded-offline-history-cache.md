# Bounded offline history cache

Status: accepted (2026-10-22)

The client persisted no conversation content, so offline the app showed only drafts and recovery state. The posture review adopted a bounded cache: offline reading is a core mobile expectation, and the codebase already has proven bounded device-local persistence.

Decision: the client caches the session list and the messages of recently viewed sessions, read-only. Bounds: 5,000 messages, 7-day expiry, clearable in Settings. The cache is encrypted at rest (device Keystore tier, same class as the credential). It is never authoritative: an authoritative refresh replaces it. Only content already loaded on the device is cached; there is no background sync. The cache never feeds Run reconciliation or recovery.

Considered options: keeping the strict exclusion (rejected: offline reading has real value, and the guardrails keep the posture intact); a full offline app with later sync (rejected: it breaks server-authoritative reconciliation).

Consequences: the non-goal wording in the user guide is rewritten when the feature ships.
