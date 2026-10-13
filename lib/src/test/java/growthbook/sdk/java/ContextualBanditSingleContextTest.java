package growthbook.sdk.java;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.GBContext;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

/**
 * Contextual bandit consumption in the single-context ({@link GrowthBook} + {@link GBContext}) path,
 * mirroring the multi-user client behavior so the feature works through both entry points.
 */
class ContextualBanditSingleContextTest {

    private static final String FEATURES_JSON =
            "{\"banner\":{\"defaultValue\":\"default\",\"rules\":[{"
                    + "\"contextualBanditRef\":\"bandit-1\","
                    + "\"contextualVariations\":[\"control\",\"treatment\"],"
                    + "\"hashAttribute\":\"id\""
                    + "}]}}";

    private static final String CONTEXTUAL_BANDITS_JSON =
            "{\"bandit-1\":{\"banditVersion\":3,\"contexts\":["
                    + "{\"leafId\":0,\"condition\":{\"country\":\"US\"},\"weights\":[1.0,0.0]},"
                    + "{\"leafId\":1,\"condition\":{\"country\":\"CA\"},\"weights\":[0.0,1.0]}"
                    + "]}}";

    private static final String WEIGHTS_LENGTH_MISMATCH_JSON =
            "{\"bandit-1\":{\"banditVersion\":5,\"contexts\":["
                    + "{\"leafId\":0,\"condition\":{\"country\":\"US\"},\"weights\":[1.0]}"
                    + "]}}";

    private static final String MALFORMED_DEFINITION_JSON =
            "{\"bandit-1\":{\"banditVersion\":1,\"contexts\":\"not-an-array\"}}";

    private static final String FEATURES_REF_WITHOUT_CONTEXTUAL_VARIATIONS =
            "{\"banner\":{\"defaultValue\":\"default\",\"rules\":[{"
                    + "\"contextualBanditRef\":\"bandit-1\","
                    + "\"variations\":[\"control\",\"treatment\"],"
                    + "\"hashAttribute\":\"id\""
                    + "}]}}";

    private static final String FEATURES_NO_VARIATIONS =
            "{\"banner\":{\"defaultValue\":\"default\",\"rules\":[{"
                    + "\"contextualBanditRef\":\"bandit-1\","
                    + "\"hashAttribute\":\"id\""
                    + "}]}}";

    @Test
    @DisplayName("Verify: a matching leaf applies its weights and records bandit metadata in the single-context path")
    void matchingLeafAppliesWeightsAndRecordsMetadata() {
        // Given
        GBContext context = GBContext.builder()
                .featuresJson(FEATURES_JSON)
                .contextualBandits(json(CONTEXTUAL_BANDITS_JSON))
                .attributesJson("{\"id\":\"u1\",\"country\":\"CA\"}")
                .build();
        GrowthBook subject = new GrowthBook(context);

        // When
        FeatureResult<String> result = subject.evalFeature("banner", String.class);

        // Then
        assertEquals("treatment", result.getValue());
        assertTrue(result.getExperimentResult().getInExperiment());
        assertEquals(Integer.valueOf(1), result.getExperimentResult().getLeafId());
        assertEquals(Integer.valueOf(3), result.getExperimentResult().getBanditVersion());
        assertEquals(Arrays.asList(0.0f, 1.0f), result.getExperimentResult().getVariationWeights());
    }

    @Test
    @DisplayName("Verify: no matching leaf falls back to leafId -1 with equal weights in the single-context path")
    void noMatchingLeafFallsBackToEqualWeights() {
        // Given
        GBContext context = GBContext.builder()
                .featuresJson(FEATURES_JSON)
                .contextualBandits(json(CONTEXTUAL_BANDITS_JSON))
                .attributesJson("{\"id\":\"u1\",\"country\":\"FR\"}")
                .build();
        GrowthBook subject = new GrowthBook(context);

        // When
        FeatureResult<String> result = subject.evalFeature("banner", String.class);

        // Then
        assertTrue(result.getExperimentResult().getInExperiment());
        assertEquals(Integer.valueOf(-1), result.getExperimentResult().getLeafId());
        assertEquals(Arrays.asList(0.5f, 0.5f), result.getExperimentResult().getVariationWeights());
    }

    @Test
    @DisplayName("Verify: a matching leaf is picked up from contextualBandits supplied as a JSON string")
    void contextualBanditsFromJsonStringAreApplied() {
        // Given
        GBContext context = GBContext.builder()
                .featuresJson(FEATURES_JSON)
                .contextualBanditsJson(CONTEXTUAL_BANDITS_JSON)
                .attributesJson("{\"id\":\"u1\",\"country\":\"US\"}")
                .build();
        GrowthBook subject = new GrowthBook(context);

        // When
        FeatureResult<String> result = subject.evalFeature("banner", String.class);

        // Then
        assertEquals("control", result.getValue());
        assertEquals(Integer.valueOf(0), result.getExperimentResult().getLeafId());
        assertEquals(Integer.valueOf(3), result.getExperimentResult().getBanditVersion());
    }

    @Test
    @DisplayName("Verify: a forced (non-hashed) variation yields the forced value but no bandit metadata")
    void forcedVariationLeavesNoBanditMetadata() {
        // Given
        GBContext context = GBContext.builder()
                .featuresJson(FEATURES_JSON)
                .contextualBandits(json(CONTEXTUAL_BANDITS_JSON))
                .forcedVariationsMap(Collections.singletonMap("banner", 0))
                .attributesJson("{\"id\":\"u1\",\"country\":\"US\"}")
                .build();
        GrowthBook subject = new GrowthBook(context);

        // When
        FeatureResult<String> result = subject.evalFeature("banner", String.class);

        // Then
        assertEquals("control", result.getValue());
        assertTrue(result.getExperimentResult().getInExperiment());
        assertFalse(result.getExperimentResult().getHashUsed());
        assertNull(result.getExperimentResult().getLeafId());
        assertNull(result.getExperimentResult().getBanditVersion());
        assertNull(result.getExperimentResult().getVariationWeights());
    }

    @Test
    @DisplayName("Verify: a leaf whose weights length differs from the variation count falls back to equal weights")
    void weightsLengthMismatchFallsBackToEqualWeights() {
        // Given
        GBContext context = GBContext.builder()
                .featuresJson(FEATURES_JSON)
                .contextualBandits(json(WEIGHTS_LENGTH_MISMATCH_JSON))
                .attributesJson("{\"id\":\"u1\",\"country\":\"US\"}")
                .build();
        GrowthBook subject = new GrowthBook(context);

        // When
        FeatureResult<String> result = subject.evalFeature("banner", String.class);

        // Then
        assertTrue(result.getExperimentResult().getInExperiment());
        assertEquals(Integer.valueOf(-1), result.getExperimentResult().getLeafId());
        assertEquals(Integer.valueOf(5), result.getExperimentResult().getBanditVersion());
        assertEquals(Arrays.asList(0.5f, 0.5f), result.getExperimentResult().getVariationWeights());
    }

    @Test
    @DisplayName("Verify: a definition that fails to deserialize evaluates as an ordinary experiment with no metadata")
    void malformedDefinitionLeavesNoMetadata() {
        // Given
        GBContext context = GBContext.builder()
                .featuresJson(FEATURES_JSON)
                .contextualBandits(json(MALFORMED_DEFINITION_JSON))
                .attributesJson("{\"id\":\"u1\",\"country\":\"US\"}")
                .build();
        GrowthBook subject = new GrowthBook(context);

        // When
        FeatureResult<String> result = subject.evalFeature("banner", String.class);

        // Then
        assertTrue(result.getExperimentResult().getInExperiment());
        assertNull(result.getExperimentResult().getLeafId());
        assertNull(result.getExperimentResult().getBanditVersion());
        assertNull(result.getExperimentResult().getVariationWeights());
    }

    @Test
    @DisplayName("Verify: contextualBandits supplied as malformed JSON yields no bandits and does not throw")
    void malformedContextualBanditsJsonYieldsNoBandits() {
        // Given
        GBContext context = GBContext.builder()
                .featuresJson(FEATURES_JSON)
                .contextualBanditsJson("{bad json")
                .attributesJson("{\"id\":\"u1\",\"country\":\"US\"}")
                .build();
        GrowthBook subject = new GrowthBook(context);

        // When
        FeatureResult<String> result = subject.evalFeature("banner", String.class);

        // Then
        assertTrue(result.getExperimentResult().getInExperiment());
        assertNull(result.getExperimentResult().getLeafId());
        assertNull(result.getExperimentResult().getBanditVersion());
        assertNull(result.getExperimentResult().getVariationWeights());
    }

    @Test
    @DisplayName("Verify: a bandit ref with ordinary variations buckets on variations and still records metadata")
    void refWithoutContextualVariationsStillResolvesBandit() {
        // Given
        GBContext context = GBContext.builder()
                .featuresJson(FEATURES_REF_WITHOUT_CONTEXTUAL_VARIATIONS)
                .contextualBandits(json(CONTEXTUAL_BANDITS_JSON))
                .attributesJson("{\"id\":\"u1\",\"country\":\"US\"}")
                .build();
        GrowthBook subject = new GrowthBook(context);

        // When
        FeatureResult<String> result = subject.evalFeature("banner", String.class);

        // Then
        assertEquals("control", result.getValue());
        assertTrue(result.getExperimentResult().getInExperiment());
        assertEquals(Integer.valueOf(0), result.getExperimentResult().getLeafId());
        assertEquals(Integer.valueOf(3), result.getExperimentResult().getBanditVersion());
        assertEquals(Arrays.asList(1.0f, 0.0f), result.getExperimentResult().getVariationWeights());
    }

    @Test
    @DisplayName("Verify: a rule with neither variations nor contextualVariations is skipped and the default value is returned")
    void ruleWithoutAnyVariationsReturnsDefault() {
        // Given
        GBContext context = GBContext.builder()
                .featuresJson(FEATURES_NO_VARIATIONS)
                .contextualBandits(json(CONTEXTUAL_BANDITS_JSON))
                .attributesJson("{\"id\":\"u1\",\"country\":\"US\"}")
                .build();
        GrowthBook subject = new GrowthBook(context);

        // When
        FeatureResult<String> result = subject.evalFeature("banner", String.class);

        // Then
        assertEquals("default", result.getValue());
        assertNull(result.getExperimentResult());
    }

    private static JsonObject json(String raw) {
        return GrowthBookJsonUtils.getInstance().gson.fromJson(raw, JsonObject.class);
    }
}
