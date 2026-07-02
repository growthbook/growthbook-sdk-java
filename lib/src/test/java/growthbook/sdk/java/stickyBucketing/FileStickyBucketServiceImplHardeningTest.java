package growthbook.sdk.java.stickyBucketing;

import growthbook.sdk.java.model.StickyAssignmentsDocument;
import growthbook.sdk.java.sandbox.FileCachingManagerImpl;
import growthbook.sdk.java.sandbox.GbCacheManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hardening tests for {@link FileStickyBucketServiceImpl}: persistence across instances,
 * path-traversal safety, concurrency, and resilience to corrupted entries.
 */
class FileStickyBucketServiceImplHardeningTest {

    private static StickyAssignmentsDocument doc(String name, String value, String assignKey, String assignVal) {
        Map<String, String> assignments = new HashMap<>();
        assignments.put(assignKey, assignVal);
        return new StickyAssignmentsDocument(name, value, assignments);
    }

    @Test
    void persistsToDiskAndIsReadableByAFreshInstance(@TempDir Path cacheDir) {
        GbCacheManager manager = new FileCachingManagerImpl(cacheDir.toString());
        StickyBucketService writer = new FileStickyBucketServiceImpl(manager);
        writer.saveAssignments(doc("id", "user-1", "exp1__0", "control"));

        // A brand-new instance has an empty in-memory cache, so this read must come from disk.
        StickyBucketService reader = new FileStickyBucketServiceImpl(new FileCachingManagerImpl(cacheDir.toString()));
        StickyAssignmentsDocument loaded = reader.getAssignments("id", "user-1");

        assertNotNull(loaded);
        assertEquals("user-1", loaded.getAttributeValue());
        assertEquals("control", loaded.getAssignments().get("exp1__0"));
    }

    @Test
    void attributeValueWithPathTraversalCannotEscapeCacheDir(@TempDir Path cacheDir) throws IOException {
        GbCacheManager manager = new FileCachingManagerImpl(cacheDir.toString());
        StickyBucketService service = new FileStickyBucketServiceImpl(manager);

        String maliciousValue = "../../../../tmp/pwned";
        service.saveAssignments(doc("id", maliciousValue, "exp__0", "v0"));

        // It must still round-trip correctly...
        StickyAssignmentsDocument loaded =
                new FileStickyBucketServiceImpl(new FileCachingManagerImpl(cacheDir.toString()))
                        .getAssignments("id", maliciousValue);
        assertNotNull(loaded);
        assertEquals("v0", loaded.getAssignments().get("exp__0"));

        // ...and everything written must be a flat, hashed file directly inside the cache dir.
        List<Path> entries;
        try (java.util.stream.Stream<Path> s = Files.list(cacheDir)) {
            entries = s.collect(Collectors.toList());
        }
        assertEquals(1, entries.size(), "expected exactly one persisted entry");
        Path only = entries.get(0);
        assertTrue(Files.isRegularFile(only), "entry must be a regular file, not a directory");
        assertTrue(only.getFileName().toString().matches("[0-9a-f]{64}"),
                "file name must be a SHA-256 hex hash, was: " + only.getFileName());
        // No traversal artifact should have been created next to the cache dir.
        assertTrue(Files.notExists(cacheDir.getParent().resolve("pwned")), "path traversal escaped the cache dir");
    }

    @Test
    void concurrentSavesAndReadsOnDistinctKeysAreConsistent(@TempDir Path cacheDir) throws Exception {
        GbCacheManager manager = new FileCachingManagerImpl(cacheDir.toString());
        StickyBucketService service = new FileStickyBucketServiceImpl(manager);

        int threads = 16;
        int perThread = 40;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicInteger failures = new AtomicInteger(0);

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < perThread; i++) {
                        String value = "u-" + threadId + "-" + i;
                        service.saveAssignments(doc("id", value, "exp__0", value));
                        StickyAssignmentsDocument back = service.getAssignments("id", value);
                        if (back == null || !value.equals(back.getAssignments().get("exp__0"))) {
                            failures.incrementAndGet();
                        }
                    }
                } catch (Exception e) {
                    failures.incrementAndGet();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "tasks did not finish in time");
        assertEquals(0, failures.get(), "concurrent distinct-key access produced inconsistencies");

        // Re-read everything from a fresh instance (disk only) to confirm durability.
        StickyBucketService fresh = new FileStickyBucketServiceImpl(new FileCachingManagerImpl(cacheDir.toString()));
        for (int t = 0; t < threads; t++) {
            for (int i = 0; i < perThread; i++) {
                String value = "u-" + t + "-" + i;
                StickyAssignmentsDocument back = fresh.getAssignments("id", value);
                assertNotNull(back, "missing persisted doc for " + value);
                assertEquals(value, back.getAssignments().get("exp__0"));
            }
        }
    }

    @Test
    void concurrentWritersToSameKeyNeverCorruptTheEntry(@TempDir Path cacheDir) throws Exception {
        GbCacheManager manager = new FileCachingManagerImpl(cacheDir.toString());
        StickyBucketService service = new FileStickyBucketServiceImpl(manager);

        int threads = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        Map<String, Boolean> writtenValues = new ConcurrentHashMap<>();
        AtomicInteger failures = new AtomicInteger(0);

        for (int t = 0; t < threads; t++) {
            final String val = "value-" + t;
            writtenValues.put(val, Boolean.TRUE);
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < 25; i++) {
                        service.saveAssignments(doc("id", "shared", "exp__0", val));
                    }
                } catch (Exception e) {
                    failures.incrementAndGet();
                }
            });
        }
        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS));
        assertEquals(0, failures.get());

        // The final persisted value must be a valid, complete doc equal to one of the writers' values.
        StickyAssignmentsDocument finalDoc =
                new FileStickyBucketServiceImpl(new FileCachingManagerImpl(cacheDir.toString()))
                        .getAssignments("id", "shared");
        assertNotNull(finalDoc);
        assertTrue(writtenValues.containsKey(finalDoc.getAssignments().get("exp__0")),
                "final entry is corrupted or interleaved: " + finalDoc.getAssignments());
    }

    @Test
    void corruptedEntryIsTreatedAsMissingNotThrown() {
        // A cache manager that always returns non-JSON garbage.
        GbCacheManager garbage = new GbCacheManager() {
            @Override
            public void saveContent(String key, String data) { }

            @Override
            public String loadCache(String key) {
                return "}{ not valid json at all";
            }

            @Override
            public void clearCache() { }
        };

        StickyBucketService service = new FileStickyBucketServiceImpl(garbage);
        // Must not throw; a corrupted entry is treated as "no assignment".
        assertNull(service.getAssignments("id", "user-1"));
    }
}
