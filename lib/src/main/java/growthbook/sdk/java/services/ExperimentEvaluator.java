package growthbook.sdk.java.services;

import growthbook.sdk.java.models.Context;
import growthbook.sdk.java.models.Experiment;
import growthbook.sdk.java.models.ExperimentResult;

public class ExperimentEvaluator implements IExperimentEvaluator {
    @Override
    public <ValueType> ExperimentResult<ValueType> evaluateExperiment(Experiment<ValueType> experiment, Context context) {
        // TODO: evaluateExperiment
        return null;
    }
}
