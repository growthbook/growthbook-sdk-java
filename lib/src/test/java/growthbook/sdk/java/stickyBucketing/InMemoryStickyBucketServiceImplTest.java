package growthbook.sdk.java.stickyBucketing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import growthbook.sdk.java.model.StickyAssignmentsDocument;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class InMemoryStickyBucketServiceImplTest {

    private final InMemoryStickyBucketServiceImpl service = new InMemoryStickyBucketServiceImpl(new ConcurrentHashMap<>());

    @Test
    @DisplayName("Returns the saved document for its attribute name and value")
    void returnsSavedDocumentForAttribute() {
        // Given
        StickyAssignmentsDocument doc = document("id", "user-1", Collections.singletonMap("exp1__0", "control"));
        service.saveAssignments(doc);

        // When
        StickyAssignmentsDocument result = service.getAssignments("id", "user-1");

        // Then
        assertEquals(doc, result);
    }

    @Test
    @DisplayName("Returns null for an attribute value with no saved document")
    void returnsNullForUnknownAttributeValue() {
        // Given
        service.saveAssignments(document("id", "user-1", Collections.singletonMap("exp1__0", "control")));

        // When
        StickyAssignmentsDocument result = service.getAssignments("id", "user-2");

        // Then
        assertNull(result);
    }

    @Test
    @DisplayName("Does not find a document when attribute name and value are swapped")
    void doesNotFindDocumentWithSwappedNameAndValue() {
        // Given
        service.saveAssignments(document("id", "user-1", Collections.<String, String>emptyMap()));

        // When
        StickyAssignmentsDocument result = service.getAssignments("user-1", "id");

        // Then
        assertNull(result);
    }

    @Test
    @DisplayName("Returns every stored document matching the attributes, keyed by name||value")
    void returnsAllMatchingDocumentsKeyedByNameAndValue() {
        // Given
        StickyAssignmentsDocument idDoc = document("id", "user-1", Collections.<String, String>emptyMap());
        StickyAssignmentsDocument cookieDoc = document("cookie_id", "cookie-99", Collections.<String, String>emptyMap());
        service.saveAssignments(idDoc);
        service.saveAssignments(cookieDoc);
        Map<String, String> attributes = new HashMap<>();
        attributes.put("id", "user-1");
        attributes.put("cookie_id", "cookie-99");

        // When
        Map<String, StickyAssignmentsDocument> result = service.getAllAssignments(attributes);

        // Then
        Map<String, StickyAssignmentsDocument> expected = new HashMap<>();
        expected.put("id||user-1", idDoc);
        expected.put("cookie_id||cookie-99", cookieDoc);
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("Skips attributes that have no stored document")
    void skipsAttributesWithoutStoredDocument() {
        // Given
        StickyAssignmentsDocument idDoc = document("id", "user-1", Collections.<String, String>emptyMap());
        service.saveAssignments(idDoc);
        service.saveAssignments(document("cookie_id", "cookie-99", Collections.<String, String>emptyMap()));
        Map<String, String> attributes = new HashMap<>();
        attributes.put("id", "user-1");
        attributes.put("cookie_id", "cookie-100");

        // When
        Map<String, StickyAssignmentsDocument> result = service.getAllAssignments(attributes);

        // Then
        assertEquals(Collections.singletonMap("id||user-1", idDoc), result);
    }

    @Test
    @DisplayName("Returns no documents when no attribute matches")
    void returnsNoDocumentsWhenNoAttributeMatches() {
        // When
        Map<String, StickyAssignmentsDocument> result = service.getAllAssignments(Collections.singletonMap("id", "nobody"));

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Returns no documents for empty attributes")
    void returnsNoDocumentsForEmptyAttributes() {
        // Given
        service.saveAssignments(document("id", "user-1", Collections.<String, String>emptyMap()));

        // When
        Map<String, StickyAssignmentsDocument> result = service.getAllAssignments(Collections.<String, String>emptyMap());

        // Then
        assertTrue(result.isEmpty());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("backingStorages")
    @DisplayName("Writes saved documents through to the supplied backing storage without copying it")
    void writesSavedDocumentsThroughToSuppliedStorage(String storageType, Map<String, StickyAssignmentsDocument> backingStorage) {
        // Given
        InMemoryStickyBucketServiceImpl subject = new InMemoryStickyBucketServiceImpl(backingStorage);
        StickyAssignmentsDocument doc = document("id", "user-1", Collections.<String, String>emptyMap());

        // When
        subject.saveAssignments(doc);

        // Then
        assertEquals(1, backingStorage.size());
        assertSame(doc, backingStorage.get("id||user-1"));
    }

    static Stream<Arguments> backingStorages() {
        return Stream.of(
                Arguments.of("HashMap", new HashMap<String, StickyAssignmentsDocument>()),
                Arguments.of("ConcurrentHashMap", new ConcurrentHashMap<String, StickyAssignmentsDocument>()));
    }

    @Test
    @DisplayName("Sees documents added to the supplied backing storage after construction")
    void seesDocumentsAddedToSuppliedStorageAfterConstruction() {
        // Given
        Map<String, StickyAssignmentsDocument> backingStorage = new HashMap<>();
        InMemoryStickyBucketServiceImpl subject = new InMemoryStickyBucketServiceImpl(backingStorage);
        StickyAssignmentsDocument doc = document("id", "user-2", Collections.<String, String>emptyMap());
        backingStorage.put("id||user-2", doc);

        // When
        StickyAssignmentsDocument result = subject.getAssignments("id", "user-2");

        // Then
        assertSame(doc, result);
    }

    @Test
    @DisplayName("Falls back to empty storage when constructed with null")
    void fallsBackToEmptyStorageForNull() {
        // Given
        InMemoryStickyBucketServiceImpl subject = new InMemoryStickyBucketServiceImpl(null);
        StickyAssignmentsDocument doc = document("id", "user-4", Collections.<String, String>emptyMap());

        // When
        StickyAssignmentsDocument before = subject.getAssignments("id", "user-4");
        subject.saveAssignments(doc);

        // Then
        assertNull(before);
        assertSame(doc, subject.getAssignments("id", "user-4"));
    }

    private static StickyAssignmentsDocument document(String attributeName, String attributeValue, Map<String, String> assignments) {
        return new StickyAssignmentsDocument(attributeName, attributeValue, new HashMap<>(assignments));
    }
}
