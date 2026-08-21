---
name: concurrency-review
description: Review Java concurrency code for thread safety, race conditions, deadlocks, and async patterns (CompletableFuture, ExecutorService). Use when user asks "check thread safety", "concurrency review", "async code review", or when reviewing multi-threaded code.
---

# Concurrency Review Skill

Review Java concurrent code for correctness, safety, and modern best practices.

## Why This Matters

> Nearly 60% of multithreaded applications encounter issues due to improper management of shared resources. - ACM Study

Concurrency bugs are:
- **Hard to reproduce** - timing-dependent
- **Hard to test** - may only appear under load
- **Hard to debug** - non-deterministic behavior

This skill helps catch issues **before** they reach production.

## When to Use
- Reviewing code with `synchronized`, `volatile`, `Lock`
- Checking `CompletableFuture`, `ExecutorService`
- Validating thread safety of shared state
- Any code accessed by multiple threads

## Repo Context (growthbook-sdk-java)

This library targets Java 8 (`--release 8`): reviews must flag any use of Virtual Threads,
`@Async`, or Java 9+ concurrency APIs as unusable here. What matters here:

- `GrowthBookClient` / `GBFeaturesRepository` are single shared instances used from many
  application threads — focus on shared mutable state, check-then-act, safe publication,
  `volatile`/atomics/`ConcurrentHashMap`.
- Refresh strategies (SSE, background refresh) run on SDK-owned threads; user callbacks and
  listeners are invoked from them and must be guarded (see `.claude/rules/lib.md`).
- Tests for concurrent behavior must stay deterministic — no sleeps (see the test style rule).

---

## CompletableFuture Patterns

### Error Handling

```java
// ❌ Exception silently swallowed
CompletableFuture.supplyAsync(() -> riskyOperation());
// If riskyOperation throws, nobody knows

// ✅ Always handle exceptions
CompletableFuture.supplyAsync(() -> riskyOperation())
    .exceptionally(ex -> {
        log.error("Operation failed", ex);
        return fallbackValue;
    });

// ✅ Or use handle() for both success and failure
CompletableFuture.supplyAsync(() -> riskyOperation())
    .handle((result, ex) -> {
        if (ex != null) {
            log.error("Failed", ex);
            return fallbackValue;
        }
        return result;
    });
```

### Combining Futures

```java
// ✅ Wait for all
CompletableFuture.allOf(future1, future2, future3)
    .thenRun(() -> log.info("All completed"));

// ✅ Wait for first
CompletableFuture.anyOf(future1, future2, future3)
    .thenAccept(result -> log.info("First result: {}", result));

// ✅ Combine results
future1.thenCombine(future2, (r1, r2) -> merge(r1, r2));
```

### Use Appropriate Executor

```java
// ❌ CPU-bound task in ForkJoinPool.commonPool (default)
CompletableFuture.supplyAsync(() -> cpuIntensiveWork());

// ✅ Custom executor for blocking/I/O operations
ExecutorService ioExecutor = Executors.newFixedThreadPool(20);
CompletableFuture.supplyAsync(() -> blockingIoCall(), ioExecutor);
```

---

## Classic Concurrency Issues

### Race Conditions: Check-Then-Act

```java
// ❌ Race condition
if (!map.containsKey(key)) {
    map.put(key, computeValue());  // Another thread may have added it
}

// ✅ Atomic operation
map.computeIfAbsent(key, k -> computeValue());

// ❌ Race condition with counter
if (count < MAX) {
    count++;  // Read-check-write is not atomic
}

// ✅ Atomic counter
AtomicInteger count = new AtomicInteger();
count.updateAndGet(c -> c < MAX ? c + 1 : c);
```

### Visibility: Missing volatile

```java
// ❌ Other threads may never see the update
private boolean running = true;

public void stop() {
    running = false;  // May not be visible to other threads
}

public void run() {
    while (running) { }  // May loop forever
}

// ✅ Volatile ensures visibility
private volatile boolean running = true;
```

### Non-Atomic long/double

```java
// ❌ 64-bit read/write is non-atomic on 32-bit JVMs
private long counter;

public void increment() {
    counter++;  // Not atomic!
}

// ✅ Use AtomicLong or synchronization
private AtomicLong counter = new AtomicLong();

// ✅ Or volatile (for single-writer scenarios)
private volatile long counter;
```

### Double-Checked Locking

```java
// ❌ Broken without volatile
private static Singleton instance;

public static Singleton getInstance() {
    if (instance == null) {
        synchronized (Singleton.class) {
            if (instance == null) {
                instance = new Singleton();  // May be seen partially constructed
            }
        }
    }
    return instance;
}

// ✅ Correct with volatile
private static volatile Singleton instance;

// ✅ Or use holder class idiom
private static class Holder {
    static final Singleton INSTANCE = new Singleton();
}

public static Singleton getInstance() {
    return Holder.INSTANCE;
}
```

### Deadlocks: Lock Ordering

```java
// ❌ Potential deadlock
// Thread 1: lock(A) -> lock(B)
// Thread 2: lock(B) -> lock(A)

public void transfer(Account from, Account to, int amount) {
    synchronized (from) {
        synchronized (to) {
            // Transfer logic
        }
    }
}

// ✅ Consistent lock ordering
public void transfer(Account from, Account to, int amount) {
    Account first = from.getId() < to.getId() ? from : to;
    Account second = from.getId() < to.getId() ? to : from;

    synchronized (first) {
        synchronized (second) {
            // Transfer logic
        }
    }
}
```

---

## Thread-Safe Collections

### Choose the Right Collection

| Use Case | Wrong | Right |
|----------|-------|-------|
| Concurrent reads/writes | `HashMap` | `ConcurrentHashMap` |
| Frequent iteration | `ConcurrentHashMap` | `CopyOnWriteArrayList` |
| Producer-consumer | `ArrayList` | `BlockingQueue` |
| Sorted concurrent | `TreeMap` | `ConcurrentSkipListMap` |

### ConcurrentHashMap Pitfalls

```java
// ❌ Non-atomic compound operation
if (!map.containsKey(key)) {
    map.put(key, value);
}

// ✅ Atomic
map.putIfAbsent(key, value);
map.computeIfAbsent(key, k -> createValue());

// ❌ Nested compute can deadlock
map.compute(key1, (k, v) -> {
    return map.compute(key2, ...);  // Deadlock risk!
});
```

---

## Concurrency Review Checklist

### 🔴 High Severity (Likely Bugs)
- [ ] No check-then-act on shared state without synchronization
- [ ] No `synchronized` calling external/unknown code (deadlock risk)
- [ ] `volatile` present for double-checked locking
- [ ] Non-volatile fields not read in loops waiting for updates
- [ ] `ConcurrentHashMap.compute()` doesn't call other map operations

### 🟡 Medium Severity (Potential Issues)
- [ ] Thread pools properly sized and named
- [ ] CompletableFuture exceptions handled (exceptionally/handle)
- [ ] `ExecutorService` properly shut down
- [ ] `Lock.unlock()` in finally block
- [ ] Thread-safe collections used for shared data

### 📝 Documentation
- [ ] Thread safety documented on shared classes
- [ ] Locking order documented for nested locks
- [ ] Each `volatile` usage justified

---

## Analysis Commands

```bash
# Find synchronized blocks
grep -rn "synchronized" --include="*.java"

# Find volatile fields
grep -rn "volatile" --include="*.java"

# Find thread pool creation
grep -rn "Executors\.\|ThreadPoolExecutor\|ExecutorService" --include="*.java"

# Find CompletableFuture without error handling
grep -rn "CompletableFuture\." --include="*.java" | grep -v "exceptionally\|handle\|whenComplete"

# Find ThreadLocal
grep -rn "ThreadLocal" --include="*.java"
```
