package growthbook.sdk.java.stickyBucketing;

import growthbook.sdk.java.model.StickyAssignmentsDocument;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Simple in-memory {@link StickyBucketService} backed by a {@link Map}
 * (can be polyfilled for other environments).
 *
 * <p>By default a thread-safe {@link ConcurrentHashMap} is used, which is required for the
 * shared, multi-threaded usage of {@code GrowthBookClient}. When supplying a custom map, it
 * must be thread-safe if the service is accessed concurrently.
 */
public class InMemoryStickyBucketServiceImpl extends AbstractStickyBucketService {

    private final Map<String, StickyAssignmentsDocument> localStorage;

    /**
     * Constructs a service backed by a thread-safe {@link ConcurrentHashMap}.
     */
    public InMemoryStickyBucketServiceImpl() {
        this(new ConcurrentHashMap<>());
    }

    /**
     * Constructs a service backed by the given map. A {@code null} map falls back to a
     * thread-safe {@link ConcurrentHashMap}.
     *
     * @param localStorage a map to store sticky assignments documents in memory.
     */
    public InMemoryStickyBucketServiceImpl(@Nullable Map<String, StickyAssignmentsDocument> localStorage) {
        this.localStorage = localStorage != null ? localStorage : new ConcurrentHashMap<>();
    }

    @Override
    public StickyAssignmentsDocument getAssignments(String attributeName, String attributeValue) {
        if (attributeName == null || attributeValue == null) {
            return null;
        }
        return localStorage.get(StickyAssignmentsDocument.key(attributeName, attributeValue));
    }

    @Override
    public void saveAssignments(StickyAssignmentsDocument doc) {
        if (doc == null || doc.getAttributeName() == null || doc.getAttributeValue() == null) {
            return;
        }
        localStorage.put(doc.getKey(), doc);
    }
}
