package growthbook.sdk.java.plugin;

import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.FeatureResult;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Holds a set of {@link GrowthBookPlugin}s registered with a GrowthBook
 * instance and dispatches lifecycle/event callbacks to each one.
 *
 * <p>Dispatch is best-effort: a plugin that throws is isolated so the failure
 * never propagates to the evaluator or other plugins. A plugin whose
 * {@link GrowthBookPlugin#init()} throws is dropped and receives no further
 * events. {@link Error}s are not swallowed — only {@link Exception}s are.
 */
@Slf4j
public final class PluginRegistry {

    private final List<GrowthBookPlugin> plugins;

    public PluginRegistry(@Nullable List<GrowthBookPlugin> plugins) {
        if (plugins == null || plugins.isEmpty()) {
            this.plugins = Collections.emptyList();
        } else {
            List<GrowthBookPlugin> nonNull = new ArrayList<>(plugins.size());
            for (GrowthBookPlugin plugin : plugins) {
                if (plugin != null) {
                    nonNull.add(plugin);
                }
            }
            this.plugins = nonNull;
        }
    }

    public boolean isEmpty() {
        return plugins.isEmpty();
    }

    public void initAll() {
        if (plugins.isEmpty()) {
            return;
        }
        // Drop any plugin whose init() fails so it never receives later events.
        plugins.removeIf(plugin -> {
            try {
                plugin.init();
                return false;
            } catch (Exception e) {
                log.warn("Plugin {} init failed; it will not receive events",
                        plugin.getClass().getName(), e);
                return true;
            }
        });
    }

    public <V> void fireExperimentViewed(Experiment<V> experiment, ExperimentResult<V> result) {
        if (plugins.isEmpty()) {
            return;
        }
        for (GrowthBookPlugin plugin : plugins) {
            try {
                plugin.onExperimentViewed(experiment, result);
            } catch (Exception e) {
                log.warn("Plugin {} onExperimentViewed failed",
                        plugin.getClass().getName(), e);
            }
        }
    }

    public <V> void fireFeatureEvaluated(String featureKey, FeatureResult<V> result) {
        if (plugins.isEmpty()) {
            return;
        }
        for (GrowthBookPlugin plugin : plugins) {
            try {
                plugin.onFeatureEvaluated(featureKey, result);
            } catch (Exception e) {
                log.warn("Plugin {} onFeatureEvaluated failed",
                        plugin.getClass().getName(), e);
            }
        }
    }

    public void closeAll() {
        if (plugins.isEmpty()) {
            return;
        }
        for (GrowthBookPlugin plugin : plugins) {
            try {
                plugin.close();
            } catch (Exception e) {
                log.warn("Plugin {} close failed",
                        plugin.getClass().getName(), e);
            }
        }
    }
}
