package growthbook.sdk.java.model.bandit;

import com.google.gson.JsonObject;
import growthbook.sdk.java.model.ExperimentResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.annotation.Nullable;
import java.util.ArrayList;

/**
 * A single leaf of a {@link ContextualBanditDefinition}: a targeting {@code condition} paired with
 * the backend-computed variation {@code weights} to apply when that condition matches the user.
 *
 * <p>Part of the read-only contextual bandit payload; instances are deserialized from the feature
 * API response and never mutated during evaluation.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ContextualBanditContext {
    /**
     * Identifier of this leaf within the bandit definition. Surfaced on {@link ExperimentResult}
     * so exposures can be attributed back to the segment that produced the weights.
     */
    @Nullable
    Integer leafId;

    /**
     * Targeting condition evaluated against the user's attributes. A {@code null} or empty
     * condition matches every user.
     */
    @Nullable
    JsonObject condition;

    /**
     * Variation weights to apply when this leaf matches. Must align with the experiment's variation
     * count and, like any weights, sum to 1.
     */
    @Nullable
    ArrayList<Float> weights;
}
