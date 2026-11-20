package growthbook.sdk.java.evaluators;

import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.multiusermode.configurations.EvaluationContext;

import javax.annotation.Nullable;

interface IExperimentEvaluator {
    <T> ExperimentResult<T> evaluateExperiment(
        Experiment<T> experiment,
        EvaluationContext context,
        @Nullable String featureId);
}
