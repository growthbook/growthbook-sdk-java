package growthbook.sdk.java.stickyBucketing;

import growthbook.sdk.java.model.StickyAssignmentsDocument;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class InMemoryStickyBucketServiceImplTest {
    private InMemoryStickyBucketServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new InMemoryStickyBucketServiceImpl(new ConcurrentHashMap<>());
    }

    @AfterEach
    void tearDown() {
        service = null;
    }

    @Test
    void getAssignments_returnDocument_whenExists() {
        Map<String, String> assignments = new HashMap<>();

        assignments.put("exp1__0", "control");
        StickyAssignmentsDocument doc = new StickyAssignmentsDocument("id", "user_1", assignments);
        service.saveAssignments(doc);

        StickyAssignmentsDocument result = service.getAssignments("id", "user_1");

        assertNotNull(result);
        assertEquals(doc.getAttributeName(), result.getAttributeName());
        assertEquals(doc.getAttributeValue(), result.getAttributeValue());
        assertEquals(doc.getAssignments(), result.getAssignments());

    }

    @Test
    void getAssignments_returnNull_whenNotExists() {
        Map<String, String> assignments = new HashMap<>();

        assignments.put("exp1__0", "control");
        StickyAssignmentsDocument doc = new StickyAssignmentsDocument("id", "user_1", assignments);
        service.saveAssignments(doc);

        StickyAssignmentsDocument result = service.getAssignments("id", "user_2");

        assertNull(result);
    }

    @Test
    void saveAssignments_storesUnderCorrectKey() {
        StickyAssignmentsDocument doc = new StickyAssignmentsDocument("id", "user_1", new HashMap<>());
        service.saveAssignments(doc);

        assertNotNull(service.getAssignments("id", "user_1"));
        assertNull(service.getAssignments("user_1", "id"));
    }

    @Test
    void saveAssignments_overwrites_existingDocument() {
        Map<String, String> assignments1 = new HashMap<>();
        assignments1.put("exp1__0", "control");
        service.saveAssignments(new StickyAssignmentsDocument("id", "user_1", assignments1));

        Map<String, String> assignments2 = new HashMap<>();
        assignments2.put("exp1__0", "variation");
        StickyAssignmentsDocument doc2 = new StickyAssignmentsDocument("id", "user_1", assignments2);
        service.saveAssignments(doc2);

        StickyAssignmentsDocument result = service.getAssignments("id", "user_1");
        assertEquals("variation", result.getAssignments().get("exp1__0"));
    }

    @Test
    void getAllAssignments_returnsMatchingDocuments() {
        service.saveAssignments(
                new StickyAssignmentsDocument("id", "user-1", new HashMap<>())
        );
        service.saveAssignments(
                new StickyAssignmentsDocument("cookie_id", "cookie-99", new HashMap<>())
        );

        Map<String, String> attributes = new HashMap<>();
        attributes.put("id", "user-1");
        attributes.put("cookie_id", "cookie-99");

        Map<String, StickyAssignmentsDocument> result = service.getAllAssignments(attributes);

        assertEquals(2, result.size());
        assertTrue(result.containsKey("id||user-1"));
        assertTrue(result.containsKey("cookie_id||cookie-99"));
    }

    @Test
    void getAllAssignments_skipsAttributes_withNoStoredDocument() {
        service.saveAssignments(
                new StickyAssignmentsDocument("id", "user-1", new HashMap<>())
        );
        service.saveAssignments(
                new StickyAssignmentsDocument("cookie_id", "cookie-99", new HashMap<>())
        );

        Map<String, String> attributes = new HashMap<>();
        attributes.put("id", "user-1");
        attributes.put("cookie_id", "cookie-100");

        Map<String, StickyAssignmentsDocument> result = service.getAllAssignments(attributes);

        assertEquals(1, result.size());
        assertTrue(result.containsKey("id||user-1"));
        assertFalse(result.containsKey("cookie_id||cookie-100"));
    }

    @Test
    void getAllAssignments_returnsEmpty_whenNoAttributesMatch() {
        Map<String, String> attributes = new HashMap<>();
        attributes.put("id", "nobody");

        Map<String, StickyAssignmentsDocument> result = service.getAllAssignments(attributes);

        assertTrue(result.isEmpty());
    }

    @Test
    void getAllAssignments_returnsEmpty_whenAttributesMapIsEmpty() {
        service.saveAssignments(new StickyAssignmentsDocument("id", "user-1", new HashMap<>()));

        Map<String, StickyAssignmentsDocument> result = service.getAllAssignments(new HashMap<>());

        assertTrue(result.isEmpty());
    }
}
