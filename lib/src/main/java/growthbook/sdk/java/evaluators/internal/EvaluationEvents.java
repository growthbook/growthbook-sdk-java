package growthbook.sdk.java.evaluators.internal;

import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.TrackData;
import growthbook.sdk.java.multiusermode.ExperimentTracker;
import growthbook.sdk.java.multiusermode.configurations.EvaluationContext;
import growthbook.sdk.java.multiusermode.usage.FeatureUsageCallbackWithUser;
import growthbook.sdk.java.multiusermode.usage.TrackingCallbackWithUser;
import growthbook.sdk.java.plugin.PluginRegistry;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.List;

/**
 * Delivers what an evaluation observed — feature usage and experiment exposures — to the user
 * callbacks and plugins of the context.
 *
 * <p>Exposures are de-duplicated per {@code (hashAttribute, hashValue, experimentKey, variationId)}
 * through the context's {@link ExperimentTracker}; without a tracker every exposure is reported.
 * A user callback that throws skips the plugins of that delivery. When the context suppresses
 * tracking errors (the multi-user client) the failure is logged and a failed exposure is released
 * from the tracker, so it is retried on a later evaluation instead of being lost; otherwise the
 * failure propagates to the caller and the exposure stays marked. Remote-evaluation track payloads
 * never propagate a callback failure. Plugin failures are isolated by {@link PluginRegistry}.
 */
@Slf4j
final class EvaluationEvents {

    private final EvaluationContext context;

    EvaluationEvents(EvaluationContext context) {
        this.context = context;
    }

    <T> void featureEvaluated(String key, FeatureResult<T> result) {
        FeatureUsageCallbackWithUser callback = context.getOptions().getFeatureUsageCallbackWithUser();
        if (callback != null) {
            try {
                callback.onFeatureUsage(key, result, context.getUser());
            } catch (RuntimeException e) {
                if (!context.isSuppressTrackingErrors()) {
                    throw e;
                }
                log.error("Feature usage callback failed for feature '{}'.", key, e);
            }
        }
        PluginRegistry registry = context.getPluginRegistry();
        if (registry != null) {
            registry.fireFeatureEvaluated(key, result,
                    context.getUser() != null ? context.getUser().getAttributes() : null);
        }
    }

    <T> void experimentViewed(Experiment<T> experiment, ExperimentResult<T> result) {
        trackExposure(experiment, result, context.isSuppressTrackingErrors());
    }

    <T> void remoteEvaluationTracks(@Nullable List<TrackData<T>> tracks) {
        if (tracks == null) {
            return;
        }
        if (context.getOptions().getTrackingCallBackWithUser() == null
                && context.getPluginRegistry() == null) {
            return;
        }

        for (TrackData<T> track : tracks) {
            if (!isWellFormed(track)) {
                log.debug("Skipping malformed remote evaluation tracking payload.");
            } else {
                trackExposure(track.getExperiment(), track.getResult(), true);
            }
        }
    }

    private static <T> boolean isWellFormed(@Nullable TrackData<T> track) {
        return track != null
                && track.getExperiment() != null
                && track.getResult() != null
                && track.getExperiment().getKey() != null
                && track.getExperiment().getVariations() != null;
    }

    private <T> void trackExposure(Experiment<T> experiment, ExperimentResult<T> result, boolean suppressErrors) {
        ExperimentTracker tracker = context.getExperimentTracker();
        String key = trackingKey(experiment, result);
        Object claim = tracker != null ? tracker.markIfFirst(key) : null;
        if (tracker != null && claim == null) {
            return;
        }
        try {
            dispatchExperimentViewed(experiment, result);
        } catch (RuntimeException e) {
            boolean retry = claim != null && context.isSuppressTrackingErrors();
            if (retry) {
                tracker.untrack(key, claim);
            }
            if (!suppressErrors) {
                throw e;
            }
            log.error(retry
                    ? "Tracking callback failed for experiment '{}'; the exposure will be retried on the next evaluation."
                    : "Tracking callback failed for experiment '{}'.", experiment.getKey(), e);
        }
    }

    private <T> void dispatchExperimentViewed(Experiment<T> experiment, ExperimentResult<T> result) {
        TrackingCallbackWithUser callback = context.getOptions().getTrackingCallBackWithUser();
        if (callback != null) {
            callback.onTrack(experiment, result, context.getUser());
        }
        PluginRegistry pluginRegistry = context.getPluginRegistry();
        if (pluginRegistry != null) {
            pluginRegistry.fireExperimentViewed(experiment, result,
                    context.getUser() != null ? context.getUser().getAttributes() : null);
        }
    }

    private static <T> String trackingKey(Experiment<T> experiment, ExperimentResult<T> result) {
        return (result.getHashAttribute() != null ? result.getHashAttribute() : "")
                + (result.getHashValue() != null ? result.getHashValue() : "")
                + experiment.getKey() + result.getVariationId();
    }
}
