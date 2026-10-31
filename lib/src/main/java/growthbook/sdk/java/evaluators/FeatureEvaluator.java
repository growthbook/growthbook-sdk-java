package growthbook.sdk.java.evaluators;

import growthbook.sdk.java.evaluators.internal.Evaluation;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.FeatureResultSource;
import growthbook.sdk.java.multiusermode.configurations.EvaluationContext;

import java.util.List;
import java.util.Map;

/**
 * <b>INTERNAL</b>: Implementation of feature evaluation.
 *
 * <p>Stateless and safe for concurrent use: each call runs in its own {@link Evaluation}.
 */
public class FeatureEvaluator implements IFeatureEvaluator {

    /**
     * Evaluates a batch of features against a single, shared {@link EvaluationContext}.
     * Evaluation state is reset between features so it does not leak. A feature whose
     * evaluation throws is recorded as {@link FeatureResultSource#UNKNOWN_FEATURE} rather than
     * aborting the batch.
     * <p>
     * Each distinct key is evaluated exactly once (so feature-usage callbacks fire once per
     * key) and the returned map preserves the order of {@code featureKeys}.
     */
    @Override
    public <T> Map<String, FeatureResult<T>> evaluateFeatures(
            List<String> featureKeys,
            EvaluationContext context,
            Class<T> valueTypeClass
    ) {
        return new Evaluation(context).evaluateFeatures(featureKeys, valueTypeClass);
    }

    @Override
    public <T> FeatureResult<T> evaluateFeature(
            String key,
            EvaluationContext context,
            Class<T> valueTypeClass
    ) throws ClassCastException {
        return new Evaluation(context).evaluateFeature(key, valueTypeClass);
    }
}
