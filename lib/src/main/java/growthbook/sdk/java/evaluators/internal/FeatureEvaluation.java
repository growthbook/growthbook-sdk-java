package growthbook.sdk.java.evaluators.internal;

import growthbook.sdk.java.evaluators.internal.bucketing.Bucketing;
import growthbook.sdk.java.evaluators.internal.condition.Conditions;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.Feature;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.FeatureResultSource;
import growthbook.sdk.java.model.FeatureRule;
import growthbook.sdk.java.model.OptionalField;
import growthbook.sdk.java.model.ParentCondition;
import growthbook.sdk.java.multiusermode.configurations.EvaluationContext;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Feature evaluation in spec order: cycle and memo check, forced value, URL override, unknown
 * feature, then the rules — prerequisites, filters, force rules and experiment rules — and
 * finally the default value. Every result is reported as feature usage and memoized for the
 * rest of the call.
 */
@Slf4j
final class FeatureEvaluation {

    private static final Object NO_FORCED_FEATURE_VALUE = new Object();

    private final Evaluation evaluation;

    FeatureEvaluation(Evaluation evaluation) {
        this.evaluation = evaluation;
    }

    FeatureResult<Object> evaluate(String key, @Nullable Class<?> valueTypeClass) {
        FeatureResult<Object> unknownFeatureResult = result(null, FeatureResultSource.UNKNOWN_FEATURE);

        try {
            if (stack().isInChain(key)) {
                return handleCircularDependency(key);
            }
            FeatureResult<Object> memoizedResult = stack().memoized(key);
            if (memoizedResult != null) {
                return memoizedResult;
            }
            stack().enter(key);
            return stack().memoize(key, resolve(key, valueTypeClass, unknownFeatureResult));
        } catch (RuntimeException e) {
            log.error("Evaluating feature '{}' failed; treating it as unknown.", key, e);
            return stack().memoize(key, unknownFeatureResult);
        }
    }

    private FeatureResult<Object> resolve(String key, @Nullable Class<?> valueTypeClass, FeatureResult<Object> unknownFeatureResult) {
        FeatureResult<Object> override = resolveForcedOverride(key);
        if (override != null) {
            return override;
        }
        FeatureResult<Object> urlOverride = resolveUrlOverride(key, valueTypeClass);
        if (urlOverride != null) {
            return urlOverride;
        }

        Map<String, Feature<?>> features = context().getGlobal().getFeatures();
        if (features == null || !features.containsKey(key)) {
            return report(key, unknownFeatureResult);
        }
        Feature<?> feature = features.get(key);
        if (feature == null) {
            return report(key, result(null, FeatureResultSource.DEFAULT_VALUE));
        }

        FeatureResult<Object> ruleResult = evaluateRules(feature, key, valueTypeClass);
        if (ruleResult != null) {
            return ruleResult;
        }
        return report(key, result(GrowthBookJsonUtils.unwrap(feature.getDefaultValue()), FeatureResultSource.DEFAULT_VALUE));
    }

    private FeatureResult<Object> handleCircularDependency(String key) {
        log.debug(
                "evaluateFeature: circular dependency detected: {} -> {}. { from: {}, to: {} }",
                stack().currentFeature(), key,
                stack().currentFeature(), key
        );
        FeatureResult<Object> cyclicResult = report(key, result(null, FeatureResultSource.CYCLIC_PREREQUISITE));
        stack().reset();
        return cyclicResult;
    }

    @Nullable
    private FeatureResult<Object> resolveForcedOverride(String key) {
        Object forcedFeatureValue = forcedFeatureValue(key);
        if (forcedFeatureValue == NO_FORCED_FEATURE_VALUE) {
            return null;
        }
        log.debug("Forced feature override with key: {} and value {}", key, forcedFeatureValue);
        return report(key, result(GrowthBookJsonUtils.unwrap(forcedFeatureValue), FeatureResultSource.OVERRIDE));
    }

    /**
     * URL overrides are opt-in: they apply only when the flag is explicitly {@code true}.
     * A {@code null} flag (reachable through {@code Options#setAllowUrlOverrides(null)}) is
     * treated as disabled, matching the documented default.
     */
    @Nullable
    private FeatureResult<Object> resolveUrlOverride(String key, @Nullable Class<?> valueTypeClass) {
        if (!Boolean.TRUE.equals(context().getOptions().getAllowUrlOverrides())) {
            return null;
        }
        Object forcedValue = forcedValueFromUrl(key, valueTypeClass);
        if (forcedValue == null) {
            return null;
        }
        return report(key, result(forcedValue, FeatureResultSource.URL_OVERRIDE));
    }

    /**
     * Walks the feature's rules in order. Returns the first terminal result a rule produces,
     * or {@code null} if no rule applied (the caller then falls back to the default value).
     */
    @Nullable
    private FeatureResult<Object> evaluateRules(Feature<?> feature, String key, @Nullable Class<?> valueTypeClass) {
        if (feature.getRules() == null) {
            return null;
        }
        Set<String> chain = stack().chain();
        for (FeatureRule<?> rule : feature.getRules()) {
            FeatureResult<Object> result = evaluateRule(rule, key, valueTypeClass, chain);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    /**
     * Evaluates a single rule. Returns the terminal result the rule produces, or {@code null}
     * when the rule does not apply and evaluation should continue with the next rule.
     */
    @Nullable
    private FeatureResult<Object> evaluateRule(
            FeatureRule<?> rule,
            String key,
            @Nullable Class<?> valueTypeClass,
            Set<String> chain
    ) {
        if (rule.getParentConditions() != null) {
            for (ParentCondition parentCondition : rule.getParentConditions()) {
                Prerequisites.Outcome outcome = evaluation.prerequisites().check(parentCondition, chain, valueTypeClass);
                if (outcome == Prerequisites.Outcome.CYCLIC) {
                    return report(key, result(null, FeatureResultSource.CYCLIC_PREREQUISITE));
                }
                if (outcome == Prerequisites.Outcome.NOT_MET) {
                    return blockedByPrerequisite(parentCondition, key);
                }
            }
        }
        if (Bucketing.isFilteredOut(rule.getFilters(), context().getUser().getAttributes())) {
            return null;
        }
        OptionalField<?> force = rule.getForce();
        if (force != null && force.isPresent()) {
            return evaluateForceRule(rule, force.getValue(), key);
        }
        return evaluateExperimentRule(rule, key);
    }

    /**
     * A failed gating prerequisite blocks the feature; a failed non-gating one only skips the rule.
     */
    @Nullable
    private FeatureResult<Object> blockedByPrerequisite(ParentCondition parentCondition, String key) {
        if (!Boolean.TRUE.equals(parentCondition.getGate())) {
            return null;
        }
        log.debug("Feature {} blocked by prerequisite {}", key, parentCondition.getId());
        return report(key, result(null, FeatureResultSource.PREREQUISITE));
    }

    /**
     * Returns the forced result, or {@code null} when the rule is skipped because its condition
     * didn't match or the user isn't in the rollout. Remote-evaluation tracks are reported before
     * the forced value is returned.
     */
    @Nullable
    private FeatureResult<Object> evaluateForceRule(FeatureRule<?> rule, @Nullable Object forcedValue, String key) {
        EvaluationContext context = context();
        if (rule.getCondition() != null
                && !Conditions.matches(context.getUser().getAttributes(), rule.getCondition(), context.getGlobal().getSavedGroups())) {
            return null;
        }

        boolean stickyBucketingEnabled = context.getOptions().getStickyBucketService() != null
                && !Boolean.TRUE.equals(rule.getDisableStickyBucketing());
        boolean included = Bucketing.isIncludedInRollout(
                context.getUser().getAttributes(),
                rule.getSeed() != null ? rule.getSeed() : key,
                rule.getHashAttribute(),
                stickyBucketingEnabled ? rule.getFallbackAttribute() : null,
                rule.getRange(),
                rule.getCoverage(),
                rule.getHashVersion()
        );
        if (!included) {
            return null;
        }

        evaluation.events().remoteEvaluationTracks(rule.getTracks());

        return report(key, FeatureResult.builder()
                .value(GrowthBookJsonUtils.unwrap(forcedValue))
                .source(FeatureResultSource.FORCE)
                .ruleId(rule.getId())
                .build());
    }

    /**
     * Returns the experiment result when the user is bucketed in and not passed through, or
     * {@code null} when the rule doesn't apply.
     */
    @Nullable
    private FeatureResult<Object> evaluateExperimentRule(FeatureRule<?> rule, String key) {
        List<?> variations = rule.getVariations();
        if (variations == null) {
            return null;
        }

        Experiment<Object> experiment = experimentFor(rule, variations, key);

        ExperimentResult<Object> result = evaluation.experiments().run(experiment, key);
        if (!result.getInExperiment() || Boolean.TRUE.equals(result.getPassThrough())) {
            return null;
        }

        return report(key, FeatureResult.builder()
                .value(GrowthBookJsonUtils.unwrap(result.getValue()))
                .ruleId(rule.getId())
                .source(FeatureResultSource.EXPERIMENT)
                .experiment(experiment)
                .experimentResult(result)
                .build());
    }

    /**
     * The experiment an experiment rule describes. Copies the legacy namespace too: it is
     * deprecated in the model but still part of the spec.
     */
    @SuppressWarnings("deprecation")
    private static Experiment<Object> experimentFor(FeatureRule<?> rule, List<?> variations, String key) {
        return Experiment
                .builder()
                .key(rule.getKey() != null ? rule.getKey() : key)
                .variations(new ArrayList<>(variations))
                .coverage(rule.getCoverage())
                .weights(rule.getWeights())
                .hashAttribute(rule.getHashAttribute())
                .fallbackAttribute(rule.getFallbackAttribute())
                .disableStickyBucketing(rule.getDisableStickyBucketing())
                .bucketVersion(rule.getBucketVersion())
                .minBucketVersion(rule.getMinBucketVersion())
                .namespace(rule.getNamespace())
                .meta(rule.getMeta())
                .ranges(rule.getRanges())
                .name(rule.getName())
                .phase(rule.getPhase())
                .seed(rule.getSeed())
                .hashVersion(rule.getHashVersion())
                .filters(rule.getFilters())
                .conditionJson(rule.getCondition())
                .customFields(rule.getCustomFields())
                .build();
    }

    /**
     * The value forced by a {@code gb~<key>} query parameter, parsed as {@code valueTypeClass}.
     * Without a page URL or a value type there is none.
     */
    @Nullable
    private Object forcedValueFromUrl(String key, @Nullable Class<?> valueTypeClass) {
        String urlString = context().getOptions().getUrl();
        if (urlString == null || valueTypeClass == null) {
            return null;
        }
        try {
            return ForcedValues.getForcedValueFromUrl(key, new URL(urlString), valueTypeClass);
        } catch (MalformedURLException e) {
            log.error(e.getMessage(), e);
            return null;
        }
    }

    private Object forcedFeatureValue(String key) {
        EvaluationContext context = context();
        Map<String, Object> userFeatures = context.getUser() != null
                ? context.getUser().getForcedFeatureValues()
                : null;
        if (userFeatures != null && userFeatures.containsKey(key)) {
            return userFeatures.get(key);
        }

        Map<String, Object> globalFeatures = context.getGlobal() != null
                ? context.getGlobal().getForcedFeatureValues()
                : null;
        if (globalFeatures != null && globalFeatures.containsKey(key)) {
            return globalFeatures.get(key);
        }

        return NO_FORCED_FEATURE_VALUE;
    }

    private FeatureResult<Object> report(String key, FeatureResult<Object> result) {
        evaluation.events().featureEvaluated(key, result);
        return result;
    }

    private static FeatureResult<Object> result(@Nullable Object value, FeatureResultSource source) {
        return FeatureResult.builder()
                .value(value)
                .source(source)
                .build();
    }

    private EvaluationContext context() {
        return evaluation.context();
    }

    private EvalStack stack() {
        return evaluation.stack();
    }
}
