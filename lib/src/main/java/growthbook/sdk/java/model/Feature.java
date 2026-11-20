package growthbook.sdk.java.model;

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
 * @param <T> value type for the feature
 */
@Data
public class Feature<T> {

    /**
     * Array of Rule objects that determine when and how the defaultValue gets overridden
     */
    @Nullable
    private final ArrayList<FeatureRule<T>> rules = new ArrayList<>();

    /**
     * The default value (should use null if not specified)
     */
    @Nullable
    private final Object defaultValue = null;
}
