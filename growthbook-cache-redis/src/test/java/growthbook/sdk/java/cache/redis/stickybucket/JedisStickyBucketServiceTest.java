package growthbook.sdk.java.cache.redis.stickybucket;

import growthbook.sdk.java.exception.StickyBucketServiceException;
import growthbook.sdk.java.model.StickyAssignmentsDocument;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.exceptions.JedisException;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static growthbook.sdk.java.cache.redis.stickybucket.AbstractRedisStickyBucketService.COMPARE_AND_SET_SCRIPT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class JedisStickyBucketServiceTest {

    private Jedis jedis;
    private JedisPool pool;

    @BeforeEach
    void setUp() {
        pool = mock(JedisPool.class);
        jedis = mock(Jedis.class);
        when(pool.getResource()).thenReturn(jedis);
    }

    @Test
    @DisplayName("Verify: getAssignments deserializes the stored document for a present key")
    void getAssignmentsReturnsStoredDocument() {
        // Given
        StickyAssignmentsDocument doc = document("id", "123", "exp__0", "control");
        when(jedis.get("id||123")).thenReturn(json(doc));
        JedisStickyBucketService service = new JedisStickyBucketService(pool);

        // When
        StickyAssignmentsDocument result = service.getAssignments("id", "123");

        // Then
        assertEquals(doc, result);
    }

    @Test
    @DisplayName("Verify: getAssignments returns null for a missing key")
    void returnsNullForMissingKey() {
        // Given
        when(jedis.get(anyString())).thenReturn(null);
        JedisStickyBucketService service = new JedisStickyBucketService(pool);

        // Then
        assertNull(service.getAssignments("id", "123"));
    }

    @Test
    @DisplayName("Verify: getAssignments returns null for null attribute inputs without touching Redis")
    void returnsNullForNullAttributeInputs() {
        // Given
        JedisStickyBucketService service = new JedisStickyBucketService(pool);

        // Then
        assertNull(service.getAssignments(null, "123"));
        assertNull(service.getAssignments("id", null));
        verify(jedis, never()).get(anyString());
    }

    @Test
    @DisplayName("Verify: getAssignments surfaces a StickyBucketServiceException when Redis fails")
    void getAssignmentsThrowsWhenRedisFails() {
        // Given
        when(jedis.get(anyString())).thenThrow(new JedisException("boom"));
        JedisStickyBucketService service = new JedisStickyBucketService(pool);

        // Then
        assertThrows(StickyBucketServiceException.class, () -> service.getAssignments("id", "123"));
    }

    @Test
    @DisplayName("Verify: getAssignments surfaces a StickyBucketServiceException for a malformed payload")
    void throwsOnMalformedPayload() {
        // Given
        when(jedis.get("id||123")).thenReturn("}not-json{");
        JedisStickyBucketService service = new JedisStickyBucketService(pool);

        // Then
        assertThrows(StickyBucketServiceException.class, () -> service.getAssignments("id", "123"));
    }

    @Test
    @DisplayName("Verify: saveAssignments installs the JSON-encoded document under the user key via an atomic compare-and-set")
    void saveAssignmentsWritesJsonUnderKey() {
        // Given
        StickyAssignmentsDocument doc = document("id", "123", "exp__0", "control");
        when(jedis.get("id||123")).thenReturn(null);
        when(jedis.eval(eq(COMPARE_AND_SET_SCRIPT), eq(1), eq("id||123"), eq("nx"), eq(""), anyString(), anyString()))
                .thenReturn(1L);
        JedisStickyBucketService service = new JedisStickyBucketService(pool);

        // When
        service.saveAssignments(doc);

        // Then
        ArgumentCaptor<String> valueCaptor = ArgumentCaptor.forClass(String.class);
        verify(jedis).eval(eq(COMPARE_AND_SET_SCRIPT), eq(1), eq("id||123"), eq("nx"), eq(""), valueCaptor.capture(), eq("0"));
        assertEquals(doc, GrowthBookJsonUtils.getInstance().gson
                .fromJson(valueCaptor.getValue(), StickyAssignmentsDocument.class));
    }

    @Test
    @DisplayName("Verify: saveAssignments ignores a null document or missing key attributes")
    void saveAssignmentsIgnoresNullDocumentOrAttributes() {
        // Given
        JedisStickyBucketService service = new JedisStickyBucketService(pool);

        // When
        service.saveAssignments(null);
        service.saveAssignments(new StickyAssignmentsDocument(null, "123", new HashMap<>()));
        service.saveAssignments(new StickyAssignmentsDocument("id", null, new HashMap<>()));

        // Then
        verifyNoInteractions(jedis);
    }

    @Test
    @DisplayName("Verify: saveAssignments surfaces a StickyBucketServiceException when Redis fails")
    void saveAssignmentsThrowsWhenRedisFails() {
        // Given
        when(jedis.get(anyString())).thenReturn(null);
        when(jedis.eval(anyString(), anyInt(), anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenThrow(new JedisException("boom"));
        JedisStickyBucketService service = new JedisStickyBucketService(pool);
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
        when(jedis.mget("id||123", "missing||x", "cookie||abc"))
                .thenReturn(Arrays.asList(json(idDoc), null, json(cookieDoc)));
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("id", "123");
        attributes.put("missing", "x");
        attributes.put("cookie", "abc");
        JedisStickyBucketService service = new JedisStickyBucketService(pool);

        // When
        Map<String, StickyAssignmentsDocument> result = service.getAllAssignments(attributes);

        // Then
        assertEquals(2, result.size());
        assertEquals(idDoc, result.get("id||123"));
        assertEquals(cookieDoc, result.get("cookie||abc"));
        verify(jedis).mget("id||123", "missing||x", "cookie||abc");
    }

    @Test
    @DisplayName("Verify: getAllAssignments returns an empty map for null or empty attributes")
    void getAllAssignmentsReturnsEmptyForNullOrEmptyAttributes() {
        // Given
        JedisStickyBucketService service = new JedisStickyBucketService(pool);

        // Then
        assertTrue(service.getAllAssignments(null).isEmpty());
        assertTrue(service.getAllAssignments(Collections.<String, String>emptyMap()).isEmpty());
        verify(jedis, never()).mget(any(String[].class));
    }

    @Test
    @DisplayName("Verify: getAllAssignments surfaces a StickyBucketServiceException when Redis fails")
    void getAllAssignmentsThrowsWhenRedisFails() {
        // Given
        when(jedis.mget("id||123")).thenThrow(new JedisException("boom"));
        JedisStickyBucketService service = new JedisStickyBucketService(pool);
        Map<String, String> attributes = Collections.singletonMap("id", "123");

        // Then
        assertThrows(StickyBucketServiceException.class, () -> service.getAllAssignments(attributes));
    }

    @Test
    @DisplayName("Verify: a configured key prefix namespaces every Redis key")
    void appliesKeyPrefix() {
        // Given
        when(jedis.get("gb:sticky:id||123")).thenReturn(json(document("id", "123", "exp__0", "control")));
        JedisStickyBucketService service = new JedisStickyBucketService(pool, "gb:sticky:");

        when(jedis.eval(eq(COMPARE_AND_SET_SCRIPT), eq(1), eq("gb:sticky:id||123"), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(1L);

        // When
        service.getAssignments("id", "123");
        service.saveAssignments(document("id", "123", "exp__0", "control"));

        // Then
        verify(jedis, atLeastOnce()).get("gb:sticky:id||123");
        verify(jedis).eval(eq(COMPARE_AND_SET_SCRIPT), eq(1), eq("gb:sticky:id||123"), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Verify: the constructor rejects a null Jedis pool")
    void constructorRejectsNullPool() {
        // When & Then
        assertThrows(NullPointerException.class, () -> new JedisStickyBucketService(null));
    }

    @Test
    @DisplayName("Verify: with no TTL configured, the compare-and-set carries a 0 TTL (no expiry)")
    void saveWithoutTtlPassesZeroTtlArg() {
        // Given
        when(jedis.get("id||123")).thenReturn(null);
        when(jedis.eval(eq(COMPARE_AND_SET_SCRIPT), eq(1), eq("id||123"), eq("nx"), eq(""), anyString(), eq("0")))
                .thenReturn(1L);
        JedisStickyBucketService service = new JedisStickyBucketService(pool);

        // When
        service.saveAssignments(document("id", "123", "exp__0", "control"));

        // Then
        verify(jedis).eval(eq(COMPARE_AND_SET_SCRIPT), eq(1), eq("id||123"), eq("nx"), eq(""), anyString(), eq("0"));
    }

    @Test
    @DisplayName("Verify: a configured TTL is carried to the compare-and-set as milliseconds")
    void saveWithTtlPassesTtlMillisArg() {
        // Given
        when(jedis.get("id||123")).thenReturn(null);
        when(jedis.eval(eq(COMPARE_AND_SET_SCRIPT), eq(1), eq("id||123"), eq("nx"), eq(""), anyString(), eq("3600000")))
                .thenReturn(1L);
        JedisStickyBucketService service = new JedisStickyBucketService(pool, Duration.ofHours(1));

        // When
        service.saveAssignments(document("id", "123", "exp__0", "control"));

        // Then
        verify(jedis).eval(eq(COMPARE_AND_SET_SCRIPT), eq(1), eq("id||123"), eq("nx"), eq(""), anyString(), eq("3600000"));
    }

    @Test
    @DisplayName("Verify: the constructor rejects a zero or negative TTL")
    void constructorRejectsNonPositiveTtl() {
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> new JedisStickyBucketService(pool, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> new JedisStickyBucketService(pool, Duration.ofMillis(-1)));
        assertThrows(IllegalArgumentException.class, () -> new JedisStickyBucketService(pool, Duration.ofNanos(500)));
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
