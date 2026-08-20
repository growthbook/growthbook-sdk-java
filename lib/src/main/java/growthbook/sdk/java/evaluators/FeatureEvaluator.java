package growthbook.sdk.java.evaluators;

import com.google.gson.JsonObject;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import growthbook.sdk.java.util.GrowthBookUtils;
import growthbook.sdk.java.model.ParentCondition;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.Feature;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.FeatureResultSource;
import growthbook.sdk.java.model.FeatureRule;
import growthbook.sdk.java.multiusermode.configurations.EvaluationContext;
import growthbook.sdk.java.multiusermode.usage.FeatureUsageCallbackWithUser;
import growthbook.sdk.java.plugin.PluginRegistry;
import lombok.extern.slf4j.Slf4j;
import javax.annotation.Nullable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;

/**
 * <b>INTERNAL</b>: Implementation of feature evaluation.
 */
@Slf4j
public class FeatureEvaluator implements IFeatureEvaluator {

    private static final Object NO_FORCED_FEATURE_VALUE = new Object();

    private final ConditionEvaluator conditionEvaluator = new ConditionEvaluator();
    private final GrowthBookJsonUtils jsonUtils = GrowthBookJsonUtils.getInstance();
    private final ExperimentEvaluator experimentEvaluator = new ExperimentEvaluator();

    /**
     * Evaluates a batch of features against a single, shared {@link EvaluationContext}.
     * The context (and the {@code GlobalContext} it references) is reused for every key
     * instead of being rebuilt per feature, and the per-evaluation stack is reset between
     * features so evaluation state does not leak. A feature whose evaluation throws is
     * recorded as {@link FeatureResultSource#UNKNOWN_FEATURE} rather than aborting the batch.
     */
    @Override
    public <T> Map<String, FeatureResult<T>> evaluateFeatures(
            List<String> featureKeys,
            EvaluationContext context,
            Class<T> valueTypeClass
    ) {
        Map<String, FeatureResult<T>> results = new HashMap<>();
        if (featureKeys == null || featureKeys.isEmpty()) {
            return results;
        }
        for (String key : featureKeys) {
            try {
                results.put(key, evaluateFeature(key, context, valueTypeClass));
            } catch (RuntimeException e) {
                log.error("Error evaluating feature '{}' in batch", key, e);
                results.put(key, FeatureResult.<T>builder()
                        .value(null)
                        .source(FeatureResultSource.UNKNOWN_FEATURE)
                        .build());
            } finally {
                context.setStack(new EvaluationContext.StackContext());
            }
        }
        return results;
    }

    @Override
    public <T> FeatureResult<T> evaluateFeature(
            String key,
            EvaluationContext context,
            Class<T> valueTypeClass
    ) throws ClassCastException {
        FeatureResult<T> unknownFeatureResult = FeatureResult
                .<T>builder()
                .value(null)
                .source(FeatureResultSource.UNKNOWN_FEATURE)
                .build();

        try {
            if (context.getStack().getEvaluatedFeatures().contains(key)) {
                return handleCircularDependency(key, context);
            }

            FeatureResult<?> memoizedResult = context.getStack().getMemoizedResults().get(key);
            if (memoizedResult != null) {
                return (FeatureResult<T>) memoizedResult;
            }
            addFeatureToEvalStack(key, context);

            FeatureResult<T> override = resolveForcedOverride(key, context);
            if (override != null) {
                return cacheResult(key, override, context);
            }

            FeatureResult<T> urlOverride = resolveUrlOverride(key, context, valueTypeClass);
            if (urlOverride != null) {
                return cacheResult(key, urlOverride, context);
            }

            Map<String, Feature<?>> features = context.getGlobal().getFeatures();
            if (features == null || features.isEmpty() || !features.containsKey(key)) {
                dispatchFeatureUsage(context, key, unknownFeatureResult);
                return cacheResult(key, unknownFeatureResult, context);
            }

            Feature<T> feature = (Feature<T>) features.get(key);
            if (feature == null) {
                FeatureResult<T> nullFeatureResult = FeatureResult
                        .<T>builder()
                        .value(null)
                        .source(FeatureResultSource.DEFAULT_VALUE)
                        .build();
                dispatchFeatureUsage(context, key, nullFeatureResult);
                return cacheResult(key, nullFeatureResult, context);
            }

            if (feature.getRules() == null || feature.getRules().isEmpty()) {
                return cacheResult(key, defaultValueResult(feature, key, context), context);
            }

            FeatureResult<T> ruleResult = evaluateRules(feature, key, context, valueTypeClass);
            if (ruleResult != null) {
                return cacheResult(key, ruleResult, context);
            }

            return cacheResult(key, defaultValueResult(feature, key, context), context);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return cacheResult(key, unknownFeatureResult, context);
        }
    }

    private <T> FeatureResult<T> handleCircularDependency(String key, EvaluationContext context) {
        log.info(
                "evaluateFeature: circular dependency detected: {} -> {}. { from: {}, to: {} }",
                context.getStack().getId(), key,
                context.getStack().getId(), key
        );
        FeatureResult<T> cyclicResult = FeatureResult
                .<T>builder()
                .value(null)
                .source(FeatureResultSource.CYCLIC_PREREQUISITE)
                .build();
        dispatchFeatureUsage(context, key, cyclicResult);
        leaveCircularLoop(context);
        return cyclicResult;
    }

    @Nullable
    private <T> FeatureResult<T> resolveForcedOverride(String key, EvaluationContext context) {
        Object forcedFeatureValue = getForcedFeatureValue(key, context);
        if (forcedFeatureValue == NO_FORCED_FEATURE_VALUE) {
            return null;
        }
        T value = (T) GrowthBookJsonUtils.unwrap(forcedFeatureValue);
        log.info("Forced feature override with key: {} and value {}", key, forcedFeatureValue);
        FeatureResult<T> overrideResult = FeatureResult
                .<T>builder()
                .value(value)
                .source(FeatureResultSource.OVERRIDE)
                .build();
        dispatchFeatureUsage(context, key, overrideResult);
        return overrideResult;
    }

    @Nullable
    private <T> FeatureResult<T> resolveUrlOverride(String key, EvaluationContext context, Class<T> valueTypeClass) {
        if (Boolean.FALSE.equals(context.getOptions().getAllowUrlOverrides())) {
            return null;
        }
        T forcedValue = evaluateForcedFeatureValueFromUrl(key, context.getOptions().getUrl(), valueTypeClass);
        if (forcedValue == null) {
            return null;
        }
        FeatureResult<T> urlFeatureResult = FeatureResult
                .<T>builder()
                .value(forcedValue)
                .source(FeatureResultSource.URL_OVERRIDE)
                .build();
        dispatchFeatureUsage(context, key, urlFeatureResult);
        return urlFeatureResult;
    }

    /**
     * Walks the feature's rules in order. Returns the first terminal result a rule produces,
     * or {@code null} if no rule applied (the caller then falls back to the default value).
     */
    @Nullable
    private <T> FeatureResult<T> evaluateRules(Feature<T> feature, String key, EvaluationContext context, Class<T> valueTypeClass) {
        Set<String> evaluatedFeatures = new HashSet<>(context.getStack().getEvaluatedFeatures());
        for (FeatureRule<T> rule : feature.getRules()) {
            FeatureResult<T> result = evaluateRule(rule, key, context, valueTypeClass, evaluatedFeatures);
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
    private <T> FeatureResult<T> evaluateRule(
            FeatureRule<T> rule,
            String key,
            EvaluationContext context,
            Class<T> valueTypeClass,
            Set<String> evaluatedFeatures
    ) {
        PrerequisiteOutcome<T> prerequisites = evaluatePrerequisites(rule, key, context, valueTypeClass, evaluatedFeatures);
        if (prerequisites.terminalResult != null) {
            return prerequisites.terminalResult;
        }
        if (prerequisites.skipRule) {
            return null;
        }
        if (Boolean.TRUE.equals(GrowthBookUtils.isFilteredOut(rule.getFilters(), context.getUser().getAttributes()))) {
            return null;
        }
        if (isForcedRule(rule)) {
            return evaluateForcedRule(rule, key, context);
        }
        return evaluateExperimentRule(rule, key, context);
    }

    private <T> PrerequisiteOutcome<T> evaluatePrerequisites(
            FeatureRule<T> rule,
            String key,
            EvaluationContext context,
            Class<T> valueTypeClass,
            Set<String> evaluatedFeatures
    ) {
        if (rule.getParentConditions() == null) {
            return PrerequisiteOutcome.pass();
        }

        for (ParentCondition parentCondition : rule.getParentConditions()) {
            context.getStack().setEvaluatedFeatures(new HashSet<>(evaluatedFeatures));
            FeatureResult<T> parentResult = evaluateFeature(parentCondition.getId(), context, valueTypeClass);

            if (FeatureResultSource.CYCLIC_PREREQUISITE.equals(parentResult.getSource())) {
                FeatureResult<T> cyclicResult = FeatureResult
                        .<T>builder()
                        .value(null)
                        .source(FeatureResultSource.CYCLIC_PREREQUISITE)
                        .build();
                dispatchFeatureUsage(context, key, cyclicResult);
                return PrerequisiteOutcome.terminal(cyclicResult);
            }

            Map<String, Object> evalObj = new HashMap<>();
            if (parentResult.getValue() != null) {
                evalObj.put("value", parentResult.getValue());
            }
            JsonObject parentAttributesJson = GrowthBookJsonUtils.getInstance().gson.toJsonTree(evalObj).getAsJsonObject();

            boolean evalCondition = conditionEvaluator.evaluateCondition(
                    parentAttributesJson,
                    parentCondition.getCondition(),
                    context.getGlobal().getSavedGroups()
            );

            if (!evalCondition) {
                if (Boolean.TRUE.equals(parentCondition.getGate())) {
                    log.info("Feature blocked by prerequisite");
                    FeatureResult<T> blockedResult = FeatureResult
                            .<T>builder()
                            .value(null)
                            .source(FeatureResultSource.PREREQUISITE)
                            .build();
                    dispatchFeatureUsage(context, key, blockedResult);
                    return PrerequisiteOutcome.terminal(blockedResult);
                }
                return PrerequisiteOutcome.skip();
            }
        }
        return PrerequisiteOutcome.pass();
    }

    private <T> boolean isForcedRule(FeatureRule<T> rule) {
        return rule.getForce() != null && rule.getForce().isPresent();
    }

    /**
     * Evaluates a force rule. Returns the forced result, or {@code null} when the rule is
     * skipped (its condition didn't match or the user isn't in the rollout). Remote-eval
     * tracking callbacks are fired via {@link ExperimentEvaluator#fireRemoteEvaluationTracks}
     * (de-duplicated per assignment) before the forced value is returned.
     */
    @Nullable
    private <T> FeatureResult<T> evaluateForcedRule(FeatureRule<T> rule, String key, EvaluationContext context) {
        if (rule.getCondition() != null
                && !conditionEvaluator.evaluateCondition(context.getUser().getAttributes(),
                rule.getCondition(), context.getGlobal().getSavedGroups())) {
            return null;
        }

        boolean stickyBucketingEnabled = context.getOptions().getStickyBucketService() != null
                && !Boolean.TRUE.equals(rule.getDisableStickyBucketing());
        String fallback = stickyBucketingEnabled ? rule.getFallbackAttribute() : null;

        String ruleKey = rule.getHashAttribute() != null ? rule.getHashAttribute() : "id";
        String seed = rule.getSeed() != null ? rule.getSeed() : key;

        if (Boolean.FALSE.equals(GrowthBookUtils.isIncludedInRollout(
                context.getUser().getAttributes(),
                seed,
                ruleKey,
                fallback,
                rule.getRange(),
                rule.getCoverage(),
                rule.getHashVersion()
        ))) {
            return null;
        }

        experimentEvaluator.fireRemoteEvaluationTracks(rule.getTracks(), context);

        T value = (T) GrowthBookJsonUtils.unwrap(rule.getForce().getValue());
        FeatureResult<T> forcedRuleFeatureValue = FeatureResult.<T>builder()
                .value(value)
                .source(FeatureResultSource.FORCE)
                .ruleId(rule.getId())
                .build();
        dispatchFeatureUsage(context, key, forcedRuleFeatureValue);
        return forcedRuleFeatureValue;
    }

    /**
     * Evaluates an experiment rule. Returns the experiment result when the user is bucketed
     * in (and not passed through), or {@code null} when the rule doesn't apply.
     */
    @Nullable
    private <T> FeatureResult<T> evaluateExperimentRule(FeatureRule<T> rule, String key, EvaluationContext context) {
        ArrayList<T> variations = rule.getVariations();
        if (variations == null) {
            return null;
        }

        String experimentKey = rule.getKey() != null ? rule.getKey() : key;
        Experiment<T> experiment = Experiment
                .<T>builder()
                .key(experimentKey)
                .variations(variations)
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

        ExperimentResult<T> result = experimentEvaluator.evaluateExperiment(experiment, context, key);
        boolean inExperiment = result.getInExperiment()
                && (result.getPassThrough() == null || !result.getPassThrough());
        if (!inExperiment) {
            return null;
        }

        T value = (T) GrowthBookJsonUtils.unwrap(result.getValue());
        FeatureResult<T> experimentFeatureResult = FeatureResult
                .<T>builder()
                .value(value)
                .ruleId(rule.getId())
                .source(FeatureResultSource.EXPERIMENT)
                .experiment(experiment)
                .experimentResult(result)
                .build();
        dispatchFeatureUsage(context, key, experimentFeatureResult);
        return experimentFeatureResult;
    }

    private <T> FeatureResult<T> defaultValueResult(Feature<T> feature, String key, EvaluationContext context) {
        T value = (T) GrowthBookJsonUtils.unwrap(feature.getDefaultValue());
        FeatureResult<T> defaultValueFeatureResult = FeatureResult
                .<T>builder()
                .source(FeatureResultSource.DEFAULT_VALUE)
                .value(value)
                .build();
        dispatchFeatureUsage(context, key, defaultValueFeatureResult);
        return defaultValueFeatureResult;
    }

    private <T> void dispatchFeatureUsage(EvaluationContext context, String key, FeatureResult<T> result) {
        FeatureUsageCallbackWithUser featureUsageCallbackWithUser = context.getOptions().getFeatureUsageCallbackWithUser();
        if (featureUsageCallbackWithUser != null) {
            featureUsageCallbackWithUser.onFeatureUsage(key, result, context.getUser());
        }
        PluginRegistry registry = context.getPluginRegistry();
        if (registry != null) {
            registry.fireFeatureEvaluated(key, result);
        }
    }

    private @Nullable <T> T evaluateForcedFeatureValueFromUrl(String key, @Nullable String urlString, Class<T> valueTypeClass) {
        if (urlString == null) return null;

        try {
            URL url = new URL(urlString);

            if (valueTypeClass.equals(Boolean.class)) {
                return (T) GrowthBookUtils.getForcedBooleanValueFromUrl(key, url);
            }

            if (valueTypeClass.equals(String.class)) {
                return (T) GrowthBookUtils.getForcedStringValueFromUrl(key, url);
            }

            if (valueTypeClass.equals(Integer.class)) {
                return (T) GrowthBookUtils.getForcedIntegerValueFromUrl(key, url);
            }

            if (valueTypeClass.equals(Float.class)) {
                return (T) GrowthBookUtils.getForcedFloatValueFromUrl(key, url);
            }

            if (valueTypeClass.equals(Double.class)) {
                return (T) GrowthBookUtils.getForcedDoubleValueFromUrl(key, url);
            }

            return GrowthBookUtils.getForcedSerializableValueFromUrl(key, url, valueTypeClass, jsonUtils.gson);
        } catch (MalformedURLException | ClassCastException e) {
            log.error(e.getMessage(), e);
            return null;
        }
    }

    private void leaveCircularLoop(EvaluationContext context) {
        context.getStack().setId(null);
        context.getStack().getEvaluatedFeatures().clear();
        context.getStack().getMemoizedResults().clear();
    }

    private void addFeatureToEvalStack(String featureKey, EvaluationContext context) {
        context.getStack().setId(featureKey);
        context.getStack().getEvaluatedFeatures().add(featureKey);
    }

    private <T> FeatureResult<T> cacheResult(String key, FeatureResult<T> result, EvaluationContext context) {
        context.getStack().getMemoizedResults().putIfAbsent(key, result);
        return result;
    }

    private Object getForcedFeatureValue(String key, EvaluationContext evaluationContext) {
        Map<String, Object> userFeatures = evaluationContext.getUser() != null
                ? evaluationContext.getUser().getForcedFeatureValues()
                : null;
        if (userFeatures != null && userFeatures.containsKey(key)) {
            return userFeatures.get(key);
        }

        Map<String, Object> globalFeatures = evaluationContext.getGlobal() != null
                ? evaluationContext.getGlobal().getForcedFeatureValues()
                : null;
        if (globalFeatures != null && globalFeatures.containsKey(key)) {
            return globalFeatures.get(key);
        }

        return NO_FORCED_FEATURE_VALUE;
    }

    /**
     * Outcome of evaluating a rule's prerequisites: either produce a terminal result, skip
     * this rule (non-blocking prerequisite failed), or pass (proceed with the rule).
     */
    private static final class PrerequisiteOutcome<T> {

        private final boolean skipRule;

        @Nullable
        private final FeatureResult<T> terminalResult;

        private PrerequisiteOutcome(@Nullable FeatureResult<T> terminalResult, boolean skipRule) {
            this.terminalResult = terminalResult;
            this.skipRule = skipRule;
        }

        static <T> PrerequisiteOutcome<T> pass() {
            return new PrerequisiteOutcome<>(null, false);
        }

        static <T> PrerequisiteOutcome<T> skip() {
            return new PrerequisiteOutcome<>(null, true);
        }

        static <T> PrerequisiteOutcome<T> terminal(FeatureResult<T> result) {
            return new PrerequisiteOutcome<>(result, false);
        }
    }
}
