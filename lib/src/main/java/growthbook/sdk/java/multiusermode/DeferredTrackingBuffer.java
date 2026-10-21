package growthbook.sdk.java.multiusermode;

import static growthbook.sdk.java.constants.SDKConstants.EMPTY_STRING;

import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.multiusermode.usage.EventLoggerDispatch;
import growthbook.sdk.java.plugin.PluginRegistry;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <b>INTERNAL</b>: Per-request buffer that accumulates experiment exposures instead of firing them
 * immediately, so a request can evaluate now and flush tracking once at the end.
 *
 * <p>Insertion-ordered and de-duplicated on {@code (hashAttribute, hashValue, experimentKey,
 * variationId)} — the same key the immediate path dedupes on. De-duplication is per-buffer (per
 * request) and deliberately independent of the client-wide {@code ExperimentTracker}, whose bounded
 * LRU is shared across users and would otherwise drop or leak exposures between requests.
 *
 * <p><b>Not thread-safe.</b> One instance belongs to one {@link UserScopedGrowthBook} and one
 * request/thread.
 *
 * <p>Bounded at {@value #MAX_CALLS} distinct exposures per buffer to cap memory for a pathological
 * request; beyond that, additional distinct exposures are dropped (logged once, never silently).
 */
@Slf4j
public final class DeferredTrackingBuffer {

    static final int MAX_CALLS = 10_000;

    private final Map<String, DeferredTrackingCall<?>> calls = new LinkedHashMap<>();
    private boolean capacityWarningLogged = false;

    /**
     * Buffers an exposure, keeping the first occurrence per dedupe key. Drops distinct exposures
     * beyond {@value #MAX_CALLS}.
     */
    public <T> void add(Experiment<T> experiment,
                        ExperimentResult<T> result,
                        UserContext userContext) {
        String key = dedupeKey(experiment, result);
        if (calls.size() >= MAX_CALLS && !calls.containsKey(key)) {
            warnCapacityReached();
            return;
        }
        // Snapshot the exposure now: callers may reuse and mutate the same Experiment/ExperimentResult
        // (e.g. setKey) between buffering and flush, which would otherwise rewrite the queued event.
        calls.computeIfAbsent(key, k -> new DeferredTrackingCall<>(
                experiment.toBuilder().build(), result.toBuilder().build(), userContext));
    }

    /**
     * Returns a snapshot of the buffered calls in insertion order.
     */
    public List<DeferredTrackingCall<?>> getCalls() {
        return new ArrayList<>(calls.values());
    }

    /**
     * Replaces the buffered calls, preserving order and re-applying de-duplication. Entries missing
     * an experiment or result are skipped.
     */
    public void setCalls(List<DeferredTrackingCall<?>> newCalls) {
        calls.clear();
        if (newCalls == null) {
            return;
        }
        for (DeferredTrackingCall<?> call : newCalls) {
            if (call == null || call.getExperiment() == null || call.getResult() == null) {
                continue;
            }
            String key = dedupeKey(call);
            if (calls.size() >= MAX_CALLS && !calls.containsKey(key)) {
                warnCapacityReached();
                break;
            }
            calls.put(key, call);
        }
    }

    /**
     * Dispatches every buffered call through the exposure sinks (tracking callback, event logger, and
     * plugins), then clears the buffer.
     *
     * <p>Drains a snapshot and clears before invoking any sink: a sink may evaluate another
     * experiment-backed feature through the same scope and buffer a new exposure mid-flush. Iterating
     * the live map would throw {@link java.util.ConcurrentModificationException} outside the per-call
     * guard and could clear newly added entries without delivering them; those entries are left for a
     * later flush instead.
     */
    public void flush(Options options, PluginRegistry pluginRegistry) {
        List<DeferredTrackingCall<?>> pendingCalls = new ArrayList<>(calls.values());
        calls.clear();
        for (DeferredTrackingCall<?> call : pendingCalls) {
            EventLoggerDispatch.fireExperimentViewed(options, call, pluginRegistry);
        }
    }

    private void warnCapacityReached() {
        if (!capacityWarningLogged) {
            capacityWarningLogged = true;
            log.warn("Deferred tracking buffer reached its cap of {} exposures; further distinct "
                    + "exposures in this request are dropped. Flush more frequently if this is expected.", MAX_CALLS);
        }
    }

    private static <T> String dedupeKey(DeferredTrackingCall<T> call) {
        return dedupeKey(call.getExperiment(), call.getResult());
    }

    private static <T> String dedupeKey(Experiment<T> experiment, ExperimentResult<T> result) {
        return (result.getHashAttribute() != null ? result.getHashAttribute() : EMPTY_STRING)
                + (result.getHashValue() != null ? result.getHashValue() : EMPTY_STRING)
                + experiment.getKey() + result.getVariationId();
    }
}
