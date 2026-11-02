## Coding guidelines

Before writing or changing any code, follow `docs/coding-guidelines.md`; it records the repository's
coding rules and what enforces each one.

## Agent skills

### Issue tracker

Issues live in this repo's GitHub Issues; use `gh`. See `docs/agents/issue-tracker.md`.

### Triage labels

Use the default triage labels listed in `docs/agents/triage-labels.md`.

### Domain docs

Use the single-context layout. See `docs/agents/domain.md`.

### Verify your changes

Run the documented loop and gates before opening a PR. See `docs/agents/verify-your-changes.md`.

### Pull request checks

A pull request merges only when both required contexts pass. See
`docs/agents/pull-request-checks.md` for the two checks, the body headings, and what blocked,
behind, and mergeable each mean.
