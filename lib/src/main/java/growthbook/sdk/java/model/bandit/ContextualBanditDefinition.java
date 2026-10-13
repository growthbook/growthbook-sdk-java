package growthbook.sdk.java.model.bandit;

import growthbook.sdk.java.model.ExperimentResult;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.annotation.Nullable;
import java.util.ArrayList;

/**
 * A contextual bandit definition shipped in the feature payload under {@code contextualBandits},
 * keyed by the {@code contextualBanditRef} a feature rule points at.
 *
 * <p>A contextual bandit assigns per-segment variation weights: each {@link ContextualBanditContext}
 * ("leaf") carries a targeting condition and the weights to use when it matches. The weights
 * themselves are computed on the GrowthBook backend; the SDK only selects the matching leaf and
 * applies its weights before bucketing.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ContextualBanditDefinition {
    /**
     * Monotonic version of the backend-computed weights, echoed onto {@link ExperimentResult} so
     * exposures can be attributed to the weight generation that produced them.
     */
    @Nullable
    Integer banditVersion;

    /**
     * Ordered leaves. Leaves are evaluated top-to-bottom and the first whose condition matches the
     * user wins; if none match, the SDK falls back to aggregate/equal weights.
     */
    @Nullable
    ArrayList<ContextualBanditContext> contexts;
}
