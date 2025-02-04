package growthbook.sdk.java.evaluators;

import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.multiusermode.configurations.EvaluationContext;

import javax.annotation.Nullable;

interface IExperimentEvaluator {
    <ValueType> ExperimentResult<ValueType> evaluateExperiment(Experiment<ValueType> experiment, EvaluationContext context, @Nullable String featureId);
}
