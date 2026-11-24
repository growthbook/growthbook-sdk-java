package growthbook.sdk.java.multiusermode.usage;

/**
 * Names of the built-in events emitted to an {@link EventLogger}. The string values match the
 * GrowthBook JavaScript SDK so downstream analytics pipelines stay consistent across SDKs.
 */
public final class GrowthBookEvent {

    private GrowthBookEvent() {
    }

    /**
     * Emitted when a user is bucketed into an experiment (an exposure event). Properties:
     * {@code experimentId}, {@code variationId}, {@code hashAttribute}, {@code hashValue}.
     */
    public static final String EXPERIMENT_VIEWED = "Experiment Viewed";

    /**
     * Emitted on every feature evaluation. Properties: {@code feature}, {@code source},
     * {@code value}, {@code ruleId}, {@code variationId}. The {@code value} property is the raw
     * evaluated value — a boxed primitive, or a {@code Map}/{@code List} for object/array features —
     * and may be {@code null}.
     */
    public static final String FEATURE_EVALUATED = "Feature Evaluated";
}
