package growthbook.sdk.java.evaluators;

import com.google.gson.JsonObject;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.FeatureResultSource;
import growthbook.sdk.java.multiusermode.configurations.EvaluationContext;
import growthbook.sdk.java.multiusermode.configurations.GlobalContext;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ForcedOverridesEvaluatorTest {
    private final FeatureEvaluator featureEvaluator = new FeatureEvaluator();
    private final ExperimentEvaluator experimentEvaluator = new ExperimentEvaluator();

    @Test
    void userForcedFeatureValueOverridesGlobalValue() {
        Map<String, Object> globalForcedFeatures = new HashMap<>();
        globalForcedFeatures.put("perf-feature", true);

        Map<String, Object> userForcedFeatures = new HashMap<>();
        userForcedFeatures.put("perf-feature", false);

        EvaluationContext context = buildContext(
                globalForcedFeatures,
                userForcedFeatures,
                null,
                null,
                createAttributes("user-1")
        );

        FeatureResult<Boolean> result = featureEvaluator.evaluateFeature("perf-feature", context, Boolean.class);

        assertEquals(FeatureResultSource.OVERRIDE, result.getSource());
        assertEquals(Boolean.FALSE, result.getValue());
    }

    @Test
    void userForcedVariationOverridesGlobalValue() {
        Map<String, Integer> globalForcedVariations = new HashMap<>();
        globalForcedVariations.put("perf-experiment", 0);

        Map<String, Integer> userForcedVariations = new HashMap<>();
        userForcedVariations.put("perf-experiment", 1);

        EvaluationContext context = buildContext(
                null,
                null,
                globalForcedVariations,
                userForcedVariations,
                createAttributes("user-1")
        );

        ExperimentResult<String> result = experimentEvaluator.evaluateExperiment(createExperiment(), context, null);

        assertEquals(Integer.valueOf(1), result.getVariationId());
        assertTrue(result.getInExperiment());
    }

    @Test
    void absentForcedMapsStillEvaluatesSafelyInMultiUserMode() {
        EvaluationContext context = buildContext(null, null, null, null, createAttributes("user-1"));

        FeatureResult<Boolean> result = featureEvaluator.evaluateFeature("unknown-feature", context, Boolean.class);

        assertEquals(FeatureResultSource.UNKNOWN_FEATURE, result.getSource());
    }

    @Test
    void absentAttributesStillEvaluatesExperimentSafely() {
        EvaluationContext context = buildContext(null, null, null, null, null);

        ExperimentResult<String> result = experimentEvaluator.evaluateExperiment(createExperiment(), context, null);

        assertEquals(Integer.valueOf(-1), result.getVariationId());
        assertFalse(result.getInExperiment());
    }

    private EvaluationContext buildContext(
            Map<String, Object> globalForcedFeatures,
            Map<String, Object> userForcedFeatures,
            Map<String, Integer> globalForcedVariations,
            Map<String, Integer> userForcedVariations,
            JsonObject attributes
    ) {
        GlobalContext global = GlobalContext.builder()
                .features(Collections.emptyMap())
                .forcedFeatureValues(globalForcedFeatures)
                .forcedVariations(globalForcedVariations)
                .build();

        UserContext user = UserContext.builder()
                .attributes(attributes)
                .forcedFeatureValues(userForcedFeatures)
                .forcedVariationsMap(userForcedVariations)
                .build();

        Options options = Options.builder()
                .enabled(true)
                .allowUrlOverrides(false)
                .build();

        return new EvaluationContext(global, user, new EvaluationContext.StackContext(), options);
    }

    private Experiment<String> createExperiment() {
        return Experiment.<String>builder()
                .key("perf-experiment")
                .variations(new ArrayList<>(Arrays.asList("control", "variant")))
                .build();
    }

    private JsonObject createAttributes(String userId) {
        JsonObject attributes = new JsonObject();
        attributes.addProperty("id", userId);
        return attributes;
    }
}
