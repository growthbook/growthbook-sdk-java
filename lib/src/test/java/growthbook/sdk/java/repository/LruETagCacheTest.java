package growthbook.sdk.java.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class LruETagCacheTest {
    private LruETagCache cache;
    private final int MAX_SIZE = 3;

    @BeforeEach
    public void setup() {
        cache = new LruETagCache(MAX_SIZE);
    }

    @Test
    void testBasicPutAndGet() {
        cache.put("url1", "etag1");
        cache.put("url2", "etag2");

        assertEquals("etag1", cache.get("url1"));
        assertEquals("etag2", cache.get("url2"));
        assertNull(cache.get("url3"));
        assertEquals(2, cache.size());
    }

    @Test
    void testLRUEviction() {
        cache.put("url1", "etag1");
        cache.put("url2", "etag2");
        cache.put("url3", "etag3");

        assertEquals(MAX_SIZE, cache.size());

        cache.put("url4", "etag4");

        assertEquals(MAX_SIZE, cache.size(), "Cache size should not exceed MAX_SIZE");
        assertNull(cache.get("url1"), "url1 should be evicted (LRU)");
        assertNotNull(cache.get("url2"));
        assertNotNull(cache.get("url3"));
        assertNotNull(cache.get("url4"));
    }

    @Test
    void testAccessUpdatesLRUPosition() {
        cache.put("url1", "etag1");
        cache.put("url2", "etag2");
        cache.put("url3", "etag3");

        cache.get("url1");

        cache.put("url4", "etag4");

        assertEquals(MAX_SIZE, cache.size());
        assertEquals("etag1", cache.get("url1"), "url1 should still be present (MRU)");
        assertNull(cache.get("url2"), "url2 should be evicted (now LRU)");
        assertEquals("etag3", cache.get("url3"));
        assertEquals("etag4", cache.get("url4"));
    }

    @Test
    void testUpdateExistingEntry() {
        cache.put("url1", "etag1");
        cache.put("url2", "etag2");
        assertEquals(2, cache.size());

        cache.put("url1", "etag1-updated");

        assertEquals(2, cache.size());
        assertEquals("etag1-updated", cache.get("url1"));
    }

    @Test
    void testPutNullRemovesEntry() {
        cache.put("url1", "etag1");
        assertNotNull(cache.get("url1"));

        cache.put("url1", null);

        assertNull(cache.get("url1"));
        assertEquals(0, cache.size());
    }

    @Test
    void testRemoveOperation() {
        cache.put("url1", "etag1");
        cache.put("url2", "etag2");

        cache.remove("url1");

        assertNull(cache.get("url1"));
        assertEquals(1, cache.size());
    }

    @Test
    void testClearOperation() {
        cache.put("url1", "etag1");
        cache.put("url2", "etag2");

        cache.clear();

        assertEquals(0, cache.size());
        assertNull(cache.get("url1"));
    }

    @Test
    void testThreadSafetyConcurrentAccess() throws InterruptedException {
        int numThreads = 10;
        int operationsPerThread = 100;
        int largeMaxSize = 50;

        LruETagCache concurrentCache = new LruETagCache(largeMaxSize);
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(numThreads * operationsPerThread);

        for (int i = 0; i < numThreads; i++) {
            final int threadId = i;
            executor.submit(() -> {
                for (int j = 0; j < operationsPerThread; j++) {
                    String key = "url" + (j % largeMaxSize);
                    String value = "etag-" + threadId + "-" + j;

                    try {
                        concurrentCache.put(key, value);
                        concurrentCache.get(key);

                        if (j % 10 == 0) {
                            concurrentCache.remove("url" + (j % largeMaxSize));
                        }
                    } catch (Exception e) {
                        fail("Exception thrown during concurrent access: " + e.getMessage());
                    } finally {
                        latch.countDown();
                    }
                }
            });
        }

        assertTrue(latch.await(5, TimeUnit.SECONDS), "Concurrent operations did not finish in time.");
        executor.shutdownNow();

        assertTrue(concurrentCache.size() <= largeMaxSize, "Cache size integrity compromised.");
    }

    @Test
    void testUpdateExistingEntryRefreshesLRUPosition() {
        cache.put("url1", "etag1");
        cache.put("url2", "etag2");
        cache.put("url3", "etag3");

        cache.put("url1", "etag1-updated");
        cache.put("url4", "etag4");

        assertEquals(MAX_SIZE, cache.size());
        assertEquals("etag1-updated", cache.get("url1"));
        assertNull(cache.get("url2"), "url2 should be evicted (now LRU)");
    }

    @Test
    void testEvictsInLeastRecentlyUsedOrderBeyondMaxSize() {
        for (int i = 1; i <= 6; i++) {
            cache.put("url" + i, "etag" + i);
        }

        assertEquals(MAX_SIZE, cache.size());
        assertNull(cache.get("url1"));
        assertNull(cache.get("url2"));
        assertNull(cache.get("url3"));
        assertEquals("etag4", cache.get("url4"));
        assertEquals("etag5", cache.get("url5"));
        assertEquals("etag6", cache.get("url6"));
    }

    @Test
    void testPutNullForUnknownUrlIsNoOp() {
        cache.put("url1", "etag1");

        cache.put("url2", null);

        assertEquals(1, cache.size());
        assertEquals("etag1", cache.get("url1"));
        assertNull(cache.get("url2"));
    }

    @Test
    void testPutNullFreesSlotWithoutEvictingOthers() {
        cache.put("url1", "etag1");
        cache.put("url2", "etag2");
        cache.put("url3", "etag3");

        cache.put("url2", null);
        cache.put("url4", "etag4");

        assertEquals(MAX_SIZE, cache.size());
        assertEquals("etag1", cache.get("url1"));
        assertEquals("etag3", cache.get("url3"));
        assertEquals("etag4", cache.get("url4"));
    }

    @Test
    void testGetOfUnknownUrlDoesNotChangeSize() {
        cache.put("url1", "etag1");

        assertNull(cache.get("missing"));

        assertEquals(1, cache.size());
    }

    @Test
    void testConcurrentPutAndGetReturnOnlyWrittenValues() throws Exception {
        int numThreads = 8;
        int operationsPerThread = 500;
        int maxSize = 16;
        LruETagCache concurrentCache = new LruETagCache(maxSize);
        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();

        try {
            for (int i = 0; i < numThreads; i++) {
                final int threadId = i;
                results.add(executor.submit(() -> {
                    start.await();
                    int mismatches = 0;
                    for (int j = 0; j < operationsPerThread; j++) {
                        String key = "url" + ((threadId * operationsPerThread + j) % (maxSize * 2));
                        concurrentCache.put(key, "etag-for-" + key);
                        String read = concurrentCache.get(key);
                        if (read != null && !read.equals("etag-for-" + key)) {
                            mismatches++;
                        }
                    }
                    return mismatches;
                }));
            }

            start.countDown();

            for (Future<Integer> result : results) {
                assertEquals(0, result.get(10, TimeUnit.SECONDS));
            }
            assertTrue(concurrentCache.size() <= maxSize);
        } finally {
            executor.shutdownNow();
        }
    }
}