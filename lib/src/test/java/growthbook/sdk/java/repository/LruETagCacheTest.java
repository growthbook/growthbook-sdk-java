package growthbook.sdk.java.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
}