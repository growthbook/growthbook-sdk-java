package growthbook.sdk.java.evaluators;

import com.google.gson.JsonObject;
import growthbook.sdk.java.model.GeneratedStickyBucketAssignmentDocModel;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import growthbook.sdk.java.util.GrowthBookUtils;
import growthbook.sdk.java.model.HashAttributeAndHashValue;
import growthbook.sdk.java.model.Namespace;
import growthbook.sdk.java.model.ParentCondition;
import growthbook.sdk.java.model.BucketRange;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.FeatureResultSource;
import growthbook.sdk.java.model.Filter;
import growthbook.sdk.java.model.StickyBucketVariation;
import growthbook.sdk.java.model.VariationMeta;
import growthbook.sdk.java.multiusermode.ExperimentTracker;
import growthbook.sdk.java.multiusermode.configurations.EvaluationContext;
import growthbook.sdk.java.multiusermode.usage.TrackingCallbackWithUser;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.*;

/**
 * <b>INTERNAL</b>: Implementation of experiment evaluation
 */
@Slf4j
public class ExperimentEvaluator implements IExperimentEvaluator {

    private final ConditionEvaluator conditionEvaluator = new ConditionEvaluator();

    /**
     * Takes Context, Experiment and returns Experiment Result
     *
     * @param experiment Experiment
     * @param context    EvaluationContext
     * @param featureId  String(can be null)
     * @return ExperimentResult
     */
    @Override
    public <ValueType> ExperimentResult<ValueType> evaluateExperiment(Experiment<ValueType> experiment,
                                                                      EvaluationContext context,
                                                                      @Nullable String featureId) {

        // If less than 2 variations, return immediately (not in experiment, variation 0)
        ArrayList<ValueType> experimentVariations = experiment.getVariations();
        if (experimentVariations == null) {
            experimentVariations = new ArrayList<>();
        }

        // If not enabled, return immediately (not in experiment, variation 0)
        Boolean isEnabled = Optional.ofNullable(context.getOptions().getEnabled()).orElse(Boolean.FALSE);
        if (!isEnabled || experimentVariations.size() < 2) {
            return getExperimentResult(context, experiment, -1, false, featureId, null, null);
        }

        // Query string overrides
        Integer override = GrowthBookUtils.getQueryStringOverride(experiment.getKey(), context.getOptions().getUrl(), experimentVariations.size());
        if (override != null) {
            return getExperimentResult(context, experiment, override, false, featureId, null, null);
        }

        // If no forced variation, not in experiment, variation 0
        Map<String, Integer> forcedVariations = getForcedVariations(context);
        if (forcedVariations == null) {
            forcedVariations = new HashMap<>();
        }

        // If context.forcedVariations[experiment.trackingKey] is defined,
        // return immediately (not in experiment, forced variation)
        Integer forcedVariation = forcedVariations.get(experiment.getKey());
        if (forcedVariation != null) {
            return getExperimentResult(context, experiment, forcedVariation, false, featureId, null, null);
        }

        // If experiment is not active, not in experiment, variation 0
        if (experiment.getIsActive() != null && !experiment.getIsActive()) {
            return getExperimentResult(context, experiment, -1, false, featureId, null, null);
        }

        String fallBack = null;
        if (isStickyBucketingEnabledForExperiment(context, experiment)) {
            fallBack = experiment.getFallbackAttribute();
        }

        // Get the user hash attribute and value
        // (context.attributes[experiment.hashAttribute || "id"])
        // and if empty, return immediately (not in experiment, variationId 0)
        HashAttributeAndHashValue hashAttribute = GrowthBookUtils.getHashAttribute(
                experiment.getHashAttribute(),
                fallBack,
                context.getUser().getAttributes()
        );

        // Skip because missing hashAttribute
        if (hashAttribute.getHashValue().isEmpty() || hashAttribute.getHashValue().equals("null")) {
            return getExperimentResult(context, experiment, -1, false, featureId, null, null);
        }

        int assigned = -1;
        boolean foundStickyBucket = false;
        boolean stickyBucketVersionIsBlocked = false;

        if (isStickyBucketingEnabledForExperiment(context, experiment)) {
            int bucketVersion = experiment.getBucketVersion() != null ? experiment.getBucketVersion() : 0;
            int minBucketVersion = experiment.getMinBucketVersion() != null ? experiment.getMinBucketVersion() : 0;
            List<VariationMeta> meta = experiment.getMeta() != null ? experiment.getMeta() : new ArrayList<>();
            StickyBucketVariation stickyBucketVariation = GrowthBookUtils.getStickyBucketVariation(
                    context,
                    experiment.getKey(),
                    experiment.getHashAttribute(),
                    experiment.getFallbackAttribute(),
                    bucketVersion,
                    minBucketVersion,
                    meta
            );

            foundStickyBucket = stickyBucketVariation.getVariation() >= 0;
            assigned = stickyBucketVariation.getVariation();
            stickyBucketVersionIsBlocked = Boolean.TRUE.equals(stickyBucketVariation.getVersionIsBlocked());
        }

        // Some checks are not needed if we already have a sticky bucket
        if (!foundStickyBucket) {

            List<Filter> filters = experiment.getFilters();
            @Deprecated
            Namespace namespace = experiment.getNamespace();

            if (filters != null) {

                // Exclude if user is filtered out (used to be called "namespace")
                if (GrowthBookUtils.isFilteredOut(filters, context.getUser().getAttributes())) {
                    return getExperimentResult(context, experiment, -1, false, featureId, null, null);
                }
            } else if (namespace != null) {

                // If experiment namespace is set, check if the hash value is included in the range, and if not
                // user is not in the experiment, variation 0.
                Boolean isInNamespace = GrowthBookUtils.inNameSpace(hashAttribute.getHashValue(), namespace);
                if (!isInNamespace) {
                    return getExperimentResult(context, experiment, -1, false, featureId, null, null);
                }
            }

            // Evaluate the condition JSON
            // can it be instead JsonObject
            JsonObject conditionJson = experiment.getConditionJson();
            if (conditionJson != null) {
                Boolean shouldEvaluate = conditionEvaluator.evaluateCondition(context.getUser().getAttributes(), conditionJson, context.getGlobal().getSavedGroups());

                // If experiment.condition is set and the condition evaluates to false,
                // return immediately (not in experiment, variationId 0)
                if (!shouldEvaluate) {
                    return getExperimentResult(context, experiment, -1, false, featureId, null, null);
                }
            }

            // 10. Exclude if prerequisites are not met
            List<ParentCondition> parenConditions = experiment.getParentConditions();
            if (parenConditions != null) {
                final Set<String> evaluatedFeatures = new HashSet<>(context.getStack().getEvaluatedFeatures());

                for (ParentCondition parentCondition : parenConditions) {
                    context.getStack().setEvaluatedFeatures(new HashSet<>(evaluatedFeatures));

                    FeatureResult<ValueType> parentResult = new FeatureEvaluator().evaluateFeature(
                            parentCondition.getId(),
                            context,
                            null
                    );

                    if (parentResult.getSource() != null) {
                        if (parentResult.getSource().equals(FeatureResultSource.CYCLIC_PREREQUISITE)) {
                            return getExperimentResult(context, experiment, -1, false, featureId, null, null);
                        }
                    }

                    Map<String, Object> evalObj = new HashMap<>();
                    if (parentResult.getValue() != null) {
                        evalObj.put("value", parentResult.getValue());
                    }
                    JsonObject attributesJson = GrowthBookJsonUtils.getInstance().gson.toJsonTree(evalObj).getAsJsonObject();

                    boolean evalCondition = conditionEvaluator.evaluateCondition(
                            attributesJson,
                            parentCondition.getCondition(),
                            context.getGlobal().getSavedGroups()
                    );

                    // blocking prerequisite eval failed: feature evaluation fails
                    if (!evalCondition) {
                        log.info("Feature blocked by prerequisite");
                        return getExperimentResult(context, experiment, -1, false, featureId, null, null);
                    }
                }
            }
        }


        String seed = experiment.getSeed();
        if (seed == null) {
            seed = experiment.getKey();
        }
        Integer hashVersion = experiment.getHashVersion();
        if (hashVersion == null) {
            hashVersion = 1;
        }
        Float hash = GrowthBookUtils.hash(hashAttribute.getHashValue(), hashVersion, seed);

        // Get the variation from the sticky bucket or get bucket ranges and choose variation
        if (hash == null) {
            return getExperimentResult(context, experiment, -1, false, featureId, null, null);
        }

        if (!foundStickyBucket) {
            // Set default variation weights and coverage if not set
            // Weights
            ArrayList<Float> weights = experiment.getWeights();
            if (weights == null) {
                weights = GrowthBookUtils.getEqualWeights(experiment.getVariations().size());
            }

            // Coverage
            Float coverage = experiment.getCoverage();
            if (coverage == null) {
                coverage = 1.0f;
            }

            // Bucket ranges
            ArrayList<BucketRange> bucketRanges = experiment.getRanges();
            if (bucketRanges == null) {
                bucketRanges = GrowthBookUtils.getBucketRanges(
                        experiment.getVariations().size(),
                        coverage,
                        weights
                );
            }

            assigned = GrowthBookUtils.chooseVariation(hash, bucketRanges);

        }

        // Unenroll if any prior sticky buckets are blocked by version
        if (stickyBucketVersionIsBlocked) {
            return getExperimentResult(context, experiment, -1, false, featureId, null, true);
        }

        // Assigned variations
        // If not assigned a variation (-1), not in experiment, variation 0
        if (assigned < 0) {
            // NOTE: While a hash is used to determine if the user is assigned a variation, since they aren't, hash passed is null
            return getExperimentResult(context, experiment, -1, false, featureId, null, null);
        }

        // If experiment has a forced index, not in experiment, variation is the forced experiment
        Integer force = experiment.getForce();
        if (force != null) {
            return getExperimentResult(context, experiment, force, false, featureId, null, null);
        }

        // If QA mode is enabled, not in experiment, variation 0
        if (Boolean.TRUE.equals(context.getOptions().getIsQaMode())) {
            return getExperimentResult(context, experiment, -1, false, featureId, null, null);
        }

        // User is in an experiment.
        // Call the tracking callback with the result.
        ExperimentResult<ValueType> result = getExperimentResult(context, experiment, assigned, true, featureId, hash, foundStickyBucket);

        // Persist sticky bucket
        if (isStickyBucketingEnabledForExperiment(context, experiment)) {

            Map<String, String> assignments = new HashMap<>();
            assignments.put(
                    GrowthBookUtils.getStickyBucketExperimentKey(
                            experiment.getKey(),
                            experiment.getBucketVersion()),
                    result.getKey()
            );

            GeneratedStickyBucketAssignmentDocModel docModel = GrowthBookUtils.generateStickyBucketAssignmentDoc(
                    context.getUser().getStickyBucketAssignmentDocs(),
                    hashAttribute.getHashAttribute(),
                    hashAttribute.getHashValue(),
                    assignments);

            if (docModel.isChanged()) {

                // update local docs
                if (context.getUser().getStickyBucketAssignmentDocs() == null) {
                    context.getUser().setStickyBucketAssignmentDocs(new HashMap<>());
                }

                context.getUser().getStickyBucketAssignmentDocs().put(
                        docModel.getKey(),
                        docModel.getStickyAssignmentsDocument()
                );

                // save doc
                if (context.getOptions().getStickyBucketService() != null) {
                    context.getOptions().getStickyBucketService().saveAssignments(docModel.getStickyAssignmentsDocument());
                }
            }
        }

        // Fire context.trackingClosure if set and the combination of hashAttribute,
        // hashValue, experiment.key, and variationId has not been tracked before
        if (!context.getOptions().getExperimentHelper().isTracked(experiment, result)) {
            TrackingCallbackWithUser trackingCallBackWithUser = context.getOptions().getTrackingCallBackWithUser();

            if (trackingCallBackWithUser != null) {
                trackingCallBackWithUser.onTrack(experiment, result, context.getUser());
            }
        }

        // Return (in experiment, assigned variation)
        return result;
    }

    private <ValueType> ExperimentResult<ValueType> getExperimentResult(
            EvaluationContext context,
            Experiment<ValueType> experiment,
            Integer variationIndex,
            Boolean hashUsed,
            String featureId,
            @Nullable Float hashBucket,
            @Nullable Boolean stickyBucketUsed
    ) {
        boolean inExperiment = true;
        Integer targetVariationIndex = variationIndex;

        ArrayList<ValueType> experimentVariations = experiment.getVariations();
        if (experimentVariations == null) {
            experimentVariations = new ArrayList<>();
        }

        // If assigned variation is not valid, use the baseline
        // and mark the user as not in the experiment
        if (targetVariationIndex < 0 || targetVariationIndex >= experimentVariations.size()) {
            // Set to 0
            targetVariationIndex = 0;
            inExperiment = false;
        }

        String fallBack = null;
        if (isStickyBucketingEnabledForExperiment(context, experiment)) {
            fallBack = experiment.getFallbackAttribute();
        }
        HashAttributeAndHashValue hashAttribute = GrowthBookUtils.getHashAttribute(
                experiment.getHashAttribute(),
                fallBack,
                context.getUser().getAttributes());

        List<VariationMeta> experimentMeta = new ArrayList<>();

        if (experiment.getMeta() != null) {
            experimentMeta = experiment.getMeta();
        }

        VariationMeta meta = null;
        if (experimentMeta.size() > targetVariationIndex) {
            meta = experimentMeta.get(targetVariationIndex);
        }

        String key = meta != null ? meta.getKey() : targetVariationIndex + "";
        String name = meta != null ? meta.getName() : null;
        Boolean passThrough = meta != null ? meta.getPassThrough() : null;

        ValueType targetValue = null;
        if (experiment.getVariations().size() > targetVariationIndex) {
            targetValue = experiment.getVariations().get(targetVariationIndex);
        }

        return ExperimentResult
                .<ValueType>builder()
                .inExperiment(inExperiment)
                .variationId(variationIndex)
                .value(targetValue)
                .hashAttribute(hashAttribute.getHashAttribute())
                .hashValue(hashAttribute.getHashValue())
                .key(key)
                .featureId(featureId)
                .hashUsed(hashUsed)
                .stickyBucketUsed(stickyBucketUsed != null ? stickyBucketUsed : false)
                .name(name)
                .bucket(hashBucket)
                .passThrough(passThrough)
                .build();
    }

    private <ValueType> boolean isStickyBucketingEnabledForExperiment(EvaluationContext context,
                                                                      Experiment<ValueType> experiment) {
        return context.getOptions().getStickyBucketService() != null
                && !Boolean.TRUE.equals(experiment.getDisableStickyBucketing());
    }

    private Map<String, Integer> getForcedVariations(EvaluationContext evaluationContext) {
        Map<String, Integer> globalForcedVariations = evaluationContext.getGlobal() != null
                ? evaluationContext.getGlobal().getForcedVariations()
                : Collections.emptyMap();

        Map<String, Integer> userForcedVariations = evaluationContext.getUser() != null
                ? evaluationContext.getUser().getForcedVariationsMap()
                : Collections.emptyMap();

        return GrowthBookUtils.mergeMaps(Arrays.asList(globalForcedVariations, userForcedVariations));
    }
}
