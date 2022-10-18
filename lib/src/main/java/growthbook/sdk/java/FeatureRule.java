package growthbook.sdk.java;

import growthbook.sdk.java.models.Namespace;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import javax.annotation.Nullable;
import java.util.ArrayList;

@Data @Builder @AllArgsConstructor
public class FeatureRule<ValueType> {
    // TODO: Condition
    // Optional targeting condition
    // @Nullable

    @Nullable
    Float coverage;

    /**
     * Immediately force a specific value (ignore every other option besides condition and coverage)
     */
    @Nullable
    ValueType force;

    // TODO: Variations
    // Run an experiment (A/B test) and randomly choose between these variations
    // @Nullable

    /**
     * How to weight traffic between variations. Must add to 1.
     */
    @Nullable
    ArrayList<Float> weights;

    /**
     * Adds the experiment to a namespace
     */
    @Nullable
    Namespace namespace;

    /**
     * What user attribute should be used to assign variations (defaults to `id`)
     */
    @Builder.Default
    String hashAttribute = "id";
}
