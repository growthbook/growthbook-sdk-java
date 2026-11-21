package growthbook.sdk.java.evaluators.internal;

import growthbook.sdk.java.model.FeatureResult;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * The state of one evaluation call: the chain of features being evaluated, to detect
 * prerequisite cycles, and the results already computed. Not thread-safe; owned by one
 * {@link Evaluation}.
 */
final class EvalStack {

    @Nullable
    private String currentFeature;
    private Set<String> chain = new HashSet<>();
    private final Map<String, FeatureResult<Object>> memoized = new HashMap<>();

    boolean isInChain(String featureKey) {
        return chain.contains(featureKey);
    }

    void enter(String featureKey) {
        currentFeature = featureKey;
        chain.add(featureKey);
    }

    @Nullable
    String currentFeature() {
        return currentFeature;
    }

    /**
     * @return a copy of the chain, to restore before each sibling prerequisite
     */
    Set<String> chain() {
        return new HashSet<>(chain);
    }

    void restoreChain(Set<String> savedChain) {
        chain = new HashSet<>(savedChain);
    }

    @Nullable
    FeatureResult<Object> memoized(String featureKey) {
        return memoized.get(featureKey);
    }

    FeatureResult<Object> memoize(String featureKey, FeatureResult<Object> result) {
        memoized.putIfAbsent(featureKey, result);
        return result;
    }

    /**
     * Forgets everything: after a cycle, so the features around it are evaluated afresh, and
     * between the features of a batch.
     */
    void reset() {
        currentFeature = null;
        chain.clear();
        memoized.clear();
    }
}
