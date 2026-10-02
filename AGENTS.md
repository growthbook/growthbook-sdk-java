# AGENTS.md

Guidance for AI coding agents working in this repository. `CLAUDE.md` is a symlink to this file.

GrowthBook Java SDK — a feature-flagging and A/B-testing client library, published as
`com.github.growthbook:growthbook-sdk-java` via JitPack and versioned by release-please.

## Modules

`settings.gradle` includes exactly three projects:

| Module | Purpose |
| --- | --- |
| `lib` | The SDK itself (`growthbook.sdk.java`). Evaluators, feature repository, models, caching SPI, sticky bucketing, plugins. |
| `growthbook-cache-jcache` | Cache adapter for any JSR-107 provider. |
| `growthbook-cache-caffeine` | Cache adapter backed by Caffeine. |

`growthbook-cache-redis/` and `growthbook-codegen/` directories exist in the working tree but are
**not** in `settings.gradle` — they live on feature branches. `./gradlew :growthbook-codegen:test`
fails with "Project not found". Scope tasks to the three modules above.

## Commands

```bash
./gradlew build                 # everything, including tests
./gradlew test                  # all tests
./gradlew :lib:test             # one module
./gradlew :lib:test --tests "growthbook.sdk.java.GBFeaturesRepositoryTest"
./gradlew jacocoTestReport      # coverage → lib/build/jacocoHtml/index.html
./gradlew javadoc               # docs → lib/build/docs/javadoc
./gradlew runPerfHarness        # evaluation performance harness
./gradlew installGitHooks       # one-time: point core.hooksPath at .githooks
```

Build with JDK 17 (matches CI). Newer JDKs break the Lombok version used here.

## CI

| Workflow | What it runs |
| --- | --- |
| `ci.yml`, `ci_linux_windows.yml` | `./gradlew build --info` on JDK 17 |
| `java_versions.yml` | `./gradlew build --info` on JDK 8, 11, 16 — the real Java 8 gate |
| `conventional-commits.yml` | PR title + release-file guard |
| `publish-docs.yml`, `release-please.yml` | javadoc; release and publish |

## Architecture

Two entry points — a behavior change in evaluation or refresh must work through both, or be
explicitly scoped and documented as one-path-only.

1. `multiusermode/GrowthBookClient` — one shared instance per application, owns the feature
   repository lifecycle (holds it in an `AtomicReference<GBFeaturesRepository>`; recommended path).
2. `GrowthBook` + `GBContext` — caller-managed, single user context (lower-level path).

### Packages in `lib`

| Package | Responsibility |
| --- | --- |
| `evaluators/` | Stateless evaluators for features, experiments, conditions; each implements an `I*Evaluator`. |
| `repository/` | Feature fetching and caching behind `IGBFeaturesRepository`: `GBFeaturesRepository` is the base, `NativeJavaGbFeatureRepository` the JDK-HTTP variant, `LocalGbFeatureRepository` the offline one. `FeatureRefreshStrategy` = `STALE_WHILE_REVALIDATE`, `SERVER_SENT_EVENTS`, `REMOTE_EVAL_STRATEGY`; `RefreshMode` = `DEFAULT`, `FORCE`. |
| `model/` | Pure data — `GBContext`, `Feature`, `Experiment`, `FeatureResult`, `Namespace`, … |
| `sandbox/` | Caching SPI: `GbCacheManager` plus `InMemoryCachingManagerImpl` and `FileCachingManagerImpl`. |
| `stickyBucketing/` | `StickyBucketService` — persists experiment assignments across requests. |
| `plugin/` | `GrowthBookPlugin` SPI and `PluginRegistry`; `plugin/tracking/` is the built-in tracking plugin. |
| `diagnostics/` | Read-only snapshot of SDK, cache, refresh and config state. |
| `remoteeval/`, `sse/`, `featurefetch/`, `retry/` | Remote evaluation, Server-Sent Events, HTTP fetching, retry policy. |
| `callback/` | `ExperimentRunCallback`, `TrackingCallback`, `FeatureUsageCallback`, `FeatureRefreshCallback`. |
| `util/` | `GrowthBookUtils` (hashing, bucketing), `DecryptionUtils`, `GrowthBookJsonUtils`. |

### Invariants

- `evaluators/` stay stateless — state lives in `GBContext`/`EvaluationContext`, never in evaluator fields.
- `model/` stays pure data: Lombok models, Gson-serializable, no I/O, no repository/evaluator imports.
- All Gson access goes through `util/GrowthBookJsonUtils` — no ad-hoc `new Gson()`.
- New refresh strategies extend `FeatureRefreshStrategy` plus repository wiring; they do not fork the
  repository class.
- `GrowthBookClient` and `GBFeaturesRepository` are shared across threads — new mutable state there
  must be safe for concurrent use. Follow the existing volatile/atomic/concurrent-collection patterns.
- User-supplied code (callbacks, listeners, sticky-bucket services, custom `GbCacheManager`) is
  untrusted: catch, log via SLF4J, continue — a throwing callback must never break the refresh cycle.
- Never log or embed API keys/tokens; payload decryption stays in `DecryptionUtils`.

## Module boundaries

| Module | May depend on |
| --- | --- |
| `lib` | third-party libraries only — no internal modules |
| `growthbook-cache-*` | `api project(':lib')` + its cache provider |

A new cache backend is a new module implementing `GbCacheManager`, never a change to `lib`.

### `GbCacheManager` contract

- `loadCache(key)` returns `null` on a miss — never throws for an absent key.
- `getLastUpdatedMillis(key)` returns epoch millis of the last write, or `null` when the key is
  absent or timestamps are unsupported; `null` makes the SDK treat freshness as unknown and refresh
  over the network.
- `saveContent(key, data)` stores payload and updated-at timestamp together.
- `clearCache()` removes everything the adapter wrote, and only that.
- Real cache-access failures throw `growthbook.sdk.java.exception.FeatureCacheException`.
- Implementations are used from concurrent refresh paths and must be thread-safe.

## Language level: Java 8

All modules compile with `sourceCompatibility`/`targetCompatibility` 1.8 and, on a Java 9+
compiler, `options.release = 8` (see the `tasks.withType(JavaCompile)` block in each
`build.gradle`). That means post-Java-8 APIs fail at compile time locally; JDK 8's javac has no
`--release` flag, so the `java_versions.yml` matrix is the backstop.

Do not use Java 9+ syntax or APIs:

- no `var`, records, switch expressions, text blocks
- no `List.of()` / `Map.of()` / `Set.of()` — use `Arrays.asList`, `Collections.unmodifiableList`
- no `Optional.isEmpty()` / `ifPresentOrElse()` — use `!optional.isPresent()`
- no `String.isBlank()` / `strip()` / `repeat()`

## Libraries

Pin exact versions — no dynamic `+` or ranges. **No new third-party dependency in `lib` without
explicit approval**: it is a published SDK and every dependency lands on consumers' classpaths.

| Concern | Library |
| --- | --- |
| JSON | Gson 2.12.1 — only, via `GrowthBookJsonUtils`. Never introduce Jackson. |
| HTTP / SSE | OkHttp 5.4.0 + `okhttp-sse` |
| Models | Lombok 1.18.32 (`@Builder`, `@Data`, `@Getter`/`@Setter`) |
| Logging | SLF4J 2.0.7 facade only — never bundle a backend, never `System.out`/`System.err` in `lib` |
| Math, collections | commons-math3 3.6.1, Guava 33.3.1-jre |
| Tests | JUnit Jupiter 5.8.2, mockito-inline 4.8.0, **WireMock** (`wiremock-jre8:2.35.0`) for HTTP |

In cache adapter modules, a client the consumer chooses between stays `compileOnly`; a provider
needed only to run tests goes in `testRuntimeOnly` (see `ehcache` in `growthbook-cache-jcache`).

## Testing

- Every behavior change in a published module comes with tests covering valid **and** invalid cases:
  `assertThrows` with the exact exception per invalid input (null, zero/negative, empty, boundary),
  plus the absent-data path.
- Deterministic by construction — no `Thread.sleep`, no real time. Inject `Clock`/`Ticker` test
  doubles and advance them explicitly; keep doubles as `private static final` classes at the bottom
  of the test class.
- No real network in unit tests — WireMock.
- Spec fixtures live in `lib/src/test/resources/test-cases.json` (shared GrowthBook conformance cases).

**Style.** The cache adapter tests (`CaffeineGbCacheManagerTest`, `JCacheGbCacheManagerTest`) and
`OptionsValidatorTest` use `@DisplayName("Verify: <observable behavior>")`, behavior-sentence method
names, and `// Given` / `// When` / `// Then` bodies. Most of `lib`'s older tests do not. Prefer this
style for new test classes; when editing an existing class, match that file.

## Comments

Javadoc is the primary form: public classes and non-trivial methods get Javadoc stating the
contract, nullability, and thread-safety expectations — not a narration of the implementation.
Inline `//` comments exist throughout `lib` and are not being hunted down, but do not add ones that
restate what the code does or log what you changed. If code needs explaining, rename or extract
first; keep a rationale in the Javadoc. A constraint the code cannot express — e.g. why a catch
block is intentionally empty — is a fair one-line inline comment.

## Public API

Everything `public` in `lib` is a contract with external consumers. Do not change or remove
`public`/`protected` signatures, move classes between packages, or change serialized field names
without a deprecation cycle (`@Deprecated` plus Javadoc pointing at the replacement). Prefer
additive changes; new behavior defaults to the old behavior unless explicitly opted in. There is no
automated compatibility check — review is the only gate.

## Commits & releases

Conventional Commits, enforced by `.githooks/commit-msg` and `conventional-commits.yml`:
`type(optional-scope): summary`, imperative mood, lowercase, no trailing period.

| Type | Effect (pre-1.0 config) |
| --- | --- |
| `feat:`, `fix:` | patch bump |
| `feat!:` / `BREAKING CHANGE:` footer | minor bump |
| `refactor:`, `docs:`, `test:`, `chore:`, `ci:`, `build:`, `perf:` | no bump |

A breaking change to the published `lib` API **must** carry `!` or a `BREAKING CHANGE:` footer.

release-please owns `CHANGELOG.md`, `versions.txt`, and the `version=` line between the
`x-release-please` markers in `gradle.properties` and `Version.java` — the list comes from
`extra-files` in `release-please-config.json`. Never hand-edit them; if a version looks wrong, fix
the commit history or the config. `.githooks/pre-commit` and the workflow both enforce this, and
both exempt merges that bring those files in from the base branch.

A `PreToolUse` hook in `.claude/settings.json` stops agents from editing them in the first place.
It is deliberately coarser than `pre-commit`: **all of `gradle.properties` is blocked**, not only
the `version=` line, because the hook sees just the target path. Changing another property such as
`group=` is a human edit. The hook requires `jq` and fails closed when it is missing.

The repo squash-merges with `COMMIT_OR_PR_TITLE`, so the PR title is what reaches `main` — and what
release-please reads — unless the PR has exactly one commit.

## Working rules

- Follow existing patterns; when in doubt, find similar code and match it.
- Keep changes scoped — no drive-by reformatting or refactoring of unrelated code.
- Do not silence problems: no `@SuppressWarnings` to hide real issues, no disabled tests, no
  catch-and-ignore.
- Use synthetic placeholder data in tests and examples — never real personal data.
- Secrets come from environment variables (e.g. `GROWTHBOOK_API_KEY`), never from source or fixtures.
