package growthbook.sdk.java.perf;

import com.google.gson.JsonObject;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.Feature;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.multiusermode.configurations.EvaluationContext;
import growthbook.sdk.java.multiusermode.configurations.GlobalContext;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.evaluators.ExperimentEvaluator;
import growthbook.sdk.java.evaluators.FeatureEvaluator;
import growthbook.sdk.java.util.GrowthBookJsonUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

public class EvaluationPerfHarness {
    private static final FeatureEvaluator FEATURE_EVALUATOR = new FeatureEvaluator();
    private static final ExperimentEvaluator EXPERIMENT_EVALUATOR = new ExperimentEvaluator();
    private static final GrowthBookJsonUtils JSON_UTILS = GrowthBookJsonUtils.getInstance();
    private static final Options OPTIONS = Options.builder()
            .enabled(true)
            .allowUrlOverrides(false)
            .build();
    private static final Map<String, Feature<?>> FEATURES = createFeatures();
    private static final UserContext DEFAULT_USER = UserContext.builder()
            .attributes(createAttributes("user-1"))
            .build();
    private static final GlobalContext FEATURE_GLOBAL = GlobalContext.builder()
            .features(FEATURES)
            .build();
    private static final GlobalContext FEATURE_GLOBAL_WITH_OVERRIDE = GlobalContext.builder()
            .features(FEATURES)
            .forcedFeatureValues(createForcedFeatureValues(false))
            .build();
    private static final GlobalContext FEATURE_GLOBAL_WITH_BASELINE_OVERRIDE = GlobalContext.builder()
            .features(FEATURES)
            .forcedFeatureValues(createForcedFeatureValues(true))
            .build();
    private static final UserContext USER_WITH_FEATURE_OVERRIDE = UserContext.builder()
            .attributes(createAttributes("user-1"))
            .forcedFeatureValues(createForcedFeatureValues(false))
            .build();
    private static final GlobalContext EXPERIMENT_GLOBAL = GlobalContext.builder()
            .features(Collections.emptyMap())
            .build();
    private static final GlobalContext EXPERIMENT_GLOBAL_WITH_FORCED_VARIATION = GlobalContext.builder()
            .features(Collections.emptyMap())
            .forcedVariations(createForcedVariations(1))
            .build();
    private static final GlobalContext EXPERIMENT_GLOBAL_WITH_BASELINE_VARIATION = GlobalContext.builder()
            .features(Collections.emptyMap())
            .forcedVariations(createForcedVariations(0))
            .build();
    private static final UserContext USER_WITH_FORCED_VARIATION = UserContext.builder()
            .attributes(createAttributes("user-1"))
            .forcedVariationsMap(createForcedVariations(1))
            .build();
    private static final Experiment<String> EXPERIMENT = Experiment.<String>builder()
            .key("perf-experiment")
            .variations(new ArrayList<>(Arrays.asList("control", "variant")))
            .build();

    private static volatile Object sink;

    public static void main(String[] args) {
        int warmupIterations = Integer.getInteger("perf.warmupIterations", 50_000);
        int iterations = Integer.getInteger("perf.iterations", 200_000);

        System.out.printf("warmupIterations=%d iterations=%d%n", warmupIterations, iterations);

        runScenario("feature-no-overrides", warmupIterations, iterations, EvaluationPerfHarness::runFeatureNoOverrides);
        runScenario("feature-global-override", warmupIterations, iterations, EvaluationPerfHarness::runFeatureGlobalOverride);
        runScenario("feature-user-override", warmupIterations, iterations, EvaluationPerfHarness::runFeatureUserOverride);
        runScenario("experiment-no-forced-variations", warmupIterations, iterations, EvaluationPerfHarness::runExperimentNoForcedVariations);
        runScenario("experiment-global-forced-variation", warmupIterations, iterations, EvaluationPerfHarness::runExperimentGlobalForcedVariation);
        runScenario("experiment-user-forced-variation", warmupIterations, iterations, EvaluationPerfHarness::runExperimentUserForcedVariation);
    }

    private static void runScenario(String name, int warmupIterations, int iterations, Scenario scenario) {
        for (int i = 0; i < warmupIterations; i++) {
            scenario.run();
        }

        long start = System.nanoTime();
        for (int i = 0; i < iterations; i++) {
            scenario.run();
        }
        long elapsedNanos = System.nanoTime() - start;

        System.out.printf(
                "%s totalMs=%.3f nsPerOp=%.1f sink=%s%n",
                name,
                elapsedNanos / 1_000_000.0,
                (double) elapsedNanos / iterations,
                sink
        );
    }

    private static void runFeatureNoOverrides() {
        EvaluationContext context = createEvaluationContext(FEATURE_GLOBAL, DEFAULT_USER);
        FeatureResult<Boolean> result = FEATURE_EVALUATOR.evaluateFeature("perf-feature", context, Boolean.class);
        sink = result.getSource();
    }

    private static void runFeatureGlobalOverride() {
        EvaluationContext context = createEvaluationContext(FEATURE_GLOBAL_WITH_OVERRIDE, DEFAULT_USER);
        FeatureResult<Boolean> result = FEATURE_EVALUATOR.evaluateFeature("perf-feature", context, Boolean.class);
        sink = result.getValue();
    }

    private static void runFeatureUserOverride() {
        EvaluationContext context = createEvaluationContext(FEATURE_GLOBAL_WITH_BASELINE_OVERRIDE, USER_WITH_FEATURE_OVERRIDE);
        FeatureResult<Boolean> result = FEATURE_EVALUATOR.evaluateFeature("perf-feature", context, Boolean.class);
        sink = result.getValue();
    }

    private static void runExperimentNoForcedVariations() {
        EvaluationContext context = createEvaluationContext(EXPERIMENT_GLOBAL, DEFAULT_USER);
        ExperimentResult<String> result = EXPERIMENT_EVALUATOR.evaluateExperiment(EXPERIMENT, context, null);
        sink = result.getVariationId();
    }

    private static void runExperimentGlobalForcedVariation() {
        EvaluationContext context = createEvaluationContext(EXPERIMENT_GLOBAL_WITH_FORCED_VARIATION, DEFAULT_USER);
        ExperimentResult<String> result = EXPERIMENT_EVALUATOR.evaluateExperiment(EXPERIMENT, context, null);
        sink = result.getVariationId();
    }

    private static void runExperimentUserForcedVariation() {
        EvaluationContext context = createEvaluationContext(EXPERIMENT_GLOBAL_WITH_BASELINE_VARIATION, USER_WITH_FORCED_VARIATION);
        ExperimentResult<String> result = EXPERIMENT_EVALUATOR.evaluateExperiment(EXPERIMENT, context, null);
        sink = result.getVariationId();
    }

    private static EvaluationContext createEvaluationContext(GlobalContext global, UserContext user) {
        return new EvaluationContext(global, user, new EvaluationContext.StackContext(), OPTIONS);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Feature<?>> createFeatures() {
        Map<String, Feature<?>> features = new HashMap<>();
        features.put("perf-feature", JSON_UTILS.gson.fromJson("{\"defaultValue\":true}", Feature.class));
        return features;
    }

    private static JsonObject createAttributes(String userId) {
        JsonObject attributes = new JsonObject();
        attributes.addProperty("id", userId);
        return attributes;
    }

    private static Map<String, Object> createForcedFeatureValues(boolean value) {
        Map<String, Object> forcedFeatureValues = new HashMap<>();
        forcedFeatureValues.put("perf-feature", value);
        return forcedFeatureValues;
    }

    private static Map<String, Integer> createForcedVariations(int variation) {
        Map<String, Integer> forcedVariations = new HashMap<>();
        forcedVariations.put("perf-experiment", variation);
        return forcedVariations;
    }

    private interface Scenario {
        void run();
    }
}
