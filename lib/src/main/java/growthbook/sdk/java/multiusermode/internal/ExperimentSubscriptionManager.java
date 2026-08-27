package growthbook.sdk.java.multiusermode.internal;

import growthbook.sdk.java.callback.ExperimentRunCallback;
import growthbook.sdk.java.model.AssignedExperiment;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Internal manager for experiment run subscriptions.
 * It tracks the last emitted assignment per experiment and only notifies subscribers on assignment changes.
 */
@Slf4j
public final class ExperimentSubscriptionManager {

    private final List<ExperimentRunCallback> callbacks = new CopyOnWriteArrayList<>();
    private final Map<String, AssignedExperiment> assigned = new ConcurrentHashMap<>();

    /**
     * Registers an experiment run callback.
     *
     * @param callback callback invoked when an experiment assignment changes
     */
    public void subscribe(ExperimentRunCallback callback) {
        this.callbacks.add(callback);
    }

    /**
     * Publishes an experiment result to callbacks when the assignment changed.
     *
     * @param experiment evaluated experiment
     * @param result evaluation result
     * @param <T> experiment value type
     */
    public <T> void publishIfChanged(Experiment<T> experiment, ExperimentResult<T> result) {
        String key = experiment.getKey();
        AssignedExperiment nextAssignment = new AssignedExperiment(
                experiment.getKey(),
                result.getInExperiment(),
                result.getVariationId()
        );
        AtomicBoolean changed = new AtomicBoolean(false);

        this.assigned.compute(key, (ignored, previous) -> {
            boolean assignmentChanged = hasAssignmentChanged(previous, result);
            changed.set(assignmentChanged);
            return assignmentChanged ? nextAssignment : previous;
        });

        if (!changed.get()) {
            return;
        }

        for (ExperimentRunCallback callback : this.callbacks) {
            try {
                callback.onRun(experiment, result);
            } catch (RuntimeException e) {
                log.error("Experiment run callback failed for experiment={}", experiment.getKey(), e);
            }
        }
    }

    private <T> boolean hasAssignmentChanged(AssignedExperiment previous, ExperimentResult<T> result) {
        return previous == null
                || !Objects.equals(previous.getInExperiment(), result.getInExperiment())
                || !Objects.equals(previous.getVariationId(), result.getVariationId());
    }
}
