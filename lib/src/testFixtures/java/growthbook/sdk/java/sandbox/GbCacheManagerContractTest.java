package growthbook.sdk.java.sandbox;

import growthbook.sdk.java.exception.FeatureCacheException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Contract every {@link GbCacheManager} implementation must satisfy.
 *
 * <p>Subclasses supply a fresh, empty cache manager per test via {@link #createCacheManager()} and
 * release any backing resources in their own lifecycle methods. Implementations whose storage can
 * be shared with entries the adapter did not write expose that storage through
 * {@link #sharedForeignStore()}, so the test can prove {@link GbCacheManager#clearCache()} leaves
 * those entries alone; the case is skipped otherwise. Implementations that can simulate a broken
 * backing store do so in {@link #failStorage()}, so the test can prove real access failures surface
 * as {@link FeatureCacheException}; those cases are skipped otherwise. Implementations that take a
 * {@link Clock} read write times from {@link #clock()}, which the test advances explicitly.
 */
public abstract class GbCacheManagerContractTest {

    private static final int THREADS = 8;
    private static final int KEYS_PER_THREAD = 25;
    private static final int LARGE_PAYLOAD_BYTES = 1024 * 1024;
    private static final String PAYLOAD = "{\"features\":{\"sample-flag\":{\"defaultValue\":\"ünïcødé\"}}}";
    private static final Instant START = Instant.parse("2026-01-01T00:00:00Z");

    private final MutableClock clock = new MutableClock(START);
    private GbCacheManager cacheManager;

    /**
     * @return a new, empty cache manager owned by the current test
     */
    protected abstract GbCacheManager createCacheManager();

    /**
     * @return a store sharing the cache manager's backing storage but written outside of it, or
     * {@code null} when the implementation owns its storage exclusively
     */
    protected ForeignStore sharedForeignStore() {
        return null;
    }

    /**
     * @return the clock cache managers that take one must read write times from
     */
    protected final Clock clock() {
        return clock;
    }

    /**
     * @param key a key the cache manager has just written
     * @return the last-updated time the cache manager must report for that write; the current time
     * of {@link #clock()} unless the implementation takes its timestamps from its storage
     */
    protected long expectedLastUpdatedMillis(String key) {
        return clock.millis();
    }

    /**
     * Makes every later access to the cache manager's backing storage fail.
     *
     * @return {@code true} when the implementation can simulate a storage failure, {@code false}
     * when it cannot fail independently of the JVM
     */
    protected boolean failStorage() {
        return false;
    }

    /**
     * Entries written into the cache manager's backing storage by someone other than the adapter.
     */
    protected interface ForeignStore {
        void write(String key, String value);

        String read(String key);
    }

    @BeforeEach
    void createCacheManagerUnderTest() {
        cacheManager = createCacheManager();
    }

    @Test
    @DisplayName("Loading an absent key returns null without throwing")
    void loadingAnAbsentKeyReturnsNull() {
        // Given
        String key = "absent-key";

        // When
        String loaded = cacheManager.loadCache(key);

        // Then
        assertNull(loaded);
    }

    @Test
    @DisplayName("Last-updated time of an absent key is null")
    void lastUpdatedTimeOfAnAbsentKeyIsNull() {
        // Given
        String key = "absent-key";

        // When
        Long lastUpdated = cacheManager.getLastUpdatedMillis(key);

        // Then
        assertNull(lastUpdated);
    }

    @Test
    @DisplayName("Saved content is loaded back unchanged")
    void savedContentIsLoadedBackUnchanged() {
        // Given
        String key = "features-key";

        // When
        cacheManager.saveContent(key, PAYLOAD);

        // Then
        assertEquals(PAYLOAD, cacheManager.loadCache(key));
    }

    @Test
    @DisplayName("Saving content records the time of the write in epoch milliseconds")
    void savingContentRecordsTheTimeOfTheWrite() {
        // Given
        String key = "features-key";

        // When
        cacheManager.saveContent(key, PAYLOAD);

        // Then
        assertEquals(expectedLastUpdatedMillis(key), cacheManager.getLastUpdatedMillis(key));
    }

    @Test
    @DisplayName("Saving to an existing key replaces both the content and the last-updated time")
    void savingToAnExistingKeyReplacesContentAndTime() {
        // Given
        String key = "features-key";
        cacheManager.saveContent(key, "{\"version\":\"first\"}");
        clock.advance(Duration.ofMinutes(1));

        // When
        cacheManager.saveContent(key, "{\"version\":\"second\"}");

        // Then
        assertEquals("{\"version\":\"second\"}", cacheManager.loadCache(key));
        assertEquals(expectedLastUpdatedMillis(key), cacheManager.getLastUpdatedMillis(key));
    }

    @Test
    @DisplayName("Saving one key leaves other keys untouched")
    void savingOneKeyLeavesOtherKeysUntouched() {
        // Given
        cacheManager.saveContent("first-key", "{\"owner\":\"first\"}");

        // When
        cacheManager.saveContent("second-key", "{\"owner\":\"second\"}");

        // Then
        assertEquals("{\"owner\":\"first\"}", cacheManager.loadCache("first-key"));
        assertEquals("{\"owner\":\"second\"}", cacheManager.loadCache("second-key"));
    }

    @Test
    @DisplayName("An empty payload is stored as content, not reported as a miss")
    void emptyPayloadIsStoredAsContent() {
        // Given
        String key = "features-key";

        // When
        cacheManager.saveContent(key, "");

        // Then
        assertEquals("", cacheManager.loadCache(key));
        assertNotNull(cacheManager.getLastUpdatedMillis(key));
    }

    @Test
    @DisplayName("Line breaks and surrounding whitespace in the payload are preserved")
    void lineBreaksAndSurroundingWhitespaceArePreserved() {
        // Given
        String payload = "  {\n  \"features\": {\r\n    \"sample-flag\": {\"defaultValue\": \"ünïcødé\"}\n  }\n}\n\n";

        // When
        cacheManager.saveContent("features-key", payload);

        // Then
        assertEquals(payload, cacheManager.loadCache("features-key"));
    }

    @Test
    @DisplayName("A payload of one megabyte is loaded back unchanged")
    void largePayloadIsLoadedBackUnchanged() {
        // Given
        String payload = largePayload();

        // When
        cacheManager.saveContent("features-key", payload);

        // Then
        assertEquals(payload, cacheManager.loadCache("features-key"));
    }

    @Test
    @DisplayName("Loading content does not change its last-updated time")
    void loadingContentDoesNotChangeLastUpdatedTime() {
        // Given
        cacheManager.saveContent("features-key", PAYLOAD);
        Long savedAt = cacheManager.getLastUpdatedMillis("features-key");
        clock.advance(Duration.ofMinutes(1));

        // When
        cacheManager.loadCache("features-key");
        cacheManager.loadCache("features-key");

        // Then
        assertEquals(savedAt, cacheManager.getLastUpdatedMillis("features-key"));
    }

    @Test
    @DisplayName("Keys that share a prefix are stored independently")
    void keysSharingAPrefixAreStoredIndependently() {
        // Given
        String legacyKey = "FEATURE_CACHE.json";
        String scopedKey = "FEATURE_CACHE_0123456789abcdef.json";
        cacheManager.saveContent(legacyKey, "{\"owner\":\"legacy\"}");

        // When
        cacheManager.saveContent(scopedKey, "{\"owner\":\"scoped\"}");

        // Then
        assertEquals("{\"owner\":\"legacy\"}", cacheManager.loadCache(legacyKey));
        assertEquals("{\"owner\":\"scoped\"}", cacheManager.loadCache(scopedKey));
        assertNull(cacheManager.loadCache("FEATURE_CACHE"));
    }

    @Test
    @DisplayName("Clearing the cache removes every saved key and its last-updated time")
    void clearingTheCacheRemovesEverySavedKey() {
        // Given
        cacheManager.saveContent("first-key", PAYLOAD);
        cacheManager.saveContent("second-key", PAYLOAD);

        // When
        cacheManager.clearCache();

        // Then
        assertNull(cacheManager.loadCache("first-key"));
        assertNull(cacheManager.getLastUpdatedMillis("first-key"));
        assertNull(cacheManager.loadCache("second-key"));
        assertNull(cacheManager.getLastUpdatedMillis("second-key"));
    }

    @Test
    @DisplayName("Clearing an empty cache succeeds and keeps it usable")
    void clearingAnEmptyCacheKeepsItUsable() {
        // Given
        cacheManager.clearCache();

        // When
        cacheManager.saveContent("features-key", PAYLOAD);

        // Then
        assertEquals(PAYLOAD, cacheManager.loadCache("features-key"));
    }

    @Test
    @DisplayName("A cleared key can be saved again with new content and a new last-updated time")
    void clearedKeyCanBeSavedAgain() {
        // Given
        cacheManager.saveContent("features-key", "{\"version\":\"first\"}");
        cacheManager.clearCache();
        clock.advance(Duration.ofMinutes(1));

        // When
        cacheManager.saveContent("features-key", "{\"version\":\"second\"}");

        // Then
        assertEquals("{\"version\":\"second\"}", cacheManager.loadCache("features-key"));
        assertEquals(expectedLastUpdatedMillis("features-key"), cacheManager.getLastUpdatedMillis("features-key"));
    }

    @Test
    @DisplayName("Clearing the cache keeps entries in shared storage that the adapter did not write")
    void clearingTheCacheKeepsForeignEntries() {
        // Given
        ForeignStore foreignStore = sharedForeignStore();
        assumeTrue(foreignStore != null, "the implementation owns its storage exclusively");
        foreignStore.write("foreign-key", "foreign-value");
        cacheManager.saveContent("features-key", PAYLOAD);

        // When
        cacheManager.clearCache();

        // Then
        assertNull(cacheManager.loadCache("features-key"));
        assertEquals("foreign-value", foreignStore.read("foreign-key"));
    }

    @Test
    @DisplayName("Concurrent saves and loads from several threads keep every key consistent")
    void concurrentSavesAndLoadsKeepEveryKeyConsistent() throws Exception {
        // Given
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        List<Future<Void>> results = new ArrayList<>();
        Set<String> sharedValues = new HashSet<>();
        try {
            for (int thread = 0; thread < THREADS; thread++) {
                sharedValues.add("{\"writer\":" + thread + "}");
                results.add(executor.submit(writerAndReader(thread, startGate)));
            }

            // When
            startGate.countDown();
            for (Future<Void> result : results) {
                result.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        // Then
        for (int thread = 0; thread < THREADS; thread++) {
            for (int index = 0; index < KEYS_PER_THREAD; index++) {
                assertEquals(valueFor(thread, index), cacheManager.loadCache(keyFor(thread, index)));
                assertNotNull(cacheManager.getLastUpdatedMillis(keyFor(thread, index)));
            }
        }
        assertTrue(sharedValues.contains(cacheManager.loadCache("shared-key")));
        assertNotNull(cacheManager.getLastUpdatedMillis("shared-key"));
    }

    @Test
    @DisplayName("Clearing the cache while other threads save never fails and leaves the cache usable")
    void clearingWhileOtherThreadsSaveNeverFails() throws Exception {
        // Given
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(THREADS);
        List<Future<Void>> results = new ArrayList<>();
        try {
            for (int thread = 0; thread < THREADS; thread++) {
                results.add(executor.submit(thread % 2 == 0 ? clearer(startGate) : writer(thread, startGate)));
            }

            // When
            startGate.countDown();
            for (Future<Void> result : results) {
                result.get(30, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
        }

        // Then
        cacheManager.saveContent("features-key", PAYLOAD);
        assertEquals(PAYLOAD, cacheManager.loadCache("features-key"));
        assertNotNull(cacheManager.getLastUpdatedMillis("features-key"));
    }

    @Test
    @DisplayName("Saving to a failing storage throws a FeatureCacheException")
    void savingToFailingStorageThrowsFeatureCacheException() {
        // Given
        assumeTrue(failStorage(), "the implementation cannot simulate a storage failure");

        // When & Then
        assertThrows(FeatureCacheException.class, () -> cacheManager.saveContent("features-key", PAYLOAD));
    }

    @Test
    @DisplayName("Loading from a failing storage throws a FeatureCacheException instead of reporting a miss")
    void loadingFromFailingStorageThrowsFeatureCacheException() {
        // Given
        assumeTrue(failStorage(), "the implementation cannot simulate a storage failure");

        // When & Then
        assertThrows(FeatureCacheException.class, () -> cacheManager.loadCache("features-key"));
    }

    @Test
    @DisplayName("Reading the last-updated time from a failing storage throws a FeatureCacheException")
    void readingLastUpdatedTimeFromFailingStorageThrowsFeatureCacheException() {
        // Given
        assumeTrue(failStorage(), "the implementation cannot simulate a storage failure");

        // When & Then
        assertThrows(FeatureCacheException.class, () -> cacheManager.getLastUpdatedMillis("features-key"));
    }

    @Test
    @DisplayName("Clearing a failing storage throws a FeatureCacheException")
    void clearingFailingStorageThrowsFeatureCacheException() {
        // Given
        assumeTrue(failStorage(), "the implementation cannot simulate a storage failure");

        // When & Then
        assertThrows(FeatureCacheException.class, () -> cacheManager.clearCache());
    }

    private Callable<Void> writerAndReader(final int thread, final CountDownLatch startGate) {
        return new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                startGate.await();
                for (int index = 0; index < KEYS_PER_THREAD; index++) {
                    cacheManager.saveContent(keyFor(thread, index), valueFor(thread, index));
                    cacheManager.saveContent("shared-key", "{\"writer\":" + thread + "}");
                    assertEquals(valueFor(thread, index), cacheManager.loadCache(keyFor(thread, index)));
                    assertNotNull(cacheManager.loadCache("shared-key"));
                }
                return null;
            }
        };
    }

    private Callable<Void> clearer(final CountDownLatch startGate) {
        return new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                startGate.await();
                for (int index = 0; index < KEYS_PER_THREAD; index++) {
                    cacheManager.clearCache();
                }
                return null;
            }
        };
    }

    private Callable<Void> writer(final int thread, final CountDownLatch startGate) {
        return new Callable<Void>() {
            @Override
            public Void call() throws Exception {
                startGate.await();
                for (int index = 0; index < KEYS_PER_THREAD; index++) {
                    cacheManager.saveContent(keyFor(thread, index), valueFor(thread, index));
                    cacheManager.loadCache(keyFor(thread, index));
                }
                return null;
            }
        };
    }

    private static String largePayload() {
        StringBuilder payload = new StringBuilder(LARGE_PAYLOAD_BYTES + PAYLOAD.length());
        payload.append("{\"padding\":\"");
        while (payload.length() < LARGE_PAYLOAD_BYTES) {
            payload.append("0123456789abcdef");
        }
        return payload.append("\"}").toString();
    }

    private static String keyFor(int thread, int index) {
        return "key-" + thread + "-" + index;
    }

    private static String valueFor(int thread, int index) {
        return "{\"thread\":" + thread + ",\"index\":" + index + "}";
    }

    private static final class MutableClock extends Clock {
        private volatile Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
