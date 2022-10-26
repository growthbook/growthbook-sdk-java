package growthbook.sdk.java.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import javax.annotation.Nullable;
import java.util.ArrayList;

/**
 * Defines a single Experiment
 */
@Data
@Builder
@AllArgsConstructor
public class Experiment<ValueType> {
    /**
     * The globally unique identifier for the experiment
     */
    String key;


    /**
     * The different variations to choose between
     */
    @Nullable
    ArrayList<ValueType> variations;

    /**
     * How to weight traffic between variations. Must add to 1.
     */
    ArrayList<Float> weights;

    /**
     * If set to false, always return the control (first variation)
     */
    Boolean isActive;

    /**
     * What percent of users should be included in the experiment (between 0 and 1, inclusive)
     */
    Float coverage;

    // TODO: Condition
//    /**
//     * Optional targeting condition
//     */

    @Nullable
    Namespace namespace;

    /**
     * All users included in the experiment will be forced into the specific variation index
     */
    // TODO: Integer?
    Float force;

    /**
     * What user attribute should be used to assign variations (defaults to `id`)
     */
    @Builder.Default
    String hashAttribute = "id";
}
