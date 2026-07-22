---
description: "Invariants of the published SDK module (lib) - API stability, thread safety, evaluation architecture"
alwaysApply: false
paths:
  - "lib/**"
globs:
  - "lib/**"
---

# `lib` — the Published SDK

`lib` is published as `com.github.growthbook:growthbook-sdk-java` via JitPack. Everything `public`
is a contract with external consumers.

## Public API Stability

- Do not change or remove `public`/`protected` signatures, move classes between packages, or change
  serialized field names without a deprecation cycle (`@Deprecated` + Javadoc pointing to the
  replacement) and a `BREAKING CHANGE` commit when the old API is finally removed.
- Prefer additive changes; new behavior defaults to the old behavior unless explicitly opted in.
- Every public class and method gets Javadoc: contract, nullability, thread-safety expectations.

## Two Entry Points — Consider Both

1. `multiusermode/GrowthBookClient` — one shared instance per application, owns the repository
   lifecycle (recommended path).
2. `GrowthBook` + `GBContext` — caller-managed, single-context (legacy/low-level path).

A behavior change in evaluation or refresh must work through both, or be explicitly scoped and
documented as one-path-only.

## Thread Safety

- `GrowthBookClient` and `GBFeaturesRepository` are shared across application threads. Any new
  mutable state in these paths must be safe for concurrent use (volatile/atomic/concurrent
  collections — see existing patterns before inventing new ones).
- User-supplied code (`callback/`, `listener/`, `stickyBucketing/`, custom `GbCacheManager`) is
  untrusted: guard invocations so a throwing callback can never break the refresh cycle — catch,
  log via SLF4J, continue (see `GrowthBookClient#refreshGlobalContext` for the pattern).

## Architecture Invariants

- `evaluators/` are stateless; state lives in `GBContext`/`EvaluationContext`, never in evaluator fields.
- `model/` is pure data: Lombok models, Gson-serializable, no I/O and no evaluator/repository imports.
- All Gson access goes through `util/GrowthBookJsonUtils` — no ad-hoc `new Gson()`.
- Feature refresh strategies (`STALE_WHILE_REVALIDATE`, `SERVER_SENT_EVENTS`, `REMOTE_EVAL`) are
  selected via repository configuration; new strategies extend the enum + repository wiring, they do
  not fork the repository class.
- Never log or embed API keys/tokens; decryption of encrypted payloads stays in `DecryptionUtils`.
