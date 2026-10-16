package growthbook.sdk.java.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for the time-aware, bounded shared payload store. Deterministic by construction: freshness
 * is driven by explicit {@code nowMillis} arguments and injected {@link SharedPayloadCache.Settings},
 * never wall-clock time.
 */
class SharedPayloadCacheTest {

    private static final String KEY = "http://host/api/features/sdk-abc";
    private static final String PAYLOAD = "{\"features\":{}}";

    private static SharedPayloadCache cacheWith(long staleTtlMillis, long maxAgeMillis, int maxEntries) {
        SharedPayloadCache cache = new SharedPayloadCache();
        cache.configure(new SharedPayloadCache.Settings(staleTtlMillis, maxAgeMillis, maxEntries));
        return cache;
    }

    @Test
    @DisplayName("Verify: a missing key is a miss with no payload")
    void missingKeyIsMiss() {
        // Given an empty store
        SharedPayloadCache cache = cacheWith(1000L, 5000L, 10);

        // When looked up
        SharedPayloadCache.Lookup lookup = cache.lookup(KEY, 0L);

        // Then it is neither fresh nor stale and carries no payload
        assertFalse(lookup.isFresh());
        assertFalse(lookup.isStale());
        assertNull(lookup.payload());
    }

    @Test
    @DisplayName("Verify: an entry younger than the stale TTL is FRESH")
    void entryWithinStaleTtlIsFresh() {
        // Given an entry stored at t=0 with a 1000ms stale TTL
        SharedPayloadCache cache = cacheWith(1000L, 5000L, 10);
        cache.put(KEY, PAYLOAD, 0L);

        // When read at t=500
        SharedPayloadCache.Lookup lookup = cache.lookup(KEY, 500L);

        // Then it is fresh and serves the payload
        assertTrue(lookup.isFresh());
        assertFalse(lookup.isStale());
        assertEquals(PAYLOAD, lookup.payload());
    }

    @Test
    @DisplayName("Verify: an entry at exactly the stale TTL is STALE (not fresh)")
    void entryAtStaleTtlBoundaryIsStale() {
        // Given an entry stored at t=0 with a 1000ms stale TTL
        SharedPayloadCache cache = cacheWith(1000L, 5000L, 10);
        cache.put(KEY, PAYLOAD, 0L);

        // When read at t=1000 (age == staleTTL)
        SharedPayloadCache.Lookup lookup = cache.lookup(KEY, 1000L);

        // Then it is stale but still carries the payload
        assertTrue(lookup.isStale());
        assertFalse(lookup.isFresh());
        assertEquals(PAYLOAD, lookup.payload());
    }

    @Test
    @DisplayName("Verify: an entry between stale TTL and max age is STALE and still served")
    void entryBetweenStaleTtlAndMaxAgeIsStale() {
        // Given an entry stored at t=0
        SharedPayloadCache cache = cacheWith(1000L, 5000L, 10);
        cache.put(KEY, PAYLOAD, 0L);

        // When read at t=3000
        SharedPayloadCache.Lookup lookup = cache.lookup(KEY, 3000L);

        // Then it is stale and carries the payload
        assertTrue(lookup.isStale());
        assertEquals(PAYLOAD, lookup.payload());
    }

    @Test
    @DisplayName("Verify: an entry at or beyond max age is a miss (discarded)")
    void entryBeyondMaxAgeIsMiss() {
        // Given an entry stored at t=0 with a 5000ms max age
        SharedPayloadCache cache = cacheWith(1000L, 5000L, 10);
        cache.put(KEY, PAYLOAD, 0L);

        // When read at t=5000 (age == maxAge)
        SharedPayloadCache.Lookup lookup = cache.lookup(KEY, 5000L);

        // Then it is a miss
        assertFalse(lookup.isFresh());
        assertFalse(lookup.isStale());
        assertNull(lookup.payload());
    }

    @Test
    @DisplayName("Verify: peek returns the stored payload regardless of age")
    void peekIgnoresFreshness() {
        // Given an entry that is already expired by lookup rules
        SharedPayloadCache cache = cacheWith(1000L, 5000L, 10);
        cache.put(KEY, PAYLOAD, 0L);

        // When peeked far past max age
        // Then the raw payload is still returned
        assertEquals(PAYLOAD, cache.peek(KEY));
    }

    @Test
    @DisplayName("Verify: peek returns null for an absent key")
    void peekReturnsNullForMissingKey() {
        // Given an empty store
        SharedPayloadCache cache = cacheWith(1000L, 5000L, 10);

        // When & Then
        assertNull(cache.peek(KEY));
    }

    @Test
    @DisplayName("Verify: a later put for the same key overwrites the payload and its age")
    void putOverwritesEntry() {
        // Given an entry that would be stale at t=1500
        SharedPayloadCache cache = cacheWith(1000L, 5000L, 10);
        cache.put(KEY, "old", 0L);

        // When overwritten at t=1500
        cache.put(KEY, "new", 1500L);

        // Then it is fresh again at t=2000 with the new value
        SharedPayloadCache.Lookup lookup = cache.lookup(KEY, 2000L);
        assertTrue(lookup.isFresh());
        assertEquals("new", lookup.payload());
    }

    @Test
    @DisplayName("Verify: the store is bounded to maxEntries, evicting the least-recently-stored key")
    void evictsLeastRecentlyStoredBeyondCapacity() {
        // Given a store bounded to 2 entries
        SharedPayloadCache cache = cacheWith(60_000L, 300_000L, 2);
        cache.put("k1", "1", 0L);
        cache.put("k2", "2", 1L);

        // When a third distinct key is stored
        cache.put("k3", "3", 2L);

        // Then the oldest (k1) is evicted and the two newest remain
        assertNull(cache.peek("k1"));
        assertEquals("2", cache.peek("k2"));
        assertEquals("3", cache.peek("k3"));
    }

    @Test
    @DisplayName("Verify: touch resets an entry's age so a would-be-stale entry is fresh again")
    void touchResetsEntryAge() {
        // Given an entry stored at t=0 that is stale by t=1500 (1000ms stale TTL)
        SharedPayloadCache cache = cacheWith(1000L, 5000L, 10);
        cache.put(KEY, PAYLOAD, 0L);
        assertTrue(cache.lookup(KEY, 1500L).isStale());

        // When touched at t=1500
        cache.touch(KEY, 1500L);

        // Then it is fresh again through t=2499 and still serves the same payload
        SharedPayloadCache.Lookup lookup = cache.lookup(KEY, 2400L);
        assertTrue(lookup.isFresh());
        assertEquals(PAYLOAD, lookup.payload());
    }

    @Test
    @DisplayName("Verify: touch on an absent key is a no-op (does not create an entry)")
    void touchAbsentKeyIsNoOp() {
        // Given an empty store
        SharedPayloadCache cache = cacheWith(1000L, 5000L, 10);

        // When touched
        cache.touch(KEY, 100L);

        // Then no entry is created
        assertNull(cache.peek(KEY));
    }

    @Test
    @DisplayName("Verify: clear removes all entries")
    void clearRemovesEntries() {
        // Given a populated store
        SharedPayloadCache cache = cacheWith(1000L, 5000L, 10);
        cache.put(KEY, PAYLOAD, 0L);

        // When cleared
        cache.clear();

        // Then the entry is gone
        assertNull(cache.peek(KEY));
    }

    @Test
    @DisplayName("Verify: Settings rejects a negative stale TTL")
    void settingsRejectsNegativeStaleTtl() {
        assertThrows(IllegalArgumentException.class, () -> new SharedPayloadCache.Settings(-1L, 5000L, 10));
    }

    @Test
    @DisplayName("Verify: Settings rejects a negative max age")
    void settingsRejectsNegativeMaxAge() {
        assertThrows(IllegalArgumentException.class, () -> new SharedPayloadCache.Settings(1000L, -1L, 10));
    }

    @Test
    @DisplayName("Verify: Settings rejects a max entries below one")
    void settingsRejectsZeroMaxEntries() {
        assertThrows(IllegalArgumentException.class, () -> new SharedPayloadCache.Settings(1000L, 5000L, 0));
    }

    @Test
    @DisplayName("Verify: Settings rejects a stale TTL greater than the max age")
    void settingsRejectsStaleTtlExceedingMaxAge() {
        assertThrows(IllegalArgumentException.class, () -> new SharedPayloadCache.Settings(5001L, 5000L, 10));
    }

    @Test
    @DisplayName("Verify: Settings accepts a stale TTL equal to the max age")
    void settingsAcceptsStaleTtlEqualToMaxAge() {
        // When & Then (no exception)
        new SharedPayloadCache.Settings(5000L, 5000L, 10);
    }
}
