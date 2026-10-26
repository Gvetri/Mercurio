# Glossary

Language for the Android client that connects to one Gateway server.

## Language

**Gateway**:
The server the client connects to over HTTPS with a bearer credential. The client never hosts a Gateway.
_Avoid_: backend, host, server app

**Session**:
A conversation on the Gateway with its own history.
_Avoid_: chat, thread

**Run**:
One execution the client submits to a Session and observes through status and events.
_Avoid_: job, task, request

**Draft**:
Unsent composer text kept for one Session and persisted per endpoint on the device.
_Avoid_: pending message, queued text

**Authoritative refresh**:
A server read whose result replaces local copies of the same data outright.
_Avoid_: sync, merge, incremental update

**Offline cache**:
The bounded, read-only device store of already-loaded session list entries and Session messages; never authoritative, clearable in Settings.
_Avoid_: local history, offline mode, conversation storage

**Active connection**:
The single Gateway endpoint and credential the client is using. The client keeps exactly one.
_Avoid_: profile, account, workspace

**Model selection**:
The per-run choice of a model offered by the Gateway catalog. Choosing a provider or credential on the client stays out of scope.
_Avoid_: provider selection, model config

**Reasoning effort**:
An optional per-run hint for how much reasoning the server applies, offered with Model selection.
_Avoid_: thinking budget, effort level

**Deep link**:
A link in the app's own scheme that opens a Session or the composer from outside the app.
_Avoid_: URI handler, app link, intent URL

**Canonical scheme**:
The one URI scheme the client owns (`mercurio`). Deep links use it; a future interactive sign-in redirect reuses it.
_Avoid_: custom scheme, protocol
