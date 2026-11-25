package growthbook.sdk.java.evaluators.internal.bucketing;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import growthbook.sdk.java.model.GBContext;
import growthbook.sdk.java.model.StickyAssignmentsDocument;
import growthbook.sdk.java.stickyBucketing.StickyBucketService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class StickyBucketPreloadTest {

    private static final String CONTEXT_FEATURES = "{"
            + "\"device-flag\": {\"defaultValue\": false, \"rules\": ["
            + "  {\"key\": \"exp-device\", \"variations\": [false, true], \"hashAttribute\": \"deviceId\", \"fallbackAttribute\": \"anonId\"}"
            + "]},"
            + "\"user-flag\": {\"defaultValue\": 0, \"rules\": ["
            + "  {\"key\": \"exp-user\", \"variations\": [0, 1]},"
            + "  {\"force\": 2, \"hashAttribute\": \"companyId\"}"
            + "]},"
            + "\"plain-flag\": {\"defaultValue\": \"a\"}"
            + "}";

    private static final String USER_ATTRIBUTES = "{"
            + "\"id\": \"user-1\", \"deviceId\": \"device-1\", \"anonId\": \"anon-1\", \"companyId\": \"company-1\""
            + "}";

    @Test
    @DisplayName("Leaves the documents untouched when no sticky bucket service is configured")
    void leavesDocumentsUntouchedWithoutService() {
        // Given
        Map<String, StickyAssignmentsDocument> existing = documents("id", "user-1");
        GBContext context = GBContext.builder()
                .featuresJson(CONTEXT_FEATURES)
                .stickyBucketAssignmentDocs(existing)
                .build();

        // When
        StickyBucketPreload.refreshStickyBuckets(context, null, attributes(USER_ATTRIBUTES));

        // Then
        assertSame(existing, context.getStickyBucketAssignmentDocs());
    }

    @Test
    @DisplayName("Stores empty documents without throwing when the sticky bucket service fails")
    void storesEmptyDocumentsWhenServiceFails() {
        // Given
        GBContext context = GBContext.builder()
                .featuresJson(CONTEXT_FEATURES)
                .stickyBucketService(new ThrowingStickyBucketService())
                .stickyBucketAssignmentDocs(documents("id", "user-1"))
                .build();

        // When
        StickyBucketPreload.refreshStickyBuckets(context, null, attributes(USER_ATTRIBUTES));

        // Then
        assertEquals(Collections.emptyMap(), context.getStickyBucketAssignmentDocs());
    }

    @Test
    @DisplayName("Stores the documents the sticky bucket service returns")
    void storesDocumentsReturnedByService() {
        // Given
        Map<String, StickyAssignmentsDocument> returned = documents("deviceId", "device-1");
        RecordingStickyBucketService service = new RecordingStickyBucketService(returned);
        GBContext context = GBContext.builder()
                .featuresJson(CONTEXT_FEATURES)
                .stickyBucketService(service)
                .build();

        // When
        StickyBucketPreload.refreshStickyBuckets(context, null, attributes(USER_ATTRIBUTES));

        // Then
        assertSame(returned, context.getStickyBucketAssignmentDocs());
    }

    @Test
    @DisplayName("Requests documents for the hash and fallback attributes of experiment rules only")
    void requestsDocumentsForExperimentRuleAttributes() {
        // Given
        RecordingStickyBucketService service = new RecordingStickyBucketService(new HashMap<String, StickyAssignmentsDocument>());
        GBContext context = GBContext.builder()
                .featuresJson(CONTEXT_FEATURES)
                .stickyBucketService(service)
                .build();

        // When
        StickyBucketPreload.refreshStickyBuckets(context, null, attributes(USER_ATTRIBUTES));

        // Then
        Map<String, String> expected = new HashMap<>();
        expected.put("id", "user-1");
        expected.put("deviceId", "device-1");
        expected.put("anonId", "anon-1");
        assertEquals(expected, service.requestedAttributes);
    }

    @Test
    @DisplayName("Uses the features of the payload instead of the context's when the payload has any")
    void usesPayloadFeaturesWhenPresent() {
        // Given
        String payload = "{\"features\": {\"account-flag\": {\"defaultValue\": false, \"rules\": ["
                + "{\"key\": \"exp-account\", \"variations\": [false, true], \"hashAttribute\": \"accountId\"}"
                + "]}}}";
        GBContext context = GBContext.builder().featuresJson(CONTEXT_FEATURES).build();

        // When
        Map<String, String> result = StickyBucketPreload.getStickyBucketAttributes(
                context, payload, attributes("{\"id\": \"user-1\", \"accountId\": \"account-1\"}"));

        // Then
        assertEquals(Collections.singletonMap("accountId", "account-1"), result);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"{\"features\": {}}", "{\"dateUpdated\": \"2024-01-01T00:00:00Z\"}"})
    @DisplayName("Uses the context's features when the payload has no features")
    void usesContextFeaturesWhenPayloadHasNone(String payload) {
        // Given
        GBContext context = GBContext.builder()
                .featuresJson("{\"flag\": {\"rules\": [{\"variations\": [0, 1]}]}}")
                .build();

        // When
        Map<String, String> result = StickyBucketPreload.getStickyBucketAttributes(context, payload, attributes(USER_ATTRIBUTES));

        // Then
        assertEquals(Collections.singletonMap("id", "user-1"), result);
    }

    @Test
    @DisplayName("Maps an identifier attribute the user lacks to an empty value")
    void mapsMissingIdentifierAttributeToEmptyValue() {
        // Given
        GBContext context = GBContext.builder().featuresJson(CONTEXT_FEATURES).build();

        // When
        Map<String, String> result = StickyBucketPreload.getStickyBucketAttributes(
                context, null, attributes("{\"id\": \"user-1\"}"));

        // Then
        Map<String, String> expected = new HashMap<>();
        expected.put("id", "user-1");
        expected.put("deviceId", "");
        expected.put("anonId", "");
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("Returns no identifier attributes when there are no features")
    void returnsNoIdentifierAttributesWithoutFeatures() {
        // Given
        GBContext context = GBContext.builder().build();

        // When
        Map<String, String> result = StickyBucketPreload.getStickyBucketAttributes(context, null, attributes(USER_ATTRIBUTES));

        // Then
        assertEquals(Collections.emptyMap(), result);
    }

    private static JsonObject attributes(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static Map<String, StickyAssignmentsDocument> documents(String attributeName, String attributeValue) {
        Map<String, StickyAssignmentsDocument> docs = new HashMap<>();
        docs.put(attributeName + "||" + attributeValue, new StickyAssignmentsDocument(
                attributeName, attributeValue, Collections.singletonMap("exp__0", "control")));
        return docs;
    }

    private static final class RecordingStickyBucketService implements StickyBucketService {
        private final Map<String, StickyAssignmentsDocument> toReturn;
        private Map<String, String> requestedAttributes;

        private RecordingStickyBucketService(Map<String, StickyAssignmentsDocument> toReturn) {
            this.toReturn = toReturn;
        }

        @Override
        public StickyAssignmentsDocument getAssignments(String attributeName, String attributeValue) {
            return null;
        }

        @Override
        public void saveAssignments(StickyAssignmentsDocument doc) {
        }

        @Override
        public Map<String, StickyAssignmentsDocument> getAllAssignments(Map<String, String> attributes) {
            requestedAttributes = new HashMap<>(attributes);
            return toReturn;
        }
    }

    private static final class ThrowingStickyBucketService implements StickyBucketService {
        @Override
        public StickyAssignmentsDocument getAssignments(String attributeName, String attributeValue) {
            throw new IllegalStateException("store unavailable");
        }

        @Override
        public void saveAssignments(StickyAssignmentsDocument doc) {
            throw new IllegalStateException("store unavailable");
        }

        @Override
        public Map<String, StickyAssignmentsDocument> getAllAssignments(Map<String, String> attributes) {
            throw new IllegalStateException("store unavailable");
        }
    }
}
