package growthbook.sdk.java.stickyBucketing;

import growthbook.sdk.java.model.StickyAssignmentsDocument;

import java.util.HashMap;
import java.util.Map;

/**
 * Base class for {@link StickyBucketService} implementations that store one document per
 * {@code attributeName||attributeValue} key.
 *
 * <p>Implementations only need to provide the single-document {@link #getAssignments} and
 * {@link #saveAssignments} operations; the bulk {@link #getAllAssignments} lookup is derived
 * from {@code getAssignments} here so it does not have to be duplicated per backend.
 */
public abstract class AbstractStickyBucketService implements StickyBucketService {

    @Override
    public Map<String, StickyAssignmentsDocument> getAllAssignments(Map<String, String> attributes) {
        Map<String, StickyAssignmentsDocument> docs = new HashMap<>();
        if (attributes == null) {
            return docs;
        }
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            StickyAssignmentsDocument doc = getAssignments(entry.getKey(), entry.getValue());
            if (doc != null) {
                docs.put(StickyAssignmentsDocument.key(entry.getKey(), entry.getValue()), doc);
            }
        }
        return docs;
    }
}
