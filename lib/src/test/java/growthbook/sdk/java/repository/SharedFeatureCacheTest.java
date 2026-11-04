package growthbook.sdk.java.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import growthbook.sdk.java.exception.FeatureFetchException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Unit tests for the shared-cache coordinator. The stale-while-revalidate read decision is driven by an
 * injected clock, so the whole FRESH / STALE / EXPIRED / skipCache matrix is exercised deterministically
 * without a socket or wall-clock time.
 */
class SharedFeatureCacheTest {

    private static final String KEY = "http://host/api/features/sdk-abc";
    private static final String PAYLOAD = "{\"features\":{}}";

    private final AtomicLong now = new AtomicLong(0);
    private SharedFeatureCache coordinator;

    @BeforeEach
    void setUp() {
        coordinator = new SharedFeatureCache(now::get);
        coordinator.configure(new SharedPayloadCache.Settings(1000L, 5000L, 10));
    }

    @Test
    @DisplayName("Verify: a missing key requires a fetch")
    void missingKeyRequiresFetch() {
        // Given an empty coordinator
        // When read
        SharedFeatureCache.Read read = coordinator.read(KEY, true, false);

        // Then the caller must fetch
        assertTrue(read.shouldFetch());
        assertNull(read.payload());
    }

    @Test
    @DisplayName("Verify: a fresh entry is served without a background reload")
    void freshEntryIsServed() {
        // Given an entry published at t=0
        coordinator.publish(KEY, PAYLOAD);

        // When read at t=500 (within the 1000ms stale TTL)
        now.set(500);
        SharedFeatureCache.Read read = coordinator.read(KEY, false, false);

        // Then it is served and not marked stale
        assertFalse(read.shouldFetch());
        assertEquals(PAYLOAD, read.payload());
        assertFalse(read.isStale());
    }

    @Test
    @DisplayName("Verify: a stale entry is served with a reload flag when stale reads are allowed")
    void staleEntryIsServedWhenAllowStale() {
        // Given an entry published at t=0
        coordinator.publish(KEY, PAYLOAD);

        // When read at t=1500 (past stale TTL, within max age) allowing stale
        now.set(1500);
        SharedFeatureCache.Read read = coordinator.read(KEY, true, false);

        // Then it is served and flagged for background revalidation
        assertFalse(read.shouldFetch());
        assertEquals(PAYLOAD, read.payload());
        assertTrue(read.isStale());
    }

    @Test
    @DisplayName("Verify: a stale entry requires a fetch when stale reads are not allowed")
    void staleEntryFetchesWhenStaleNotAllowed() {
        // Given a stale entry
        coordinator.publish(KEY, PAYLOAD);
        now.set(1500);

        // When read without allowing stale (prefetch warm-up)
        SharedFeatureCache.Read read = coordinator.read(KEY, false, false);

        // Then the caller must fetch
        assertTrue(read.shouldFetch());
    }

    @Test
    @DisplayName("Verify: an expired entry requires a fetch even when stale reads are allowed")
    void expiredEntryRequiresFetch() {
        // Given an entry published at t=0
        coordinator.publish(KEY, PAYLOAD);

        // When read at t=5000 (past the 5000ms max age)
        now.set(5000);
        SharedFeatureCache.Read read = coordinator.read(KEY, true, false);

        // Then the caller must fetch
        assertTrue(read.shouldFetch());
    }

    @Test
    @DisplayName("Verify: skipCache always requires a fetch, even for a fresh entry")
    void skipCacheAlwaysFetches() {
        // Given a fresh entry
        coordinator.publish(KEY, PAYLOAD);
        now.set(100);

        // When read with skipCache
        SharedFeatureCache.Read read = coordinator.read(KEY, true, true);

        // Then the caller must fetch
        assertTrue(read.shouldFetch());
    }

    @Test
    @DisplayName("Verify: isFresh reflects the stale TTL window")
    void isFreshReflectsWindow() {
        // Given an entry published at t=0
        coordinator.publish(KEY, PAYLOAD);

        // When/Then fresh within the window, not fresh past it
        now.set(999);
        assertTrue(coordinator.isFresh(KEY));
        now.set(1000);
        assertFalse(coordinator.isFresh(KEY));
    }

    @Test
    @DisplayName("Verify: peek returns the raw payload regardless of freshness")
    void peekIgnoresFreshness() {
        // Given an entry that is already expired by read rules
        coordinator.publish(KEY, PAYLOAD);
        now.set(999_999);

        // When peeked
        // Then the raw payload is still returned
        assertEquals(PAYLOAD, coordinator.peek(KEY));
    }

    @Test
    @DisplayName("Verify: runDeduped runs the action and returns its result")
    void runDedupedRunsAction() throws FeatureFetchException {
        // Given a coordinator
        // When an action is run through it
        String result = coordinator.runDeduped(KEY, () -> "value");

        // Then its result is returned
        assertEquals("value", result);
    }

    @Test
    @DisplayName("Verify: reset clears entries and restores default settings")
    void resetClearsAndRestoresDefaults() {
        // Given a stored entry and custom (zero) settings that would treat everything as expired
        coordinator.configure(new SharedPayloadCache.Settings(0L, 1L, 10));
        coordinator.publish(KEY, PAYLOAD);

        // When reset
        coordinator.reset();
        coordinator.publish(KEY, PAYLOAD);

        // Then the entry is fresh again under restored default settings (60s stale TTL)
        assertTrue(coordinator.isFresh(KEY));
    }

    @Test
    @DisplayName("Verify: deriveKey is the bare endpoint when there is no decryption key or custom header")
    void deriveKeyIsBareEndpointForCommonCase() {
        // Given no decryption key and no headers
        String key = SharedFeatureCache.deriveKey(KEY, null, Collections.emptyMap());

        // Then the key is the endpoint unchanged
        assertEquals(KEY, key);
    }

    @Test
    @DisplayName("Verify: deriveKey is stable for identical inputs")
    void deriveKeyIsStable() {
        // Given identical decryption key and headers
        Map<String, String> headers = Collections.singletonMap("X-Tenant", "a");

        // When derived twice
        String first = SharedFeatureCache.deriveKey(KEY, "dk", headers);
        String second = SharedFeatureCache.deriveKey(KEY, "dk", headers);

        // Then the keys match
        assertEquals(first, second);
    }

    @Test
    @DisplayName("Verify: deriveKey is independent of header insertion order")
    void deriveKeyIgnoresHeaderOrder() {
        // Given the same headers inserted in different orders
        Map<String, String> ordered = new LinkedHashMap<>();
        ordered.put("A", "1");
        ordered.put("B", "2");
        Map<String, String> reversed = new LinkedHashMap<>();
        reversed.put("B", "2");
        reversed.put("A", "1");

        // When derived
        // Then the keys match (headers are canonicalized)
        assertEquals(
                SharedFeatureCache.deriveKey(KEY, null, ordered),
                SharedFeatureCache.deriveKey(KEY, null, reversed)
        );
    }

    @Test
    @DisplayName("Verify: deriveKey differs for a different header value")
    void deriveKeyDiffersForDifferentHeaders() {
        // Given two header sets that differ in value
        String a = SharedFeatureCache.deriveKey(KEY, null, Collections.singletonMap("X-Tenant", "a"));
        String b = SharedFeatureCache.deriveKey(KEY, null, Collections.singletonMap("X-Tenant", "b"));

        // Then the keys differ
        assertNotEquals(a, b);
    }

    @Test
    @DisplayName("Verify: deriveKey differs for a different decryption key")
    void deriveKeyDiffersForDifferentDecryptionKey() {
        // Given two different decryption keys
        String a = SharedFeatureCache.deriveKey(KEY, "dk-1", Collections.emptyMap());
        String b = SharedFeatureCache.deriveKey(KEY, "dk-2", Collections.emptyMap());

        // Then the keys differ
        assertNotEquals(a, b);
    }
}
