package growthbook.sdk.java.evaluators;

import growthbook.sdk.java.evaluators.internal.Evaluation;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.TrackData;
import growthbook.sdk.java.multiusermode.ExperimentTracker;
import growthbook.sdk.java.multiusermode.configurations.EvaluationContext;

import javax.annotation.Nullable;
import java.util.List;

/**
 * <b>INTERNAL</b>: Implementation of experiment evaluation
 *
 * <p>Stateless and safe for concurrent use: each call runs in its own {@link Evaluation}.
 */
public class ExperimentEvaluator implements IExperimentEvaluator {

    /**
     * Takes Context, Experiment and returns Experiment Result
     *
     * @param experiment Experiment
     * @param context    EvaluationContext
     * @param featureId  String(can be null)
     * @return ExperimentResult
     */
    @Override
    public <T> ExperimentResult<T> evaluateExperiment(Experiment<T> experiment,
                                                                      EvaluationContext context,
                                                                      @Nullable String featureId) {
        return new Evaluation(context).runExperiment(experiment, featureId);
    }

    /**
     * Fires tracking callbacks for experiments that were evaluated remotely (a feature rule's
     * {@code tracks}). De-duplicates per {@code (hashAttribute, hashValue, experimentKey, variationId)}
     * through the context's {@link ExperimentTracker}, the same one local evaluation uses, so
     * reusing a cached remote-eval response does not re-fire exposure events.
     */
    public <T> void fireRemoteEvaluationTracks(
            @Nullable List<TrackData<T>> tracks,
            EvaluationContext context
    ) {
        new Evaluation(context).fireRemoteEvaluationTracks(tracks);
    }
}
