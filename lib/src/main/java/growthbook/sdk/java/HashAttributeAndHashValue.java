package growthbook.sdk.java;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * Model for care a pair of hashAttribute and hashValue
 */
@Data
@AllArgsConstructor
@RequiredArgsConstructor
public class HashAttributeAndHashValue {
    /**
     * All users included in the experiment will be forced into the specific variation index
     */
    private String hashAttribute;

    /**
     * Value by hashAttribute
     */
    private String hashValue;
}
