# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Commands

```bash
# Build everything (includes tests)
./gradlew build

# Run all tests
./gradlew test

# Run a single test class
./gradlew test --tests "growthbook.sdk.java.GBFeaturesRepositoryTest"

# Run a single test method
./gradlew test --tests "growthbook.sdk.java.GBFeaturesRepositoryTest.testMethodName"

# Generate code coverage report → lib/build/jacocoHtml/index.html
./gradlew jacocoTestReport

# Run performance harness
./gradlew runPerfHarness
```

CI runs `./gradlew build --info` on Java 17 (Temurin).

## Architecture

The SDK exposes two usage patterns:

### 1. GrowthBookClient (multi-user, recommended)
`multiusermode/GrowthBookClient` — a single shared instance that owns the feature repository lifecycle. Configured via its inner `Options` builder. The `FeatureRepositoryProvider` manages the underlying `GBFeaturesRepository` and wires up refresh strategies automatically.

### 2. GrowthBook (single-context, lower-level)
`GrowthBook` — the classic entry point. The caller creates a `GBFeaturesRepository`, builds a `GBContext`, and passes it in manually. Used when you need full control or are evaluating a single user context.

### Core packages

| Package | Responsibility |
|---|---|
| `evaluators/` | Stateless evaluators for features, experiments, and conditions. Each implements a corresponding `I*Evaluator` interface. |
| `repository/` | Feature data fetching & caching. `GBFeaturesRepository` is the base; `NativeJavaGbFeatureRepository` adds SSE and remote-eval support. Refresh strategies: `STALE_WHILE_REVALIDATE`, `SERVER_SENT_EVENTS`, `REMOTE_EVAL`. |
| `model/` | Pure data: `GBContext`, `Feature`, `Experiment`, `FeatureResult`, `ExperimentResult`, `Namespace`, etc. |
| `sandbox/` | Pluggable caching layer. `GbCacheManager` interface with `InMemoryCachingManagerImpl` and `FileCachingManagerImpl`. |
| `stickyBucketing/` | `StickyBucketService` interface for persisting experiment assignments across requests. |
| `listener/` | `FeatureRefreshListener` / `FeatureRefreshSubscription` — pub/sub for feature refresh events, dispatched through `FeatureRefreshListenerDispatcher`. |
| `callback/` | Callback interfaces: `ExperimentRunCallback`, `TrackingCallback`, `FeatureUsageCallback`, `FeatureRefreshCallback`. |
| `util/` | `GrowthBookUtils` (hashing, bucketing), `DecryptionUtils`, `GrowthBookJsonUtils` (Gson wrappers). |

### Key dependencies
- **Gson 2.12.1** — all JSON serialization/deserialization
- **OkHttp 4.11.0 + okhttp-sse** — HTTP fetching and Server-Sent Events
- **Lombok** — `@Builder`, `@Getter`, `@Setter`, `@Data` used extensively on models
- **SLF4J** — logging facade; no backend bundled