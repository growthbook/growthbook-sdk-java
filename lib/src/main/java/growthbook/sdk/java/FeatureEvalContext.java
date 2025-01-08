package growthbook.sdk.java;

import java.util.Set;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;

/**
 * Model consist already evaluated features
 * Can be deleted because of no usage
 */
@Data
@Deprecated
@AllArgsConstructor
@RequiredArgsConstructor
class FeatureEvalContext {
    /**
     * Unique feature identifier
     */
    private String id;

    /**
     * Collection of unique feature identifier that used for handle recursion
     * in evaluate feature method
     */
    private Set<String> evaluatedFeatures;
}
