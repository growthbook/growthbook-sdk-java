package growthbook.sdk.java;

import com.google.gson.JsonObject;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * A ParentCondition defines a prerequisite. It consists of a parent feature's id (string),
 * a condition (Condition),and an optional gate (boolean) flag.
 */
@Data
@AllArgsConstructor
@RequiredArgsConstructor
public class ParentCondition {
    /**
     * Parent feature's ID
     */
    private String id;

    /**
     * Target condition
     */
    private JsonObject condition;

    /**
     * If gate is true, then this is a blocking feature-level prerequisite;
     * otherwise it applies to the current rule only
     */
    private Boolean gate;
}
