package growthbook.sdk.java.stickyBucketing;

import java.util.Map;

public interface StickyBucketService {

    StickyAssignmentsDocument getAssignments(String attributeName, String attributeValue);

    void saveAssignments(StickyAssignmentsDocument doc);

    Map<String, StickyAssignmentsDocument> getAllAssignments(Map<String, String> attributes);
}
