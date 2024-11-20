package growthbook.sdk.java;

import com.google.gson.JsonObject;
import growthbook.sdk.java.multiusermode.configurations.EvaluationContext;

import javax.annotation.Nullable;

interface IExperimentEvaluator {
    <ValueType> ExperimentResult<ValueType> evaluateExperiment(Experiment<ValueType> experiment, EvaluationContext context, @Nullable String featureId);
}
