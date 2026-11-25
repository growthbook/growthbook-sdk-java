package growthbook.sdk.java.cache.redis.stickybucket;

import com.google.gson.Gson;
import growthbook.sdk.java.exception.StickyBucketServiceException;
import growthbook.sdk.java.model.StickyAssignmentsDocument;
import growthbook.sdk.java.util.GrowthBookJsonUtils;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The part of the Redis sticky bucket services that does no I/O: key naming, the TTL argument,
 * document (de)serialization and the merge applied before a compare-and-set. Shared by the blocking
 * {@link AbstractRedisStickyBucketService} and the non-blocking
 * {@link LettuceAsyncStickyBucketService} so both write the same bytes under the same keys.
 *
 * <p>Immutable and thread-safe.
 */
final class RedisStickyBucketCodec {

    /** Separator between attribute name and value in a key; matches the SDK's internal key format. */
    static final String KEY_SEPARATOR = "||";

    /** How many times a save re-reads, re-merges and retries after losing a compare-and-set. */
    static final int MAX_SAVE_ATTEMPTS = 5;

    private final Gson gson = GrowthBookJsonUtils.getInstance().gson;
    private final String keyPrefix;

    /** Entry TTL in milliseconds, or {@code null} for no expiry (the default). */
    private final Long ttlMillis;

    /**
     * @param keyPrefix namespace applied to every Redis key; {@code null} uses
     *                  {@link AbstractRedisStickyBucketService#DEFAULT_KEY_PREFIX}
     * @param ttl       optional per-entry expiry; {@code null} writes entries without a TTL. When set
     *                  it must be at least 1 millisecond.
     * @throws IllegalArgumentException if {@code ttl} is zero, negative, or below 1 millisecond
     */
    RedisStickyBucketCodec(String keyPrefix, Duration ttl) {
        this.keyPrefix = keyPrefix == null ? AbstractRedisStickyBucketService.DEFAULT_KEY_PREFIX : keyPrefix;
        if (ttl == null) {
            this.ttlMillis = null;
        } else {
            if (ttl.isZero() || ttl.isNegative()) {
                throw new IllegalArgumentException("ttl must be greater than 0");
            }
            if (ttl.toMillis() < 1) {
                throw new IllegalArgumentException("ttl must be at least 1 millisecond");
            }
            this.ttlMillis = ttl.toMillis();
        }
    }

    /**
     * @return the TTL argument ({@code ARGV[4]}) for
     * {@link AbstractRedisStickyBucketService#COMPARE_AND_SET_SCRIPT}: the configured expiry in
     * milliseconds, or {@code "0"} when entries are written without a TTL
     */
    String ttlMillisArg() {
        return ttlMillis == null ? "0" : Long.toString(ttlMillis);
    }

    /** @return the SDK-facing logical key, {@code attributeName||attributeValue} */
    String docKey(String attributeName, String attributeValue) {
        return attributeName + KEY_SEPARATOR + attributeValue;
    }

    /** @return the namespaced Redis key for a logical key from {@link #docKey} */
    String redisKey(String docKey) {
        return keyPrefix + docKey;
    }

    /** @return the logical keys for the attributes that have both a name and a value, in iteration order */
    List<String> logicalKeys(Map<String, String> attributes) {
        List<String> keys = new ArrayList<>(attributes.size());
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                keys.add(docKey(entry.getKey(), entry.getValue()));
            }
        }
        return keys;
    }

    /** @return the namespaced Redis keys, positionally aligned with {@code logicalKeys} */
    List<String> redisKeys(List<String> logicalKeys) {
        List<String> keys = new ArrayList<>(logicalKeys.size());
        for (String logicalKey : logicalKeys) {
            keys.add(redisKey(logicalKey));
        }
        return keys;
    }

    /**
     * @param logicalKeys the keys that were read, in order
     * @param payloads    the stored JSON for each key, positionally aligned; {@code null} or a missing
     *                    trailing element means the key is absent
     * @return the documents that exist, keyed by logical key
     * @throws StickyBucketServiceException if a payload is malformed
     */
    Map<String, StickyAssignmentsDocument> documentsByLogicalKey(List<String> logicalKeys, List<String> payloads) {
        Map<String, StickyAssignmentsDocument> docs = new LinkedHashMap<>();
        for (int i = 0; i < logicalKeys.size(); i++) {
            StickyAssignmentsDocument doc = deserialize(i < payloads.size() ? payloads.get(i) : null);
            if (doc != null) {
                docs.put(logicalKeys.get(i), doc);
            }
        }
        return docs;
    }

    /**
     * @param raw the stored JSON, or {@code null}/empty for an absent key
     * @return the document, or {@code null} for an absent key
     * @throws StickyBucketServiceException if the payload is not a sticky bucket document
     */
    StickyAssignmentsDocument deserialize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        StickyAssignmentsDocument document;
        try {
            document = gson.fromJson(raw, StickyAssignmentsDocument.class);
        } catch (RuntimeException e) {
            throw new StickyBucketServiceException("Malformed sticky bucket assignments payload in Redis", e);
        }
        if (document != null && document.getAssignments() == null) {
            throw new StickyBucketServiceException(
                    "Sticky bucket assignments payload in Redis is missing its assignments map");
        }
        return document;
    }

    /**
     * @param currentRaw the JSON currently stored, or {@code null} for an absent key
     * @param incoming   the document being saved; its assignments win on conflict
     * @return the JSON to install: the stored assignments with {@code incoming}'s merged over them
     * @throws StickyBucketServiceException if {@code currentRaw} is malformed
     */
    String mergedJson(String currentRaw, StickyAssignmentsDocument incoming) {
        Map<String, String> merged = new LinkedHashMap<>();
        StickyAssignmentsDocument current = deserialize(currentRaw);
        if (current != null && current.getAssignments() != null) {
            merged.putAll(current.getAssignments());
        }
        merged.putAll(incoming.getAssignments());
        return gson.toJson(
                new StickyAssignmentsDocument(incoming.getAttributeName(), incoming.getAttributeValue(), merged));
    }
}
