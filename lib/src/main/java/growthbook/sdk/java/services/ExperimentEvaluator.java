package growthbook.sdk.java.services;

import growthbook.sdk.java.models.Context;
import growthbook.sdk.java.models.Experiment;
import growthbook.sdk.java.models.ExperimentResult;

import java.util.HashMap;

public class ExperimentEvaluator implements IExperimentEvaluator {
    @Override
    public <ValueType> ExperimentResult<ValueType> evaluateExperiment(Experiment<ValueType> experiment, Context context) {
        // If less than 2 variations, return immediately (not in experiment, variation 0)
        // If not enabled, return immediately (not in experiment, variation 0)
        if (!context.getEnabled() || experiment.getVariations().size() < 2) {
            return getExperimentResult(experiment, context, 0, false);
        }

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
