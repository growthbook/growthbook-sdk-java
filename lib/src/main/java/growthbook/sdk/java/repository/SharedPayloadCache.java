package growthbook.sdk.java.repository;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-shared store of raw feature payloads keyed by a repository's features endpoint URL, which
 * uniquely encodes {@code (apiHost, clientKey)}. Mirrors the JS SDK's module-level feature cache:
 * entries are classified {@link State#FRESH}, {@link State#STALE}, or {@link State#EXPIRED} against a
 * two-tier window — a {@code staleTtl} after which a cached payload is served but should be revalidated,
 * and a hard {@code maxAge} after which it is discarded — and the store is bounded to {@code maxEntries}
 * with least-recently-stored eviction.
 *
 * <p>This is a time-aware, bounded map: it holds no fetch logic and performs no de-duplication (see
 * {@link SingleFlight}). It complements, and does not replace, the per-instance {@code GbCacheManager};
 * a repository consults it only when it has opted into the shared cache. All operations are safe for
 * concurrent use.
 */
final class SharedPayloadCache {

    /**
     * Freshness classification of a lookup, mirroring the JS SDK's {@code staleAt}/{@code maxAge} logic.
     */
    enum State {
        FRESH,
        STALE,
        EXPIRED
    }

    /**
     * Result of a {@link #lookup(String, long)}: the payload (null unless {@link State#FRESH} or
     * {@link State#STALE}) and its freshness.
     */
    static final class Lookup {
        static final Lookup MISS = new Lookup(null, State.EXPIRED);

        @Nullable
        private final String payload;
        private final State state;

        private Lookup(@Nullable String payload, State state) {
            this.payload = payload;
            this.state = state;
        }

        @Nullable
        String payload() {
            return payload;
        }

        boolean isFresh() {
            return state == State.FRESH;
        }

        boolean isStale() {
            return state == State.STALE;
        }
    }

    /**
     * Tunables mirroring the JS SDK's {@code cacheSettings} (defaults: 60s stale TTL, 4h max age,
     * 10 entries).
     */
    static final class Settings {
        private final long staleTtlMillis;
        private final long maxAgeMillis;
        private final int maxEntries;

        Settings(long staleTtlMillis, long maxAgeMillis, int maxEntries) {
            if (staleTtlMillis < 0) {
                throw new IllegalArgumentException("staleTtlMillis must not be negative");
            }
            if (maxAgeMillis < 0) {
                throw new IllegalArgumentException("maxAgeMillis must not be negative");
            }
            if (staleTtlMillis > maxAgeMillis) {
                throw new IllegalArgumentException("staleTtlMillis must not exceed maxAgeMillis");
            }
            if (maxEntries < 1) {
                throw new IllegalArgumentException("maxEntries must be at least 1");
            }
            this.staleTtlMillis = staleTtlMillis;
            this.maxAgeMillis = maxAgeMillis;
            this.maxEntries = maxEntries;
        }

        static Settings defaults() {
            return new Settings(60_000L, 4L * 60L * 60L * 1000L, 10);
        }
    }

    private static final class Entry {
        private final String payload;
        private final long storedAtMillis;

        private Entry(String payload, long storedAtMillis) {
            this.payload = payload;
            this.storedAtMillis = storedAtMillis;
        }
    }

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
    private final Object writeLock = new Object();
    private volatile Settings settings = Settings.defaults();

    void configure(Settings settings) {
        this.settings = settings;
    }

    /**
     * Classifies the entry for {@code key} at {@code nowMillis}. An entry older than {@code maxAge} is a
     * {@link Lookup#MISS}; within {@code staleTtl} it is {@link State#FRESH}; in between it is
     * {@link State#STALE} and carries its payload for immediate use pending revalidation.
     */
    Lookup lookup(String key, long nowMillis) {
        Entry entry = entries.get(key);
        if (entry == null) {
            return Lookup.MISS;
        }
        Settings current = this.settings;
        long ageMillis = nowMillis - entry.storedAtMillis;
        if (ageMillis >= current.maxAgeMillis) {
            return Lookup.MISS;
        }
        State state = ageMillis < current.staleTtlMillis ? State.FRESH : State.STALE;
        return new Lookup(entry.payload, state);
    }

    /**
     * Returns the stored payload for {@code key} ignoring freshness, or {@code null} if absent. Used to
     * read back a payload that a fetch has just published.
     */
    @Nullable
    String peek(String key) {
        Entry entry = entries.get(key);
        return entry == null ? null : entry.payload;
    }

    void put(String key, String payload, long nowMillis) {
        synchronized (writeLock) {
            entries.put(key, new Entry(payload, nowMillis));
            evictToCapacity();
        }
    }

    /**
     * Resets the stored-at timestamp of an existing entry to {@code nowMillis}, restarting its
     * stale/expiry window without changing the payload. No-op when the key is absent. Used when a
     * conditional request confirms the cached payload is still valid (HTTP 304), so the entry is not
     * needlessly reclassified {@link State#STALE} and revalidated by other instances.
     */
    void touch(String key, long nowMillis) {
        synchronized (writeLock) {
            Entry entry = entries.get(key);
            if (entry != null) {
                entries.put(key, new Entry(entry.payload, nowMillis));
            }
        }
    }

    void clear() {
        synchronized (writeLock) {
            entries.clear();
        }
    }

    private void evictToCapacity() {
        int maxEntries = this.settings.maxEntries;
        while (entries.size() > maxEntries) {
            String oldestKey = null;
            long oldestStoredAt = Long.MAX_VALUE;
            for (Map.Entry<String, Entry> candidate : entries.entrySet()) {
                long storedAt = candidate.getValue().storedAtMillis;
                if (storedAt < oldestStoredAt) {
                    oldestStoredAt = storedAt;
                    oldestKey = candidate.getKey();
                }
            }
            if (oldestKey == null) {
                return;
            }
            entries.remove(oldestKey);
        }
    }
}
