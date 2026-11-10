package growthbook.sdk.java.multiusermode.usage;

import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.FeatureResultSource;
import growthbook.sdk.java.multiusermode.DeferredTrackingCall;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.plugin.PluginRegistry;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Executor;

import static growthbook.sdk.java.constants.SDKConstants.EMPTY_STRING;

/**
 * <b>INTERNAL</b>: Single dispatch point for tracking callbacks and the structured
 * {@link EventLogger}. Stateless so evaluators stay stateless. Every user-supplied callback is
 * guarded here: a thrown exception is logged and swallowed so it can never break evaluation.
 */
@Slf4j
public final class EventLoggerDispatch {

    private EventLoggerDispatch() {
    }

    private static final String PROP_EXPERIMENT_ID = "experimentId";
    private static final String PROP_VARIATION_ID = "variationId";
    private static final String PROP_HASH_ATTRIBUTE = "hashAttribute";
    private static final String PROP_HASH_VALUE = "hashValue";
    private static final String PROP_FEATURE = "feature";
    private static final String PROP_SOURCE = "source";
    private static final String PROP_VALUE = "value";
    private static final String PROP_RULE_ID = "ruleId";
    private static final String DEFAULT_RULE_ID = "$default";

    /**
     * Fires the exposure sinks for an experiment the user was bucketed into: the
     * {@link TrackingCallbackWithUser} (if set) and the {@link EventLogger}
     * {@link GrowthBookEvent#EXPERIMENT_VIEWED} event (if set).
     */
    public static <T> void fireExperimentViewed(Options options,
                                                Experiment<T> experiment,
                                                ExperimentResult<T> result,
                                                UserContext userContext) {
        if (options == null) {
            return;
        }

        TrackingCallbackWithUser trackingCallback = options.getTrackingCallBackWithUser();
        if (trackingCallback != null) {
            try {
                trackingCallback.onTrack(experiment, result, userContext);
            } catch (RuntimeException e) {
                log.warn("Tracking callback threw during experiment exposure; ignoring.", e);
            }
        }

        EventLogger eventLogger = options.getEventLogger();
        if (eventLogger != null) {
            Map<String, Object> properties = new HashMap<>();
            properties.put(PROP_EXPERIMENT_ID, experiment.getKey());
            properties.put(PROP_VARIATION_ID, result.getKey());
            properties.put(PROP_HASH_ATTRIBUTE, result.getHashAttribute());
            properties.put(PROP_HASH_VALUE, result.getHashValue());
            fireEvent(eventLogger, GrowthBookEvent.EXPERIMENT_VIEWED, properties, userContext,
                    options.getEventLoggerExecutor());
        }
    }

    /**
     * Fires the exposure sinks for a previously buffered deferred call. The wildcard is captured
     * into a single type variable so the experiment and its result share one value type.
     */
    public static <T> void fireExperimentViewed(Options options, DeferredTrackingCall<T> call) {
        fireExperimentViewed(options, call.getExperiment(), call.getResult(), call.getUserContext());
    }

    /**
     * Fires the exposure sinks for a buffered deferred call, including the registered plugins. Used by
     * the deferred-flush path so plugins receive the same {@code Experiment Viewed} events as the
     * immediate path; the plugin registry is guarded internally.
     */
    public static <T> void fireExperimentViewed(Options options,
                                                DeferredTrackingCall<T> call,
                                                @Nullable PluginRegistry pluginRegistry) {
        fireExperimentViewed(options, call.getExperiment(), call.getResult(), call.getUserContext());
        if (pluginRegistry != null) {
            pluginRegistry.fireExperimentViewed(call.getExperiment(), call.getResult());
        }
    }

    /**
     * Fires the {@link EventLogger} {@link GrowthBookEvent#FEATURE_EVALUATED} event (if set).
     * The {@link FeatureUsageCallbackWithUser} is fired separately at the feature evaluation sites.
     */
    public static void fireFeatureEvaluated(Options options,
                                            String featureKey,
                                            FeatureResult<?> result,
                                            UserContext userContext) {
        if (options == null) {
            return;
        }

        EventLogger eventLogger = options.getEventLogger();
        if (eventLogger == null) {
            return;
        }

        FeatureResultSource source = result.getSource();
        String ruleId = FeatureResultSource.DEFAULT_VALUE.equals(source)
                ? DEFAULT_RULE_ID
                : (result.getRuleId() != null ? result.getRuleId() : EMPTY_STRING);
        String variationId = result.getExperimentResult() != null
                ? result.getExperimentResult().getKey()
                : EMPTY_STRING;

        Map<String, Object> properties = new HashMap<>();
        properties.put(PROP_FEATURE, featureKey);
        properties.put(PROP_SOURCE, source != null ? source.toString() : null);
        properties.put(PROP_VALUE, result.getValue());
        properties.put(PROP_RULE_ID, ruleId);
        properties.put(PROP_VARIATION_ID, variationId);
        fireEvent(eventLogger, GrowthBookEvent.FEATURE_EVALUATED, properties, userContext,
                options.getEventLoggerExecutor());
    }

    /**
     * Fires an application-defined custom event on the configured {@link EventLogger} (if set),
     * guarded. A {@code null} property map is treated as empty.
     */
    public static void logEvent(Options options,
                                String eventName,
                                Map<String, Object> properties,
                                UserContext userContext) {
        if (options == null) {
            return;
        }
        EventLogger eventLogger = options.getEventLogger();
        if (eventLogger == null) {
            log.debug("logEvent('{}') was called but no EventLogger is configured; dropping the event.", eventName);
            return;
        }
        fireEvent(eventLogger, eventName, properties == null ? new HashMap<>() : properties, userContext,
                options.getEventLoggerExecutor());
    }

    private static void fireEvent(EventLogger eventLogger,
                                  String eventName,
                                  Map<String, Object> properties,
                                  UserContext userContext,
                                  @Nullable Executor executor) {
        try {
            if (executor != null) {
                executor.execute(() -> safeLog(eventLogger, eventName, properties, userContext));
            } else {
                safeLog(eventLogger, eventName, properties, userContext);
            }
        } catch (RuntimeException e) {
            log.warn("Event logger dispatch failed for event '{}'; ignoring.", eventName, e);
        }
    }

    private static void safeLog(EventLogger eventLogger,
                                String eventName,
                                Map<String, Object> properties,
                                UserContext userContext) {
        try {
            eventLogger.logEvent(eventName, properties, userContext);
        } catch (RuntimeException e) {
            log.warn("Event logger threw for event '{}'; ignoring.", eventName, e);
        }
    }
}
