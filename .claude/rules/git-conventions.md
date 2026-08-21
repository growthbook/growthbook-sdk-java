---
description: "Commit conventions and release automation rules - releases are generated from commit messages"
alwaysApply: true
---

# Git & Release Conventions

Releases are fully automated by **release-please** (`release-please-config.json`, release type
`java-yoshi`). The version bump and `CHANGELOG.md` are derived from commit messages — a wrong
commit type produces a wrong release.

## Commit Messages: Conventional Commits

Format: `type(optional-scope): summary` — e.g. `feat: codegen CLI with write and check output modes`,
`refactor: centralize user-facing messages in Messages class`.

| Type | Effect on release (pre-1.0 config of this repo) |
| --- | --- |
| `feat:` | patch bump (`bump-patch-for-minor-pre-major`) |
| `fix:` | patch bump |
| `feat!:` / `BREAKING CHANGE:` footer | minor bump (`bump-minor-pre-major`) |
| `refactor:`, `docs:`, `test:`, `chore:`, `ci:`, `build:`, `perf:` | no bump, still land in the changelog where applicable |

- Summary in imperative mood, lowercase, no trailing period.
- A breaking change to the published `lib` API **must** carry `!` or a `BREAKING CHANGE:` footer —
  this is what protects consumers.

## Files Owned by release-please — Never Hand-Edit

- `CHANGELOG.md` — generated from commits
- `version` in `gradle.properties` (`x-release-please` markers)
- `versions.txt`
- `lib/src/main/java/growthbook/sdk/java/Version.java`

If a version looks wrong, fix the commit history/config, not these files.

## Git Hooks (.githooks/)

Committed hooks enforce the rules above locally; activate once with `./gradlew installGitHooks`
(sets `core.hooksPath`):

- `commit-msg` rejects non-Conventional-Commit messages (merge/revert/fixup commits are exempt).
- `pre-commit` rejects staged changes to the release-please-owned files (for `gradle.properties`,
  only the `version=` line is protected).

`git commit --no-verify` bypasses them — use it only when you can explain why.
