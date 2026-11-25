package growthbook.sdk.java;

import growthbook.sdk.java.callback.FeatureUsageCallback;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.FeatureResultSource;
import growthbook.sdk.java.model.GBContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrowthBookEvalFeaturesTest {

    private static final String FEATURES_JSON = "{"
            + "\"f_bool\":{\"defaultValue\":true},"
            + "\"f_str\":{\"defaultValue\":\"hello\"},"
            + "\"f_num\":{\"defaultValue\":42},"
            + "\"f_forced\":{\"defaultValue\":0,\"rules\":[{\"condition\":{\"employee\":true},\"force\":100}]}"
            + "}";
    private static final String ATTRIBUTES_JSON = "{\"id\":\"user-1\",\"employee\":true}";

    @Test
    @DisplayName("Returns one result per key, matching individual evaluation")
    void returnsOneResultPerKeyMatchingIndividualEvaluation() {
        // Given
        GrowthBook subject = newSubject();
        List<String> keys = Arrays.asList("f_bool", "f_str", "f_num", "f_forced");

        // When
        Map<String, FeatureResult<Object>> batch = subject.evalFeatures(keys, Object.class);

        // Then
        assertEquals(keys, new ArrayList<>(batch.keySet()));
        for (String key : keys) {
            FeatureResult<Object> single = subject.evalFeature(key, Object.class);
            assertEquals(single.getValue(), batch.get(key).getValue(), key);
            assertEquals(single.getSource(), batch.get(key).getSource(), key);
        }
    }

    @Test
    @DisplayName("Applies a matching force rule")
    void appliesMatchingForceRule() {
        // Given
        GrowthBook subject = newSubject();

        // When
        Map<String, FeatureResult<Object>> batch = subject.evalFeatures(Collections.singletonList("f_forced"), Object.class);

        // Then
        assertEquals(FeatureResultSource.FORCE, batch.get("f_forced").getSource());
        assertEquals(100, ((Number) batch.get("f_forced").getValue()).intValue());
    }

    @Test
    @DisplayName("Includes an unknown key as an unknown feature result")
    void includesUnknownKeyAsUnknownFeature() {
        // Given
        GrowthBook subject = newSubject();

        // When
        Map<String, FeatureResult<Object>> batch = subject.evalFeatures(Collections.singletonList("missing"), Object.class);

        // Then
        assertEquals(Collections.singletonList("missing"), new ArrayList<>(batch.keySet()));
        assertEquals(FeatureResultSource.UNKNOWN_FEATURE, batch.get("missing").getSource());
    }

    @Test
    @DisplayName("Returns an empty map for empty or null keys")
    void returnsEmptyMapForEmptyOrNullKeys() {
        // Given
        GrowthBook subject = newSubject();

        // When
        Map<String, FeatureResult<Object>> fromEmpty = subject.evalFeatures(Collections.<String>emptyList(), Object.class);
        Map<String, FeatureResult<Object>> fromNull = subject.evalFeatures(null, Object.class);

        // Then
        assertTrue(fromEmpty.isEmpty());
        assertTrue(fromNull.isEmpty());
    }

    @Test
    @DisplayName("Preserves the input order and evaluates a duplicate key once")
    void preservesInputOrderAndEvaluatesDuplicateKeyOnce() {
        // Given
        List<String> usage = new ArrayList<>();
        GrowthBook subject = new GrowthBook(GBContext.builder()
                .featuresJson(FEATURES_JSON)
                .attributesJson(ATTRIBUTES_JSON)
                .featureUsageCallback(new RecordingFeatureUsageCallback(usage))
                .build());

        // When
        Map<String, FeatureResult<Object>> batch = subject.evalFeatures(
                Arrays.asList("f_num", "f_bool", "missing", "f_str", "f_bool"), Object.class);

        // Then
        assertEquals(Arrays.asList("f_num", "f_bool", "missing", "f_str"), new ArrayList<>(batch.keySet()));
        assertEquals(1, Collections.frequency(usage, "f_bool"));
    }

    @Test
    @DisplayName("Ignores URL overrides unless they are allowed")
    void ignoresUrlOverridesUnlessAllowed() {
        // Given
        GrowthBook subject = new GrowthBook(GBContext.builder()
                .featuresJson(FEATURES_JSON)
                .attributesJson(ATTRIBUTES_JSON)
                .url("http://localhost/?gb~f_str=fromurl")
                .build());

        // When
        Map<String, FeatureResult<String>> batch = subject.evalFeatures(Collections.singletonList("f_str"), String.class);

        // Then
        assertEquals(FeatureResultSource.DEFAULT_VALUE, batch.get("f_str").getSource());
        assertEquals("hello", batch.get("f_str").getValue());
    }

    @Test
    @DisplayName("Applies URL overrides when they are allowed")
    void appliesUrlOverridesWhenAllowed() {
        // Given
        GrowthBook subject = new GrowthBook(GBContext.builder()
                .featuresJson(FEATURES_JSON)
                .attributesJson(ATTRIBUTES_JSON)
                .url("http://localhost/?gb~f_str=fromurl")
                .allowUrlOverrides(true)
                .build());

        // When
        Map<String, FeatureResult<String>> batch = subject.evalFeatures(Collections.singletonList("f_str"), String.class);

        // Then
        assertEquals(FeatureResultSource.URL_OVERRIDE, batch.get("f_str").getSource());
        assertEquals("fromurl", batch.get("f_str").getValue());
    }

    private static GrowthBook newSubject() {
        return new GrowthBook(GBContext.builder().featuresJson(FEATURES_JSON).attributesJson(ATTRIBUTES_JSON).build());
    }

    private static final class RecordingFeatureUsageCallback implements FeatureUsageCallback {
        private final List<String> usage;

        RecordingFeatureUsageCallback(List<String> usage) {
            this.usage = usage;
        }

        @Override
        public <T> void onFeatureUsage(String featureKey, FeatureResult<T> result) {
            usage.add(featureKey);
        }
    }
}
