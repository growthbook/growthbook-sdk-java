package growthbook.sdk.java.stickyBucketing;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.Map;

@Data
@AllArgsConstructor
@RequiredArgsConstructor
public class StickyAssignmentsDocument {
    private String attributeName;

    private String attributeValue;

    private Map<String, String> assignments;
}
