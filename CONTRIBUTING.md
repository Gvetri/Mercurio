# Contributing

## Public scope

Mercurio is an independent Android client. Contributions must keep the public repository self-contained and must not add private organization details, real Gateway endpoints, credentials, provider secrets, local Hermes runtime code, desktop web interface code, or WebView-based desktop reuse.

The client connects to a pre-existing Hermes Gateway. It does not host the Gateway or embed a local Hermes runtime.

## Development setup

Read [Development setup](docs/development-setup.md) first: it states the toolchain a build needs and
the two machine-local settings a clone does not carry — the `local.properties` SDK location, and the
`ANDROID_HOME` and `JAVA_HOME` export the release-script suites read. Use the checked-in Gradle
wrapper:

```text
./gradlew :app:assembleDebug
```

## Test-first changes

For behavioral changes, write one focused failing test, run it, implement the smallest passing change, and then refactor without changing behavior. Use deterministic fakes at datasource and repository boundaries. Do not add a mocking framework.

Run focused tests while working:

```text
./gradlew :feature:entry:application:test
./gradlew :feature:entry:data:test
./gradlew :feature:entry:presentation:testDebugUnitTest
./gradlew :feature:entry:wiring:testDebugUnitTest
```

Run the local deterministic gate before submitting a change:

```text
./gradlew qualityGate
```

The gate enforces the scopes and thresholds declared in
`buildSrc/src/main/kotlin/org/mercurio/client/buildlogic/QualityPolicy.kt`; see
[Quality gates](docs/quality-gates.md) for the required checks and how to change a declared value.
The verification tasks also run on their own:

```text
./gradlew coverageVerify mutationVerify verifyDeterministicFakes
```

A declared threshold is evidence, not a summary: a missing, empty, stale, narrowed, or unparsable
report fails the verification that reads it, so skipping a report-producing task cannot make the gate
pass.

If an emulator is available, also run the app instrumentation scope:

```text
./gradlew :app:verifyConnectedAndroidTests
```

## Coding guidelines

The repository's coding rules — including the comments policy — and what enforces each one live in
[Coding guidelines](docs/coding-guidelines.md).

## Branch and pull request workflow

`main` is protected, so create one issue-scoped branch per change, named
`<type>/<issue>-<short-description>` such as `feat/25-protected-main-workflow`, and open a pull
request against `main`. Deliver one pull request per issue. Integration branches are not allowed: a
branch that accumulates several issues or tickets before review holds too many files, turns every
review into a large cumulative diff, and ties unrelated fixes together. A multi-ticket effort may
share its full specification context, but each issue ships as its own self-contained vertical
slice — a change that works and reviews on its own — through its own issue-scoped pull request. A
change that depends on an unmerged pull request waits for that merge and then bases on the updated
`main` instead of stacking on the open branch.

While the pull request is a draft it runs `formatCheck` and `:app:lintDebug`
only; marking it ready for review runs the complete gate against the same head, and the ruleset
requires both `quality-gate` and `pr-template`. See
[What a pull request must satisfy](docs/agents/pull-request-checks.md) for the body headings and
what blocked, behind, and mergeable each mean. Merging is squash-only, the squash subject is the
pull-request title, and both that title and every commit must be a Conventional Commit. Do not merge
or cherry-pick an external fork directly: carry the change onto an in-repository branch first. See
[The protected main branch and the pull-request workflow](docs/protected-main-workflow.md).

## Emulator journeys

The Maestro journey suite runs on the nightly schedule and on a manual dispatch, not on ordinary pull
requests. A pull request asks for it with the `run-maestro` label. Add that label when the change
touches a key area: the app shell, a user-visible screen, a user-visible string or resource, the
journey fixtures, or the state, Gateway, and wiring code that those journeys assert. Agents add the
label for those paths without being asked. Add it before the push that starts the gate: a label added
while a run is in flight cancels that run. See
[Requesting the journey lane](docs/deterministic-emulator-journeys.md#requesting-the-journey-lane)
for the key areas and their reasons.

## Release signing

Release keys are available only to the main-only signing job, never to pull-request or build
jobs. Nightly signing is unattended; standalone signing and Stable Public Beta promotion remain
human-approved. See [Protected Android release signing](docs/release-signing.md) for source validation, secret
custody, recovery, and the controlled key-incident process. Do not add local signing credentials or
provider credentials to a contribution. [Nightly releases](docs/nightly-releases.md) use
workflow-generated versions; do not edit the local development version to publish a Nightly.

## Visual regression baselines

The presentation module keeps the approved Roborazzi reference images in
`feature/entry/presentation/src/test/snapshots`. Pull requests verify the
images with the JVM/Robolectric Compose test gate.

### Approved state matrix

`feature/entry/presentation/src/test/roborazzi-baselines.txt` lists the PNGs
that belong to the matrix. Each entry maps to one test method in
`SelectedVisualRegressionTest`, in `SelectedShellVisualRegressionTest` for the
shell window contracts, in `SelectedMarkdownVisualRegressionTest` for the
Markdown rendering contracts, in `ChatDesignStatesTest` for the chat states, in
`HistoryCacheVisualRegressionTest` for the cache states, in `SettingsDesignStatesTest`
for the approved `mercurio/settings` states, or in `AppLockGateVisualRegressionTest`
for the app-lock gate.

| State | Compose surface | Reason for inclusion |
| --- | --- | --- |
| `connection_form` | Gateway connection | Stable first-use form with synthetic inputs |
| `connection_form_dark` | Gateway connection | The same form on the dark token set |
| `connection_error` | Gateway connection | Recoverable authentication error |
| `connection_error_dark` | Gateway connection | The same error on the dark token set |
| `connection_saving_light` | Gateway connection | Verified secure-save phase with locked controls |
| `connection_saving_dark` | Gateway connection | The same phase on the dark token set |
| `connection_storage_retry_light` | Gateway connection | Secure-save failure with `Try again` and the completion limit |
| `connection_storage_retry_dark` | Gateway connection | The same failure on the dark token set |
| `connection_replacement_ready_light` | Gateway connection | Replacement form with the fixed endpoint |
| `connection_replacement_ready_dark` | Gateway connection | The same form on the dark token set |
| `connection_remove_confirmation_light` | Gateway connection | Standard removal confirmation over the retained Sessions surface |
| `connection_remove_confirmation_dark` | Gateway connection | The same confirmation on the dark token set |
| `connection_remove_confirmation_wide_light` | Gateway connection | The confirmation centered on the wide two-pane window |
| `connection_remove_confirmation_wide_dark` | Gateway connection | The same wide confirmation on the dark token set |
| `connection_storage_retry_short_ime_200_light` | Gateway connection | Short-window storage retry at 200% font scale, scrolled to the retained failure message |
| `app_lock_gate_light` | App lock gate | The whole-app gate with nothing but the approved empty state and its unlock action |
| `app_lock_gate_dark` | App lock gate | The same gate on the dark token set |
| `session_list_populated_light` | Session list | Pinned and unpinned server metadata in the approved composition |
| `session_list_populated_dark` | Session list | The same list on the dark token set |
| `session_list_selected_row_light` | Session list | The selected-row component tint on the light token set |
| `session_list_selected_row_dark` | Session list | The selected-row component tint on the dark token set |
| `session_list_empty_light` | Session list | Valid empty Gateway response |
| `session_list_empty_dark` | Session list | The same empty state on the dark token set |
| `session_list_no_match_light` | Session list | Local search with no match and the clear control |
| `session_list_no_match_dark` | Session list | The same no-match state on the dark token set |
| `session_list_loading_light` | Session list | First read in progress with skeleton rows and the list-end footer |
| `session_list_loading_dark` | Session list | The same loading state on the dark token set |
| `session_list_unavailable_light` | Session list | Cached rows with the offline status strip |
| `session_list_unavailable_dark` | Session list | The same unavailable state on the dark token set |
| `session_list_pending_title_light` | Session list | An untitled Session row with the pending-title preview |
| `session_list_pending_title_dark` | Session list | The same pending-title state on the dark token set |
| `session_list_row_actions_light` | Session list | A held row with its anchored context menu |
| `session_list_row_actions_dark` | Session list | The same context menu on the dark token set |
| `session_list_delete_confirmation_light` | Session list | The delete confirm dialog over the list |
| `session_list_delete_confirmation_dark` | Session list | The same dialog on the dark token set |
| `session_list_refreshing_light` | Session list | The pull-to-refresh indicator over a readable list |
| `session_list_refreshing_dark` | Session list | The same refreshing state on the dark token set |
| `session_list_loading_more_light` | Session list | The 48 dp list-end footer while the next page loads |
| `session_list_loading_more_dark` | Session list | The same footer on the dark token set |
| `session_list_rename_light` | Session list | The inline rename editor with the filled check/cross pair |
| `session_list_rename_dark` | Session list | The same inline editor on the dark token set |
| `session_list_settings_open` | Session list | The Settings surface open over the list |
| `gateway_removal_confirmation` | Session list | D-02 Gateway-removal confirmation dialog |
| `empty_session_detail` | Session detail | New Session with no history |
| `session_detail_with_messages` | Session detail | Stable user and assistant history |
| `session_detail_overflowing_transcript` | Session detail | A transcript longer than the pane, opened at its newest message |
| `session_detail_active_run` | Session detail | Active run with a partial response |
| `session_detail_error` | Session detail | Failed send with the draft preserved |

### Shell contract matrix

`SelectedShellVisualRegressionTest` pins the adaptive shell contract with one
Session/conversation state rendered in intentional light and dark themes on a
phone single-pane window and on a two-pane window.

| State | Window | Reason for inclusion |
| --- | --- | --- |
| `phone_single_pane_light` | `w411dp-h891dp-notnight` | Phone conversation with the light token set |
| `phone_single_pane_dark` | `w411dp-h891dp-night` | Phone conversation with the dark token set |
| `two_pane_light` | `w1000dp-h800dp-notnight` | Session list and conversation side by side, light token set |
| `two_pane_dark` | `w1000dp-h800dp-night` | Session list and conversation side by side, dark token set |

### Markdown contract matrix

`SelectedMarkdownVisualRegressionTest` pins the supported rendering of untrusted
Gateway content: a fenced code block in its horizontally scrollable region, a
long multi-block message, and a message with inline, display and refused math, each
on the phone viewport in the light and the dark token set.

| State | Window | Reason for inclusion |
| --- | --- | --- |
| `markdown_code_block_light` | `w411dp-h891dp-notnight` | Code block with its language label, its Copy action, and an inert rejected-scheme link |
| `markdown_code_block_dark` | `w411dp-h891dp-night` | The same state on the dark token set |
| `markdown_long_content_light` | `w411dp-h891dp-notnight` | Long multi-block response that scrolls natively instead of truncating |
| `markdown_long_content_dark` | `w411dp-h891dp-night` | The same state on the dark token set |
| `markdown_math_light` | `w411dp-h891dp-notnight` | Inline formula in its line, a centred display formula in its own surface, and a refused expression kept as its exact source |
| `markdown_math_dark` | `w411dp-h891dp-night` | The same state on the dark token set |

### Settings design-state matrix

`SettingsDesignStatesTest` pins the shipped states of the approved `mercurio/settings` page: the
connected list, the connection-actions menu, Notifications on, Media off, About, and the 200% font
scale witness, each in the light and the dark token set. A section whose capability has not shipped
is absent from the capture, so the matrix grows with the code issues that add those capabilities.

| State | Window | Reason for inclusion |
| --- | --- | --- |
| `settings_02_connected_light` | `w360dp-h800dp-notnight` | Connected default with every shipped section |
| `settings_02_connected_dark` | `w360dp-h800dp-night` | The same list on the dark token set |
| `settings_03_connection_menu_light` | `w360dp-h800dp-notnight` | The only menu: change credential and remove connection |
| `settings_03_connection_menu_dark` | `w360dp-h800dp-night` | The same menu on the dark token set |
| `settings_06_security_no_enrolment_light` | `w360dp-h800dp-notnight` | App lock with no screen lock set: the row names the precondition and the switch is disabled |
| `settings_06_security_no_enrolment_dark` | `w360dp-h800dp-night` | The same state on the dark token set |
| `settings_07_security_off_light` | `w360dp-h800dp-notnight` | App lock enrolled and off |
| `settings_07_security_off_dark` | `w360dp-h800dp-night` | The same state on the dark token set |
| `settings_08_security_on_light` | `w360dp-h800dp-notnight` | App lock enrolled and on |
| `settings_08_security_on_dark` | `w360dp-h800dp-night` | The same state on the dark token set |
| `settings_10_notifications_on_light` | `w360dp-h800dp-notnight` | Notification opt-in on with its current-behaviour line |
| `settings_10_notifications_on_dark` | `w360dp-h800dp-night` | The same state on the dark token set |
| `settings_12_media_off_light` | `w360dp-h800dp-notnight` | Automatic picture loading off |
| `settings_12_media_off_dark` | `w360dp-h800dp-night` | The same state on the dark token set |
| `settings_23_about_light` | `w360dp-h640dp-notnight` | Version, build, and install-source rows in the shortened window, scrolled to the install-source value |
| `settings_23_about_dark` | `w360dp-h640dp-night` | The same rows on the dark token set |
| `settings_27_font_scale_200_light` | `w360dp-h640dp-notnight` | 200% type in the shortened window |
| `settings_27_font_scale_200_dark` | `w360dp-h640dp-night` | The same witness on the dark token set |

The Session list has no creation dialog: `+ new session` creates the Session and keeps
the list in place, and the new row carries the pending-title preview until the Gateway
titles it. The Gateway-removal confirmation
(`gateway_removal_confirmation`, and the `connection_remove_confirmation*` states over the
retained Sessions surface) uses the stock `AlertDialog` surface. The Delete confirmation
uses the same stock `AlertDialog`, hosted at the entry root so it stays reachable in both
layouts. Rename stays inline: the row's title cell becomes the editor with the filled
check and the neutral cross, so it stays covered without a dialog overlay.

The matrix uses Robolectric SDK 35, native graphics mode, and synthetic in-memory values.
Each capture declares its viewport qualifier: the default is `w411dp-h891dp-notnight`; the
dialog variants select `w411dp-h891dp-night` for the dark capture, `w1000dp-h800dp-notnight`
for the two-pane capture, and `w411dp-h289dp-notnight` for the Session-dialog
constrained-height witness or `w411dp-h480dp-notnight` for the Gateway short-window witness,
both of which also set a 2x font scale. The shell and markdown contract classes pass an
explicit light or dark token set, and the entry contract class passes an explicit value for
its dark captures and otherwise resolves the declared viewport's day or night configuration,
so each capture proves one designed window and theme configuration. The settings design-state
class works the same way. All four classes exclude timestamps,
live Gateway/provider data, credentials, animated loading states, Activity lifecycle
behavior, and other system-dependent rendering. Behavior and semantics tests remain in their
existing test classes.

The pull-request Compose job first runs
`verifyRoborazziBaselineManifest`. It reports `Missing baselines` and
`Unexpected baselines` by path. It then runs `verifyRoborazziDebug`, which
compares each captured image and fails changed pixels with the Roborazzi
comparison report. Both steps are part of the required `compose-jvm-tests`
check.

When a deliberate UI change requires a baseline update:

1. Run `./gradlew :feature:entry:presentation:compareRoborazziDebug`.
2. Review the comparison report at
   `feature/entry/presentation/build/reports/roborazzi/debug/index.html` and
   the generated images under
   `feature/entry/presentation/build/outputs/roborazzi-comparison`.
3. Run `./gradlew :feature:entry:presentation:recordRoborazziDebug` to replace
   the approved reference images.
4. Review the PNG changes and update
   `feature/entry/presentation/src/test/roborazzi-baselines.txt` when the
   selected state matrix changes.
5. Run `./gradlew :feature:entry:presentation:verifyRoborazziBaselineManifest`
   and `./gradlew :feature:entry:presentation:verifyRoborazziDebug`.

Do not update a baseline to hide an unintended change. Keep the matrix small
and stable instead of adding screenshots only to increase the count.

## Design changes

A change to a surface a user can see starts from a GitHub issue that approves the design first. The
feature spec, the design issue, and the code issue are three linked issues, and the code issue stays
blocked until the design issue closes. [Design delivery](docs/design-delivery.md) is the contract: the
issue chain, the design name rule, the states matrix, the light and dark rule, the two screenshot
checks, the evidence checklist, the stop rule, and the Impeccable gates. The issue templates are in
`.github/ISSUE_TEMPLATE`.

The `design` label marks a design issue and is the only label that authorizes a design change. A
code issue carries `ready-for-agent` only after its design issue has closed. The design source is
private and is not part of this repository; a public issue carries the design name
`mercurio/<screen>`, never a design path, a design link, or a host name.

A pull request that touches a designed surface fills the design evidence section from the
pull-request template in addition to the required sections.

## Module changes

Keep feature logic in a feature-first vertical slice. Domain and application modules must remain plain Kotlin/JVM. Put concrete adapters in data, UDF state and Compose rendering in presentation, and implementation selection in wiring. Android-only persistence bridges may remain in wiring when the data module stays JVM-only; keep their feature behavior in data. Keep the app module a thin composition root.

## Public documentation

Documentation must describe public behavior and public build steps only. Do not copy private planning documents or internal deployment procedures into this repository.
