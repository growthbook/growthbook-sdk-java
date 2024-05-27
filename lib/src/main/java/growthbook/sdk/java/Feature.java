package growthbook.sdk.java;

import lombok.Getter;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * The feature with a generic value type.
 * <ul>
 * <li>defaultValue (any) - The default value (should use null if not specified)</li>
 * <li>rules (FeatureRule[]) - Array of FeatureRule objects that determine when and how the defaultValue gets overridden</li>
 * </ul>
 *
 * @param <ValueType> value type for the feature
 */
public class Feature<ValueType> {

    @Nullable
    private final ArrayList<FeatureRule<ValueType>> rules = new ArrayList<>();

    /**
     * The default value for a feature evaluation
     * <p>
     *
     * return value of the feature
     */
    @Getter
    private final Object defaultValue = null;

    /**
     * Returns the rules for evaluating the feature
     *
     * @return rules list
     */
    @Nullable
    public List<FeatureRule<ValueType>> getRules() {
        return this.rules;
    }
}
