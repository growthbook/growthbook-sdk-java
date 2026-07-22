---
description: "Contract for growthbook-cache-* adapter modules implementing the GbCacheManager SPI"
alwaysApply: false
paths:
  - "growthbook-cache-redis/**"
  - "growthbook-cache-jcache/**"
  - "growthbook-cache-caffeine/**"
globs:
  - "growthbook-cache-redis/**"
  - "growthbook-cache-jcache/**"
  - "growthbook-cache-caffeine/**"
---

# Cache Adapter Modules

Each `growthbook-cache-*` module implements the `GbCacheManager` SPI from `lib`
(`growthbook.sdk.java.sandbox`). One backend = one module, depending on `api project(':lib')`.

## SPI Contract (must hold for every adapter)

- `loadCache(key)` returns `null` on a miss — never throws for an absent key.
- `getLastUpdatedMillis(key)` returns epoch millis of the last write, or `null` when the key is
  absent or timestamp tracking is unsupported; `null` makes the SDK treat freshness as unknown and
  refresh over the network.
- `saveContent(key, data)` stores the payload **and** its updated-at timestamp together.
- `clearCache()` removes everything the adapter wrote (and only that — e.g. the Redis adapter
  scans by its own key prefix).
- Real cache-access failures throw `growthbook.sdk.java.exception.FeatureCacheException`.
- Adapters are used from concurrent refresh paths — implementations must be thread-safe.

## Module Conventions

- Options objects follow the established shape: `defaults()`, a validating `builder()`
  (`IllegalArgumentException` for non-positive sizes/durations, `NullPointerException` for nulls),
  and an injectable clock/ticker so tests never sleep.
- Cache client libraries the consumer chooses between (Jedis, Lettuce) are `compileOnly`; a
  provider needed to run tests goes in `testImplementation`.
- Tests follow the repo test style rule (these modules are its reference implementation) and cover
  every contract point above — including miss, overwrite, expiry, and invalid-options cases.
