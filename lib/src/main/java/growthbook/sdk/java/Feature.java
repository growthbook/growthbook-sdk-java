package growthbook.sdk.java;

import lombok.Data;

import javax.annotation.Nullable;
import java.util.ArrayList;

/**
 * The feature with a generic value type.
 * <ul>
 * <li>defaultValue (any) - The default value (should use null if not specified)</li>
 * <li>rules (FeatureRule[]) - Array of FeatureRule objects that determine when and how the defaultValue gets overridden</li>
 * </ul>
 *
 * @param <ValueType> value type for the feature
 */
@Data
public class Feature<ValueType> {

    /**
     * Array of Rule objects that determine when and how the defaultValue gets overridden
     */
    @Nullable
    private final ArrayList<FeatureRule<ValueType>> rules = new ArrayList<>();

    /**
     * The default value (should use null if not specified)
     */
    private final Object defaultValue = null;
}
