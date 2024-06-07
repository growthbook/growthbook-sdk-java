package growthbook.sdk.java.stickyBucketing;

import java.util.Map;

/**
 * Sticky Bucket Service is responsible for persisting previously seen
 * variations and ensure that the user experience remains consistent for your users.
 */
public interface StickyBucketService {

    /**
     * Lookup a sticky bucket document
     *
     * @param attributeName  attributeName with attributeValue together present
     *                       a key that us for find proper StickyAssignmentsDocument
     * @param attributeValue attributeName with attributeValue together present
     *                       a key that us for find proper StickyAssignmentsDocument
     * @return StickyAssignmentsDocument
     */
    StickyAssignmentsDocument getAssignments(String attributeName, String attributeValue);

    /**
     * Insert new record if not exists, otherwise update
     *
     * @param doc StickyAssignmentsDocument
     */
    void saveAssignments(StickyAssignmentsDocument doc);

    /**
     * Method for getting all Assignments by attributes from GBContext
     *
     * @param attributes Map of String key and String value that you have in GBContext
     * @return Map with key String and value StickyAssignmentsDocument
     */
    Map<String, StickyAssignmentsDocument> getAllAssignments(Map<String, String> attributes);
}
