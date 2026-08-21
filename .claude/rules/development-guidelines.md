---
description: "General development guidelines, code quality standards, and common patterns for the GrowthBook Java SDK"
alwaysApply: true
---

# Development Guidelines

## General Rules

- Follow existing patterns in the codebase. When in doubt, search for similar existing code and follow that pattern.
- Every behavior change in a published module comes with tests covering both valid and invalid cases (see Test style below).
- Do not silence problems: no `@SuppressWarnings` to hide real issues, no disabling failing tests, no catching-and-ignoring exceptions.
- Keep changes scoped: do not reformat or "drive-by refactor" code unrelated to the task.

## Language Level: Java 8

All published modules compile with `sourceCompatibility = JavaVersion.VERSION_1_8`. Do **not** use Java 9+ syntax or APIs:

- no `var`, records, switch expressions, text blocks
- no `List.of()` / `Map.of()` / `Set.of()` — use `Arrays.asList`, `Collections.unmodifiableList`, etc.
- no `Optional.isEmpty()` / `Optional.ifPresentOrElse()` — use `!optional.isPresent()`
- no `String.isBlank()` / `strip()` / `repeat()`

Build with JDK 17 (matches CI). Newer JDKs (e.g. 25) break the Lombok version used here.

## Libraries

- **JSON**: Gson only (via `GrowthBookJsonUtils` wrappers in `lib`). Never introduce Jackson.
- **HTTP/SSE**: OkHttp + okhttp-sse.
- **Models**: Lombok (`@Builder`, `@Data`, `@Getter`/`@Setter`) is the established pattern in `lib`'s `model/` package.
- **Logging**: SLF4J facade only; never bundle a backend, never use `System.out`/`System.err` in `lib` (the codegen CLI prints to stdout/stderr by design).

## Comments

- The codebase style is Javadoc-only: classes and non-trivial methods get Javadoc stating the contract,
  invariants, and non-obvious "why" — never a narration of the implementation.
- Do **not** add inline `//` comments that restate what the code does, describe what was changed, or
  explain a decision already covered by Javadoc. If code needs explaining, rename or extract until it
  doesn't; if a rationale is worth keeping, put it in the class/method Javadoc.
- The only acceptable inline comment is a constraint the code cannot express — e.g. why a catch block
  is intentionally empty. One short line, English only.

## Testing

- JUnit 5 (`junit-jupiter`) everywhere; Mockito (`mockito-inline`) in `lib`; OkHttp `MockWebServer` for HTTP tests.
- Run scoped: `./gradlew :lib:test`, `./gradlew :growthbook-codegen:test`. Single test: `./gradlew :lib:test --tests "growthbook.sdk.java.GBFeaturesRepositoryTest"`.
- Testcontainers does not work in this environment — integration tests use an external Redis and `assumeTrue` guards.

### Test style — follow the cache-adapter tests

The reference for how tests must look is the `growthbook-cache-*` test classes
(e.g. `CaffeineGbCacheManagerTest`, `JedisGbCacheManagerTest`). Concretely:

- Every `@Test` carries `@DisplayName("Verify: <observable behavior in plain words>")` —
  e.g. `"Verify: a missing key is a miss (null content and null timestamp)"`.
- Method names are behavior sentences (`returnsNullForMissingEntry`, `expiresEntriesWhenExpireAfterWriteIsConfigured`),
  never the name of the method under test.
- Body is structured with `// Given` / `// When` / `// Then` comments (`// When & Then` for `assertThrows`-only tests).
- One observable behavior per test — no multi-scenario mega-tests.
- **Cover both valid and invalid cases.** For every rule or validation, test the happy path *and*
  every rejection: `assertThrows` with the exact exception type per invalid input (nulls, zero/negative,
  empty, boundary values), plus the "absent data" path (missing entries, empty responses).
- Deterministic by construction: no `Thread.sleep`, no real time — inject `Clock`/`Ticker`-style
  test doubles and advance them explicitly. Test doubles live as `private static final` classes
  at the bottom of the test class.
- No real network or external endpoints in unit tests — `MockWebServer` for HTTP.

## Dependencies

- **No new third-party dependencies in `lib` without explicit approval** — it is a published SDK
  and every dependency lands on consumers' classpaths (GrowthBook SDKs are deliberately lean).
  Prefer the JDK standard library; if something seems to need a new library, raise it first.
- Cache provider clients in adapter modules stay `compileOnly` — the consumer picks the client.
- Pin exact versions (no dynamic `+`/ranges). Version bumps go in dedicated `chore`/`build` commits,
  not mixed into feature work.

## Compatibility & Releases

- `lib` is a published library: do not change or remove public API signatures without a deprecation path.
- Versioning is managed by release-please (`gradle.properties` markers, `CHANGELOG.md`, `release-please-config.json`) — do not hand-edit the version.

## Security

- Never log or include API keys/tokens in exception messages, generated code, or test fixtures. Secrets come from environment variables (e.g. `GROWTHBOOK_API_KEY`).
- Use synthetic placeholder data in tests and examples — no real personal data.
