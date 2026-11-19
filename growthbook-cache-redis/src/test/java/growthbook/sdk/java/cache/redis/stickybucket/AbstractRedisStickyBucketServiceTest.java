package growthbook.sdk.java.cache.redis.stickybucket;

import growthbook.sdk.java.exception.StickyBucketServiceException;
import growthbook.sdk.java.model.StickyAssignmentsDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbstractRedisStickyBucketServiceTest {

    @Test
    @DisplayName("Verify: two concurrent saves from the same starting document keep both assignments")
    void concurrentSavesFromStaleCopiesDoNotEraseEachOther() {
        // Given: both requests loaded the same starting document that already held exp0
        Map<String, String> store = new HashMap<>();
        InMemoryRedisStickyBucketService service = new InMemoryRedisStickyBucketService(store, "");
        store.put("id||123", json(document("id", "123", single("exp0", "control"))));

        // When: each request adds a different experiment onto its own stale copy and saves
        service.saveAssignments(document("id", "123", pair("exp0", "control", "exp1", "a")));
        service.saveAssignments(document("id", "123", pair("exp0", "control", "exp2", "b")));

        // Then: the second save merged onto the current document rather than overwriting it
        StickyAssignmentsDocument merged = service.getAssignments("id", "123");
        assertEquals("control", merged.getAssignments().get("exp0"));
        assertEquals("a", merged.getAssignments().get("exp1"));
        assertEquals("b", merged.getAssignments().get("exp2"));
    }

    @Test
    @DisplayName("Verify: a later save wins on a conflicting key while keeping the other assignments")
    void incomingAssignmentsWinOnConflict() {
        // Given
        Map<String, String> store = new HashMap<>();
        InMemoryRedisStickyBucketService service = new InMemoryRedisStickyBucketService(store, "");
        store.put("id||123", json(document("id", "123", single("exp0", "control"))));

        // When
        service.saveAssignments(document("id", "123", single("exp0", "treatment")));

        // Then
        assertEquals("treatment", service.getAssignments("id", "123").getAssignments().get("exp0"));
    }

    @Test
    @DisplayName("Verify: saveAssignments rejects a document whose assignments map is null")
    void saveRejectsNullAssignments() {
        // Given
        Map<String, String> store = new HashMap<>();
        InMemoryRedisStickyBucketService service = new InMemoryRedisStickyBucketService(store, "");

        // When & Then
        assertThrows(StickyBucketServiceException.class,
                () -> service.saveAssignments(new StickyAssignmentsDocument("id", "123", null)));
        assertTrue(store.isEmpty());
    }

    @Test
    @DisplayName("Verify: getAssignments rejects a stored document missing its assignments map")
    void readRejectsIncompleteDocument() {
        // Given: an incomplete payload with no assignments field
        Map<String, String> store = new HashMap<>();
        store.put("id||123", "{\"attributeName\":\"id\",\"attributeValue\":\"123\"}");
        InMemoryRedisStickyBucketService service = new InMemoryRedisStickyBucketService(store, "");

        // When & Then
        assertThrows(StickyBucketServiceException.class, () -> service.getAssignments("id", "123"));
    }

    @Test
    @DisplayName("Verify: saveAssignments fails when the compare-and-set never succeeds")
    void saveThrowsAfterExhaustingRetries() {
        // Given: a service whose compare-and-set always loses the race
        AbstractRedisStickyBucketService service = new AbstractRedisStickyBucketService("") {
            @Override
            protected String read(String redisKey) {
                return json(document("id", "123", single("exp0", "control")));
            }

            @Override
            protected boolean compareAndSet(String redisKey, String expected, String updated) {
                return false;
            }

            @Override
            protected List<String> readMany(List<String> redisKeys) {
                return Collections.emptyList();
            }
        };

        // When & Then
        assertThrows(StickyBucketServiceException.class,
                () -> service.saveAssignments(document("id", "123", single("exp1", "a"))));
    }

    private static StickyAssignmentsDocument document(String name, String value, Map<String, String> assignments) {
        return new StickyAssignmentsDocument(name, value, assignments);
    }

    private static Map<String, String> single(String key, String value) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(key, value);
        return map;
    }

    private static Map<String, String> pair(String k1, String v1, String k2, String v2) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put(k1, v1);
        map.put(k2, v2);
        return map;
    }

    private static String json(StickyAssignmentsDocument doc) {
        return growthbook.sdk.java.util.GrowthBookJsonUtils.getInstance().gson.toJson(doc);
    }

    private static final class InMemoryRedisStickyBucketService extends AbstractRedisStickyBucketService {
        private final Map<String, String> store;

        InMemoryRedisStickyBucketService(Map<String, String> store, String keyPrefix) {
            super(keyPrefix);
            this.store = store;
        }

        @Override
        protected String read(String redisKey) {
            return store.get(redisKey);
        }

        @Override
        protected boolean compareAndSet(String redisKey, String expected, String updated) {
            String current = store.get(redisKey);
            boolean matches = expected == null ? current == null : expected.equals(current);
            if (!matches) {
                return false;
            }
            store.put(redisKey, updated);
            return true;
        }

        @Override
        protected List<String> readMany(List<String> redisKeys) {
            List<String> values = new ArrayList<>(redisKeys.size());
            for (String redisKey : redisKeys) {
                values.add(store.get(redisKey));
            }
            return values;
        }
    }
}
