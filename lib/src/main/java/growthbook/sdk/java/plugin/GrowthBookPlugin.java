package growthbook.sdk.java.plugin;

import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.FeatureResult;

/**
 * A plugin that can observe experiment and feature evaluations on a GrowthBook
 * instance. Mirrors the plugin contracts in the GrowthBook Python and Go SDKs.
 *
 * <p>Implementations MUST be thread-safe. All methods default to no-ops so
 * custom plugins only override what they need. Methods should return quickly
 * (enqueue work, don't block evaluation). Exceptions thrown by these methods
 * are caught by {@link PluginRegistry} so one failing plugin can't break
 * evaluation or other plugins.
 */
public interface GrowthBookPlugin {

    /**
     * Called once when the plugin is registered with a GrowthBook instance.
     * If this throws, the plugin is treated as failed and the other methods
     * still need to remain safe to call (no-op is acceptable).
     */
    default void init() {
    }

    /**
     * Invoked after a user is bucketed into an experiment (once per
     * unique hashAttribute/hashValue/experiment.key/variation combination).
     */
    default <V> void onExperimentViewed(Experiment<V> experiment, ExperimentResult<V> result) {
    }

    /**
     * Invoked every time a feature is evaluated.
     */
    default <V> void onFeatureEvaluated(String featureKey, FeatureResult<V> result) {
    }

    /**
     * Invoked when the owning GrowthBook instance is closed. Implementations
     * should flush any buffered work synchronously. MUST be safe to call
     * multiple times and even if {@link #init()} failed.
     */
    default void close() {
    }
}
