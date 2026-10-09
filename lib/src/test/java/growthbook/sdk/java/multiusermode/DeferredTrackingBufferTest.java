package growthbook.sdk.java.multiusermode;

import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.multiusermode.usage.TrackingCallbackWithUser;
import growthbook.sdk.java.plugin.GrowthBookPlugin;
import growthbook.sdk.java.plugin.PluginRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeferredTrackingBufferTest {

    private final UserContext user = UserContext.builder().build();

    @Test
    @DisplayName("Verify: identical exposures collapse to a single buffered call")
    void deduplicatesIdenticalExposures() {
        // Given
        DeferredTrackingBuffer buffer = new DeferredTrackingBuffer();

        // When
        buffer.add(experiment("exp"), result(1, "id", "u1", "v1"), user);
        buffer.add(experiment("exp"), result(1, "id", "u1", "v1"), user);

        // Then
        assertEquals(1, buffer.getCalls().size());
    }

    @Test
    @DisplayName("Verify: distinct exposures are kept in insertion order")
    void keepsDistinctExposuresInOrder() {
        // Given
        DeferredTrackingBuffer buffer = new DeferredTrackingBuffer();

        // When
        buffer.add(experiment("a"), result(0, "id", "u1", "v0"), user);
        buffer.add(experiment("b"), result(1, "id", "u1", "v1"), user);

        // Then
        List<DeferredTrackingCall<?>> calls = buffer.getCalls();
        assertEquals(2, calls.size());
        assertEquals("a", calls.get(0).getExperiment().getKey());
        assertEquals("b", calls.get(1).getExperiment().getKey());
    }

    @Test
    @DisplayName("Verify: flush dispatches each buffered exposure once and drains the buffer")
    void flushDispatchesAndClears() {
        // Given
        AtomicInteger trackCount = new AtomicInteger();
        Options options = Options.builder()
                .trackingCallBackWithUser(tracker(trackCount))
                .build();
        DeferredTrackingBuffer buffer = new DeferredTrackingBuffer();
        buffer.add(experiment("a"), result(0, "id", "u1", "v0"), user);
        buffer.add(experiment("b"), result(1, "id", "u1", "v1"), user);

        // When
        buffer.flush(options, null);

        // Then
        assertEquals(2, trackCount.get());
        assertTrue(buffer.getCalls().isEmpty());
    }

    @Test
    @DisplayName("Verify: flushing an empty buffer is a no-op")
    void flushEmptyBufferIsNoOp() {
        // Given
        AtomicInteger trackCount = new AtomicInteger();
        Options options = Options.builder()
                .trackingCallBackWithUser(tracker(trackCount))
                .build();
        DeferredTrackingBuffer buffer = new DeferredTrackingBuffer();

        // When
        buffer.flush(options, null);

        // Then
        assertEquals(0, trackCount.get());
    }

    @Test
    @DisplayName("Verify: setCalls replaces the buffer, re-deduplicates, and skips malformed entries")
    void setCallsReplacesDeduplicatesAndSkipsMalformed() {
        // Given
        DeferredTrackingBuffer buffer = new DeferredTrackingBuffer();
        List<DeferredTrackingCall<?>> incoming = new ArrayList<>();
        incoming.add(new DeferredTrackingCall<>(experiment("a"), result(0, "id", "u1", "v0"), user));
        incoming.add(new DeferredTrackingCall<>(experiment("a"), result(0, "id", "u1", "v0"), user));
        incoming.add(new DeferredTrackingCall<String>(null, null, user));

        // When
        buffer.setCalls(incoming);

        // Then
        assertEquals(1, buffer.getCalls().size());
        assertEquals("a", buffer.getCalls().get(0).getExperiment().getKey());
    }

    @Test
    @DisplayName("Verify: distinct exposures beyond the cap are dropped")
    void dropsDistinctExposuresBeyondCap() {
        // Given
        DeferredTrackingBuffer buffer = new DeferredTrackingBuffer();

        // When more than the cap of distinct exposures are added
        for (int i = 0; i < DeferredTrackingBuffer.MAX_CALLS + 5; i++) {
            buffer.add(experiment("exp-" + i), result(0, "id", "u1", "v0"), user);
        }

        // Then the buffer never exceeds the cap
        assertEquals(DeferredTrackingBuffer.MAX_CALLS, buffer.getCalls().size());
    }

    @Test
    @DisplayName("Verify: setCalls truncates at the cap")
    void setCallsTruncatesAtCap() {
        // Given
        DeferredTrackingBuffer buffer = new DeferredTrackingBuffer();
        List<DeferredTrackingCall<?>> many = new ArrayList<>();
        for (int i = 0; i < DeferredTrackingBuffer.MAX_CALLS + 5; i++) {
            many.add(new DeferredTrackingCall<>(experiment("exp-" + i), result(0, "id", "u1", "v0"), user));
        }

        // When
        buffer.setCalls(many);

        // Then
        assertEquals(DeferredTrackingBuffer.MAX_CALLS, buffer.getCalls().size());
    }

    @Test
    @DisplayName("Verify: setCalls with null clears the buffer")
    void setCallsWithNullClears() {
        // Given
        DeferredTrackingBuffer buffer = new DeferredTrackingBuffer();
        buffer.add(experiment("a"), result(0, "id", "u1", "v0"), user);

        // When
        buffer.setCalls(null);

        // Then
        assertTrue(buffer.getCalls().isEmpty());
    }

    @Test
    @DisplayName("Verify: flush delivers each buffered exposure to registered plugins")
    void flushDeliversToPlugins() {
        // Given
        AtomicInteger pluginViews = new AtomicInteger();
        PluginRegistry pluginRegistry = new PluginRegistry(Collections.singletonList(new GrowthBookPlugin() {
            @Override
            public <V> void onExperimentViewed(Experiment<V> experiment, ExperimentResult<V> result) {
                pluginViews.incrementAndGet();
            }
        }));
        Options options = Options.builder().build();
        DeferredTrackingBuffer buffer = new DeferredTrackingBuffer();
        buffer.add(experiment("a"), result(0, "id", "u1", "v0"), user);
        buffer.add(experiment("b"), result(1, "id", "u1", "v1"), user);

        // When
        buffer.flush(options, pluginRegistry);

        // Then
        assertEquals(2, pluginViews.get());
        assertTrue(buffer.getCalls().isEmpty());
    }

    @Test
    @DisplayName("Verify: buffering snapshots the exposure so later mutation of the experiment does not change it")
    void addSnapshotsExposureAgainstLaterMutation() {
        // Given
        DeferredTrackingBuffer buffer = new DeferredTrackingBuffer();
        Experiment<String> experiment = experiment("original");
        buffer.add(experiment, result(0, "id", "u1", "v0"), user);

        // When the caller reuses and mutates the experiment after buffering
        experiment.setKey("mutated");

        // Then the queued exposure keeps the value captured at add time
        assertEquals("original", buffer.getCalls().get(0).getExperiment().getKey());
    }

    @Test
    @DisplayName("Verify: an exposure buffered by a callback during flush does not break the flush and is kept for later")
    void flushToleratesExposureAddedByCallback() {
        // Given a tracking callback that buffers a new exposure into the same buffer mid-flush
        DeferredTrackingBuffer buffer = new DeferredTrackingBuffer();
        AtomicInteger fired = new AtomicInteger();
        Options options = Options.builder()
                .trackingCallBackWithUser(new TrackingCallbackWithUser() {
                    @Override
                    public <T> void onTrack(Experiment<T> experiment,
                                            ExperimentResult<T> result,
                                            UserContext userContext) {
                        if (fired.incrementAndGet() == 1) {
                            buffer.add(experiment("late"), result(9, "id", "u1", "v9"), user);
                        }
                    }
                })
                .build();
        buffer.add(experiment("a"), result(0, "id", "u1", "v0"), user);

        // When flushing (must not throw ConcurrentModificationException)
        buffer.flush(options, null);

        // Then the original exposure fired and the callback-added one remains for a later flush
        assertEquals(1, fired.get());
        assertEquals(1, buffer.getCalls().size());
        assertEquals("late", buffer.getCalls().get(0).getExperiment().getKey());
    }

    private Experiment<String> experiment(String key) {
        return Experiment.<String>builder()
                .key(key)
                .variations(new ArrayList<>(Arrays.asList("control", "variant")))
                .build();
    }

    private ExperimentResult<String> result(int variationId, String hashAttribute, String hashValue, String key) {
        return ExperimentResult.<String>builder()
                .inExperiment(true)
                .variationId(variationId)
                .hashAttribute(hashAttribute)
                .hashValue(hashValue)
                .key(key)
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
