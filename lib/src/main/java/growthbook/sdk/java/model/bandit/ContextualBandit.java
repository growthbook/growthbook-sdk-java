package growthbook.sdk.java.model.bandit;

import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.annotation.Nullable;
import java.util.ArrayList;

/**
 * The outcome of resolving a {@link ContextualBanditDefinition} for a given user: the selected leaf
 * and the weights that were applied to the {@link Experiment}. Attached to the experiment during
 * evaluation and, when the user is bucketed in, copied onto {@link ExperimentResult} for tracking.
 *
 * <p>A {@code leafId} of {@code -1} means no leaf matched (or the definition was missing) and the
 * experiment fell back to its aggregate or equal weights.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ContextualBandit {
    /**
     * The matched leaf's id, or {@code -1} when no leaf matched and fallback weights were used.
     */
    @Nullable
    Integer leafId;

    /**
     * The weights actually applied to the experiment for this user.
     */
    @Nullable
    ArrayList<Float> variationWeights;

    /**
     * The bandit version of the definition that produced these weights.
     */
    @Nullable
    Integer banditVersion;
}
