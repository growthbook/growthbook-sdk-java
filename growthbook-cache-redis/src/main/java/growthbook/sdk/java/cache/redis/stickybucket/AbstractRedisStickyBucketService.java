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
 * <p>Concrete subclasses only translate the three primitives ({@link #read}, {@link #compareAndSet},
 * {@link #readMany}) to their client's API. Because assignments are independent per key, a service
 * built on a thread-safe client (Jedis pool, Lettuce connection) is safe for concurrent use.
 *
 * <p><strong>Concurrent saves.</strong> The SDK builds the document to save by merging new
 * assignments onto a copy it read at the start of evaluation, so a blind overwrite would let two
 * concurrent requests erase each other's assignments. {@link #saveAssignments} instead re-reads the
 * current Redis document, merges onto it (the incoming assignments win on conflict) and installs the
 * result with an atomic compare-and-set, retrying when a competing writer slips in between.
 */
public abstract class AbstractRedisStickyBucketService implements StickyBucketService {

    /** Separator between attribute name and value in a key; matches the SDK's internal key format. */
    protected static final String KEY_SEPARATOR = "||";

    /** Default namespace prefix. Empty for wire-compatibility with the TypeScript service. */
    public static final String DEFAULT_KEY_PREFIX = "";

    /**
     * Compare-and-set over a single key. {@code ARGV[1]} is the mode ({@code nx} to set only when the
     * key is absent, {@code cas} to set only when the current value equals {@code ARGV[2]}),
     * {@code ARGV[3]} is the new value. Returns 1 when the write happened, 0 otherwise.
     */
    protected static final String COMPARE_AND_SET_SCRIPT =
            "local current = redis.call('GET', KEYS[1])\n"
                    + "if ARGV[1] == 'nx' then\n"
                    + "    if current == false then\n"
                    + "        redis.call('SET', KEYS[1], ARGV[3])\n"
                    + "        return 1\n"
                    + "    end\n"
                    + "    return 0\n"
                    + "end\n"
                    + "if current == ARGV[2] then\n"
                    + "    redis.call('SET', KEYS[1], ARGV[3])\n"
                    + "    return 1\n"
                    + "end\n"
                    + "return 0";

    private static final int MAX_SAVE_ATTEMPTS = 5;

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
        if (document.getAssignments() == null) {
            throw new StickyBucketServiceException(
                    "Sticky bucket assignments document is missing its assignments map");
        }
        String redisKey = redisKey(docKey(document.getAttributeName(), document.getAttributeValue()));
        for (int attempt = 0; attempt < MAX_SAVE_ATTEMPTS; attempt++) {
            String currentRaw = fetch(redisKey);
            String updated = gson.toJson(mergeOnto(currentRaw, document));
            if (persist(redisKey, currentRaw, updated)) {
                return;
            }
        }
        throw new StickyBucketServiceException(
                "Failed to persist sticky bucket assignments to Redis after " + MAX_SAVE_ATTEMPTS
                        + " concurrent update attempts");
    }

    private StickyAssignmentsDocument mergeOnto(String currentRaw, StickyAssignmentsDocument incoming) {
        Map<String, String> merged = new LinkedHashMap<>();
        StickyAssignmentsDocument current = deserialize(currentRaw);
        if (current != null && current.getAssignments() != null) {
            merged.putAll(current.getAssignments());
        }
        merged.putAll(incoming.getAssignments());
        return new StickyAssignmentsDocument(incoming.getAttributeName(), incoming.getAttributeValue(), merged);
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

    private boolean persist(String redisKey, String expected, String updated) {
        try {
            return compareAndSet(redisKey, expected, updated);
        } catch (RuntimeException e) {
            throw new StickyBucketServiceException("Failed to persist sticky bucket assignments to Redis", e);
        }
    }

    private StickyAssignmentsDocument deserialize(String raw) {
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
     * @param redisKey the namespaced key
     * @return the raw JSON stored for the key, or {@code null} when the key is absent
     */
    protected abstract String read(String redisKey);

    /**
     * Atomically installs {@code updated} for {@code redisKey}, without expiry, only when the current
     * stored value still matches {@code expected}. A {@code null} {@code expected} means "write only
     * if the key is currently absent".
     *
     * @param redisKey the namespaced key
     * @param expected the value previously read, or {@code null} to require an absent key
     * @param updated  the JSON-encoded {@link StickyAssignmentsDocument} to store
     * @return {@code true} when the value was written, {@code false} when a competing writer changed
     * it first (the caller re-reads, re-merges and retries)
     */
    protected abstract boolean compareAndSet(String redisKey, String expected, String updated);

    /**
     * @param redisKeys namespaced keys to fetch, in order
     * @return a list positionally aligned with {@code redisKeys}; each element is the stored JSON or
     * {@code null} for an absent key
     */
    protected abstract List<String> readMany(List<String> redisKeys);
}
