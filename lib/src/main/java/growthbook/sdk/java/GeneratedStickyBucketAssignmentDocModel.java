package growthbook.sdk.java;

import growthbook.sdk.java.stickyBucketing.StickyAssignmentsDocument;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * Model that created for generating StickyBucketAssignmentDocModel
 */
@Data
@AllArgsConstructor
@RequiredArgsConstructor
public class GeneratedStickyBucketAssignmentDocModel {
    /**
     * Unique key of StickyAssignment Document in format: "attributeName||attributeValue"
     */
    private String key;

    /**
     * StickyAssignmentsDocument class is presenting a model
     * for accumulate stickyBucketing data
     */
    private StickyAssignmentsDocument stickyAssignmentsDocument;

    /**
     * Boolean value that check if data changed
     */
    private boolean changed;
}
