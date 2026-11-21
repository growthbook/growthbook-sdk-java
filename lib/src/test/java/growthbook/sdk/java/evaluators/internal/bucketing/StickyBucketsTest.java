package growthbook.sdk.java.evaluators.internal.bucketing;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import growthbook.sdk.java.model.GeneratedStickyBucketAssignmentDocModel;
import growthbook.sdk.java.model.StickyAssignmentsDocument;
import growthbook.sdk.java.model.StickyBucketVariation;
import growthbook.sdk.java.model.VariationMeta;
import growthbook.sdk.java.multiusermode.configurations.EvaluationContext;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StickyBucketsTest {

    private static final String USER_ATTRIBUTES = "{\"id\": \"user-1\", \"deviceId\": \"device-1\"}";
    private static final List<VariationMeta> META = Arrays.asList(
            VariationMeta.builder().key("control").build(),
            VariationMeta.builder().key("treatment").build());

    @Test
    @DisplayName("Returns no assignments when the user has no documents")
    void returnsNoAssignmentsWhenUserHasNoDocuments() {
        // Given
        EvaluationContext context = context(USER_ATTRIBUTES, null);

        // When
        Map<String, String> assignments = StickyBuckets.getStickyBucketAssignments(context, "id", "deviceId");

        // Then
        assertTrue(assignments.isEmpty());
    }

    @Test
    @DisplayName("Returns no assignments when no document matches the user's attributes")
    void returnsNoAssignmentsWhenNoDocumentMatches() {
        // Given
        Map<String, StickyAssignmentsDocument> docs = docs(doc("id", "user-2", "exp__0", "1"));
        EvaluationContext context = context(USER_ATTRIBUTES, docs);

        // When
        Map<String, String> assignments = StickyBuckets.getStickyBucketAssignments(context, "id", "deviceId");

        // Then
        assertTrue(assignments.isEmpty());
    }

    @Test
    @DisplayName("Merges the fallback attribute's document with the hash attribute's")
    void mergesFallbackDocumentWithHashAttributeDocument() {
        // Given
        Map<String, StickyAssignmentsDocument> docs = docs(
                doc("id", "user-1", "exp-a__0", "control"),
                doc("deviceId", "device-1", "exp-b__0", "treatment"));
        EvaluationContext context = context(USER_ATTRIBUTES, docs);

        // When
        Map<String, String> assignments = StickyBuckets.getStickyBucketAssignments(context, "id", "deviceId");

        // Then
        Map<String, String> expected = new HashMap<>();
        expected.put("exp-a__0", "control");
        expected.put("exp-b__0", "treatment");
        assertEquals(expected, assignments);
    }

    @Test
    @DisplayName("Lets the hash attribute's assignment win over the fallback attribute's")
    void hashAttributeAssignmentWinsOverFallback() {
        // Given
        Map<String, StickyAssignmentsDocument> docs = docs(
                doc("id", "user-1", "exp__0", "control"),
                doc("deviceId", "device-1", "exp__0", "treatment"));
        EvaluationContext context = context(USER_ATTRIBUTES, docs);

        // When
        Map<String, String> assignments = StickyBuckets.getStickyBucketAssignments(context, "id", "deviceId");

        // Then
        assertEquals(Collections.singletonMap("exp__0", "control"), assignments);
    }

    @Test
    @DisplayName("Ignores the fallback document when the user lacks the fallback attribute")
    void ignoresFallbackDocumentWhenFallbackAttributeMissing() {
        // Given
        Map<String, StickyAssignmentsDocument> docs = docs(
                doc("id", "user-1", "exp-a__0", "control"),
                doc("deviceId", "", "exp-b__0", "treatment"));
        EvaluationContext context = context("{\"id\": \"user-1\"}", docs);

        // When
        Map<String, String> assignments = StickyBuckets.getStickyBucketAssignments(context, "id", "deviceId");

        // Then
        assertEquals(Collections.singletonMap("exp-a__0", "control"), assignments);
    }

    @Test
    @DisplayName("Reads the id document when the experiment has no hash attribute")
    void readsIdDocumentWhenNoHashAttribute() {
        // Given
        Map<String, StickyAssignmentsDocument> docs = docs(doc("id", "user-1", "exp__0", "treatment"));
        EvaluationContext context = context(USER_ATTRIBUTES, docs);

        // When
        Map<String, String> assignments = StickyBuckets.getStickyBucketAssignments(context, null, null);

        // Then
        assertEquals(Collections.singletonMap("exp__0", "treatment"), assignments);
    }

    @Test
    @DisplayName("Blocks the user when they have an assignment for a bucket version below the minimum")
    void blocksUserWithAssignmentBelowMinimumBucketVersion() {
        // Given
        Map<String, StickyAssignmentsDocument> docs = docs(doc("id", "user-1", "exp__1", "control"));
        EvaluationContext context = context(USER_ATTRIBUTES, docs);

        // When
        StickyBucketVariation result = StickyBuckets.getStickyBucketVariation(context, "exp", "id", null, 2, 2, META);

        // Then
        assertEquals(-1, result.getVariation());
        assertTrue(result.getVersionIsBlocked());
    }

    @Test
    @DisplayName("Blocks the user when their assignment is for bucket version zero and the minimum is one")
    void blocksUserWithVersionZeroAssignmentWhenMinimumIsOne() {
        // Given
        Map<String, StickyAssignmentsDocument> docs = docs(doc("id", "user-1", "exp__0", "treatment"));
        EvaluationContext context = context(USER_ATTRIBUTES, docs);

        // When
        StickyBucketVariation result = StickyBuckets.getStickyBucketVariation(context, "exp", "id", null, 1, 1, META);

        // Then
        assertEquals(-1, result.getVariation());
        assertTrue(result.getVersionIsBlocked());
    }

    @Test
    @DisplayName("Does not block the user whose assignment is at the minimum bucket version")
    void doesNotBlockUserWithAssignmentAtMinimumVersion() {
        // Given
        Map<String, StickyAssignmentsDocument> docs = docs(doc("id", "user-1", "exp__2", "treatment"));
        EvaluationContext context = context(USER_ATTRIBUTES, docs);

        // When
        StickyBucketVariation result = StickyBuckets.getStickyBucketVariation(context, "exp", "id", null, 2, 2, META);

        // Then
        assertEquals(1, result.getVariation());
        assertNull(result.getVersionIsBlocked());
    }

    @Test
    @DisplayName("Returns no variation when the user has no assignment for the experiment")
    void returnsNoVariationWithoutAssignment() {
        // Given
        Map<String, StickyAssignmentsDocument> docs = docs(doc("id", "user-1", "other__0", "treatment"));
        EvaluationContext context = context(USER_ATTRIBUTES, docs);

        // When
        StickyBucketVariation result = StickyBuckets.getStickyBucketVariation(context, "exp", "id", null, null, null, META);

        // Then
        assertEquals(-1, result.getVariation());
        assertNull(result.getVersionIsBlocked());
    }

    @Test
    @DisplayName("Returns no variation when the stored variation key is unknown")
    void returnsNoVariationForUnknownVariationKey() {
        // Given
        Map<String, StickyAssignmentsDocument> docs = docs(doc("id", "user-1", "exp__0", "removed-variation"));
        EvaluationContext context = context(USER_ATTRIBUTES, docs);

        // When
        StickyBucketVariation result = StickyBuckets.getStickyBucketVariation(context, "exp", "id", null, 0, 0, META);

        // Then
        assertEquals(-1, result.getVariation());
        assertNull(result.getVersionIsBlocked());
    }

    @Test
    @DisplayName("Returns no variation when the experiment has no variation metadata")
    void returnsNoVariationWithoutMeta() {
        // Given
        Map<String, StickyAssignmentsDocument> docs = docs(doc("id", "user-1", "exp__0", "treatment"));
        EvaluationContext context = context(USER_ATTRIBUTES, docs);

        // When
        StickyBucketVariation result = StickyBuckets.getStickyBucketVariation(context, "exp", "id", null, 0, 0, null);

        // Then
        assertEquals(-1, result.getVariation());
        assertNull(result.getVersionIsBlocked());
    }

    @Test
    @DisplayName("Returns the metadata index of the stored variation key")
    void returnsMetaIndexOfStoredVariationKey() {
        // Given
        Map<String, StickyAssignmentsDocument> docs = docs(doc("id", "user-1", "exp__3", "treatment"));
        EvaluationContext context = context(USER_ATTRIBUTES, docs);

        // When
        StickyBucketVariation result = StickyBuckets.getStickyBucketVariation(context, "exp", "id", null, 3, 0, META);

        // Then
        assertEquals(1, result.getVariation());
        assertNull(result.getVersionIsBlocked());
    }

    @Test
    @DisplayName("Treats missing bucket versions as zero")
    void treatsMissingBucketVersionsAsZero() {
        // Given
        Map<String, StickyAssignmentsDocument> docs = docs(doc("id", "user-1", "exp__0", "control"));
        EvaluationContext context = context(USER_ATTRIBUTES, docs);

        // When
        StickyBucketVariation result = StickyBuckets.getStickyBucketVariation(context, "exp", null, null, null, null, META);

        // Then
        assertEquals(0, result.getVariation());
        assertNull(result.getVersionIsBlocked());
    }

    @Test
    @DisplayName("Resolves the variation from the fallback attribute's document")
    void resolvesVariationFromFallbackDocument() {
        // Given
        Map<String, StickyAssignmentsDocument> docs = docs(doc("deviceId", "device-1", "exp__0", "treatment"));
        EvaluationContext context = context("{\"deviceId\": \"device-1\"}", docs);

        // When
        StickyBucketVariation result = StickyBuckets.getStickyBucketVariation(context, "exp", "id", "deviceId", 0, 0, META);

        // Then
        assertEquals(1, result.getVariation());
    }

    @Test
    @DisplayName("Builds the experiment key from the key and the bucket version, zero when missing")
    void buildsExperimentKeyFromKeyAndBucketVersion() {
        // Given
        // When
        String withVersion = StickyBuckets.getStickyBucketExperimentKey("exp", 4);
        String withoutVersion = StickyBuckets.getStickyBucketExperimentKey("exp", null);

        // Then
        assertEquals("exp__4", withVersion);
        assertEquals("exp__0", withoutVersion);
    }

    @Test
    @DisplayName("Reports an unchanged document and reuses its assignments when nothing is new")
    void reportsUnchangedDocumentWhenNothingIsNew() {
        // Given
        StickyAssignmentsDocument existing = doc("id", "user-1", "exp__0", "control");
        Map<String, StickyAssignmentsDocument> docs = docs(existing);

        // When
        GeneratedStickyBucketAssignmentDocModel result = StickyBuckets.generateStickyBucketAssignmentDoc(
                docs, "id", "user-1", Collections.singletonMap("exp__0", "control"));

        // Then
        assertFalse(result.isChanged());
        assertEquals("id||user-1", result.getKey());
        assertSame(existing.getAssignments(), result.getStickyAssignmentsDocument().getAssignments());
    }

    @Test
    @DisplayName("Reports a changed document with merged assignments when an assignment is new")
    void reportsChangedDocumentWhenAssignmentIsNew() {
        // Given
        Map<String, StickyAssignmentsDocument> docs = docs(doc("id", "user-1", "exp-a__0", "control"));

        // When
        GeneratedStickyBucketAssignmentDocModel result = StickyBuckets.generateStickyBucketAssignmentDoc(
                docs, "id", "user-1", Collections.singletonMap("exp-b__0", "treatment"));

        // Then
        Map<String, String> expected = new HashMap<>();
        expected.put("exp-a__0", "control");
        expected.put("exp-b__0", "treatment");
        assertTrue(result.isChanged());
        assertEquals(new StickyAssignmentsDocument("id", "user-1", expected), result.getStickyAssignmentsDocument());
    }

    @Test
    @DisplayName("Reports a changed document when an assignment's variation differs")
    void reportsChangedDocumentWhenVariationDiffers() {
        // Given
        StickyAssignmentsDocument existing = doc("id", "user-1", "exp__0", "control");
        Map<String, StickyAssignmentsDocument> docs = docs(existing);

        // When
        GeneratedStickyBucketAssignmentDocModel result = StickyBuckets.generateStickyBucketAssignmentDoc(
                docs, "id", "user-1", Collections.singletonMap("exp__0", "treatment"));

        // Then
        assertTrue(result.isChanged());
        assertEquals(Collections.singletonMap("exp__0", "treatment"), result.getStickyAssignmentsDocument().getAssignments());
        assertEquals(Collections.singletonMap("exp__0", "control"), existing.getAssignments());
    }

    @Test
    @DisplayName("Creates a changed document when the user has no documents")
    void createsChangedDocumentWhenUserHasNoDocuments() {
        // Given
        Map<String, StickyAssignmentsDocument> docs = null;

        // When
        GeneratedStickyBucketAssignmentDocModel result = StickyBuckets.generateStickyBucketAssignmentDoc(
                docs, "deviceId", "device-1", Collections.singletonMap("exp__0", "control"));

        // Then
        assertTrue(result.isChanged());
        assertEquals("deviceId||device-1", result.getKey());
        assertEquals(
                new StickyAssignmentsDocument("deviceId", "device-1", Collections.singletonMap("exp__0", "control")),
                result.getStickyAssignmentsDocument());
    }

    @Test
    @DisplayName("Reports an unchanged empty document when there are no documents and no assignments")
    void reportsUnchangedEmptyDocumentWithoutDocumentsOrAssignments() {
        // Given
        Map<String, StickyAssignmentsDocument> docs = null;

        // When
        GeneratedStickyBucketAssignmentDocModel result = StickyBuckets.generateStickyBucketAssignmentDoc(
                docs, "id", "user-1", Collections.<String, String>emptyMap());

        // Then
        assertFalse(result.isChanged());
        assertTrue(result.getStickyAssignmentsDocument().getAssignments().isEmpty());
    }

    private static EvaluationContext context(String attributesJson, Map<String, StickyAssignmentsDocument> docs) {
        JsonObject attributes = JsonParser.parseString(attributesJson).getAsJsonObject();
        UserContext user = UserContext.builder()
                .attributes(attributes)
                .stickyBucketAssignmentDocs(docs)
                .build();
        return new EvaluationContext(null, user, new EvaluationContext.StackContext(), null);
    }

    private static StickyAssignmentsDocument doc(String attributeName, String attributeValue, String experimentKey, String variationKey) {
        Map<String, String> assignments = new HashMap<>();
        assignments.put(experimentKey, variationKey);
        return new StickyAssignmentsDocument(attributeName, attributeValue, assignments);
    }

    private static Map<String, StickyAssignmentsDocument> docs(StickyAssignmentsDocument... documents) {
        Map<String, StickyAssignmentsDocument> docs = new HashMap<>();
        for (StickyAssignmentsDocument document : documents) {
            docs.put(document.getAttributeName() + "||" + document.getAttributeValue(), document);
        }
        return docs;
    }
}
