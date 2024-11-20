package growthbook.sdk.java;

import java.util.*;
import javax.annotation.Nullable;
import com.google.gson.JsonObject;
import growthbook.sdk.java.multiusermode.configurations.EvaluationContext;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.multiusermode.usage.TrackingCallbackWithUser;
import lombok.extern.slf4j.Slf4j;

/**
 * <b>INTERNAL</b>: Implementation of experiment evaluation
 */
@Slf4j
public class ExperimentEvaluator implements IExperimentEvaluator {

    private final ConditionEvaluator conditionEvaluator = new ConditionEvaluator();
    private final GrowthBookJsonUtils jsonUtils = GrowthBookJsonUtils.getInstance();
    private final Set<String> trackedExperiments = new HashSet<>();

    /**
     * Takes Context, Experiment and returns Experiment Result
     *
     * @param experiment         Experiment
     * @param context            EvaluationContext
     * @param featureId          String(can be null)
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
        Map<String, Integer> forcedVariations = context.getUser().getForcedVariationsMap();
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
            stickyBucketVersionIsBlocked = stickyBucketVariation.getVersionIsBlocked() != null ? stickyBucketVariation.getVersionIsBlocked() : false;
        }

        /*JsonObject attributes = context.getAttributes();
        if (attributes == null) {
            attributes = new JsonObject();
        }*/

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
                for (ParentCondition parentCondition : parenConditions) {

                    UserContext user = UserContext.builder()
                            .attributes(jsonUtils.gson.fromJson(parentCondition.getCondition(), JsonObject.class))
                            .build();

                    EvaluationContext subContext = new EvaluationContext(context.getGlobal(), user, context.getStack(), context.getOptions());

                    FeatureResult<ValueType> parentResult = new FeatureEvaluator().evaluateFeature(
                            parentCondition.getId(),
                            subContext,
                            null
                            // TODO:M Created a subContext. Verify the behavior!
//                            jsonUtils.gson.fromJson(
//                                    parentCondition.getCondition(),
//                                    JsonObject.class
//                            )
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
            assignments.put(GrowthBookUtils
                    .getStickyBucketExperimentKey(experiment.getKey(), experiment.getBucketVersion()), result.getKey());

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
                context.getOptions().getStickyBucketService().saveAssignments(docModel.getStickyAssignmentsDocument());
            }
        }

        // Fire context.trackingClosure if set and the combination of hashAttribute,
        // hashValue, experiment.key, and variationId has not been tracked before
        if (!isExperimentTracked(experiment, result)) {
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

        if (experiment.meta != null) {
            experimentMeta = experiment.meta;
        }

        VariationMeta meta = null;
        if (experimentMeta.size() > targetVariationIndex) {
            meta = experimentMeta.get(targetVariationIndex);
        }

        String key = meta != null ? meta.getKey() : targetVariationIndex + "";
        String name = meta != null ? meta.getName() : null;
        Boolean passThrough = meta != null ? meta.getPassThrough() : null;

        ValueType targetValue = null;
        if (experiment.variations.size() > targetVariationIndex) {
            targetValue = experiment.variations.get(targetVariationIndex);
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

    private <ValueType> boolean isExperimentTracked(Experiment<ValueType> experiment, ExperimentResult<ValueType> result) {
        // TODO:M Always returns false? -- What's the point?
        String experimentKey = experiment.key;

        String key = (
                result.hashAttribute != null ? result.getHashAttribute() : "")
                + (result.getHashValue() != null ? result.getHashValue() : "")
                + (experimentKey + result.getVariationId());

        if (trackedExperiments.contains(key)) {
            return false;
        }
        trackedExperiments.add(key);
        return false;
    }

    private <ValueType> boolean isStickyBucketingEnabledForExperiment(EvaluationContext context,
                                                                      Experiment<ValueType> experiment) {
        return context.getOptions().getStickyBucketService() != null
                && !Boolean.TRUE.equals(experiment.disableStickyBucketing);
    }
}
