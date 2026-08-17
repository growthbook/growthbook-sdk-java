package growthbook.sdk.java.stickyBucketing;

import growthbook.sdk.java.model.StickyAssignmentsDocument;

import java.util.Map;

/**
 * Persists previously seen variations so a user's experience stays consistent across
 * evaluations (sticky bucketing).
 *
 * <p>Documents are keyed by {@code attributeName||attributeValue}
 * (see {@link StickyAssignmentsDocument#key(String, String)}).
 */
public interface StickyBucketService {

    /**
     * Looks up the sticky bucket document for a single attribute name/value pair.
     *
     * @param attributeName  the identifier attribute name (e.g. {@code id})
     * @param attributeValue the identifier attribute value
     * @return the matching {@link StickyAssignmentsDocument}, or {@code null} if none exists
     */
    StickyAssignmentsDocument getAssignments(String attributeName, String attributeValue);

    /**
     * Inserts the document if it does not exist, otherwise updates it.
     *
     * @param doc the {@link StickyAssignmentsDocument} to persist
     */
    void saveAssignments(StickyAssignmentsDocument doc);

    /**
     * Looks up documents for many attribute name/value pairs at once.
     *
     * @param attributes a map of identifier attribute names to values
     * @return a map keyed by {@code attributeName||attributeValue} of the documents that exist
     */
    Map<String, StickyAssignmentsDocument> getAllAssignments(Map<String, String> attributes);
}
