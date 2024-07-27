package growthbook.sdk.java;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import com.google.gson.JsonObject;
import lombok.extern.slf4j.Slf4j;

/**
 * <b>INTERNAL</b>: Implementation of experiment evaluation
 */
@Slf4j
class ExperimentEvaluator implements IExperimentEvaluator {

    private final ConditionEvaluator conditionEvaluator = new ConditionEvaluator();
    private final GrowthBookJsonUtils jsonUtils = GrowthBookJsonUtils.getInstance();

    /**
     * Takes Context & Experiment & returns Experiment Result
     *
     * @param experiment         Experiment
     * @param context            GBContext
     * @param featureId          String(can be null)
     * @param attributeOverrides JsonObject
     * @return ExperimentResult
     */
    @Override
    public <ValueType> ExperimentResult<ValueType> evaluateExperiment(Experiment<ValueType> experiment,
                                                                      GBContext context,
                                                                      @Nullable String featureId,
                                                                      JsonObject attributeOverrides) {

        // If less than 2 variations, return immediately (not in experiment, variation 0)
        ArrayList<ValueType> experimentVariations = experiment.getVariations();
        if (experimentVariations == null) {
            experimentVariations = new ArrayList<>();
        }

        // If not enabled, return immediately (not in experiment, variation 0)
        if ((context.getEnabled() != null && !context.getEnabled()) || experimentVariations.size() < 2) {
            return getExperimentResult(context, experiment, -1, false, featureId, null, null, attributeOverrides);
        }

        // Query string overrides
        Integer override = GrowthBookUtils.getQueryStringOverride(experiment.getKey(), context.getUrl(), experimentVariations.size());
        if (override != null) {
            return getExperimentResult(context, experiment, override, false, featureId, null, null, attributeOverrides);
        }

        // If no forced variation, not in experiment, variation 0
        Map<String, Integer> forcedVariations = context.getForcedVariationsMap();
        if (forcedVariations == null) {
            forcedVariations = new HashMap<>();
        }

        // If context.forcedVariations[experiment.trackingKey] is defined,
        // return immediately (not in experiment, forced variation)
        Integer forcedVariation = forcedVariations.get(experiment.getKey());
        if (forcedVariation != null) {
            return getExperimentResult(context, experiment, forcedVariation, false, featureId, null, null, attributeOverrides);
        }

        // If experiment is not active, not in experiment, variation 0
        if (experiment.getIsActive() != null && !experiment.getIsActive()) {
            return getExperimentResult(context, experiment, -1, false, featureId, null, null, attributeOverrides);
        }

        String fallBack = null;
        if (context.getStickyBucketService() != null && !Boolean.TRUE.equals(experiment.disableStickyBucketing)) {
            fallBack = experiment.getFallbackAttribute();
        }

        // Get the user hash attribute and value
        // (context.attributes[experiment.hashAttribute || "id"])
        // and if empty, return immediately (not in experiment, variationId 0)
        HashAttributeAndHashValue hashAttribute = GrowthBookUtils.getHashAttribute(
                context,
                experiment.getHashAttribute(),
                fallBack,
                attributeOverrides
        );

        // Skip because missing hashAttribute
        if (hashAttribute.getHashValue().isEmpty() || hashAttribute.getHashValue().equals("null")) {
            return getExperimentResult(context, experiment, -1, false, featureId, null, null, attributeOverrides);
        }

        int assigned = -1;
        boolean foundStickyBucket = false;
        boolean stickyBucketVersionIsBlocked = false;

        if (context.getStickyBucketService() != null && !Boolean.TRUE.equals(experiment.disableStickyBucketing)) {
            int bucketVersion = experiment.getBucketVersion() != null ? experiment.getBucketVersion() : 0;
            int minBucketVersion = experiment.getMinBucketVersion() != null ? experiment.getMinBucketVersion() : 0;
            List<VariationMeta> meta = experiment.getMeta() != null ? experiment.getMeta() : new ArrayList<>();
            StickyBucketVariation stickyBucketVariation = GrowthBookUtils.getStickyBucketVariation(
                    context,
                    experiment.getKey(),
                    experiment.getHashAttribute(),
                    experiment.getFallbackAttribute(),
                    attributeOverrides,
                    bucketVersion,
                    minBucketVersion,
                    meta
            );

            foundStickyBucket = stickyBucketVariation.getVariation() >= 0;
            assigned = stickyBucketVariation.getVariation();
            stickyBucketVersionIsBlocked = stickyBucketVariation.getVersionIsBlocked() != null ? stickyBucketVariation.getVersionIsBlocked() : false;
        }

        JsonObject attributes = context.getAttributes();
        if (attributes == null) {
            attributes = new JsonObject();
        }

        // Some checks are not needed if we already have a sticky bucket
        if (!foundStickyBucket) {

            List<Filter> filters = experiment.getFilters();
            @Deprecated
            Namespace namespace = experiment.getNamespace();

            if (filters != null) {

                // Exclude if user is filtered out (used to be called "namespace")
                if (GrowthBookUtils.isFilteredOut(filters, attributeOverrides, context)) {
                    return getExperimentResult(context, experiment, -1, false, featureId, null, null, attributeOverrides);
                }
            } else if (namespace != null) {

                // If experiment namespace is set, check if the hash value is included in the range, and if not
                // user is not in the experiment, variation 0.
                Boolean isInNamespace = GrowthBookUtils.inNameSpace(hashAttribute.getHashValue(), namespace);
                if (!isInNamespace) {
                    return getExperimentResult(context, experiment, -1, false, featureId, null, null, attributeOverrides);
                }
            }

            // Evaluate the condition JSON
            // can it be instead JsonObject
            JsonObject conditionJson = experiment.getConditionJson();
            if (conditionJson != null) {
                Boolean shouldEvaluate = conditionEvaluator.evaluateCondition(attributes, conditionJson, context.getSavedGroups());

                // If experiment.condition is set and the condition evaluates to false,
                // return immediately (not in experiment, variationId 0)
                if (!shouldEvaluate) {
                    return getExperimentResult(context, experiment, -1, false, featureId, null, null, attributeOverrides);
                }
            }

            // 10. Exclude if prerequisites are not met
            List<ParentCondition> parenConditions = experiment.getParentConditions();
            if (parenConditions != null) {
                for (ParentCondition parentCondition : parenConditions) {
                    FeatureResult<ValueType> parentResult = new FeatureEvaluator().evaluateFeature(
                            parentCondition.getId(),
                            context,
                            null,
                            jsonUtils.gson.fromJson(
                                    parentCondition.getCondition(),
                                    JsonObject.class
                            )
                    );

                    if (parentResult.getSource() != null) {
                        if (parentResult.getSource().equals(FeatureResultSource.CYCLIC_PREREQUISITE)) {
                            return getExperimentResult(context, experiment, -1, false, featureId, null, null, attributeOverrides);
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
                            context.getSavedGroups()
                    );

                    // blocking prerequisite eval failed: feature evaluation fails
                    if (!evalCondition) {
                        log.info("Feature blocked by prerequisite");
                        return getExperimentResult(context, experiment, -1, false, featureId, null, null, attributeOverrides);
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
            return getExperimentResult(context, experiment, -1, false, featureId, null, null, attributeOverrides);
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
            return getExperimentResult(context, experiment, -1, false, featureId, null, true, attributeOverrides);
        }

        // Assigned variations
        // If not assigned a variation (-1), not in experiment, variation 0
        if (assigned < 0) {
            // NOTE: While a hash is used to determine if the user is assigned a variation, since they aren't, hash passed is null
            return getExperimentResult(context, experiment, -1, false, featureId, null, null, attributeOverrides);
        }

        // If experiment has a forced index, not in experiment, variation is the forced experiment
        Integer force = experiment.getForce();
        if (force != null) {
            return getExperimentResult(context, experiment, force, false, featureId, null, null, attributeOverrides);
        }

        // If QA mode is enabled, not in experiment, variation 0
        if (context.getIsQaMode() != null && context.getIsQaMode()) {
            return getExperimentResult(context, experiment, -1, false, featureId, null, null, attributeOverrides);
        }

        // User is in an experiment.
        // Call the tracking callback with the result.
        ExperimentResult<ValueType> result = getExperimentResult(context, experiment, assigned, true, featureId, hash, foundStickyBucket, attributeOverrides);

        // Persist sticky bucket
        if (context.getStickyBucketService() != null && !Boolean.TRUE.equals(experiment.disableStickyBucketing)) {
            Map<String, String> assignments = new HashMap<>();
            assignments.put(GrowthBookUtils.getStickyBucketExperimentKey(
                    experiment.getKey(),
                    experiment.getBucketVersion()), result.getKey());

            GeneratedStickyBucketAssignmentDocModel generatedStickyBucketAssignmentDocModel = GrowthBookUtils.generateStickyBucketAssignmentDoc(
                    context,
                    hashAttribute.getHashAttribute(),
                    hashAttribute.getHashValue(),
                    assignments);

            if (generatedStickyBucketAssignmentDocModel.isChanged()) {

                // update local docs
                if (context.getStickyBucketAssignmentDocs() == null) {
                    context.setStickyBucketAssignmentDocs(new HashMap<>());
                }

                context.getStickyBucketAssignmentDocs().put(
                        generatedStickyBucketAssignmentDocModel.getKey(),
                        generatedStickyBucketAssignmentDocModel.getStickyAssignmentsDocument()
                );

                // save doc
                context.getStickyBucketService().saveAssignments(generatedStickyBucketAssignmentDocModel.getStickyAssignmentsDocument());
            }
        }

        // Fire context.trackingClosure if set and the combination of hashAttribute,
        // hashValue, experiment.key, and variationId has not been tracked before
        if (!context.getExperimentHelper().isTracked(experiment, result)) {
            TrackingCallback trackingCallback = context.getTrackingCallback();

            if (trackingCallback != null) {
                trackingCallback.onTrack(experiment, result);
            }
        }

        // Return (in experiment, assigned variation)
        return result;
    }

    private <ValueType> ExperimentResult<ValueType> getExperimentResult(
            GBContext context,
            Experiment<ValueType> experiment,
            Integer variationIndex,
            Boolean hashUsed,
            String featureId,
            @Nullable Float hashBucket,
            @Nullable Boolean stickyBucketUsed,
            JsonObject attributeOverrides
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
        if (context.getStickyBucketService() != null && !Boolean.TRUE.equals(experiment.disableStickyBucketing)) {
            fallBack = experiment.getFallbackAttribute();
        }
        HashAttributeAndHashValue hashAttribute = GrowthBookUtils.getHashAttribute(
                context,
                experiment.getHashAttribute(),
                fallBack,
                attributeOverrides);

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
}
