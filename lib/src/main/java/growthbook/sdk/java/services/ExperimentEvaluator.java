package growthbook.sdk.java.services;

import com.google.gson.JsonElement;
import com.sun.org.apache.xpath.internal.operations.Bool;
import growthbook.sdk.java.models.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

public class ExperimentEvaluator implements IExperimentEvaluator {

    private ConditionEvaluator conditionEvaluator = new ConditionEvaluator();

    @Override
    public <ValueType> ExperimentResult<ValueType> evaluateExperiment(Experiment<ValueType> experiment, Context context) {
        // If less than 2 variations, return immediately (not in experiment, variation 0)
        // If not enabled, return immediately (not in experiment, variation 0)
        if (!context.getEnabled() || experiment.getVariations().size() < 2) {
            return getExperimentResult(experiment, context, 0, false);
        }

        // If no forced variation, not in experiment, variation 0
        Map<String, Integer> forcedVariations = context.getForcedVariationsMap();
        if (forcedVariations == null) {
            forcedVariations = new HashMap<>();
        }
        Integer forcedVariation = forcedVariations.get(experiment.getKey());
        if (forcedVariation != null) {
            return getExperimentResult(experiment, context, forcedVariation, false);
        }

        // If experiment is not active, not in experiment, variation 0
        if (!experiment.getIsActive()) {
            return getExperimentResult(experiment, context, 0, false);
        }

        // Get the user hash attribute and the value. If empty, not in experiment, variation 0
        HashMap<String, String> attributes = context.getAttributes();
        if (attributes == null) {
            attributes = new HashMap<>();
        }
        String hashAttribute = experiment.getHashAttribute();
        if (hashAttribute == null) {
            hashAttribute = "id";
        }
        String attributeValue = attributes.get(hashAttribute);
        if (attributeValue == null || attributeValue.isEmpty()) {
            return getExperimentResult(experiment, context, 0, false);
        }

        // If experiment namespace is set, check if the hash value is included in the range, and if not
        // user is not in the experiment, variation 0.
        Namespace namespace = experiment.getNamespace();
        if (namespace != null) {
            Boolean isInNamespace = GrowthBookUtils.inNameSpace(attributeValue, namespace);
            if (!isInNamespace) {
                return getExperimentResult(experiment, context, 0, false);
            }
        }

        // Evaluate the condition JSON
        String jsonStringCondition = experiment.getConditionJson();
        if (jsonStringCondition != null) {
            String attributesJson = GrowthBookJsonUtils.getInstance().gson.toJson(attributes);
            Boolean shouldEvaluate = conditionEvaluator.evaluateCondition(attributesJson, jsonStringCondition);
            if (!shouldEvaluate) {
                return getExperimentResult(experiment, context, 0, false);
            }
        }

        // Set default variation weights and coverage if not set
        // Weights
        ArrayList<Float> weights = experiment.getWeights();
        if (weights == null) {
            weights = GrowthBookUtils.getEqualWeights(experiment.getVariations().size());
        }
        experiment.setWeights(weights);

        // Coverage
        Float coverage = experiment.getCoverage();
        if (coverage == null) {
            coverage = 1.0f;
        }
        experiment.setCoverage(coverage);

        // Bucket ranges
        ArrayList<BucketRange> bucketRanges = GrowthBookUtils.getBucketRanges(
                experiment.getVariations().size(),
                experiment.getCoverage(),
                experiment.getWeights()
        );

        // Assigned variations
        // If not assigned a variation (-1), not in experiment, variation 0
        Float hash = GrowthBookUtils.hash(attributeValue + experiment.getKey());
        Integer assignedVariation = GrowthBookUtils.chooseVariation(hash, bucketRanges);
        if (assignedVariation == -1) {
            return getExperimentResult(experiment, context, 0, false);
        }

        // If experiment has a forced index, not in experiment, variation is the forced experiment
        Integer force = experiment.getForce();
        if (force != null) {
            return getExperimentResult(experiment, context, force, false);
        }

        // If QA mode is enabled, not in experiment, variation 0
        if (context.getIsQaMode()) {
            return getExperimentResult(experiment, context, 0, false);
        }

        // User is in an experiment.
        // Call the tracking callback with the result.
        ExperimentResult<ValueType> result = getExperimentResult(experiment, context, assignedVariation, true);
        TrackingCallback trackingCallback = context.getTrackingCallback();
        if (trackingCallback != null) {
            trackingCallback.onTrack(experiment, result);
        }

        return result;
    }

    private <ValueType> ExperimentResult<ValueType> getExperimentResult(
            Experiment<ValueType> experiment,
            Context context,
            Integer variationIndex,
            Boolean inExperiment
    ) {
        Integer targetVariationIndex = variationIndex;
        ValueType targetValue = null;

        if (targetVariationIndex < 0 || targetVariationIndex >= experiment.getVariations().size()) {
            // Set to 0
            targetVariationIndex = 0;
        }

        if (!experiment.getVariations().isEmpty()) {
            targetValue = experiment.getVariations().get(targetVariationIndex);
        }

        String hashAttribute = experiment.getHashAttribute();
        if (hashAttribute == null) {
            hashAttribute = "id";
        }

        HashMap<String, String> attributes = context.getAttributes();
        String hashValue = "";
        if (attributes != null) {
            hashValue = attributes.get(hashAttribute);
            if (hashValue == null) {
                hashValue = "";
            }
        }

        return ExperimentResult
                .<ValueType>builder()
                .inExperiment(inExperiment)
                .variationId(variationIndex)
                .hashValue(hashValue)
                .hashAttribute(hashAttribute)
                .value(targetValue)
                .build();
    }
}
