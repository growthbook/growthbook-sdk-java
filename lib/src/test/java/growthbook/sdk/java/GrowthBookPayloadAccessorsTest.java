package growthbook.sdk.java;

import com.google.gson.JsonObject;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.Feature;
import growthbook.sdk.java.model.GBContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrowthBookPayloadAccessorsTest {

    @Test
    @DisplayName("Verify: accessors return empty (not null) before any features are loaded")
    void accessorsReturnEmptyBeforeLoad() {
        // Given
        GrowthBook subject = new GrowthBook();

        // Then
        assertTrue(subject.getFeatures().isEmpty());
        assertTrue(subject.getExperiments().isEmpty());
        assertTrue(subject.getAllResults().isEmpty());
        assertNotNull(subject.getPayload());
        assertEquals(0, subject.getPayload().getAsJsonObject("features").size());
        assertEquals(subject.getPayload(), subject.getDecryptedPayload());
    }

    @Test
    @DisplayName("Verify: getFeatures reflects the features loaded through the context")
    void getFeaturesReflectsLoadedContext() {
        // Given
        GBContext context = GBContext.builder()
                .featuresJson("{\"feature-a\":{\"defaultValue\":true},\"feature-b\":{\"defaultValue\":false}}")
                .build();
        GrowthBook subject = new GrowthBook(context);

        // When
        Map<String, Feature<?>> features = subject.getFeatures();

        // Then
        assertEquals(2, features.size());
        assertTrue(features.containsKey("feature-a"));
        assertTrue(features.containsKey("feature-b"));
    }

    @Test
    @DisplayName("Verify: getFeatures returns an unmodifiable snapshot")
    void getFeaturesIsUnmodifiable() {
        // Given
        GBContext context = GBContext.builder()
                .featuresJson("{\"feature-a\":{\"defaultValue\":true}}")
                .build();
        GrowthBook subject = new GrowthBook(context);

        // When & Then
        assertThrows(UnsupportedOperationException.class, () -> subject.getFeatures().put("x", new Feature<>()));
    }

    @Test
    @DisplayName("Verify: for an unencrypted context payload getDecryptedPayload equals getPayload")
    void decryptedEqualsPayloadForUnencryptedContext() {
        // Given
        JsonObject savedGroups = new JsonObject();
        savedGroups.addProperty("admins", "value");
        GBContext context = GBContext.builder()
                .featuresJson("{\"feature-a\":{\"defaultValue\":true}}")
                .savedGroups(savedGroups)
                .build();
        GrowthBook subject = new GrowthBook(context);

        // When
        JsonObject payload = subject.getPayload();

        // Then
        assertTrue(payload.getAsJsonObject("features").has("feature-a"));
        assertTrue(payload.has("savedGroups"));
        assertEquals(payload, subject.getDecryptedPayload());
    }

    @Test
    @DisplayName("Verify: getAllResults records the last result of every experiment run")
    void getAllResultsReflectsRuns() {
        // Given
        GBContext context = GBContext.builder()
                .attributesJson("{\"id\":\"user-123\"}")
                .build();
        GrowthBook subject = new GrowthBook(context);
        Experiment<String> experiment = Experiment.<String>builder()
                .key("my-exp")
                .variations(new ArrayList<>(Arrays.asList("control", "variation")))
                .build();

        // When
        ExperimentResult<String> result = subject.run(experiment);

        // Then
        Map<String, ExperimentResult<?>> results = subject.getAllResults();
        assertEquals(1, results.size());
        assertEquals(result, results.get("my-exp"));
    }

    @Test
    @DisplayName("Verify: destroy clears the results tracked by getAllResults")
    void destroyClearsResults() {
        // Given
        GBContext context = GBContext.builder().attributesJson("{\"id\":\"user-123\"}").build();
        GrowthBook subject = new GrowthBook(context);
        subject.run(Experiment.<String>builder()
                .key("my-exp")
                .variations(new ArrayList<>(Arrays.asList("control", "variation")))
                .build());
        assertFalse(subject.getAllResults().isEmpty());

        // When
        subject.destroy();

        // Then
        assertTrue(subject.getAllResults().isEmpty());
    }

    @Test
    @DisplayName("Verify: getAllResults returns an unmodifiable snapshot")
    void getAllResultsIsUnmodifiable() {
        // Given
        GrowthBook subject = new GrowthBook();

        // When & Then
        assertThrows(UnsupportedOperationException.class, () -> subject.getAllResults().put("x", null));
    }

    @Test
    @DisplayName("Verify: getFeatures returns deep copies — mutating a returned rule list cannot change the SDK's state")
    void getFeaturesReturnsDeepCopies() {
        // Given
        GBContext context = GBContext.builder()
                .featuresJson("{\"flag\":{\"defaultValue\":false,\"rules\":[{\"force\":true}]}}")
                .build();
        GrowthBook subject = new GrowthBook(context);

        // When — a caller clears the rules on the returned copy
        subject.getFeatures().get("flag").getRules().clear();

        // Then — the SDK's live features are untouched, and a fresh read still has the rule
        assertEquals(1, context.getFeatures().get("flag").getRules().size());
        assertEquals(1, subject.getFeatures().get("flag").getRules().size());
    }

    @Test
    @DisplayName("Verify: getAllResults returns copies — mutating one result cannot change later snapshots")
    void getAllResultsReturnsCopies() {
        // Given
        GBContext context = GBContext.builder().attributesJson("{\"id\":\"user-123\"}").build();
        GrowthBook subject = new GrowthBook(context);
        Experiment<String> experiment = Experiment.<String>builder()
                .key("my-exp")
                .variations(new ArrayList<>(Arrays.asList("control", "variation")))
                .build();
        ExperimentResult<String> runResult = subject.run(experiment);
        Integer original = subject.getAllResults().get("my-exp").getVariationId();

        // When — mutate both a returned snapshot and the object run(...) handed back
        subject.getAllResults().get("my-exp").setVariationId(99);
        runResult.setVariationId(77);

        // Then — a later snapshot is unaffected by either mutation
        assertEquals(original, subject.getAllResults().get("my-exp").getVariationId());
    }

    @Test
    @DisplayName("Verify: getAllResults preserves a variation value's runtime type (Integer stays Integer)")
    void getAllResultsPreservesValueType() {
        // Given an Experiment<Integer>
        GBContext context = GBContext.builder().attributesJson("{\"id\":\"user-123\"}").build();
        GrowthBook subject = new GrowthBook(context);
        Experiment<Integer> experiment = Experiment.<Integer>builder()
                .key("int-exp")
                .variations(new ArrayList<>(Arrays.asList(10, 20)))
                .build();

        // When
        ExperimentResult<Integer> runResult = subject.run(experiment);
        Object snapshotValue = subject.getAllResults().get("int-exp").getValue();

        // Then getAllResults returns the same Integer type run(...) did, not a Long
        assertEquals(runResult.getValue(), snapshotValue);
        assertTrue(snapshotValue instanceof Integer);
    }
}
