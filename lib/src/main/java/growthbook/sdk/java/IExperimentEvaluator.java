package growthbook.sdk.java;

import javax.annotation.Nullable;

interface IExperimentEvaluator {
    <ValueType> ExperimentResult<ValueType> evaluateExperiment(Experiment<ValueType> experiment, GBContext context, @Nullable String featureId);
}
