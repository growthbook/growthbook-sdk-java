package growthbook.sdk.java.model;

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
     * Separator between the attribute name and value in a sticky bucket document key.
     */
    public static final String KEY_SEPARATOR = "||";

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

    /**
     * Builds the canonical document key for an attribute name/value pair,
     * i.e. {@code attributeName||attributeValue}. This is the single source of truth
     * for the sticky bucket document key format.
     *
     * @param attributeName  the attribute name
     * @param attributeValue the attribute value
     * @return the document key
     */
    public static String key(String attributeName, String attributeValue) {
        return attributeName + KEY_SEPARATOR + attributeValue;
    }

    /**
     * @return the canonical document key for this document ({@code attributeName||attributeValue}).
     */
    public String getKey() {
        return key(attributeName, attributeValue);
    }
}
