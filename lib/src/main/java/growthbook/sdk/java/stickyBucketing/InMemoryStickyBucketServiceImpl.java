package growthbook.sdk.java.stickyBucketing;

import growthbook.sdk.java.model.StickyAssignmentsDocument;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * For simple bucket persistence using the in memory's storage(Map) (can be polyfilled for other environments)
 */
public class InMemoryStickyBucketServiceImpl implements StickyBucketService {
    private final Map<String, StickyAssignmentsDocument> localStorage;

    /**
     * Constructs a new {@code InMemoryStickyBucketServiceImpl} backed by a thread-safe map,
     * suitable for use with {@code GrowthBookClient} where evaluations run concurrently.
     */
    public InMemoryStickyBucketServiceImpl() {
        this(new ConcurrentHashMap<>());
    }

    /**
     * Constructs a new {@code InMemoryStickyBucketServiceImpl} with the specified local storage.
     *
     * @param localStorage a map to store sticky assignments documents in memory. The map is used
     *                     directly as the backing storage, so assignments saved by this service are
     *                     visible to the caller and vice versa. A plain map (e.g. {@link HashMap})
     *                     is only safe when the service is used from a single thread; pass a
     *                     thread-safe {@link ConcurrentMap} (e.g. {@link ConcurrentHashMap}) when the
     *                     service is shared by a {@code GrowthBookClient} evaluating on multiple
     *                     threads. A {@code null} map falls back to an empty {@link ConcurrentHashMap}.
     */
    public InMemoryStickyBucketServiceImpl(Map<String, StickyAssignmentsDocument> localStorage) {
        this.localStorage = localStorage == null ? new ConcurrentHashMap<>() : localStorage;
    }

    /**
     * Method for getting all assignments document from cache (in memory: hashmap)
     *
     * @param attributeName  attributeName with attributeValue together present
     *                       a key that us for find proper StickyAssignmentsDocument
     * @param attributeValue attributeName with attributeValue together present
     *                       a key that us for find proper StickyAssignmentsDocument
     * @return StickyAssignmentsDocument
     */
    @Override
    public StickyAssignmentsDocument getAssignments(String attributeName, String attributeValue) {
        return localStorage.get(attributeName + "||" + attributeValue);
    }

    /**
     * Saves a user's assignments document, merged into the one already stored: assignments from
     * concurrent evaluations of the same user add up instead of the last save overwriting the
     * others, and on a conflict the saved document wins. The merge is atomic when the backing map
     * is a {@link ConcurrentMap}.
     *
     * @param doc StickyAssignmentsDocument
     */
    @Override
    public void saveAssignments(StickyAssignmentsDocument doc) {
        localStorage.merge(doc.getAttributeName() + "||" + doc.getAttributeValue(), doc, InMemoryStickyBucketServiceImpl::merged);
    }

    private static StickyAssignmentsDocument merged(StickyAssignmentsDocument stored, StickyAssignmentsDocument saved) {
        Map<String, String> assignments = new HashMap<>();
        if (stored.getAssignments() != null) {
            assignments.putAll(stored.getAssignments());
        }
        if (saved.getAssignments() != null) {
            assignments.putAll(saved.getAssignments());
        }
        return new StickyAssignmentsDocument(saved.getAttributeName(), saved.getAttributeValue(), assignments);
    }

    /**
     * Method for getting sticky bucket assignments from cache (in memory: hashmap) by attributes of context
     *
     * @param attributes Map of String key and String value that you have in GBContext
     * @return Map with key String and value StickyAssignmentsDocument
     */
    @Override
    public Map<String, StickyAssignmentsDocument> getAllAssignments(Map<String, String> attributes) {
        Map<String, StickyAssignmentsDocument> docs = new HashMap<>();

        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            StickyAssignmentsDocument doc = getAssignments(key, value);

            if (doc != null) {
                String docKey = doc.getAttributeName() + "||" + doc.getAttributeValue();
                docs.put(docKey, doc);
            }
        }

        return docs;
    }
}
