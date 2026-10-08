package growthbook.sdk.java;

import growthbook.sdk.java.callback.TrackingCallback;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.GBContext;
import growthbook.sdk.java.model.TrackData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrowthBookDeferredTrackingTest {

    @Test
    @DisplayName("Verify: with deferral disabled the tracking callback fires immediately on run")
    void deferralDisabledFiresImmediately() {
        // Given
        AtomicInteger trackCount = new AtomicInteger();
        GBContext context = GBContext.builder()
                .attributesJson("{\"id\":\"user-1\"}")
                .trackingCallback(legacyTracker(trackCount))
                .build();
        GrowthBook subject = new GrowthBook(context);

        // When
        ExperimentResult<String> result = subject.run(experiment());

        // Then
        assertTrue(result.getInExperiment());
        assertEquals(1, trackCount.get());
        assertTrue(subject.getDeferredTrackingCalls().isEmpty());
    }

    @Test
    @DisplayName("Verify: with deferral enabled the exposure is buffered until fireDeferredTrackingCalls")
    void deferralEnabledBuffersUntilFlush() {
        // Given
        AtomicInteger trackCount = new AtomicInteger();
        GBContext context = GBContext.builder()
                .attributesJson("{\"id\":\"user-1\"}")
                .deferTrackingCalls(true)
                .trackingCallback(legacyTracker(trackCount))
                .build();
        GrowthBook subject = new GrowthBook(context);

        // When
        subject.run(experiment());

        // Then nothing fired yet, but the exposure is buffered
        assertEquals(0, trackCount.get());
        assertEquals(1, subject.getDeferredTrackingCalls().size());

        // When flushed
        subject.fireDeferredTrackingCalls();

        // Then it fires once and the buffer is drained
        assertEquals(1, trackCount.get());
        assertTrue(subject.getDeferredTrackingCalls().isEmpty());
    }

    @Test
    @DisplayName("Verify: logEvent fires the event logger immediately with the given name and properties")
    void logEventFiresEventLoggerImmediately() {
        // Given
        AtomicReference<String> eventName = new AtomicReference<>();
        AtomicReference<Map<String, Object>> eventProps = new AtomicReference<>();
        GBContext context = GBContext.builder()
                .attributesJson("{\"id\":\"user-1\"}")
                .eventLogger((name, props, user) -> {
                    eventName.set(name);
                    eventProps.set(props);
                })
                .build();
        GrowthBook subject = new GrowthBook(context);

        Map<String, Object> props = new HashMap<>();
        props.put("cart_size", 3);

        // When
        subject.logEvent("checkout_started", props);

        // Then
        assertEquals("checkout_started", eventName.get());
        assertEquals(3, eventProps.get().get("cart_size"));
    }

    @Test
    @DisplayName("Verify: deferred calls can be exported from one instance and replayed on another")
    void deferredCallsRoundTripAcrossInstances() {
        // Given a source instance that buffers an exposure
        GBContext source = GBContext.builder()
                .attributesJson("{\"id\":\"user-1\"}")
                .deferTrackingCalls(true)
                .trackingCallback(legacyTracker(new AtomicInteger()))
                .build();
        GrowthBook sourceBook = new GrowthBook(source);
        sourceBook.run(experiment());
        List<TrackData<?>> exported = sourceBook.getDeferredTrackingCalls();
        assertEquals(1, exported.size());

        // And a target instance that only replays
        AtomicInteger trackCount = new AtomicInteger();
        GBContext target = GBContext.builder()
                .attributesJson("{\"id\":\"user-1\"}")
                .deferTrackingCalls(true)
                .trackingCallback(legacyTracker(trackCount))
                .build();
        GrowthBook targetBook = new GrowthBook(target);

        // When
        targetBook.setDeferredTrackingCalls(exported);
        targetBook.fireDeferredTrackingCalls();

        // Then
        assertEquals(1, trackCount.get());
    }

    @Test
    @DisplayName("Verify: setEventLogger updates the sink used by later evaluations")
    void setEventLoggerUpdatesActiveSink() {
        // Given a book created without an event logger
        AtomicReference<String> eventName = new AtomicReference<>();
        GBContext context = GBContext.builder()
                .attributesJson("{\"id\":\"user-1\"}")
                .build();
        GrowthBook subject = new GrowthBook(context);

        // When a logger is set after construction
        subject.setEventLogger((name, props, user) -> eventName.set(name));
        subject.logEvent("later", new HashMap<>());

        // Then the new sink receives the event
        assertEquals("later", eventName.get());
    }

    @Test
    @DisplayName("Verify: single-context events dispatch through the configured executor")
    void singleContextEventsDispatchThroughExecutor() {
        // Given a counting (synchronous) executor
        AtomicInteger dispatches = new AtomicInteger();
        AtomicReference<String> eventName = new AtomicReference<>();
        Executor executor = task -> {
            dispatches.incrementAndGet();
            task.run();
        };
        GBContext context = GBContext.builder()
                .attributesJson("{\"id\":\"user-1\"}")
                .eventLoggerExecutor(executor)
                .eventLogger((name, props, user) -> eventName.set(name))
                .build();
        GrowthBook subject = new GrowthBook(context);

        // When
        subject.logEvent("evt", new HashMap<>());

        // Then
        assertEquals(1, dispatches.get());
        assertEquals("evt", eventName.get());
    }

    private Experiment<String> experiment() {
        return Experiment.<String>builder()
                .key("perf-experiment")
                .variations(new ArrayList<>(Arrays.asList("control", "variant")))
                .build();
    }

    private static TrackingCallback legacyTracker(AtomicInteger counter) {
        return new TrackingCallback() {
            @Override
            public <T> void onTrack(Experiment<T> experiment, ExperimentResult<T> result) {
                counter.incrementAndGet();
            }
        };
    }
}
