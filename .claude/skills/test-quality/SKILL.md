---
name: test-quality
description: Write high-quality JUnit 5 tests in this repo's established style (DisplayName "Verify:", Given/When/Then, full valid+invalid coverage). Use when user says "add tests", "write tests", "improve test coverage", or when reviewing/creating test classes.
---

# Test Quality Skill (JUnit 5, repo style)

Write tests the way this repository already writes them. The authoritative style definition lives in
`.claude/rules/development-guidelines.md` (section *Test style*); the reference implementations are
the `growthbook-cache-*` test classes (e.g. `CaffeineGbCacheManagerTest`, `JedisGbCacheManagerTest`).

## When to Use
- Writing new test classes
- Reviewing/improving existing tests
- User asks to "add tests" / "improve test coverage"

## Stack — What This Repo Actually Uses

- **JUnit 5** (`junit-jupiter`) with **built-in assertions** (`assertEquals`, `assertTrue`,
  `assertNull`, `assertThrows`). **No AssertJ** — do not introduce it.
- **Mockito** (`mockito-inline`) in `lib` only.
- **OkHttp MockWebServer** for anything HTTP — never a real endpoint.
- Golden files in `src/test/resources/expected/` for generated-output comparisons (codegen).
- JaCoCo via `./gradlew jacocoTestReport` (report: `lib/build/jacocoHtml/index.html`).
- Test code must compile with `--release 8` — no Java 9+ APIs.

## The House Style

```java
@Test
@DisplayName("Verify: a missing key is a miss (null content and null timestamp)")
void returnsNullForMissingEntry() {
    // Given
    CaffeineGbCacheManager cacheManager = CaffeineGbCacheManager.create();

    // Then
    assertNull(cacheManager.loadCache("features"));
    assertNull(cacheManager.getLastUpdatedMillis("features"));
}
```

Checklist for every test:
- `@DisplayName("Verify: <observable behavior in plain words>")` on each `@Test`.
- Method name is a behavior sentence (`returnsNullForMissingEntry`,
  `expiresEntriesWhenExpireAfterWriteIsConfigured`) — never the name of the method under test.
- Body structured with `// Given` / `// When` / `// Then` (`// When & Then` for
  `assertThrows`-only tests).
- One observable behavior per test.
- Test class is package-private, methods package-private, no `public` noise.

## Coverage: Valid AND Invalid Cases — Non-Negotiable

For every rule or validation, cover:

1. **Happy path** — the documented behavior.
2. **Every rejection** — one `assertThrows` per invalid input with the **exact** exception type:

```java
@Test
@DisplayName("Verify: invalid cache options fail fast")
void validatesOptions() {
    // Given
    CaffeineCacheOptions.Builder options = CaffeineCacheOptions.builder();

    // When & Then
    assertThrows(NullPointerException.class, () -> CaffeineGbCacheManager.create(null));
    assertThrows(IllegalArgumentException.class, () -> options.maximumSize(0));
    assertThrows(IllegalArgumentException.class, () -> options.expireAfterWrite(Duration.ZERO));
}
```

3. **Absent data** — missing entries, empty collections/responses, `null` members in JSON.
4. **Boundaries** — zero, negative, exactly-at-limit values.
5. **Failure modes of collaborators** — HTTP 4xx/5xx via MockWebServer, unreachable server,
   malformed payloads.

When reviewing tests, report which of these five buckets are missing.

## Determinism

- No `Thread.sleep`, no wall-clock time: inject `Clock`/`Ticker`-style test doubles and advance
  them explicitly.
- Test doubles are `private static final` classes at the bottom of the test class
  (see `MutableClock`/`MutableTicker` in `CaffeineGbCacheManagerTest`).
- MockWebServer responses are enqueued explicitly; assert `server.getRequestCount()` /
  `takeRequest()` when request shape matters.

## Assertions — Getting the Most from Plain JUnit

```java
// Message argument explains the intent on failure
assertEquals(3, server.getRequestCount(), "expected the request to be retried");
assertTrue(result.err.contains("Usage:"), "usage must be printed on bad arguments");

// Exception content is part of the contract
CodegenException e = assertThrows(CodegenException.class, () -> source.fetchFeatures());
assertTrue(e.getMessage().contains("401"));
assertFalse(e.getMessage().contains(API_KEY), "token must never appear in error messages");

// Whole-value equality against a golden file beats field-by-field picking
assertEquals(TestResources.read("/expected/Features.java"), written);
```

## Anti-patterns

- Generic names (`test1`, `testFeature`) or a missing `@DisplayName`.
- Multi-scenario mega-tests mixing unrelated assertions.
- Asserting private/internal state instead of observable behavior.
- `try { ... fail(); } catch` instead of `assertThrows`.
- Sleeping to "wait" for async/expiry behavior.
- Real network, real Redis in unit tests (Redis integration tests use an external instance behind
  `assumeTrue` — Testcontainers does not work in this environment).

## Commands

```bash
./gradlew :growthbook-codegen:test
./gradlew :lib:test --tests "growthbook.sdk.java.GBFeaturesRepositoryTest"
./gradlew jacocoTestReport   # coverage → lib/build/jacocoHtml/index.html
```
