package growthbook.sdk.java.stickyBucketing;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.util.Map;

/**
 * StickyAssignmentsDocument class is presenting a model
 * for accumulate such data as: attributeName, attributeValue and assignments
 */
@Data
@AllArgsConstructor
@RequiredArgsConstructor
public class StickyAssignmentsDocument {
    /**
     * The name of the attribute used to identify the user (e.g. `id`, `cookie_id`, etc.)
     */
    private String attributeName;

    /**
     * The value of the attribute (e.g. `123`)
     */
    private String attributeValue;

    /**
     * A dictionary of persisted experiment assignments. For example: `{"exp1__0":"control"}`
     */
    private Map<String, String> assignments;
}
