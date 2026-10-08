package growthbook.sdk.java.multiusermode;

import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.multiusermode.usage.TrackingCallbackWithUser;
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
        buffer.flush(options);

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
        buffer.flush(options);

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
