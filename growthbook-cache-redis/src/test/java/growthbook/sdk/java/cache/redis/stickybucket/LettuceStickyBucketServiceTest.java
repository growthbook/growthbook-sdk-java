package growthbook.sdk.java.cache.redis.stickybucket;

import growthbook.sdk.java.exception.StickyBucketServiceException;
import growthbook.sdk.java.model.StickyAssignmentsDocument;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import io.lettuce.core.KeyValue;
import io.lettuce.core.RedisException;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LettuceStickyBucketServiceTest {

    @SuppressWarnings("unchecked")
    private final StatefulRedisConnection<String, String> connection = mock(StatefulRedisConnection.class);
    @SuppressWarnings("unchecked")
    private final RedisCommands<String, String> commands = mock(RedisCommands.class);

    @BeforeEach
    void setUp() {
        when(connection.sync()).thenReturn(commands);
    }

    @Test
    @DisplayName("Verify: getAssignments deserializes the stored document for a present key")
    void getAssignmentsReturnsStoredDocument() {
        // Given
        StickyAssignmentsDocument doc = document("id", "123", "exp__0", "control");
        when(commands.get("id||123")).thenReturn(json(doc));
        LettuceStickyBucketService service = new LettuceStickyBucketService(connection);

        // When
        StickyAssignmentsDocument result = service.getAssignments("id", "123");

        // Then
        assertEquals(doc, result);
    }

    @Test
    @DisplayName("Verify: getAssignments returns null for a missing key")
    void returnsNullForMissingKey() {
        // Given
        when(commands.get(anyString())).thenReturn(null);
        LettuceStickyBucketService service = new LettuceStickyBucketService(connection);

        // Then
        assertNull(service.getAssignments("id", "123"));
    }

    @Test
    @DisplayName("Verify: getAssignments returns null for null attribute inputs without touching Redis")
    void returnsNullForNullAttributeInputs() {
        // Given
        LettuceStickyBucketService service = new LettuceStickyBucketService(connection);

        // Then
        assertNull(service.getAssignments(null, "123"));
        assertNull(service.getAssignments("id", null));
        verify(commands, never()).get(anyString());
    }

    @Test
    @DisplayName("Verify: getAssignments surfaces a StickyBucketServiceException when Redis fails")
    void getAssignmentsThrowsWhenRedisFails() {
        // Given
        when(commands.get(anyString())).thenThrow(new RedisException("boom"));
        LettuceStickyBucketService service = new LettuceStickyBucketService(connection);

        // Then
        assertThrows(StickyBucketServiceException.class, () -> service.getAssignments("id", "123"));
    }

    @Test
    @DisplayName("Verify: getAssignments surfaces a StickyBucketServiceException for a malformed payload")
    void throwsOnMalformedPayload() {
        // Given
        when(commands.get("id||123")).thenReturn("}not-json{");
        LettuceStickyBucketService service = new LettuceStickyBucketService(connection);

        // Then
        assertThrows(StickyBucketServiceException.class, () -> service.getAssignments("id", "123"));
    }

    @Test
    @DisplayName("Verify: saveAssignments writes the JSON-encoded document under the user key")
    void saveAssignmentsWritesJsonUnderKey() {
        // Given
        StickyAssignmentsDocument doc = document("id", "123", "exp__0", "control");
        LettuceStickyBucketService service = new LettuceStickyBucketService(connection);

        // When
        service.saveAssignments(doc);

        // Then
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(commands).set(eq("id||123"), valueCaptor.capture());
        assertEquals(doc, GrowthBookJsonUtils.getInstance().gson
                .fromJson(valueCaptor.getValue(), StickyAssignmentsDocument.class));
    }

    @Test
    @DisplayName("Verify: saveAssignments ignores a null document or missing key attributes")
    void saveAssignmentsIgnoresNullDocumentOrAttributes() {
        // Given
        LettuceStickyBucketService service = new LettuceStickyBucketService(connection);

        // When
        service.saveAssignments(null);
        service.saveAssignments(new StickyAssignmentsDocument(null, "123", new HashMap<>()));
        service.saveAssignments(new StickyAssignmentsDocument("id", null, new HashMap<>()));

        // Then
        verify(commands, never()).set(anyString(), anyString());
    }

    @Test
    @DisplayName("Verify: saveAssignments surfaces a StickyBucketServiceException when Redis fails")
    void saveAssignmentsThrowsWhenRedisFails() {
        // Given
        when(commands.set(anyString(), anyString())).thenThrow(new RedisException("boom"));
        LettuceStickyBucketService service = new LettuceStickyBucketService(connection);
        StickyAssignmentsDocument doc = document("id", "123", "exp__0", "control");

        // When & Then
        assertThrows(StickyBucketServiceException.class, () -> service.saveAssignments(doc));
    }

    @Test
    @DisplayName("Verify: getAllAssignments reads keys in bulk and maps hits by their logical key")
    void getAllAssignmentsReadsInBulkKeyedByLogicalKey() {
        // Given
        StickyAssignmentsDocument idDoc = document("id", "123", "exp__0", "control");
        StickyAssignmentsDocument cookieDoc = document("cookie", "abc", "exp__1", "variation");
        when(commands.mget("id||123", "missing||x", "cookie||abc")).thenReturn(Arrays.asList(
                KeyValue.just("id||123", json(idDoc)),
                KeyValue.<String, String>empty("missing||x"),
                KeyValue.just("cookie||abc", json(cookieDoc))
        ));
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("id", "123");
        attributes.put("missing", "x");
        attributes.put("cookie", "abc");
        LettuceStickyBucketService service = new LettuceStickyBucketService(connection);

        // When
        Map<String, StickyAssignmentsDocument> result = service.getAllAssignments(attributes);

        // Then
        assertEquals(2, result.size());
        assertEquals(idDoc, result.get("id||123"));
        assertEquals(cookieDoc, result.get("cookie||abc"));
        verify(commands).mget("id||123", "missing||x", "cookie||abc");
    }

    @Test
    @DisplayName("Verify: getAllAssignments returns an empty map for null or empty attributes")
    void getAllAssignmentsReturnsEmptyForNullOrEmptyAttributes() {
        // Given
        LettuceStickyBucketService service = new LettuceStickyBucketService(connection);

        // Then
        assertTrue(service.getAllAssignments(null).isEmpty());
        assertTrue(service.getAllAssignments(Collections.<String, String>emptyMap()).isEmpty());
        verify(commands, never()).mget(any(String[].class));
    }

    @Test
    @DisplayName("Verify: getAllAssignments surfaces a StickyBucketServiceException when Redis fails")
    void getAllAssignmentsThrowsWhenRedisFails() {
        // Given
        when(commands.mget("id||123")).thenThrow(new RedisException("boom"));
        LettuceStickyBucketService service = new LettuceStickyBucketService(connection);
        Map<String, String> attributes = Collections.singletonMap("id", "123");

        // Then
        assertThrows(StickyBucketServiceException.class, () -> service.getAllAssignments(attributes));
    }

    @Test
    @DisplayName("Verify: a configured key prefix namespaces every Redis key")
    void appliesKeyPrefix() {
        // Given
        when(commands.get("gb:sticky:id||123")).thenReturn(json(document("id", "123", "exp__0", "control")));
        LettuceStickyBucketService service = new LettuceStickyBucketService(connection, "gb:sticky:");

        // When
        service.getAssignments("id", "123");
        service.saveAssignments(document("id", "123", "exp__0", "control"));

        // Then
        verify(commands).get("gb:sticky:id||123");
        verify(commands).set(eq("gb:sticky:id||123"), anyString());
    }

    @Test
    @DisplayName("Verify: the constructor rejects a null connection")
    void constructorRejectsNullConnection() {
        // When & Then
        assertThrows(NullPointerException.class, () -> new LettuceStickyBucketService(null));
    }

    private static StickyAssignmentsDocument document(String name, String value, String expKey, String variation) {
        Map<String, String> assignments = new HashMap<>();
        assignments.put(expKey, variation);
        return new StickyAssignmentsDocument(name, value, assignments);
    }

    private static String json(StickyAssignmentsDocument doc) {
        return GrowthBookJsonUtils.getInstance().gson.toJson(doc);
    }
}
