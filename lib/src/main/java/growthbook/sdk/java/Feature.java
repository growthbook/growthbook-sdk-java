package growthbook.sdk.java;

import javax.annotation.Nullable;
import java.util.ArrayList;

/**
 * The feature with a generic value type.
 * <ul>
 * <li>defaultValue (any) - The default value (should use null if not specified)</li>
 * <li>rules (FeatureRule[]) - Array of FeatureRule objects that determine when and how the defaultValue gets overridden</li>
 * </ul>
 * @param <ValueType> value type for the feature
 */
public class Feature<ValueType> {

    @Nullable
    private final ArrayList<FeatureRule<ValueType>> rules = new ArrayList<>();

    private final Object defaultValue = null;

    /**
     * The default value for a feature evaluation
     * @return value of the feature
     */
    public Object getDefaultValue() {
        return this.defaultValue;
    }

    /**
     * Returns the rules for evaluating the feature
     * @return rules list
     */
    @Nullable
    public ArrayList<FeatureRule<ValueType>> getRules() {
        return this.rules;
    }
}
