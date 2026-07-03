package growthbook.sdk.java.cache.redis;

import java.util.HashMap;
import java.util.Map;

/**
 * In-memory view of a cached payload and the epoch-millis timestamp of when it was written.
 *
 * <p>Owns the Redis hash field names and the hash &harr; entry mapping so the Jedis and Lettuce
 * adapters share identical on-the-wire representation.
 */
final class RedisCacheEntry {

    static final String FIELD_DATA = "data";
    static final String FIELD_UPDATED_AT = "updatedAt";

    private final String data;
    private final long lastUpdatedMillis;

    RedisCacheEntry(String data, long lastUpdatedMillis) {
        this.data = data;
        this.lastUpdatedMillis = lastUpdatedMillis;
    }

    String getData() {
        return data;
    }

    long getLastUpdatedMillis() {
        return lastUpdatedMillis;
    }

    /**
     * @return the Redis hash fields representing a payload written at the given timestamp
     */
    static Map<String, String> toHash(String data, long lastUpdatedMillis) {
        Map<String, String> hash = new HashMap<>();
        hash.put(FIELD_DATA, data);
        hash.put(FIELD_UPDATED_AT, Long.toString(lastUpdatedMillis));
        return hash;
    }

    /**
     * @param hash the Redis hash fields for a key (possibly empty)
     * @return the parsed entry, or {@code null} when the hash is empty or carries no payload (a miss)
     */
    static RedisCacheEntry fromHash(Map<String, String> hash) {
        if (hash == null || hash.isEmpty()) {
            return null;
        }
        String data = hash.get(FIELD_DATA);
        if (data == null) {
            return null;
        }
        return new RedisCacheEntry(data, parseTimestamp(hash.get(FIELD_UPDATED_AT)));
    }

    private static long parseTimestamp(String value) {
        if (value == null) {
            return 0L;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
