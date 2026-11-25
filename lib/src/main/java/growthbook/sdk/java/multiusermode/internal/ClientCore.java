package growthbook.sdk.java.multiusermode.internal;

import growthbook.sdk.java.callback.ExperimentRunCallback;
import growthbook.sdk.java.evaluators.ExperimentEvaluator;
import growthbook.sdk.java.evaluators.FeatureEvaluator;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.multiusermode.ExperimentTracker;
import growthbook.sdk.java.multiusermode.configurations.EvaluationContext;
import growthbook.sdk.java.plugin.GrowthBookPlugin;
import growthbook.sdk.java.plugin.PluginRegistry;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

/**
 * What {@code GrowthBook} and {@code GrowthBookClient} share per SDK instance: the plugins, the
 * exposure tracker, experiment subscriptions, feature and experiment evaluation, and an ordered
 * teardown. Each facade builds its own {@link EvaluationContext} and passes it in.
 *
 * <p>Thread-safe; how safe a facade is depends on how it builds its contexts.
 */
@Slf4j
public final class ClientCore {

    private final PluginRegistry plugins;
    private final ExperimentTracker tracker;
    private final ExperimentSubscriptionManager subscriptions = new ExperimentSubscriptionManager();
    private final FeatureEvaluator featureEvaluator;
    private final ExperimentEvaluator experimentEvaluator;

    /**
     * @param plugins the instance's plugins, initialized by {@link #startPlugins()}
     */
    public ClientCore(@Nullable List<GrowthBookPlugin> plugins) {
        this(plugins, new ExperimentTracker());
    }

    /**
     * @param plugins the instance's plugins, initialized by {@link #startPlugins()}
     * @param tracker the exposure tracker shared by every evaluation of the instance
     */
    public ClientCore(@Nullable List<GrowthBookPlugin> plugins, ExperimentTracker tracker) {
        this(plugins, tracker, new FeatureEvaluator(), new ExperimentEvaluator());
    }

    /**
     * @param plugins             the instance's plugins, initialized by {@link #startPlugins()}
     * @param featureEvaluator    the feature evaluator, replaceable for tests
     * @param experimentEvaluator the experiment evaluator, replaceable for tests
     */
    public ClientCore(@Nullable List<GrowthBookPlugin> plugins, FeatureEvaluator featureEvaluator, ExperimentEvaluator experimentEvaluator) {
        this(plugins, new ExperimentTracker(), featureEvaluator, experimentEvaluator);
    }

    private ClientCore(@Nullable List<GrowthBookPlugin> plugins, ExperimentTracker tracker,
                       FeatureEvaluator featureEvaluator, ExperimentEvaluator experimentEvaluator) {
        this.plugins = new PluginRegistry(plugins);
        this.tracker = tracker;
        this.featureEvaluator = featureEvaluator;
        this.experimentEvaluator = experimentEvaluator;
    }

    /**
     * Initializes the plugins. A facade calls this last in its constructor, once nothing else can fail,
     * so a constructor that throws never leaves started plugins behind without an instance to close
     * them.
     */
    public void startPlugins() {
        plugins.initAll();
    }

    /**
     * Attaches this instance's plugins and exposure tracker to a context, so events never route
     * through another instance's plugins and every entry point shares one de-duplication.
     *
     * @return the same context
     */
    public EvaluationContext scope(EvaluationContext context) {
        context.setPluginRegistry(plugins);
        context.setExperimentTracker(tracker);
        return context;
    }

    public <T> FeatureResult<T> evalFeature(String key, Class<T> valueTypeClass, EvaluationContext context) {
        return featureEvaluator.evaluateFeature(key, context, valueTypeClass);
    }

    public <T> Map<String, FeatureResult<T>> evalFeatures(List<String> keys, Class<T> valueTypeClass, EvaluationContext context) {
        return featureEvaluator.evaluateFeatures(keys, context, valueTypeClass);
    }

    /**
     * Runs an experiment and notifies subscribers when the assignment changed.
     */
    public <T> ExperimentResult<T> run(Experiment<T> experiment, EvaluationContext context) {
        ExperimentResult<T> result = experimentEvaluator.evaluateExperiment(experiment, context, null);
        subscriptions.publishIfChanged(experiment, result);
        return result;
    }

    public void subscribe(ExperimentRunCallback callback) {
        subscriptions.subscribe(callback);
    }

    public void clearSubscriptions() {
        subscriptions.clear();
    }

    /**
     * Closes the plugins first, so they can flush buffered events while everything else still
     * works, then each of {@code resources} in order. A step that throws is logged and the
     * remaining steps still run.
     */
    public void close(Runnable... resources) {
        closeQuietly("plugins", plugins::closeAll);
        for (Runnable resource : resources) {
            closeQuietly("resource", resource);
        }
    }

    private static void closeQuietly(String what, Runnable close) {
        try {
            close.run();
        } catch (RuntimeException e) {
            log.warn("Closing {} failed; continuing with the rest of the shutdown.", what, e);
        }
    }
}
