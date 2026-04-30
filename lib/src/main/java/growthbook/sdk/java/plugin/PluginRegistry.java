package growthbook.sdk.java.plugin;

import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.multiusermode.configurations.EvaluationContext;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Holds a set of {@link GrowthBookPlugin}s registered with a GrowthBook
 * instance and dispatches lifecycle/event callbacks to each one.
 *
 * <p>Dispatch is best-effort: exceptions from one plugin never propagate
 * to the evaluator or other plugins. {@link #initAll()} captures init failures
 * so a failing plugin becomes a no-op rather than aborting registration.
 */
@Slf4j
public final class PluginRegistry {

    private final List<GrowthBookPlugin> plugins;

    public PluginRegistry(@Nullable List<GrowthBookPlugin> plugins) {
        if (plugins == null || plugins.isEmpty()) {
            this.plugins = Collections.emptyList();
        } else {
            this.plugins = new ArrayList<>(plugins);
        }
    }

    public boolean isEmpty() {
        return plugins.isEmpty();
    }

    public void initAll() {
        for (GrowthBookPlugin plugin : plugins) {
            try {
                plugin.init();
            } catch (Throwable t) {
                log.warn("Plugin {} init failed; continuing as no-op",
                        plugin.getClass().getName(), t);
            }
        }
    }

    public <V> void fireExperimentViewed(Experiment<V> experiment, ExperimentResult<V> result) {
        fireExperimentViewed(experiment, result, null);
    }

    public <V> void fireExperimentViewed(
            Experiment<V> experiment,
            ExperimentResult<V> result,
            @Nullable EvaluationContext context
    ) {
        if (plugins.isEmpty()) return;
        for (GrowthBookPlugin plugin : plugins) {
            try {
                if (context == null) {
                    plugin.onExperimentViewed(experiment, result);
                } else {
                    plugin.onExperimentViewed(experiment, result, context);
                }
            } catch (Throwable t) {
                log.warn("Plugin {} onExperimentViewed failed",
                        plugin.getClass().getName(), t);
            }
        }
    }

    public <V> void fireFeatureEvaluated(String featureKey, FeatureResult<V> result) {
        fireFeatureEvaluated(featureKey, result, null);
    }

    public <V> void fireFeatureEvaluated(
            String featureKey,
            FeatureResult<V> result,
            @Nullable EvaluationContext context
    ) {
        if (plugins.isEmpty()) return;
        for (GrowthBookPlugin plugin : plugins) {
            try {
                if (context == null) {
                    plugin.onFeatureEvaluated(featureKey, result);
                } else {
                    plugin.onFeatureEvaluated(featureKey, result, context);
                }
            } catch (Throwable t) {
                log.warn("Plugin {} onFeatureEvaluated failed",
                        plugin.getClass().getName(), t);
            }
        }
    }

    public void closeAll() {
        for (GrowthBookPlugin plugin : plugins) {
            try {
                plugin.close();
            } catch (Throwable t) {
                log.warn("Plugin {} close failed",
                        plugin.getClass().getName(), t);
            }
        }
    }
}
