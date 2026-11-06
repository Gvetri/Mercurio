## Development setup

A fresh clone carries no `local.properties` and no suite environment. See `docs/development-setup.md`.

## Coding guidelines

Before writing or changing any code, follow `docs/coding-guidelines.md`; it records the repository's
coding rules and what enforces each one.

## Agent skills

### Development setup

A fresh clone carries no SDK location and no suite environment. See `docs/development-setup.md`.

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
`docs/agents/pull-request-checks.md` for the two checks, the body headings, what blocked, behind, and
mergeable each mean, and the review record the two-axis review leaves on the pull request.
