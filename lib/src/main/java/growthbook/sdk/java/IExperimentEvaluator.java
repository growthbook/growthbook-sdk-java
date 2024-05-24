package growthbook.sdk.java;

import com.google.gson.JsonObject;

import javax.annotation.Nullable;

interface IExperimentEvaluator {
    <ValueType> ExperimentResult<ValueType> evaluateExperiment(Experiment<ValueType> experiment, GBContext context, @Nullable String featureId, JsonObject attributeOverrides);
}
