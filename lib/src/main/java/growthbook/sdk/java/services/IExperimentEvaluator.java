package growthbook.sdk.java.services;

import growthbook.sdk.java.models.Context;
import growthbook.sdk.java.models.Experiment;
import growthbook.sdk.java.models.ExperimentResult;

public interface IExperimentEvaluator {
    ExperimentResult evaluateExperiment(Experiment experiment, Context context);
}
