package growthbook.sdk.java;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.annotation.Nullable;
import java.util.*;

/**
 * <b>INTERNAL</b>: Implementation of experiment evaluation
 */
class ExperimentEvaluator implements IExperimentEvaluator {

    private final ConditionEvaluator conditionEvaluator = new ConditionEvaluator();

    @Override
    public <ValueType> ExperimentResult<ValueType> evaluateExperiment(Experiment<ValueType> experiment, GBContext context, @Nullable String featureId) {
        // If less than 2 variations, return immediately (not in experiment, variation 0)
        // If not enabled, return immediately (not in experiment, variation 0)
        ArrayList<ValueType> experimentVariations = experiment.getVariations();
        if (experimentVariations == null) {
            experimentVariations = new ArrayList<>();
        }
        if ((context.getEnabled() != null && !context.getEnabled()) || experimentVariations.size() < 2) {
            return getExperimentResult(experiment, context, 0, false, false, featureId, null);
        }

        // Query string overrides
        Integer override = GrowthBookUtils.getQueryStringOverride(experiment.getKey(), context.getUrl(), experimentVariations.size());
        if (override != null) {
            return getExperimentResult(experiment, context, override, true, false, featureId, null);
        }

        // If no forced variation, not in experiment, variation 0
        Map<String, Integer> forcedVariations = context.getForcedVariationsMap();
        if (forcedVariations == null) {
            forcedVariations = new HashMap<>();
        }
        Integer forcedVariation = forcedVariations.get(experiment.getKey());
        if (forcedVariation != null) {
            return getExperimentResult(experiment, context, forcedVariation, true, false, featureId, null);
        }

        // If experiment is not active, not in experiment, variation 0
        if (experiment.getIsActive() != null && !experiment.getIsActive()) {
            return getExperimentResult(experiment, context, 0, false, false, featureId, null);
        }

        // Get the user hash attribute and the value. If empty, not in experiment, variation 0
        JsonObject attributes = context.getAttributes();
        if (attributes == null) {
            attributes = new JsonObject();
        }

        String hashAttribute = experiment.getHashAttribute();
        if (hashAttribute == null || hashAttribute.equals("")) {
            hashAttribute = "id";
        }
        JsonElement attributeValueElement = attributes.get(hashAttribute);

        if (
                attributeValueElement == null ||
                        attributeValueElement.isJsonNull() ||
                        (attributeValueElement.isJsonPrimitive() &&
                                attributeValueElement.getAsJsonPrimitive().isString() &&
                                Objects.equals(attributeValueElement.getAsString(), ""))
        ) {
            return getExperimentResult(experiment, context, 0, false, false, featureId, null);
        }

        String attributeValue = attributeValueElement.getAsString();

        List<Filter> filters = experiment.getFilters();
        Namespace namespace = experiment.getNamespace();
        if (filters != null) {
            // Exclude if user is filtered out (used to be called "namespace")
            if (GrowthBookUtils.isFilteredOut(filters, attributes)) {
                return getExperimentResult(experiment, context, 0, false, false, featureId, null);
            }
        } else if (namespace != null) {
            // If experiment namespace is set, check if the hash value is included in the range, and if not
            // user is not in the experiment, variation 0.
            Boolean isInNamespace = GrowthBookUtils.inNameSpace(attributeValue, namespace);
            if (!isInNamespace) {
                return getExperimentResult(experiment, context, 0, false, false, featureId, null);
            }
        }

        // Evaluate the condition JSON
        String jsonStringCondition = experiment.getConditionJson();
        if (jsonStringCondition != null) {
            String attributesJson = GrowthBookJsonUtils.getInstance().gson.toJson(attributes);
            Boolean shouldEvaluate = conditionEvaluator.evaluateCondition(attributesJson, jsonStringCondition);
            if (!shouldEvaluate) {
                return getExperimentResult(experiment, context, 0, false, false, featureId, null);
            }
        }

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

        // Assigned variations
        // If not assigned a variation (-1), not in experiment, variation 0
        String seed = experiment.getSeed();
        if (seed == null) {
            seed = experiment.getKey();
        }
        Integer hashVersion = experiment.getHashVersion();
        if (hashVersion == null) {
            hashVersion = 1;
        }
        Float hash = GrowthBookUtils.hash(attributeValue, hashVersion, seed);
        if (hash == null) {
            return getExperimentResult(experiment, context, 0, false, false, featureId, null);
        }
        Integer assignedVariation = GrowthBookUtils.chooseVariation(hash, bucketRanges);
        if (assignedVariation == -1) {
            // NOTE: While a hash is used to determine if the user is assigned a variation, since they aren't, hash passed is null
            return getExperimentResult(experiment, context, 0, false, false, featureId, null);
        }

        // If experiment has a forced index, not in experiment, variation is the forced experiment
        Integer force = experiment.getForce();
        if (force != null) {
            return getExperimentResult(experiment, context, force, true, false, featureId, null);
        }

        // If QA mode is enabled, not in experiment, variation 0
        if (context.getIsQaMode() != null && context.getIsQaMode()) {
            return getExperimentResult(experiment, context, 0, false, false, featureId, null);
        }

        // User is in an experiment.
        // Call the tracking callback with the result.
        ExperimentResult<ValueType> result = getExperimentResult(experiment, context, assignedVariation, true, true, featureId, hash);
        TrackingCallback trackingCallback = context.getTrackingCallback();
        if (trackingCallback != null) {
            trackingCallback.onTrack(experiment, result);
        }

        return result;
    }

    private <ValueType> ExperimentResult<ValueType> getExperimentResult(
            Experiment<ValueType> experiment,
            GBContext context,
            Integer variationIndex,
            Boolean inExperiment,
            Boolean hashUsed,
            String featureId,
            @Nullable Float hashBucket
    ) {
        ArrayList<ValueType> experimentVariations = experiment.getVariations();
        if (experimentVariations == null) {
            experimentVariations = new ArrayList<>();
        }
        if (variationIndex < 0 || variationIndex >= experimentVariations.size()) {
            variationIndex = 0;
            inExperiment = false;
        }

        ValueType targetValue = null;

        if (!experimentVariations.isEmpty()) {
            targetValue = experiment.getVariations().get(variationIndex);
        }

        String hashAttribute = experiment.getHashAttribute();
        if (hashAttribute == null) {
            hashAttribute = "id";
        }

        String hashValue = "";
        JsonObject attributes = context.getAttributes();
        if (attributes != null) {
            JsonElement hashAttributeElement = attributes.get(hashAttribute);
            if (hashAttributeElement != null && !hashAttributeElement.isJsonNull()) {
                hashValue = hashAttributeElement.getAsString();
            }
        }

        VariationMeta maybeMeta = null;
        ArrayList<VariationMeta> metaList = experiment.getMeta();
        if (metaList == null) {
            metaList = new ArrayList<>();
        }
        if (variationIndex < metaList.size()) {
            maybeMeta = metaList.get(variationIndex);
        }

        return ExperimentResult
                .<ValueType>builder()
                .inExperiment(inExperiment)
                .variationId(variationIndex)
                .key(maybeMeta == null ? variationIndex.toString() : maybeMeta.getKey())
                .featureId(featureId)
                .hashValue(hashValue)
                .hashUsed(hashUsed)
                .hashAttribute(hashAttribute)
                .value(targetValue)
                .bucket(hashBucket)
                .name(maybeMeta == null ? null : maybeMeta.getName())
                .passThrough(maybeMeta == null ? null : maybeMeta.getPassThrough())
                .build();
    }
}
