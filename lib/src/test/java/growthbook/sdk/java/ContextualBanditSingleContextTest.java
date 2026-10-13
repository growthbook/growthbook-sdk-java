package growthbook.sdk.java;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.GBContext;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

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

    private static JsonObject json(String raw) {
        return GrowthBookJsonUtils.getInstance().gson.fromJson(raw, JsonObject.class);
    }
}
