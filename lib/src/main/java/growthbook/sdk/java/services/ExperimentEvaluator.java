package growthbook.sdk.java.services;

import com.sun.org.apache.xpath.internal.operations.Bool;
import growthbook.sdk.java.models.Context;
import growthbook.sdk.java.models.Experiment;
import growthbook.sdk.java.models.ExperimentResult;
import growthbook.sdk.java.models.Namespace;

import java.util.HashMap;
import java.util.Map;

public class ExperimentEvaluator implements IExperimentEvaluator {
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

        // TODO: Evaluate experiment condition

        // TODO: Variation weights and coverage

        // TODO: Bucket ranges

        // TODO: evaluateExperiment
        return null;
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
