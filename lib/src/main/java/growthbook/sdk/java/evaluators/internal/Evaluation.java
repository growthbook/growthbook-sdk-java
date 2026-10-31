package growthbook.sdk.java.evaluators.internal;

import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.FeatureResultSource;
import growthbook.sdk.java.model.TrackData;
import growthbook.sdk.java.multiusermode.configurations.EvaluationContext;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * One evaluation call over an {@link EvaluationContext}: the root that feature evaluation,
 * experiment evaluation and prerequisite checks share.
 *
 * <p>The call's own state — the prerequisite chain and the memoized results — lives in its
 * {@link EvalStack} and nowhere else, so the public evaluators stay stateless and the stack
 * carried by {@link EvaluationContext} is not used. The context is only read, except for the sticky bucket
 * documents of its {@code UserContext}, which the spec updates so later evaluations for the same
 * user see new assignments.
 *
 * <p>Not thread-safe: create one per call and do not share it between threads.
 */
@Slf4j
public final class Evaluation {

    private final EvaluationEvents events;
    private final EvaluationContext context;
    private final FeatureEvaluation features;
    private final Prerequisites prerequisites;
    private final ExperimentEvaluation experiments;
    private final EvalStack stack = new EvalStack();

    public Evaluation(EvaluationContext context) {
        this.context = context;
        this.events = new EvaluationEvents(context);
        this.features = new FeatureEvaluation(this);
        this.experiments = new ExperimentEvaluation(this);
        this.prerequisites = new Prerequisites(this);
    }

    /**
     * Evaluates one feature.
     *
     * @param key            the feature key
     * @param valueTypeClass the expected value type, used to parse URL overrides
     * @param <T>            the value type
     * @return the feature result; {@link FeatureResultSource#UNKNOWN_FEATURE} when evaluation fails
     */
    public <T> FeatureResult<T> evaluateFeature(String key, Class<T> valueTypeClass) {
        return asType(features.evaluate(key, valueTypeClass));
    }

    /**
     * Evaluates each distinct key once, in the order given, with a fresh stack per feature so
     * evaluation state does not leak between them. A feature whose evaluation throws is recorded
     * as {@link FeatureResultSource#UNKNOWN_FEATURE} rather than aborting the batch.
     *
     * @param featureKeys    the feature keys; {@code null} or empty yields an empty map
     * @param valueTypeClass the expected value type
     * @param <T>            the value type
     * @return results keyed by feature key, in the order of {@code featureKeys}
     */
    public <T> Map<String, FeatureResult<T>> evaluateFeatures(@Nullable List<String> featureKeys, Class<T> valueTypeClass) {
        Map<String, FeatureResult<T>> results = new LinkedHashMap<>();
        if (featureKeys == null || featureKeys.isEmpty()) {
            return results;
        }
        for (String key : featureKeys) {
            if (results.containsKey(key)) {
                continue;
            }
            try {
                results.put(key, evaluateFeature(key, valueTypeClass));
            } catch (RuntimeException e) {
                log.error("Error evaluating feature '{}' in batch", key, e);
                results.put(key, FeatureResult.<T>builder()
                        .value(null)
                        .source(FeatureResultSource.UNKNOWN_FEATURE)
                        .build());
            } finally {
                stack.reset();
            }
        }
        return results;
    }

    /**
     * Runs an experiment. The experiment is only read, never modified.
     *
     * @param experiment the experiment
     * @param featureId  the feature the experiment belongs to, or {@code null} for an inline experiment
     * @param <T>        the variation type
     * @return the experiment result
     */
    public <T> ExperimentResult<T> runExperiment(Experiment<T> experiment, @Nullable String featureId) {
        return experiments.run(experiment, featureId);
    }

    /**
     * Reports exposures that a remote evaluation already decided, de-duplicated like local ones.
     *
     * @param tracks the rule's tracking payload; {@code null} or malformed entries are skipped
     * @param <T>    the variation type
     */
    public <T> void fireRemoteEvaluationTracks(@Nullable List<TrackData<T>> tracks) {
        events.remoteEvaluationTracks(tracks);
    }

    /**
     * The one place where an evaluated value takes the caller's type. Values are not converted:
     * like the public API has always done, a value of another type surfaces as a
     * {@link ClassCastException} where the caller reads it.
     */
    @SuppressWarnings("unchecked")
    private static <T> FeatureResult<T> asType(FeatureResult<Object> result) {
        return (FeatureResult<T>) result;
    }

    EvaluationContext context() {
        return context;
    }

    EvalStack stack() {
        return stack;
    }

    EvaluationEvents events() {
        return events;
    }

    FeatureEvaluation features() {
        return features;
    }

    ExperimentEvaluation experiments() {
        return experiments;
    }

    Prerequisites prerequisites() {
        return prerequisites;
    }
}
