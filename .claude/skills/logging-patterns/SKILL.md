---
name: logging-patterns
description: Java logging best practices for library code with SLF4J - parameterized logging, log levels, exception logging at SDK boundaries, and what not to log. Use when user asks about logging, debugging application flow, or analyzing logs.
---

# Logging Patterns Skill

Effective logging for a published Java library through the SLF4J facade.

## When to Use
- User says "add logging" / "improve logs" / "debug this"
- Analyzing application flow from logs
- AI/Claude Code needs to analyze application behavior

## Repo Context (growthbook-sdk-java)

This repo publishes a **library**, which changes the rules:

- `lib` logs through the SLF4J facade only and never bundles or configures a backend — backend
  configuration (Logback, JSON encoders, appenders) belongs to *consumer apps and playgrounds*,
  never to the SDK itself.
- Never log API keys, tokens, or decrypted payloads; user attributes are potentially personal data —
  log feature keys and counts, not attribute values.
- Library log levels: `error` for unusable states, `warn` for degraded-but-working (e.g. failed
  refresh with retained stale features), `debug` for flow tracing. Be conservative with `info` —
  libraries should be quiet by default.
- The `growthbook-codegen` CLI intentionally uses stdout/stderr, not a logger.

---

## AI-Friendly Logging

> **Key insight:** Consumers render SLF4J events however they like (plain text, JSON). A library's
> job is to emit events that stay useful after any rendering: stable wording plus parameterized fields.

What a library author controls:

- **Stable, searchable event wording** — keep the message a constant phrase and put all variability
  in parameters: `log.warn("Feature refresh failed, keeping stale features. attempt={}", attempt)`.
  A fixed message string is greppable across log files and SDK versions; never build it with
  concatenation.
- **One event, one line** — no multi-line banners; stack traces are the only multi-line output.
- **Machine-extractable context** — pass identifiers (feature key, attempt count, duration) as
  SLF4J parameters, not baked into prose, so structured backends can capture them as fields.

Backend, appender, and JSON-encoder configuration (Logback, Logstash encoder, `logback.xml`) is the
consumer application's responsibility — never add it to the SDK.

---

## SLF4J Basics

### Logger Declaration

```java
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FeatureRepository {
    private static final Logger log = LoggerFactory.getLogger(FeatureRepository.class);
}

// Or with Lombok
@Slf4j
public class FeatureRepository {
    // use `log` directly
}
```

### Parameterized Logging

```java
// ✅ GOOD: Evaluated only if level enabled
log.debug("Processing order {} for user {}", orderId, userId);

// ❌ BAD: Always concatenates
log.debug("Processing order " + orderId + " for user " + userId);

// ✅ For expensive operations
if (log.isDebugEnabled()) {
    log.debug("Full order details: {}", order.toJson());
}
```

---

## Log Levels

| Level | When | Example |
|-------|------|---------|
| **ERROR** | Unusable states | Initial feature fetch failed, cache unusable |
| **WARN** | Degraded but working | Refresh failed with stale features retained, deprecated API used |
| **INFO** | Rare lifecycle events | Repository initialized (libraries stay quiet by default) |
| **DEBUG** | Technical details | Method params, evaluation flow |
| **TRACE** | Very detailed | Loop iterations (rarely used) |

```java
log.error("Failed to initialize features repository", exception);
log.warn("Feature refresh failed, retaining stale features. attempt={}", attempt);
log.info("Features repository initialized. refreshStrategy={}", refreshStrategy);
log.debug("Evaluating feature. key={}", featureKey);
```

---

## What to Log

### Lifecycle Events (INFO — sparingly)

```java
// Rare, one-time events with key configuration facts (never secrets)
log.info("Features repository initialized. refreshStrategy={}", refreshStrategy);
log.info("Features refreshed. count={}", features.size());
```

### External Calls (with timing)

```java
long start = System.currentTimeMillis();
try {
    Response response = fetchFeatures(request);
    log.debug("Features fetch succeeded. status={}, durationMs={}",
        response.code(), System.currentTimeMillis() - start);
    return response;
} catch (IOException e) {
    log.error("Features fetch failed. durationMs={}",
        System.currentTimeMillis() - start, e);
    throw e;
}
```

### Flow Steps (DEBUG, for AI tracing)

```java
public FeatureResult evaluate(String featureKey) {
    log.debug("Evaluation started. key={}", featureKey);

    FeatureResult result = doEvaluate(featureKey);
    log.debug("Evaluation completed. key={}, source={}", featureKey, result.getSource());

    return result;
}
```

---

## What NOT to Log

```java
// ❌ NEVER log sensitive data
log.info("Fetching features. apiKey={}", apiKey);          // API keys / client keys
log.debug("Decrypted payload: {}", decryptedFeatures);     // Decrypted feature payloads
log.debug("User attributes: {}", userAttributes);          // Potentially personal data (PII)
log.info("Request. token={}", token);                      // Tokens

// ✅ Safe alternatives
log.info("Fetching features. clientKeyPresent={}", clientKey != null);
log.debug("Features decrypted. count={}", features.size());
log.debug("Evaluating with {} attributes", userAttributes.size());
```

---

## Exception Logging

### Log Once at Boundary

```java
// ❌ BAD: Logs same exception multiple times
void methodA() {
    try { methodB(); }
    catch (Exception e) { log.error("Error", e); throw e; }  // Log #1
}
void methodB() {
    try { methodC(); }
    catch (Exception e) { log.error("Error", e); throw e; }  // Log #2
}

// ✅ GOOD: Log once at the SDK boundary — the outermost place that decides
// whether to degrade gracefully or rethrow
public void refreshFeatures() {
    try {
        fetchAndStoreFeatures();
    } catch (IOException e) {
        log.warn("Feature refresh failed, retaining stale features", e);  // Full stack trace, logged once
    }
}
```

### Include Context

```java
// ❌ Useless
log.error("Error occurred", e);

// ✅ Useful for debugging
log.error("Feature refresh failed. refreshStrategy={}, attempt={}",
    refreshStrategy, attempt, e);
```

---

## Quick Reference

```java
// === Setup ===
private static final Logger log = LoggerFactory.getLogger(MyClass.class);

// === Parameterized logging ===
log.info("Event happened. key={}, count={}", key, count);
log.error("Operation failed. context={}", ctx, exception);

// === Levels ===
log.error()  // Unusable states
log.warn()   // Degraded but working
log.info()   // Rare lifecycle events (libraries stay quiet)
log.debug()  // Flow tracing
```

---

## Related Skills

- `security-audit` - What must never appear in logs (secrets, API keys, PII)
- `java-code-review` - General code review, including logging checks
