package growthbook.sdk.java.evaluators;

import com.google.gson.JsonObject;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.FeatureResultSource;
import growthbook.sdk.java.model.TrackData;
import growthbook.sdk.java.multiusermode.DeferredTrackingBuffer;
import growthbook.sdk.java.multiusermode.ExperimentTracker;
import growthbook.sdk.java.multiusermode.configurations.EvaluationContext;
import growthbook.sdk.java.multiusermode.configurations.GlobalContext;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.multiusermode.usage.EventLoggerDispatch;
import growthbook.sdk.java.multiusermode.usage.GrowthBookEvent;
import growthbook.sdk.java.multiusermode.usage.TrackingCallbackWithUser;
import growthbook.sdk.java.plugin.GrowthBookPlugin;
import growthbook.sdk.java.plugin.PluginRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventLoggerEvaluationTest {

    private final FeatureEvaluator featureEvaluator = new FeatureEvaluator();
    private final ExperimentEvaluator experimentEvaluator = new ExperimentEvaluator();

    @Test
    @DisplayName("Verify: an experiment exposure fires both the tracking callback and the Experiment Viewed event")
    void experimentExposureFiresTrackingCallbackAndEvent() {
        // Given
        AtomicInteger trackCount = new AtomicInteger();
        AtomicReference<String> eventName = new AtomicReference<>();
        AtomicReference<Map<String, Object>> eventProps = new AtomicReference<>();
        Options options = Options.builder()
                .enabled(true)
                .allowUrlOverrides(false)
                .trackingCallBackWithUser(tracker(trackCount))
                .eventLogger((name, props, user) -> {
                    eventName.set(name);
                    eventProps.set(props);
                })
                .build();
        EvaluationContext context = context(options, null);

        // When
        ExperimentResult<String> result = experimentEvaluator.evaluateExperiment(experiment(), context, null);

        // Then
        assertTrue(result.getInExperiment());
        assertEquals(1, trackCount.get());
        assertEquals(GrowthBookEvent.EXPERIMENT_VIEWED, eventName.get());
        assertEquals("perf-experiment", eventProps.get().get("experimentId"));
        assertEquals(result.getKey(), eventProps.get().get("variationId"));
        assertEquals("user-1", eventProps.get().get("hashValue"));
    }

    @Test
    @DisplayName("Verify: a feature evaluation fires the Feature Evaluated event with source and rule id")
    void featureEvaluationFiresFeatureEvaluatedEvent() {
        // Given
        AtomicReference<String> eventName = new AtomicReference<>();
        AtomicReference<Map<String, Object>> eventProps = new AtomicReference<>();
        Options options = Options.builder()
                .enabled(true)
                .allowUrlOverrides(false)
                .eventLogger((name, props, user) -> {
                    eventName.set(name);
                    eventProps.set(props);
                })
                .build();
        EvaluationContext context = context(options, null);

        // When
        FeatureResult<Boolean> result = featureEvaluator.evaluateFeature("missing-feature", context, Boolean.class);

        // Then
        assertNull(result.getValue());
        assertEquals(GrowthBookEvent.FEATURE_EVALUATED, eventName.get());
        assertEquals("missing-feature", eventProps.get().get("feature"));
        assertEquals("unknownFeature", eventProps.get().get("source"));
        assertEquals("", eventProps.get().get("ruleId"));
        assertEquals("", eventProps.get().get("variationId"));
    }

    @Test
    @DisplayName("Verify: a throwing event logger is swallowed and evaluation still returns a result")
    void throwingEventLoggerIsSwallowed() {
        // Given
        Options options = Options.builder()
                .enabled(true)
                .allowUrlOverrides(false)
                .eventLogger((name, props, user) -> {
                    throw new IllegalStateException("sink down");
                })
                .build();
        EvaluationContext context = context(options, null);

        // When
        FeatureResult<Boolean> result = featureEvaluator.evaluateFeature("missing-feature", context, Boolean.class);

        // Then
        assertNotNull(result);
        assertNull(result.getValue());
    }

    @Test
    @DisplayName("Verify: an exposure is buffered (not fired) when a deferred tracking buffer is present")
    void exposureIsBufferedWhenDeferred() {
        // Given
        AtomicInteger trackCount = new AtomicInteger();
        Options options = Options.builder()
                .enabled(true)
                .allowUrlOverrides(false)
                .trackingCallBackWithUser(tracker(trackCount))
                .build();
        DeferredTrackingBuffer buffer = new DeferredTrackingBuffer();
        EvaluationContext context = context(options, buffer);

        // When
        ExperimentResult<String> result = experimentEvaluator.evaluateExperiment(experiment(), context, null);

        // Then
        assertTrue(result.getInExperiment());
        assertEquals(0, trackCount.get());
        assertEquals(1, buffer.getCalls().size());

        // When the buffer is flushed
        buffer.flush(options, null);

        // Then the exposure fires exactly once and the buffer is drained
        assertEquals(1, trackCount.get());
        assertEquals(0, buffer.getCalls().size());
    }

    @Test
    @DisplayName("Verify: events are dispatched through the configured executor")
    void eventsDispatchedThroughExecutor() {
        // Given a counting (synchronous) executor
        AtomicInteger dispatches = new AtomicInteger();
        AtomicReference<String> eventName = new AtomicReference<>();
        Executor executor = task -> {
            dispatches.incrementAndGet();
            task.run();
        };
        Options options = Options.builder()
                .enabled(true)
                .allowUrlOverrides(false)
                .eventLoggerExecutor(executor)
                .eventLogger((name, props, user) -> eventName.set(name))
                .build();
        EvaluationContext context = context(options, null);

        // When
        featureEvaluator.evaluateFeature("missing-feature", context, Boolean.class);

        // Then the event went through the executor
        assertEquals(1, dispatches.get());
        assertEquals(GrowthBookEvent.FEATURE_EVALUATED, eventName.get());
    }

    @Test
    @DisplayName("Verify: Feature Evaluated carries variationId and ruleId for an experiment result")
    void featureEvaluatedCarriesExperimentDetails() {
        // Given a feature result produced by an experiment
        AtomicReference<Map<String, Object>> props = new AtomicReference<>();
        Options options = Options.builder()
                .eventLogger((name, p, user) -> props.set(p))
                .build();
        ExperimentResult<String> experimentResult = ExperimentResult.<String>builder()
                .key("v1")
                .variationId(1)
                .build();
        FeatureResult<String> result = FeatureResult.<String>builder()
                .value("treatment")
                .source(FeatureResultSource.EXPERIMENT)
                .ruleId("rule-1")
                .experimentResult(experimentResult)
                .build();

        // When
        EventLoggerDispatch.fireFeatureEvaluated(options, "promo", result, UserContext.builder().build());

        // Then
        assertEquals("promo", props.get().get("feature"));
        assertEquals("experiment", props.get().get("source"));
        assertEquals("treatment", props.get().get("value"));
        assertEquals("rule-1", props.get().get("ruleId"));
        assertEquals("v1", props.get().get("variationId"));
    }

    @Test
    @DisplayName("Verify: remote evaluation tracks fire exposures immediately and de-duplicate")
    void remoteEvalTracksFireAndDeduplicate() {
        // Given two identical tracks
        AtomicInteger trackCount = new AtomicInteger();
        Options options = Options.builder()
                .enabled(true)
                .allowUrlOverrides(false)
                .trackingCallBackWithUser(tracker(trackCount))
                .build();
        EvaluationContext context = context(options, null);
        ExperimentResult<String> result = ExperimentResult.<String>builder()
                .variationId(1).hashAttribute("id").hashValue("user-1").key("v1").build();
        List<TrackData<String>> tracks = Arrays.asList(
                new TrackData<>(experiment(), result),
                new TrackData<>(experiment(), result));

        // When
        experimentEvaluator.fireRemoteEvaluationTracks(tracks, context);

        // Then the exposure fires only once
        assertEquals(1, trackCount.get());
    }

    @Test
    @DisplayName("Verify: remote evaluation tracks are buffered when the context defers tracking")
    void remoteEvalTracksBufferedWhenDeferred() {
        // Given a deferred context
        AtomicInteger trackCount = new AtomicInteger();
        Options options = Options.builder()
                .enabled(true)
                .allowUrlOverrides(false)
                .trackingCallBackWithUser(tracker(trackCount))
                .build();
        DeferredTrackingBuffer buffer = new DeferredTrackingBuffer();
        EvaluationContext context = context(options, buffer);
        ExperimentResult<String> result = ExperimentResult.<String>builder()
                .variationId(1).hashAttribute("id").hashValue("user-1").key("v1").build();

        // When
        experimentEvaluator.fireRemoteEvaluationTracks(
                Collections.singletonList(new TrackData<>(experiment(), result)), context);

        // Then nothing fires yet; the exposure is buffered
        assertEquals(0, trackCount.get());
        assertEquals(1, buffer.getCalls().size());
    }

    @Test
    @DisplayName("Verify: a throwing tracking callback is swallowed and evaluation still returns")
    void throwingTrackingCallbackIsSwallowed() {
        // Given a tracking callback that throws
        Options options = Options.builder()
                .enabled(true)
                .allowUrlOverrides(false)
                .trackingCallBackWithUser(new TrackingCallbackWithUser() {
                    @Override
                    public <T> void onTrack(Experiment<T> experiment, ExperimentResult<T> result, UserContext user) {
                        throw new IllegalStateException("tracking down");
                    }
                })
                .build();
        EvaluationContext context = context(options, null);
        context.setSuppressTrackingErrors(true);

        // When & Then evaluation still completes
        ExperimentResult<String> result = experimentEvaluator.evaluateExperiment(experiment(), context, null);
        assertTrue(result.getInExperiment());
    }

    @Test
    @DisplayName("A throwing tracking callback does not stop the Experiment Viewed event or plugins")
    void throwingTrackingCallbackDoesNotBlockEventLoggerOrPlugins() {
        // Given a throwing callback, an event logger and a plugin, as on the multi-user client
        AtomicInteger callbackCalls = new AtomicInteger();
        List<String> loggedEvents = new ArrayList<>();
        AtomicInteger pluginCalls = new AtomicInteger();
        EvaluationContext context = context(throwingCallbackOptions(callbackCalls, loggedEvents), null);
        context.setPluginRegistry(countingPlugins(pluginCalls));
        context.setSuppressTrackingErrors(true);

        // When
        ExperimentResult<String> result = experimentEvaluator.evaluateExperiment(experiment(), context, null);

        // Then
        assertTrue(result.getInExperiment());
        assertEquals(1, callbackCalls.get());
        assertEquals(Collections.singletonList(GrowthBookEvent.EXPERIMENT_VIEWED), loggedEvents);
        assertEquals(1, pluginCalls.get());
    }

    @Test
    @DisplayName("A retried exposure re-runs only the failed tracking callback, not the event logger or plugins")
    void retriedExposureDoesNotDuplicateEventLoggerOrPlugins() {
        // Given
        AtomicInteger callbackCalls = new AtomicInteger();
        List<String> loggedEvents = new ArrayList<>();
        AtomicInteger pluginCalls = new AtomicInteger();
        EvaluationContext context = context(throwingCallbackOptions(callbackCalls, loggedEvents), null);
        context.setPluginRegistry(countingPlugins(pluginCalls));
        context.setSuppressTrackingErrors(true);

        // When the same exposure is evaluated again after the callback failed
        experimentEvaluator.evaluateExperiment(experiment(), context, null);
        experimentEvaluator.evaluateExperiment(experiment(), context, null);

        // Then the callback is retried, while the event and the plugin fired once
        assertEquals(2, callbackCalls.get());
        assertEquals(Collections.singletonList(GrowthBookEvent.EXPERIMENT_VIEWED), loggedEvents);
        assertEquals(1, pluginCalls.get());
    }

    @Test
    @DisplayName("A callback that fails once and then succeeds leaves the event logged exactly once")
    void callbackRecoveringOnRetryLogsEventOnce() {
        // Given a callback that fails on its first call only
        AtomicInteger callbackCalls = new AtomicInteger();
        List<String> loggedEvents = new ArrayList<>();
        Options options = Options.builder()
                .enabled(true)
                .allowUrlOverrides(false)
                .trackingCallBackWithUser(new TrackingCallbackWithUser() {
                    @Override
                    public <T> void onTrack(Experiment<T> experiment, ExperimentResult<T> result, UserContext user) {
                        if (callbackCalls.incrementAndGet() == 1) {
                            throw new IllegalStateException("tracking down");
                        }
                    }
                })
                .eventLogger((name, properties, user) -> loggedEvents.add(name))
                .build();
        EvaluationContext context = context(options, null);
        context.setSuppressTrackingErrors(true);

        // When the exposure is evaluated three times
        experimentEvaluator.evaluateExperiment(experiment(), context, null);
        experimentEvaluator.evaluateExperiment(experiment(), context, null);
        experimentEvaluator.evaluateExperiment(experiment(), context, null);

        // Then the callback ran until it succeeded, and the event was logged once
        assertEquals(2, callbackCalls.get());
        assertEquals(Collections.singletonList(GrowthBookEvent.EXPERIMENT_VIEWED), loggedEvents);
    }

    @Test
    @DisplayName("A successfully tracked exposure takes a single tracker entry")
    void successfulExposureUsesOneTrackerEntry() {
        // Given a shared tracker that holds a single entry
        AtomicInteger callbackCalls = new AtomicInteger();
        List<String> loggedEvents = new ArrayList<>();
        Options options = Options.builder()
                .enabled(true)
                .allowUrlOverrides(false)
                .trackingCallBackWithUser(tracker(callbackCalls))
                .eventLogger((name, properties, user) -> loggedEvents.add(name))
                .build();
        EvaluationContext context = context(options, null);
        context.setSharedExperimentTracker(new ExperimentTracker(1));

        // When the same exposure is evaluated twice
        experimentEvaluator.evaluateExperiment(experiment(), context, null);
        experimentEvaluator.evaluateExperiment(experiment(), context, null);

        // Then it is still deduped: a second entry would have evicted the exposure's own key
        assertEquals(1, callbackCalls.get());
        assertEquals(Collections.singletonList(GrowthBookEvent.EXPERIMENT_VIEWED), loggedEvents);
    }

    @Test
    @DisplayName("When callback errors are rethrown, the Experiment Viewed event is still logged first")
    void rethrownCallbackFailureStillLogsEvent() {
        // Given errors are not suppressed, as on the single-context GrowthBook
        AtomicInteger callbackCalls = new AtomicInteger();
        List<String> loggedEvents = new ArrayList<>();
        EvaluationContext context = context(throwingCallbackOptions(callbackCalls, loggedEvents), null);

        // When & Then the callback failure still propagates, after the event was logged
        assertThrows(IllegalStateException.class,
                () -> experimentEvaluator.evaluateExperiment(experiment(), context, null));
        assertEquals(Collections.singletonList(GrowthBookEvent.EXPERIMENT_VIEWED), loggedEvents);
    }

    @Test
    @DisplayName("Verify: an executor that rejects the event task is swallowed and evaluation still returns")
    void rejectingExecutorIsSwallowed() {
        // Given an executor that always rejects
        Executor rejecting = task -> {
            throw new RejectedExecutionException("queue full");
        };
        Options options = Options.builder()
                .enabled(true)
                .allowUrlOverrides(false)
                .eventLoggerExecutor(rejecting)
                .eventLogger((name, props, user) -> { })
                .build();
        EvaluationContext context = context(options, null);

        // When & Then evaluation still returns a result
        FeatureResult<Boolean> result = featureEvaluator.evaluateFeature("missing-feature", context, Boolean.class);
        assertNotNull(result);
    }

    private static Options throwingCallbackOptions(AtomicInteger callbackCalls, List<String> loggedEvents) {
        return Options.builder()
                .enabled(true)
                .allowUrlOverrides(false)
                .trackingCallBackWithUser(new TrackingCallbackWithUser() {
                    @Override
                    public <T> void onTrack(Experiment<T> experiment, ExperimentResult<T> result, UserContext user) {
                        callbackCalls.incrementAndGet();
                        throw new IllegalStateException("tracking down");
                    }
                })
                .eventLogger((name, properties, user) -> loggedEvents.add(name))
                .build();
    }

    private static PluginRegistry countingPlugins(AtomicInteger pluginCalls) {
        return new PluginRegistry(Collections.<GrowthBookPlugin>singletonList(new GrowthBookPlugin() {
            @Override
            public <V> void onExperimentViewed(Experiment<V> experiment, ExperimentResult<V> result) {
                pluginCalls.incrementAndGet();
            }
        }));
    }

    private EvaluationContext context(Options options, DeferredTrackingBuffer buffer) {
        GlobalContext global = GlobalContext.builder()
                .features(Collections.emptyMap())
                .build();
        JsonObject attributes = new JsonObject();
        attributes.addProperty("id", "user-1");
        UserContext user = UserContext.builder().attributes(attributes).build();
        EvaluationContext context = new EvaluationContext(global, user, new EvaluationContext.StackContext(), options);
        context.setDeferredTracking(buffer);
        return context;
    }

    private Experiment<String> experiment() {
        return Experiment.<String>builder()
                .key("perf-experiment")
                .variations(new ArrayList<>(Arrays.asList("control", "variant")))
                .build();
    }

    private static TrackingCallbackWithUser tracker(AtomicInteger counter) {
        return new TrackingCallbackWithUser() {
            @Override
            public <T> void onTrack(Experiment<T> experiment,
                                    ExperimentResult<T> result,
                                    UserContext userContext) {
                counter.incrementAndGet();
            }
        };
    }
}
