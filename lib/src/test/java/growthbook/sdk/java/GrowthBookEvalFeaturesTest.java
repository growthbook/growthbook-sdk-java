package growthbook.sdk.java;

import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.FeatureResultSource;
import growthbook.sdk.java.model.GBContext;
import org.junit.jupiter.api.Test;

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
}
