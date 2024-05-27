package growthbook.sdk.java.stickyBucketing;

import java.util.HashMap;
import java.util.Map;

public class InMemoryStickyBucketServiceImpl implements StickyBucketService {
    private final Map<String, StickyAssignmentsDocument> localStorage;

    public InMemoryStickyBucketServiceImpl(Map<String, StickyAssignmentsDocument> localStorage) {
        this.localStorage = localStorage;
    }

    @Override
    public StickyAssignmentsDocument getAssignments(String attributeName, String attributeValue) {
        return localStorage.get(attributeName + "||" + attributeValue);
    }

    @Override
    public void saveAssignments(StickyAssignmentsDocument doc) {
        localStorage.put(doc.getAttributeName() + "||" + doc.getAttributeValue(), doc);
    }

    @Override
    public Map<String, StickyAssignmentsDocument> getAllAssignments(Map<String, String> attributes) {
        Map<String, StickyAssignmentsDocument> docs = new HashMap<>();

        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            ;
            StickyAssignmentsDocument doc = getAssignments(key, value);

            if (doc != null) {
                String docKey = doc.getAttributeName() + "||" + doc.getAttributeValue();
                docs.put(docKey, doc);
            }
        }

        return docs;
    }
}
