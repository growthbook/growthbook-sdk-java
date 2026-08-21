# Concurrency Review Skill

> Review Java concurrent code for thread safety, race conditions, and deadlocks

## What It Does

Reviews multi-threaded Java code for:
- Race conditions and visibility issues
- Deadlock potential
- CompletableFuture error handling
- Thread pool configuration

## Why It Matters

> Nearly 60% of multithreaded applications encounter issues due to improper management of shared resources.

Concurrency bugs are hard to reproduce, hard to test, and hard to debug. Catching them in code review is far better than finding them in production.

## When to Use

- "Review this for thread safety"
- "Check concurrency issues"
- "Is this async code correct?"
- Reviewing code with `synchronized`, `volatile`
- Checking `CompletableFuture` or `ExecutorService` usage

## Key Topics Covered

### Classic Issues
| Issue | Example |
|-------|---------|
| Race condition | Check-then-act without sync |
| Visibility | Missing volatile |
| Deadlock | Inconsistent lock ordering |

## Example Usage

```
You: Review this service for thread safety

Claude: [Checks shared mutable state]
        [Validates synchronization]
        [Checks CompletableFuture error handling]
```

## Severity Levels

| Level | Meaning |
|-------|---------|
| 🔴 High | Likely bug - race condition, deadlock risk |
| 🟡 Medium | Potential issue - measure/verify |

## Related Skills

- `performance-smell-detection` - Performance issues (not thread safety)
- `java-code-review` - General code review (includes basic concurrency)

## References

- [Java Concurrency Code Review Checklist](https://github.com/code-review-checklists/java-concurrency)
- [Baeldung - Common Concurrency Pitfalls](https://www.baeldung.com/java-common-concurrency-pitfalls)
- Book: "Java Concurrency in Practice" by Brian Goetz
