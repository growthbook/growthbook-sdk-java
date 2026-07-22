---
description: "Invariants and structure of the growthbook-codegen CLI module"
alwaysApply: false
paths:
  - "growthbook-codegen/**"
globs:
  - "growthbook-codegen/**"
---

# growthbook-codegen Rules

CLI (`growthbook.codegen.Cli`) that fetches feature definitions from the GrowthBook REST API and generates a constants class of `TypedKey`s. Packaged as a self-contained runnable jar.

## Invariants

- **Deterministic output**: generated source must be reproducible — feature keys sorted alphabetically, no timestamps — so regeneration yields clean git diffs and `--check` works in CI.
- **Token safety**: the API token comes only from the `GROWTHBOOK_API_KEY` env var and is sent only as the `Authorization` header. It must never appear in messages, logs, diffs, or generated code.
- **All user-facing strings live in `Messages`**; exit codes in `ExitCodes`. Don't inline new message literals in the pipeline.
- **No dependency on `:lib`** — the generated code references `growthbook.sdk.java.model.TypedKey`, but the codegen module itself must stay standalone (Gson + OkHttp only).
- Java 8 compatible, like the rest of the repo.

## Structure

`source/` (API fetching) → `render/` (pure source rendering) → `output/` (write or `--check` diff via `util/UnifiedDiff`). `CliOptions` parses args; keep rendering pure (no I/O).

## Tests

`./gradlew :growthbook-codegen:test` — end-to-end CLI tests use `MockWebServer`; expected generated source lives in `src/test/resources/expected/`.
