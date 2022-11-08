package growthbook.sdk.java.internal.services;

import growthbook.sdk.java.models.GBContext;
import growthbook.sdk.java.models.Experiment;
import growthbook.sdk.java.models.ExperimentResult;

import javax.annotation.Nullable;

interface IExperimentEvaluator {
    <ValueType> ExperimentResult<ValueType> evaluateExperiment(Experiment<ValueType> experiment, GBContext context, @Nullable String featureId);
}
