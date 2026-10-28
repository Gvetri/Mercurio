# Canonical URI scheme

Status: accepted (2026-10-26)

Inbound links need a scheme the client owns. A scheme is public once links exist, and the future interactive sign-in flow needs its browser redirect to live in the same namespace.

Decision: `mercurio` is the client's one canonical URI scheme. Deep links use it (`mercurio://session/{id}`, `mercurio://compose`), and it is reserved as the redirect scheme for the future interactive sign-in flow. No other scheme is registered, and no unbuilt actions are reserved in the scheme.

Considered options: a generic scheme shared across apps (rejected: collision risk, no anchor in the product name); a domain-flavored scheme (rejected: longer, no gain at this layer); choosing the scheme together with the future sign-in flow (rejected: links ship first, and changing a public scheme after links exist is a breaking change).
