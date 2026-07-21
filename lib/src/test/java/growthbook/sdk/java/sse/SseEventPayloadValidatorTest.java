package growthbook.sdk.java.sse;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SseEventPayloadValidatorTest {

    @Test
    void rejectsEmptyPayloads() {
        assertFalse(SseEventPayloadValidator.isValidFeaturePayload("features", ""));
        assertFalse(SseEventPayloadValidator.isValidFeaturePayload("features", "   "));
        assertFalse(SseEventPayloadValidator.isValidFeaturePayload("features", null));
    }

    @Test
    void acceptsNonEmptyPayloads() {
        assertTrue(SseEventPayloadValidator.isValidFeaturePayload(
                "features",
                "{\"features\":{\"test\":{\"defaultValue\":true}}}"
        ));
    }

    @Test
    void rejectsHeartbeatEvents() {
        String payload = "{\"features\":{\"test\":{\"defaultValue\":true}}}";

        assertFalse(SseEventPayloadValidator.isValidFeaturePayload("heartbeat", payload));
        assertFalse(SseEventPayloadValidator.isValidFeaturePayload("keepalive", payload));
        assertFalse(SseEventPayloadValidator.isValidFeaturePayload("ping", payload));
    }
}
