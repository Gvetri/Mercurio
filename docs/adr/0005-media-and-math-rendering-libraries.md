# Media and math rendering libraries instead of hand-rolled pipelines

Status: accepted (2026-10-22)

The codebase hand-rolls its Markdown parsing, syntax highlighting, HTTP, and persistence layers, and carries no image loader, HTML parser, or formula engine. The automatic-fetching decision requires a real media pipeline — fetch, decode, cache, cancellation, and timeout as terminal states — and true math display without a WebView.

Decision: adopt Coil (Apache-2.0) for Message and preview image loading; keep preview metadata extraction a hand-rolled bounded scanner over meta tags and the page title. Adopt the Android port of JLatexMath for formula layout, subject to a licence-compatibility gate: the Android port is GPL-2.0 while the application is MIT, and the compatibility decision is recorded when the ticket runs; if the licence is refused, math renders as styled literal text and this record is amended.

Considered options: a fully hand-rolled media pipeline (rejected: it re-implements lifecycle, caching, and decode edge cases that are known bug classes, against a thin-shell budget); jsoup for preview parsing (rejected: a full HTML parser to read a few head tags); WebView-based rendering of rich content (rejected: the written no-WebView architecture rule stays in force).

Consequences: the runtime dependency list grows by one confirmed entry and one gated entry, establishing an explicit precedent: hand-roll small closed surfaces, buy stateful platform machinery. Licence review becomes a named acceptance criterion for every dependency ticket.

## Licence gate result — math (2026-11-02)

Status: accepted. The gate above is closed for JLatexMath.

The licence is not plain GPL-2.0. The upstream `LICENSE` of JLaTeXMath, and the `LICENSE` of the Android port, carry GPL-2.0-or-later **with the Classpath-style linking exception**: the copyright holders give permission to link the library with independent modules, and to copy and distribute the resulting executable under terms of your choice, provided the licence terms of each independent module are met. The MIT application is such an independent module, so the combined executable may ship under MIT; the GPL obligations stay with the library itself, and this record keeps the licence text and the source-availability statement that those obligations require.

Evidence: the upstream `LICENSE` at `opencollab/jlatexmath` (read 2026-11-02) carries the exception; the port's `LICENSE` at tag `v0.2.0` reuses it verbatim; the Maven Central POM of the pinned artifact declares `GNU General Public License, version 2`, which is the licence without its exception stated; the artifact itself is `ru.noties:jlatexmath-android:0.2.0`, recorded here so a later reader resolves the same bytes.

The licence text and the GPL-2.0 copy travel with the repository under `third_party/jlatexmath/` (`LICENSE`, `COPYING`), because the published AAR carries only the font notices. That file also records the font licences: Knuth, OFL, public domain, and GPL-2.0 for the Greek fonts, which the core artifact does not bundle.

Source availability (GPLv2 §3) is met without shipping source in the APK: the library is used unmodified, and the exact source of the pinned artifact is the public upstream tag `v0.2.0` of `noties/jlatexmath-android`, reachable by any recipient of the application. No written offer is required for a work whose complete corresponding source is publicly available unmodified at a stable tag, and no modification means no §2 change notices are owed.

Platform surface, stated accurately: linking the AAR adds one non-exported `ContentProvider` (`ru.noties.jlatexmath.JLatexMathInitProvider`, authority `<applicationId>.jlatexmathinitprovider`) that seeds the engine's asset context at process start, and it adds an Android `View` class (`JLatexMathView`) that this client never instantiates; the client draws through `JLatexMathDrawable` only.

Known limitation, accepted: the port's upstream repository is archived, so the pinned `0.2.0` is frozen. It is a pure Java layout engine with no network access and no background work, so a frozen artifact is a stability property here, not an exposure. A maintained fork exists but is not published to Maven Central; adding a build-time repository for it is a separate supply-chain decision.

The literal fallback stays: an expression the engine refuses renders its exact source text in a code surface, never blank and never partial. No WebView and no script engine enter the client.
