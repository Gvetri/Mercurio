---
name: Code issue
about: Implement an approved design on one named screen or component
title: "Implement: "
labels: enhancement
---

# Implement: <surface>

**Status: Blocked by the design issue. Do not start until the design issue closes and
`ready-for-agent` is added.**

## Parent

Part of the feature spec #<spec>.

## What to build

Implement the approved design in Material 3. The design issue is the reference. Do not guess.

## Design name

`mercurio/<screen>`

## Requirements

- Material 3 only. No ad-hoc hex value, no one-off spacing, no bespoke component.
- No generator. Copy the design values by hand, and map each value to a Material 3 role or token.
- Cover every state the design issue lists, in light and dark.
- Portrait phone layout.
- When the code cannot match the approved design, stop and open a design issue instead of
  improvising.

## Verification

- Run the local value check against `HermesDesignTokens.kt` before the pull request.
- The `compose-jvm-tests` gate compares the app with its own baseline, pixel-exact. Add or update the
  baseline for every state in light and dark.
- The reviewer compares each capture with the design page using the checklist: roles, type scale,
  spacing steps, states, and both themes.
- Run the Maestro journey when the `run-maestro` label is set.
- Run the Impeccable native audit on the implemented screen and record the findings and the fixes.

## Evidence in the pull request

- Link to the design issue and the design name.
- Before and after captures for every state in light and dark.
- The checklist result.
- The value check result.
- The screenshot test report.
- The Maestro result when `run-maestro` is set.
- The Impeccable audit result.
- The tradeoff note, or `none`.

The full contract and the evidence table are in `docs/design-delivery.md`.

## Blocked by

- #<design>. This issue becomes `ready-for-agent` only after the design issue closes.
