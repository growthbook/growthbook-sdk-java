package growthbook.sdk.java.stickyBucketing;

import lombok.RequiredArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * For simple bucket persistence using the in memory's storage(Map) (can be polyfilled for other environments)
 */
@RequiredArgsConstructor
public class InMemoryStickyBucketServiceImpl implements StickyBucketService {
    private final Map<String, StickyAssignmentsDocument> localStorage;

    /**
     * Method for saving assignments document to cache
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
     * Method for saving assignments document to cache
     *
     * @param doc StickyAssignmentsDocument
     */
    @Override
    public void saveAssignments(StickyAssignmentsDocument doc) {
        localStorage.put(doc.getAttributeName() + "||" + doc.getAttributeValue(), doc);
    }

    /**
     * Method for getting sticky bucket assignments from attributes of context
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
