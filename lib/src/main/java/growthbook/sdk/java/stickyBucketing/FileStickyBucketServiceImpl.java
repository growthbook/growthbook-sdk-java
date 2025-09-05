package growthbook.sdk.java.stickyBucketing;

import com.google.gson.Gson;
import growthbook.sdk.java.model.StickyAssignmentsDocument;
import growthbook.sdk.java.sandbox.GbCacheManager;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * File-based implementation of {@link StickyBucketService}.
 *
 * <p>
 * This service persists sticky bucket assignments using a {@link GbCacheManager}.
 * Each sticky assignment document is stored separately as a JSON string with a key
 * composed of a prefix and the combination of attribute name and value.
 * </p>
 *
 * <p>
 * Example key format: <code>gbStickyBuckets__userId||12345</code>
 * </p>
 */
@Slf4j
public class FileStickyBucketServiceImpl implements StickyBucketService {
    private final String prefix;
    private final GbCacheManager gbCacheManager;
    private final Gson gson;

    /**
     * Constructs a new service with the default prefix "gbStickyBuckets__".
     *
     * @param gbCacheManager the cache manager used to persist sticky assignments
     */
    public FileStickyBucketServiceImpl(GbCacheManager gbCacheManager) {
        this.gbCacheManager = gbCacheManager;
        this.prefix = "gbStickyBuckets__";
        this.gson = GrowthBookJsonUtils.getInstance().gson;
    }

    /**
     * Retrieves a sticky assignments document for the given attribute name and value.
     *
     * @param attributeName  the name of the attribute
     * @param attributeValue the value of the attribute
     * @return a {@link CompletableFuture} containing the {@link StickyAssignmentsDocument},
     * or {@code null} if not found
     */
    @Override
    public CompletableFuture<StickyAssignmentsDocument> getAssignments(String attributeName, String attributeValue) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                String key = buildKey(attributeName, attributeValue);
                String json = gbCacheManager.loadCache(key);
                if (json == null) {
                    return null;
                }
                return gson.fromJson(json, StickyAssignmentsDocument.class);
            } catch (Exception e) {
                log.error("Failed to load StickyAssignmentsDocument for {}={}, error: {}",
                        attributeName, attributeValue, e.getMessage(), e);
                return null;
            }
        });
    }

    /**
     * Saves a sticky assignments document in the cache.
     * If a document with the same key exists, it will be overwritten.
     *
     * @param doc the {@link StickyAssignmentsDocument} to save
     */
    @Override
    public void saveAssignments(StickyAssignmentsDocument doc) {
        CompletableFuture.runAsync(() -> {
            String key = buildKey(doc.getAttributeName(), doc.getAttributeValue());
            try {
                String json = gson.toJson(doc);
                gbCacheManager.saveContent(key, json);
            } catch (Exception e) {
                log.error("Failed to save StickyAssignmentsDocument for key={}, error: {}", key, e.getMessage(), e);
            }
        });
    }


    /**
     * Retrieves all sticky assignments documents for the given attributes.
     *
     * @param attributes a map of attribute names to values
     * @return a {@link CompletableFuture} containing a map where keys are
     * "prefix + attributeName||attributeValue" and values are
     * {@link StickyAssignmentsDocument} instances
     */
    @Override
    public CompletableFuture<Map<String, StickyAssignmentsDocument>> getAllAssignments(Map<String, String> attributes) {
        return CompletableFuture.supplyAsync(() -> {
            Map<String, StickyAssignmentsDocument> docs = new HashMap<>();
            for (Map.Entry<String, String> entry : attributes.entrySet()) {
                try {
                    String key = buildKey(entry.getKey(), entry.getValue());
                    String json = gbCacheManager.loadCache(key);
                    if (json == null) {
                        continue;
                    }
                    StickyAssignmentsDocument doc = gson.fromJson(json, StickyAssignmentsDocument.class);
                    if (doc != null) {
                        docs.put(entry.getKey() + "||" + entry.getValue(), doc);
                    }
                } catch (Exception e) {
                    log.error("Error while loading sticky assignment for {}={}, error: {}",
                            entry.getKey(), entry.getValue(), e.getMessage(), e);
                }
            }
            return docs;
        });
    }

    /**
     * Builds the cache key for a given attribute name and value, including the prefix.
     *
     * @param attributeName  the attribute name
     * @param attributeValue the attribute value
     * @return the full cache key string
     */
    private String buildKey(String attributeName, String attributeValue) {
        return prefix + attributeName + "||" + attributeValue;
    }
}
