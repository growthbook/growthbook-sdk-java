package growthbook.sdk.java;

import growthbook.sdk.java.stickyBucketing.StickyAssignmentsDocument;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

@Data
@AllArgsConstructor
@RequiredArgsConstructor
public class GeneratedStickyBucketAssignmentDocModel {
    private String key;
    private StickyAssignmentsDocument stickyAssignmentsDocument;
    private boolean changed;
}
