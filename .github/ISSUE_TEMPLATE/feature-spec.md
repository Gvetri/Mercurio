---
name: Feature spec
about: Specify one screen or component change and its design and code issues
title: "Spec: "
labels: enhancement
---

# Spec: <surface>

**Status: Draft — the operator approves the scope before the child issues are published.**

## Problem

What a user cannot do, or what is wrong for them, and what it costs.

## Solution

The change in one or two sentences, and how a user will see it.

## Scope

- The surface, and what it covers.
- Portrait phone only. Tablets and foldables are out of scope.
- Light and dark.

## Child issues

- Design issue: #<design>
- Code issue: #<code>

The code issue is blocked by the design issue and gets `ready-for-agent` only after the design issue
closes. See `docs/design-delivery.md`.

## Out of scope

- What this change does not touch.

## Notes

- The design name is `mercurio/<screen>`. No private link, host name, or account name appears here.
