package growthbook.sdk.java.repository;

import growthbook.sdk.java.exception.FeatureFetchException;

import javax.annotation.Nullable;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.LongSupplier;

/**
 * Coordinates the process-shared feature payload cache. It owns the time-aware {@link SharedPayloadCache},
 * the {@link SingleFlight} fetch de-duplicator, and the shared-cache settings, and centralizes the
 * stale-while-revalidate read decision. A repository that has opted into sharing delegates all
 * cross-instance caching and de-duplication here, keeping the instance-coupled work — applying a payload,
 * performing the network refresh — in the repository.
 *
 * <p>{@link #SHARED} is the process-wide singleton wired to the system clock. Tests construct their own
 * instance with a fixed clock to exercise the read decision deterministically.
 *
 * <p>All operations are safe for concurrent use.
 */
final class SharedFeatureCache {

    /**
     * Process-wide instance shared by all opted-in repositories.
     */
    static final SharedFeatureCache SHARED = new SharedFeatureCache(System::currentTimeMillis);

    /**
     * Outcome of a shared-cache read: the payload to apply immediately (null when the caller must fetch),
     * and whether that payload was stale and therefore needs background revalidation.
     */
    static final class Read {
        private static final Read FETCH = new Read(null, false);

        @Nullable
        private final String payload;
        private final boolean stale;

        private Read(@Nullable String payload, boolean stale) {
            this.payload = payload;
            this.stale = stale;
        }

        boolean shouldFetch() {
            return payload == null;
        }

        @Nullable
        String payload() {
            return payload;
        }

        boolean isStale() {
            return stale;
        }
    }

    private final SharedPayloadCache cache = new SharedPayloadCache();
    private final SingleFlight<String> dedup = new SingleFlight<>();
    private final LongSupplier clock;

    SharedFeatureCache(LongSupplier clock) {
        this.clock = clock;
    }

    /**
     * Decides whether a cached payload can be served. A fresh entry is always served; a stale entry is
     * served only when {@code allowStale} (and {@link Read#isStale()} tells the caller to revalidate in
     * the background). Any other case — miss, expired, stale-but-not-allowed, or {@code skipCache} —
     * requires a fetch.
     */
    Read read(String key, boolean allowStale, boolean skipCache) {
        if (skipCache) {
            return Read.FETCH;
        }
        SharedPayloadCache.Lookup lookup = cache.lookup(key, clock.getAsLong());
        if (lookup.isFresh()) {
            return new Read(lookup.payload(), false);
        }
        if (lookup.isStale() && allowStale) {
            return new Read(lookup.payload(), true);
        }
        return Read.FETCH;
    }

    boolean isFresh(String key) {
        return cache.lookup(key, clock.getAsLong()).isFresh();
    }

    @Nullable
    String peek(String key) {
        return cache.peek(key);
    }

    void publish(String key, String payload) {
        cache.put(key, payload, clock.getAsLong());
    }

    /**
     * Refreshes the stored-at timestamp of an existing shared entry, restarting its stale/expiry window.
     * A no-op when the key is absent. Called when a conditional refresh returns HTTP 304, confirming the
     * cached payload is still valid.
     */
    void touch(String key) {
        cache.touch(key, clock.getAsLong());
    }

    String runDeduped(String key, SingleFlight.Action<String> action) throws FeatureFetchException {
        return dedup.run(key, action);
    }

    void configure(SharedPayloadCache.Settings settings) {
        cache.configure(settings);
    }

    /**
     * Resets to a cold, default-configured state (clears entries, restores default settings, drops
     * in-flight fetches). For test isolation.
     */
    void reset() {
        cache.clear();
        cache.configure(SharedPayloadCache.Settings.defaults());
        dedup.clear();
    }

    /**
     * Derives the cache key: the features endpoint (which encodes {@code (apiHost, clientKey)}) plus a
     * SHA-256 fingerprint of the {@code decryptionKey} and custom request headers, so instances that
     * would receive a different payload never share an entry. The common case — no decryption key and no
     * custom headers — is the bare endpoint, hashing no secret material.
     */
    static String deriveKey(
            String featuresEndpoint,
            @Nullable String decryptionKey,
            Map<String, String> apiHostRequestHeaders
    ) {
        if (decryptionKey == null && apiHostRequestHeaders.isEmpty()) {
            return featuresEndpoint;
        }

        StringBuilder canonical = new StringBuilder();
        canonical.append(decryptionKey == null ? "" : decryptionKey);
        for (Map.Entry<String, String> header : new TreeMap<>(apiHostRequestHeaders).entrySet()) {
            canonical.append('\n').append(header.getKey()).append('=').append(header.getValue());
        }
        return featuresEndpoint + '#' + sha256Hex(canonical.toString());
    }

    private static String sha256Hex(String input) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is a mandated JDK algorithm; its absence is unrecoverable.
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
