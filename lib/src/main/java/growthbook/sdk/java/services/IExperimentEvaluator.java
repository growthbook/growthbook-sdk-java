package growthbook.sdk.java.services;

import growthbook.sdk.java.models.Context;
import growthbook.sdk.java.models.Experiment;
import growthbook.sdk.java.models.ExperimentResult;

public interface IExperimentEvaluator {
    public <ValueType> ExperimentResult<ValueType> evaluateExperiment(Experiment<ValueType> experiment, Context context);
}
