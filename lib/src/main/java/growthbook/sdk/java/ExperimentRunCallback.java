package growthbook.sdk.java;

/**
 * A callback to be executed with an {@link ExperimentResult} whenever an experiment is run.
 */
public interface ExperimentRunCallback {
    /**
     * A callback to be executed with an {@link ExperimentResult} whenever an experiment is run.
     * @param experimentResult {@link ExperimentResult}
     */
    void onRun(ExperimentResult experimentResult);
}
