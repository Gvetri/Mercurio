---
name: Design issue
about: Authorize a design change for one named screen or component
title: "Design: "
labels: design
---

# Design: <surface>

**Status: Ready for agent — code cannot start until this issue closes.**

## Parent

Part of the feature spec #<spec>.

## Design name

`mercurio/<screen>`

The private resolution table maps this name to the design system, the master page, and the screen
page. This issue carries no private link. The design package's `DECISIONS.md` and `DESIGN.md` are read
before this issue answers anything; it does not settle an open decision on its own. See
`docs/design-delivery.md`.

## States matrix

Every cell is a frame, or a named decision that the state does not exist.

| State | Light | Dark |
| --- | --- | --- |
| Empty | | |
| Loading | | |
| Unavailable | | |
| Populated | | |
| <menu, confirmation, in-place edit> | | |

## Component list

The ids from `components.manifest.json` that this screen uses, and every slot it fills.

- `<component-id>` — fills `<slot>`

## Preview

The design page in light and dark. The page is the private design source; this issue records what it
contains, never a path or a link.

## Impeccable record

Each finding is classified as fixed, accepted, or a false positive. A false positive names the reason
and the measurement that clears it.

- `<finding>` — fixed: `<fix>`

## Tradeoff note

What departs from the platform guidance and why it stays, or `none`.

## Approval

- Approved page revision hash: `<hash>`
- Approved by the operator on approval of that hash.

## Acceptance criteria

- [ ] Every state has a frame in light and dark, or a named decision that it does not exist.
- [ ] The component list matches `components.manifest.json`.
- [ ] The Impeccable record is complete and recorded here.
- [ ] The tradeoff note is recorded here, or states `none`.
- [ ] The operator approves the page revision, and its hash is recorded.
- [ ] The code issue can start.

## Blocked by

No GitHub issue. This issue starts after the design source and the private resolution table are
ready for the named screen.
