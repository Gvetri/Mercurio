# Committed commit hooks

Two hooks live in `.githooks/` and run before git creates a commit. They are a local convenience, not
a guardrail: CI decides whether a change lands.

Wire both once per clone:

```text
git config core.hooksPath .githooks
```

## What the hooks check

`commit-msg` judges the subject line of the message you are about to commit. It rejects a subject
outside the declared types, the optional lower-case scope, the optional `!` breaking marker, the
no-trailing-period rule, and the 100-character bound. The rule has one home,
`.github/scripts/commit_header.py`: the `commit-message` check imports the same module, so the local
answer and the CI answer cannot disagree. A merge subject passes locally as it does in CI.

`pre-commit` runs two things over the staged files only:

- The formatter, through `./gradlew ktlintFormat` with `-PinternalKtlintGitFilter`, the ktlint plugin's
  own staged-file filter, naming exactly the staged Kotlin sources (`.kt` and `.kts`). The formatted
  files are staged again, so the commit carries the formatted content. The hook needs the same
  configured build the fast loop needs.
- The comments policy, through `.github/scripts/verify-comments.py --files`, which judges the same
  staged set and ignores every file you have not staged.

The hook refuses the commit when a staged Kotlin file also carries unstaged changes: the formatter
rewrites the worktree copy, so staging it again would carry the unstaged hunks into the commit.

## The two limits

- Hooks do not travel with a clone. `core.hooksPath` is repository-local configuration, so a fresh
  clone and another machine start without them until the command above runs again.
- The bypass flag skips them. `git commit --no-verify` runs neither hook, and nothing records that it
  did.

CI remains the only guardrail. The `formatting` check runs `formatCheck commentPolicyVerify` over the
whole repository and the `commit-message` check runs the header rule over the pull-request title and
every commit, so a hook that never ran changes neither verdict.

## Tests

`.github/scripts/tests/test_commit_header.py` pins the rule and its command line, and
`.github/scripts/tests/test_git_hooks.py` invokes each hook against an accepted and a rejected input
in a throwaway repository. The formatter is the one part the suite cannot run, so the suite installs a
`gradlew` that records which files it was asked to format.
