# Localization

Every user-facing string the client renders lives in a string resource. No Kotlin source carries
copy: a state holder names the resource, and the composition resolves it against the locale the
device is using. This document records that structure, the base locale, and what a translated
language has to add.

## Where the copy lives

| Surface | Resource file | Notes |
| --- | --- | --- |
| Composer, transcript, Session list, Settings, Local diagnostics, connection setup | `feature/entry/presentation/src/main/res/values/strings.xml` | The complete UI surface |
| Run status notification and the diagnostics export chooser | `feature/entry/wiring/src/main/res/values/strings.xml` | Notification channel name, title, and outcome text |
| Application label | `app/src/main/res/values/strings.xml` | The launcher label only |

The English base locale is `values/`. A brand string (`brand_name`, `shell_wordmark`,
`run_status_notification_title`, `app_name`) is marked `translatable="false"`, because a product
name does not change with the reader's language.

## How a state names copy

A Session the Gateway has not titled yet stores no title at all: the pending-title placeholder is
copy the renderer supplies. Two consequences follow deliberately. Search matches server-provided
titles and previews only, so it never matches the placeholder text, and a rename draft for such a
Session starts empty rather than from the placeholder the reader sees.

A UI state never holds a resolved string for copy the client authored. `UiText` carries it instead:

- `UiText.Resource(id)` and `UiText.Plural(id, count)` name one resource, with format arguments.
- `UiText.Value(value)` carries data the Gateway supplied, such as a Session title or a model
  display name, which the client never translates.
- `UiText.Joined(parts, separator)` joins several pieces of copy with a design separator.

A composable resolves the value once, at the point it draws the text. The error taxonomy the client
shows for a failed operation follows the same rule: each category names its resource, and the
approved wording stays fixed in the base locale.

## Adding a language

A translation is a drop-in: create `values-<locale>/strings.xml` in the module that owns the strings
and translate the entries. No Kotlin change is involved, and a string the file leaves out falls back
to the base locale, so a partial translation is a valid state rather than a build failure.

`feature/entry/presentation/src/main/res/values-it/strings.xml` and
`feature/entry/wiring/src/main/res/values-it/strings.xml` are the shipped scaffolding for that
drop-in. They carry no translation yet, so the client renders the base locale for every string.

## What verifies it

`LocalizedResourcesTest` reads the module's resources and fails when a string is empty, when a
shipped locale directory is missing, or when a string no longer falls back to the base locale. The
pinned Compose baselines stay the authority for what each surface draws: the extraction changed
where copy comes from, not what it says.
