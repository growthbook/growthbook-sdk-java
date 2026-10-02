package growthbook.sdk.java;

import growthbook.sdk.java.callback.FeatureUsageCallback;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.FeatureResultSource;
import growthbook.sdk.java.model.GBContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GrowthBookEvalFeaturesTest {

    private static final String FEATURES_JSON =
            "{"
                    + "\"f_bool\":{\"defaultValue\":true},"
                    + "\"f_str\":{\"defaultValue\":\"hello\"},"
                    + "\"f_num\":{\"defaultValue\":42},"
                    + "\"f_forced\":{\"defaultValue\":0,\"rules\":[{\"condition\":{\"employee\":true},\"force\":100}]}"
                    + "}";

    private GrowthBook newSubject() {
        GBContext context = GBContext.builder()
                .featuresJson(FEATURES_JSON)
                .attributesJson("{\"id\":\"user-1\",\"employee\":true}")
                .build();
        return new GrowthBook(context);
    }

    @Test
    void evalFeatures_returnsOneResultPerKey_matchingIndividualEvaluation() {
        GrowthBook subject = newSubject();
        List<String> keys = Arrays.asList("f_bool", "f_str", "f_num", "f_forced");

        Map<String, FeatureResult<Object>> batch = subject.evalFeatures(keys, Object.class);

        assertEquals(keys.size(), batch.size());
        for (String key : keys) {
            FeatureResult<Object> single = subject.evalFeature(key, Object.class);
            FeatureResult<Object> batched = batch.get(key);
            assertNotNull(batched, "missing result for " + key);
            assertEquals(single.getValue(), batched.getValue(), "value mismatch for " + key);
            assertEquals(single.getSource(), batched.getSource(), "source mismatch for " + key);
        }
    }

    @Test
    void evalFeatures_forcedRuleIsApplied() {
        GrowthBook subject = newSubject();

        Map<String, FeatureResult<Object>> batch = subject.evalFeatures(
                Collections.singletonList("f_forced"), Object.class);

        assertEquals(FeatureResultSource.FORCE, batch.get("f_forced").getSource());
    }

    @Test
    void evalFeatures_unknownKeyIsIncludedAsUnknownFeature() {
        GrowthBook subject = newSubject();

        Map<String, FeatureResult<Object>> batch = subject.evalFeatures(
                Collections.singletonList("does_not_exist"), Object.class);

        assertEquals(1, batch.size());
        assertNotNull(batch.get("does_not_exist"));
        assertEquals(FeatureResultSource.UNKNOWN_FEATURE, batch.get("does_not_exist").getSource());
    }

    @Test
    void evalFeatures_emptyOrNullKeys_returnEmptyMap() {
        GrowthBook subject = newSubject();

        assertTrue(subject.evalFeatures(Collections.emptyList(), Object.class).isEmpty());
        assertTrue(subject.evalFeatures(null, Object.class).isEmpty());
    }

    @Test
    void evalFeatures_preservesInputOrder_andEvaluatesEachKeyOnce() {
        List<String> usage = new ArrayList<>();
        GBContext context = GBContext.builder()
                .featuresJson(FEATURES_JSON)
                .attributesJson("{\"id\":\"user-1\",\"employee\":true}")
                // onFeatureUsage is a generic method, so this cannot be a lambda.
                .featureUsageCallback(new FeatureUsageCallback() {
                    @Override
                    public <ValueType> void onFeatureUsage(String featureKey, FeatureResult<ValueType> result) {
                        usage.add(featureKey);
                    }
                })
                .build();
        GrowthBook subject = new GrowthBook(context);

        List<String> keys = Arrays.asList("f_num", "f_bool", "missing", "f_str", "f_bool");
        List<String> expectedOrder = Arrays.asList("f_num", "f_bool", "missing", "f_str");

        Map<String, FeatureResult<Object>> batch = subject.evalFeatures(keys, Object.class);

        assertEquals(expectedOrder, new ArrayList<>(batch.keySet()));
        // A duplicate key is evaluated once, so its usage callback fires once.
        assertEquals(1, Collections.frequency(usage, "f_bool"));
    }

    @Test
    void evalFeatures_urlOverridesDisabledByDefault_doNotApply() {
        GBContext context = GBContext.builder()
                .featuresJson(FEATURES_JSON)
                .attributesJson("{\"id\":\"user-1\",\"employee\":true}")
                .url("https://example.com/?gb~f_str=fromurl")
                .build();
        GrowthBook subject = new GrowthBook(context);

        Map<String, FeatureResult<String>> batch = subject.evalFeatures(
                Collections.singletonList("f_str"), String.class);

        assertEquals(FeatureResultSource.DEFAULT_VALUE, batch.get("f_str").getSource());
        assertEquals("hello", batch.get("f_str").getValue());
    }

    @Test
    void evalFeatures_urlOverridesEnabled_apply() {
        GBContext context = GBContext.builder()
                .featuresJson(FEATURES_JSON)
                .attributesJson("{\"id\":\"user-1\",\"employee\":true}")
                .url("https://example.com/?gb~f_str=fromurl")
                .allowUrlOverrides(true)
                .build();
        GrowthBook subject = new GrowthBook(context);

        Map<String, FeatureResult<String>> batch = subject.evalFeatures(
                Collections.singletonList("f_str"), String.class);

        assertEquals(FeatureResultSource.URL_OVERRIDE, batch.get("f_str").getSource());
        assertEquals("fromurl", batch.get("f_str").getValue());
    }
}
