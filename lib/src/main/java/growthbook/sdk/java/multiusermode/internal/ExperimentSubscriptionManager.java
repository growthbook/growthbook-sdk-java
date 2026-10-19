package growthbook.sdk.java.multiusermode.internal;

import growthbook.sdk.java.callback.ExperimentRunCallback;
import growthbook.sdk.java.model.AssignedExperiment;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * <b>INTERNAL</b>: not part of the supported API. Public only because {@code GrowthBookClient} lives in
 * the parent package — treat its signatures as free to change without a major version bump.
 *
 * Internal manager for experiment run subscriptions.
 * It tracks the last emitted assignment per experiment and only notifies subscribers on assignment changes.
 */
@Slf4j
public final class ExperimentSubscriptionManager {

    private final List<ExperimentRunCallback> callbacks = new CopyOnWriteArrayList<>();
    private final Map<String, AssignedExperiment> assigned = new ConcurrentHashMap<>();
    private final Map<String, ExperimentResult<?>> lastResults = new ConcurrentHashMap<>();

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
        // ConcurrentHashMap rejects null keys; a key-less experiment still dedupes,
        // under one shared sentinel entry.
        String key = experiment.getKey() != null ? experiment.getKey() : "";
        if (experiment.getKey() != null) {
            this.lastResults.put(experiment.getKey(), result);
        }
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

    /**
     * Returns the last {@link ExperimentResult} seen for every experiment run through this manager,
     * keyed by experiment key. Reflects every run, not only assignment changes.
     *
     * @return an unmodifiable snapshot; empty when no experiment has been run
     */
    public Map<String, ExperimentResult<?>> getAllResults() {
        return Collections.unmodifiableMap(new LinkedHashMap<>(this.lastResults));
    }

    /**
     * Drops the tracked assignments and results so a subsequent {@link #getAllResults()} starts empty.
     * Registered callbacks are left intact. Invoked when the owning client shuts down.
     */
    public void clear() {
        this.assigned.clear();
        this.lastResults.clear();
    }

    private <T> boolean hasAssignmentChanged(AssignedExperiment previous, ExperimentResult<T> result) {
        return previous == null
                || !Objects.equals(previous.getInExperiment(), result.getInExperiment())
                || !Objects.equals(previous.getVariationId(), result.getVariationId());
    }
}
