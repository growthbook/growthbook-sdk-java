---
description: "GrowthBook Java SDK project architecture, Gradle modules, and build layout"
alwaysApply: true
---

# GrowthBook Java SDK — Project Overview

You are working on the GrowthBook Java SDK, a feature-flagging and A/B-testing client library (group `com.github.growthbook`, distributed via JitPack, versioned by release-please).

## Gradle Modules (settings.gradle)

| Module | Purpose |
| --- | --- |
| `lib` | The SDK itself (`growthbook.sdk.java`). Evaluators, feature repository, models, caching SPI, sticky bucketing. |
| `growthbook-cache-redis` | Cache adapter backed by Redis (Jedis/Lettuce as `compileOnly` provider deps). |
| `growthbook-cache-jcache` | Cache adapter for any JSR-107 provider. |
| `growthbook-cache-caffeine` | Cache adapter backed by Caffeine. |
| `growthbook-codegen` | Standalone CLI that generates a typed feature-key constants class from the GrowthBook REST API. |

## Not Part of the Build

`growth-book-playground/` and `sdk-playground/` exist in the repo but are **not** included in `settings.gradle` — they are standalone playground apps with their own Gradle builds that consume the SDK (composite build / published artifact) and may lag behind the current SDK code. Do not add them to root Gradle invocations, do not include them in repo-wide refactors or renames, and scope tasks to real modules (e.g. `./gradlew :lib:test`).

## CI

GitHub Actions runs `./gradlew build --info` on Java 17 (Temurin). All published modules must stay compatible with Java 8 bytecode (`sourceCompatibility = 1.8`).
