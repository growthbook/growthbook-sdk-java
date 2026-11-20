package growthbook.sdk.java.sse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SseEventPayloadValidatorTest {

    private static final String PAYLOAD = "{\"features\":{\"feature-1\":{\"defaultValue\":true}}}";

    @Test
    @DisplayName("Accepts a non-empty payload on a features event")
    void acceptsNonEmptyPayloadOnFeaturesEvent() {
        // When
        boolean valid = SseEventPayloadValidator.isValidFeaturePayload("features", PAYLOAD);

        // Then
        assertTrue(valid);
    }

    @ParameterizedTest(name = "\"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("Rejects a blank payload")
    void rejectsBlankPayload(String data) {
        // When
        boolean valid = SseEventPayloadValidator.isValidFeaturePayload("features", data);

        // Then
        assertFalse(valid);
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"heartbeat", "keepalive", "ping", " PING "})
    @DisplayName("Rejects heartbeat events regardless of case and padding")
    void rejectsHeartbeatEvents(String eventType) {
        // When
        boolean valid = SseEventPayloadValidator.isValidFeaturePayload(eventType, PAYLOAD);

        // Then
        assertFalse(valid);
    }

    @ParameterizedTest(name = "\"{0}\"")
    @NullAndEmptySource
    @DisplayName("Treats a missing event type as a feature event")
    void treatsMissingEventTypeAsFeatureEvent(String eventType) {
        // When
        boolean valid = SseEventPayloadValidator.isValidFeaturePayload(eventType, PAYLOAD);

        // Then
        assertTrue(valid);
    }
}
