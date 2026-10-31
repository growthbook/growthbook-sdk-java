package growthbook.sdk.java.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LruETagCacheTest {

    private static final int MAX_SIZE = 3;

    private LruETagCache cache;

    @BeforeEach
    void setUp() {
        cache = new LruETagCache(MAX_SIZE);
    }

    @Test
    @DisplayName("Returns the stored ETag per URL and null for an unknown URL")
    void returnsStoredETagPerUrl() {
        // Given
        cache.put("url1", "etag1");
        cache.put("url2", "etag2");

        // When / Then
        assertEquals("etag1", cache.get("url1"));
        assertEquals("etag2", cache.get("url2"));
        assertNull(cache.get("url3"));
        assertEquals(2, cache.size());
    }

    @Test
    @DisplayName("Reading an unknown URL does not change the size")
    void readingUnknownUrlDoesNotChangeSize() {
        // Given
        cache.put("url1", "etag1");

        // When
        String missing = cache.get("missing");

        // Then
        assertNull(missing);
        assertEquals(1, cache.size());
    }

    @Test
    @DisplayName("Evicts the least recently inserted URL when the max size is exceeded")
    void evictsLeastRecentlyInsertedUrlBeyondMaxSize() {
        // Given
        cache.put("url1", "etag1");
        cache.put("url2", "etag2");
        cache.put("url3", "etag3");

        // When
        cache.put("url4", "etag4");

        // Then
        assertEquals(MAX_SIZE, cache.size());
        assertNull(cache.get("url1"));
        assertEquals("etag2", cache.get("url2"));
        assertEquals("etag3", cache.get("url3"));
        assertEquals("etag4", cache.get("url4"));
    }

    @Test
    @DisplayName("Evicts in least recently used order when many URLs exceed the max size")
    void evictsInLeastRecentlyUsedOrderWhenManyUrlsExceedMaxSize() {
        // Given
        for (int i = 1; i <= 6; i++) {
            cache.put("url" + i, "etag" + i);
        }

        // When / Then
        assertEquals(MAX_SIZE, cache.size());
        assertNull(cache.get("url1"));
        assertNull(cache.get("url2"));
        assertNull(cache.get("url3"));
        assertEquals("etag4", cache.get("url4"));
        assertEquals("etag5", cache.get("url5"));
        assertEquals("etag6", cache.get("url6"));
    }

    @Test
    @DisplayName("Reading a URL makes it most recently used so it survives the next eviction")
    void readingUrlMakesItMostRecentlyUsed() {
        // Given
        cache.put("url1", "etag1");
        cache.put("url2", "etag2");
        cache.put("url3", "etag3");
        cache.get("url1");

        // When
        cache.put("url4", "etag4");

        // Then
        assertEquals(MAX_SIZE, cache.size());
        assertEquals("etag1", cache.get("url1"));
        assertNull(cache.get("url2"));
        assertEquals("etag3", cache.get("url3"));
        assertEquals("etag4", cache.get("url4"));
    }

    @Test
    @DisplayName("Overwriting a URL replaces its ETag without growing the cache")
    void overwritingUrlReplacesETagWithoutGrowing() {
        // Given
        cache.put("url1", "etag1");
        cache.put("url2", "etag2");

        // When
        cache.put("url1", "etag1-updated");

        // Then
        assertEquals(2, cache.size());
        assertEquals("etag1-updated", cache.get("url1"));
    }

    @Test
    @DisplayName("Overwriting a URL makes it most recently used so it survives the next eviction")
    void overwritingUrlMakesItMostRecentlyUsed() {
        // Given
        cache.put("url1", "etag1");
        cache.put("url2", "etag2");
        cache.put("url3", "etag3");
        cache.put("url1", "etag1-updated");

        // When
        cache.put("url4", "etag4");

        // Then
        assertEquals(MAX_SIZE, cache.size());
        assertEquals("etag1-updated", cache.get("url1"));
        assertNull(cache.get("url2"));
    }

    @Test
    @DisplayName("Putting a null ETag removes the URL")
    void puttingNullETagRemovesUrl() {
        // Given
        cache.put("url1", "etag1");

        // When
        cache.put("url1", null);

        // Then
        assertNull(cache.get("url1"));
        assertEquals(0, cache.size());
    }

    @Test
    @DisplayName("Putting a null ETag for an unknown URL leaves the cache unchanged")
    void puttingNullETagForUnknownUrlLeavesCacheUnchanged() {
        // Given
        cache.put("url1", "etag1");

        // When
        cache.put("url2", null);

        // Then
        assertEquals(1, cache.size());
        assertEquals("etag1", cache.get("url1"));
        assertNull(cache.get("url2"));
    }

    @Test
    @DisplayName("Putting a null ETag frees a slot without evicting other URLs")
    void puttingNullETagFreesSlotWithoutEvictingOthers() {
        // Given
        cache.put("url1", "etag1");
        cache.put("url2", "etag2");
        cache.put("url3", "etag3");
        cache.put("url2", null);

        // When
        cache.put("url4", "etag4");

        // Then
        assertEquals(MAX_SIZE, cache.size());
        assertEquals("etag1", cache.get("url1"));
        assertEquals("etag3", cache.get("url3"));
        assertEquals("etag4", cache.get("url4"));
    }

    @Test
    @DisplayName("Removing a URL drops only that URL")
    void removingUrlDropsOnlyThatUrl() {
        // Given
        cache.put("url1", "etag1");
        cache.put("url2", "etag2");

        // When
        cache.remove("url1");

        // Then
        assertNull(cache.get("url1"));
        assertEquals("etag2", cache.get("url2"));
        assertEquals(1, cache.size());
    }

    @Test
    @DisplayName("Clearing drops every URL")
    void clearingDropsEveryUrl() {
        // Given
        cache.put("url1", "etag1");
        cache.put("url2", "etag2");

        // When
        cache.clear();

        // Then
        assertEquals(0, cache.size());
        assertNull(cache.get("url1"));
        assertNull(cache.get("url2"));
    }

    @Test
    @DisplayName("Concurrent puts, reads and removes never exceed the max size or throw")
    void concurrentPutsReadsAndRemovesStayWithinMaxSize() throws Exception {
        // Given
        int threads = 10;
        int operationsPerThread = 100;
        int maxSize = 50;
        LruETagCache concurrentCache = new LruETagCache(maxSize);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> results = new ArrayList<>();

        try {
            // When
            for (int i = 0; i < threads; i++) {
                final int threadId = i;
                results.add(executor.submit(() -> {
                    start.await();
                    for (int j = 0; j < operationsPerThread; j++) {
                        String key = "url" + (j % maxSize);
                        concurrentCache.put(key, "etag-" + threadId + "-" + j);
                        concurrentCache.get(key);
                        if (j % 10 == 0) {
                            concurrentCache.remove(key);
                        }
                    }
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> result : results) {
                result.get(10, TimeUnit.SECONDS);
            }

            // Then
            assertTrue(concurrentCache.size() <= maxSize);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("Concurrent readers only ever see an ETag that was written for that URL")
    void concurrentReadersSeeOnlyWrittenETags() throws Exception {
        // Given
        int threads = 8;
        int operationsPerThread = 500;
        int maxSize = 16;
        LruETagCache concurrentCache = new LruETagCache(maxSize);
        ExecutorService executor = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();

        try {
            // When
            for (int i = 0; i < threads; i++) {
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

            // Then
            for (Future<Integer> result : results) {
                assertEquals(0, result.get(10, TimeUnit.SECONDS));
            }
            assertTrue(concurrentCache.size() <= maxSize);
        } finally {
            executor.shutdownNow();
        }
    }
}
