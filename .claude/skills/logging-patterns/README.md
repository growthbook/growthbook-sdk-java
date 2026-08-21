# Logging Patterns

**Load**: `view .claude/skills/logging-patterns/SKILL.md`

---

## Description

Java logging best practices for library code with SLF4J: parameterized logging, log levels, exception logging at SDK boundaries, and what never to log. The SDK logs through the SLF4J facade only and never bundles or configures a backend.

---

## Use Cases

- "Add logging to this class"
- "Debug this flow" (AI reads logs)
- "Why is this request failing?" (analyze logs)
- "What log level should this be?"

---

## Key Insight: Stable Events for AI

The library cannot control how consumers render logs (text or JSON). What it controls:

| Lever | Practice |
|-------|----------|
| Wording | Constant message phrase, greppable across versions |
| Context | Identifiers as SLF4J parameters, not concatenated prose |
| Shape | One event per line; stack traces are the only multi-line output |

```java
log.warn("Feature refresh failed, retaining stale features. attempt={}", attempt);
```

---

## Topics Covered

| Topic | Description |
|-------|-------------|
| **AI-Friendly Logging** | Stable, searchable event wording with parameterized fields |
| **SLF4J Basics** | Logger declaration, parameterized logging |
| **Log Levels** | When to use ERROR, WARN, INFO, DEBUG in a library |
| **What to Log** | Lifecycle events, external call timing, flow steps |
| **What NOT to Log** | API keys, tokens, decrypted payloads, PII |
| **Exception Logging** | Log once at the SDK boundary, with context |

---

## Related Skills

- `security-audit` - What must never appear in logs
- `java-code-review` - General code review

---

## Resources

- [10 Best Practices for Logging in Java (Better Stack)](https://betterstack.com/community/guides/logging/how-to-start-logging-with-java/)
- [SLF4J Manual](https://www.slf4j.org/manual.html)
