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
 * User callbacks are untrusted: one that throws is logged and the evaluation goes on with the
 * same result. Plugin failures are isolated by {@link PluginRegistry}.
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
                log.warn("Feature usage callback failed for feature '{}'; continuing.", key, e);
            }
        }
        PluginRegistry registry = context.getPluginRegistry();
        if (registry != null) {
            registry.fireFeatureEvaluated(key, result,
                    context.getUser() != null ? context.getUser().getAttributes() : null);
        }
    }

    <T> void experimentViewed(Experiment<T> experiment, ExperimentResult<T> result) {
        if (markTracked(experiment, result)) {
            dispatchExperimentViewed(experiment, result);
        }
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
            } else if (markTracked(track.getExperiment(), track.getResult())) {
                dispatchExperimentViewed(track.getExperiment(), track.getResult());
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

    private <T> void dispatchExperimentViewed(Experiment<T> experiment, ExperimentResult<T> result) {
        TrackingCallbackWithUser callback = context.getOptions().getTrackingCallBackWithUser();
        if (callback != null) {
            try {
                callback.onTrack(experiment, result, context.getUser());
            } catch (RuntimeException e) {
                log.warn("Tracking callback failed for experiment '{}'; continuing.", experiment.getKey(), e);
            }
        }
        PluginRegistry pluginRegistry = context.getPluginRegistry();
        if (pluginRegistry != null) {
            pluginRegistry.fireExperimentViewed(experiment, result,
                    context.getUser() != null ? context.getUser().getAttributes() : null);
        }
    }

    private <T> boolean markTracked(Experiment<T> experiment, ExperimentResult<T> result) {
        ExperimentTracker tracker = context.getExperimentTracker();
        return tracker == null || tracker.markTracked(trackingKey(experiment, result));
    }

    private static <T> String trackingKey(Experiment<T> experiment, ExperimentResult<T> result) {
        return (result.getHashAttribute() != null ? result.getHashAttribute() : "")
                + (result.getHashValue() != null ? result.getHashValue() : "")
                + experiment.getKey() + result.getVariationId();
    }
}
