package growthbook.sdk.java.cache.redis.stickybucket;

import com.google.gson.Gson;
import growthbook.sdk.java.exception.StickyBucketServiceException;
import growthbook.sdk.java.model.StickyAssignmentsDocument;
import growthbook.sdk.java.stickyBucketing.StickyBucketService;
import growthbook.sdk.java.util.GrowthBookJsonUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shared {@link StickyBucketService} behaviour for Redis adapters, independent of the Redis client.
 *
 * <p>Each user's assignments are stored as a single JSON-encoded Redis string under the key
 * {@code <keyPrefix><attributeName>||<attributeValue>}. This on-the-wire shape matches the
 * TypeScript {@code RedisStickyBucketService}: with the default (empty) prefix a Java and a TS
 * service can share one Redis and read each other's assignments.
 *
 * <p>Assignments are written <strong>without</strong> a TTL on purpose — an assignment must outlive
 * any single process so a user keeps the same variation across restarts and across a horizontally
 * scaled fleet.
 *
 * <p><strong>Failure handling.</strong> Genuine Redis access failures and malformed payloads are
 * reported by throwing {@link StickyBucketServiceException}; they are never swallowed. Whether to
 * degrade to fresh bucketing or abort the request is left to the caller, mirroring how the feature
 * cache surfaces {@code FeatureCacheException}. An absent key is a normal miss ({@code null}), not a
 * failure.
 *
 * <p>Concrete subclasses only translate the three primitives ({@link #read}, {@link #write},
 * {@link #readMany}) to their client's API. Because assignments are independent per key, a service
 * built on a thread-safe client (Jedis pool, Lettuce connection) is safe for concurrent use.
 */
public abstract class AbstractRedisStickyBucketService implements StickyBucketService {

    /** Separator between attribute name and value in a key; matches the SDK's internal key format. */
    protected static final String KEY_SEPARATOR = "||";

    /** Default namespace prefix. Empty for wire-compatibility with the TypeScript service. */
    public static final String DEFAULT_KEY_PREFIX = "";

    private final Gson gson = GrowthBookJsonUtils.getInstance().gson;
    private final String keyPrefix;

    protected AbstractRedisStickyBucketService(String keyPrefix) {
        this.keyPrefix = keyPrefix == null ? DEFAULT_KEY_PREFIX : keyPrefix;
    }

    @Override
    public StickyAssignmentsDocument getAssignments(String attributeName, String attributeValue) {
        if (attributeName == null || attributeValue == null) {
            return null;
        }
        return deserialize(fetch(redisKey(docKey(attributeName, attributeValue))));
    }

    @Override
    public void saveAssignments(StickyAssignmentsDocument document) {
        if (document == null || document.getAttributeName() == null || document.getAttributeValue() == null) {
            return;
        }
        store(redisKey(docKey(document.getAttributeName(), document.getAttributeValue())), gson.toJson(document));
    }

    @Override
    public Map<String, StickyAssignmentsDocument> getAllAssignments(Map<String, String> attributes) {
        Map<String, StickyAssignmentsDocument> docs = new LinkedHashMap<>();
        if (attributes == null || attributes.isEmpty()) {
            return docs;
        }

        List<String> logicalKeys = logicalKeys(attributes);
        if (logicalKeys.isEmpty()) {
            return docs;
        }

        List<String> payloads = fetchMany(redisKeys(logicalKeys));
        for (int i = 0; i < logicalKeys.size(); i++) {
            StickyAssignmentsDocument doc = deserialize(i < payloads.size() ? payloads.get(i) : null);
            if (doc != null) {
                docs.put(logicalKeys.get(i), doc);
            }
        }
        return docs;
    }

    /**
     * @return the SDK-facing logical key ({@code attributeName||attributeValue}) used both as the
     * map key returned by {@link #getAllAssignments} and, once namespaced, as the Redis key
     */
    protected final String docKey(String attributeName, String attributeValue) {
        return attributeName + KEY_SEPARATOR + attributeValue;
    }

    /**
     * @param docKey the logical key from {@link #docKey}
     * @return the namespaced Redis key
     */
    protected final String redisKey(String docKey) {
        return keyPrefix + docKey;
    }

    private List<String> logicalKeys(Map<String, String> attributes) {
        List<String> keys = new ArrayList<>(attributes.size());
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            if (entry.getKey() != null && entry.getValue() != null) {
                keys.add(docKey(entry.getKey(), entry.getValue()));
            }
        }
        return keys;
    }

    private List<String> redisKeys(List<String> logicalKeys) {
        List<String> keys = new ArrayList<>(logicalKeys.size());
        for (String logicalKey : logicalKeys) {
            keys.add(redisKey(logicalKey));
        }
        return keys;
    }

    private String fetch(String redisKey) {
        try {
            return read(redisKey);
        } catch (RuntimeException e) {
            throw new StickyBucketServiceException("Failed to read sticky bucket assignments from Redis", e);
        }
    }

    private List<String> fetchMany(List<String> redisKeys) {
        try {
            List<String> payloads = readMany(redisKeys);
            return payloads == null ? Collections.<String>emptyList() : payloads;
        } catch (RuntimeException e) {
            throw new StickyBucketServiceException("Failed to read sticky bucket assignments from Redis", e);
        }
    }

    private void store(String redisKey, String json) {
        try {
            write(redisKey, json);
        } catch (RuntimeException e) {
            throw new StickyBucketServiceException("Failed to persist sticky bucket assignments to Redis", e);
        }
    }

    private StickyAssignmentsDocument deserialize(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        try {
            return gson.fromJson(raw, StickyAssignmentsDocument.class);
        } catch (RuntimeException e) {
            throw new StickyBucketServiceException("Malformed sticky bucket assignments payload in Redis", e);
        }
    }

    /**
     * @param redisKey the namespaced key
     * @return the raw JSON stored for the key, or {@code null} when the key is absent
     */
    protected abstract String read(String redisKey);

    /**
     * Stores the JSON payload for a key, without expiry.
     *
     * @param redisKey the namespaced key
     * @param json     the JSON-encoded {@link StickyAssignmentsDocument}
     */
    protected abstract void write(String redisKey, String json);

    /**
     * @param redisKeys namespaced keys to fetch, in order
     * @return a list positionally aligned with {@code redisKeys}; each element is the stored JSON or
     * {@code null} for an absent key
     */
    protected abstract List<String> readMany(List<String> redisKeys);
}
