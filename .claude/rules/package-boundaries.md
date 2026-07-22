---
description: "Module and package dependency boundaries - must be strictly followed"
alwaysApply: true
---

# Module & Package Boundaries

## Module Dependency Rules

| Module | Can depend on |
| --- | --- |
| `lib` | third-party libs only (Gson, OkHttp, Guava, commons-math3, Lombok, SLF4J) — **no internal modules** |
| `growthbook-cache-*` | `api project(':lib')` + their cache provider |
| `growthbook-codegen` | Gson + OkHttp only — **no dependency on `:lib`** |

- Cache adapters implement the `GbCacheManager` SPI from `lib`'s `sandbox/` package. New cache backends follow the same shape: separate module, `api project(':lib')`.
- In `growthbook-cache-redis`, client libraries (Jedis, Lettuce) are `compileOnly` so consumers pick their own; keep it that way.
- `growthbook-codegen` intentionally does not depend on `:lib`: it only *emits* source code referencing `growthbook.sdk.java.model.TypedKey`, which compiles in the consumer's project. Do not add a `:lib` dependency to it.

## Inside `lib`

Package responsibilities are documented in the root `CLAUDE.md`. Boundary rules that matter when editing:

- `evaluators/` stay stateless; state lives in `GBContext`/repository, not in evaluator fields.
- `model/` stays pure data (Lombok models, no I/O, no OkHttp/repository imports).
- `util/GrowthBookJsonUtils` is the single entry point for Gson configuration — don't create ad-hoc `Gson` instances elsewhere.
