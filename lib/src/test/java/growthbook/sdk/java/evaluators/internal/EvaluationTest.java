package growthbook.sdk.java.evaluators.internal;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.FeatureResultSource;
import growthbook.sdk.java.model.ParentCondition;
import growthbook.sdk.java.model.TrackData;
import growthbook.sdk.java.multiusermode.ExperimentTracker;
import growthbook.sdk.java.multiusermode.configurations.EvaluationContext;
import growthbook.sdk.java.multiusermode.configurations.GlobalContext;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.multiusermode.usage.FeatureUsageCallbackWithUser;
import growthbook.sdk.java.multiusermode.usage.TrackingCallbackWithUser;
import growthbook.sdk.java.multiusermode.util.TransformationUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvaluationTest {

    private static final String EXPERIMENT_FEATURE = "{"
            + "'checkout-button': {'defaultValue': 'none', 'rules': ["
            + "  {'key': 'exp-1', 'variations': ['control', 'treatment']}"
            + "]}}";

    private static final String CYCLIC_FEATURE_DEFINITIONS = ""
            + "'feature-a': {'defaultValue': false, 'rules': ["
            + "  {'parentConditions': [{'id': 'feature-b', 'condition': {'value': true}}], 'force': true}"
            + "]},"
            + "'feature-b': {'defaultValue': false, 'rules': ["
            + "  {'parentConditions': [{'id': 'feature-a', 'condition': {'value': true}}], 'force': true}"
            + "]}";

    private static final String CYCLIC_FEATURES = "{" + CYCLIC_FEATURE_DEFINITIONS + "}";

    @Test
    @DisplayName("Reports an exposure once per instance when a feature rule and runExperiment hit the same variation")
    void reportsExposureOncePerInstanceAcrossEntryPoints() {
        // Given
        RecordingTrackingCallback tracking = new RecordingTrackingCallback();
        EvaluationContext context = context(EXPERIMENT_FEATURE,
                Options.builder().trackingCallBackWithUser(tracking).build(), new ExperimentTracker());

        // When
        FeatureResult<Object> featureResult = new Evaluation(context).evaluateFeature("checkout-button", Object.class);
        ExperimentResult<String> experimentResult = new Evaluation(context).runExperiment(twoVariationExperiment(), null);

        // Then
        assertEquals(FeatureResultSource.EXPERIMENT, featureResult.getSource());
        assertTrue(experimentResult.getInExperiment());
        assertEquals(featureResult.getExperimentResult().getVariationId(), experimentResult.getVariationId());
        assertEquals(Collections.singletonList("exp-1"), tracking.experimentKeys);
    }

    @Test
    @DisplayName("Reports every exposure when the context has no experiment tracker")
    void reportsEveryExposureWithoutTracker() {
        // Given
        RecordingTrackingCallback tracking = new RecordingTrackingCallback();
        EvaluationContext context = context(EXPERIMENT_FEATURE,
                Options.builder().trackingCallBackWithUser(tracking).build(), null);

        // When
        new Evaluation(context).evaluateFeature("checkout-button", Object.class);
        new Evaluation(context).runExperiment(twoVariationExperiment(), null);

        // Then
        assertEquals(Arrays.asList("exp-1", "exp-1"), tracking.experimentKeys);
    }

    @Test
    @DisplayName("Reports exposures of different users separately through the same tracker")
    void reportsExposuresOfDifferentUsersSeparately() {
        // Given
        RecordingTrackingCallback tracking = new RecordingTrackingCallback();
        ExperimentTracker tracker = new ExperimentTracker();
        Options options = Options.builder().trackingCallBackWithUser(tracking).build();
        EvaluationContext firstUser = context(EXPERIMENT_FEATURE, options, tracker, "user-1");
        EvaluationContext secondUser = context(EXPERIMENT_FEATURE, options, tracker, "user-2");

        // When
        new Evaluation(firstUser).runExperiment(twoVariationExperiment(), null);
        new Evaluation(secondUser).runExperiment(twoVariationExperiment(), null);

        // Then
        assertEquals(2, tracking.experimentKeys.size());
    }

    @Test
    @DisplayName("Returns the same feature result when the tracking callback throws")
    void keepsFeatureResultWhenTrackingCallbackThrows() {
        // Given
        EvaluationContext reference = context(EXPERIMENT_FEATURE, Options.builder().build(), null);
        EvaluationContext throwing = context(EXPERIMENT_FEATURE,
                Options.builder().trackingCallBackWithUser(new ThrowingTrackingCallback()).build(), null);
        FeatureResult<Object> expected = new Evaluation(reference).evaluateFeature("checkout-button", Object.class);

        // When
        FeatureResult<Object> actual = new Evaluation(throwing).evaluateFeature("checkout-button", Object.class);

        // Then
        assertEquals(FeatureResultSource.EXPERIMENT, actual.getSource());
        assertEquals(expected.getValue(), actual.getValue());
        assertEquals(expected.getExperimentResult().getVariationId(), actual.getExperimentResult().getVariationId());
    }

    @Test
    @DisplayName("Returns the same experiment result when the tracking callback throws")
    void keepsExperimentResultWhenTrackingCallbackThrows() {
        // Given
        EvaluationContext reference = context(EXPERIMENT_FEATURE, Options.builder().build(), null);
        EvaluationContext throwing = context(EXPERIMENT_FEATURE,
                Options.builder().trackingCallBackWithUser(new ThrowingTrackingCallback()).build(), null);
        ExperimentResult<String> expected = new Evaluation(reference).runExperiment(twoVariationExperiment(), null);

        // When
        ExperimentResult<String> actual = new Evaluation(throwing).runExperiment(twoVariationExperiment(), null);

        // Then
        assertEquals(expected, actual);
    }

    @Test
    @DisplayName("Returns the same feature result when the feature usage callback throws")
    void keepsFeatureResultWhenFeatureUsageCallbackThrows() {
        // Given
        EvaluationContext context = context("{'dark-mode': {'defaultValue': true}}",
                Options.builder().featureUsageCallbackWithUser(new ThrowingFeatureUsageCallback()).build(), null);

        // When
        FeatureResult<Boolean> result = new Evaluation(context).evaluateFeature("dark-mode", Boolean.class);

        // Then
        assertEquals(FeatureResultSource.DEFAULT_VALUE, result.getSource());
        assertEquals(Boolean.TRUE, result.getValue());
    }

    @Test
    @DisplayName("Reports feature usage with the evaluated key and result")
    void reportsFeatureUsage() {
        // Given
        RecordingFeatureUsageCallback usage = new RecordingFeatureUsageCallback();
        EvaluationContext context = context("{'dark-mode': {'defaultValue': true}}",
                Options.builder().featureUsageCallbackWithUser(usage).build(), null);

        // When
        new Evaluation(context).evaluateFeature("dark-mode", Boolean.class);

        // Then
        assertEquals(Collections.singletonList("dark-mode"), usage.featureKeys);
    }

    @Test
    @DisplayName("Returns unknown feature when the key is not in the features")
    void returnsUnknownFeatureForMissingKey() {
        // Given
        EvaluationContext context = context("{'dark-mode': {'defaultValue': true}}", Options.builder().build(), null);

        // When
        FeatureResult<Boolean> result = new Evaluation(context).evaluateFeature("missing-feature", Boolean.class);

        // Then
        assertEquals(FeatureResultSource.UNKNOWN_FEATURE, result.getSource());
        assertNull(result.getValue());
    }

    @Test
    @DisplayName("Returns unknown feature when the context has no features")
    void returnsUnknownFeatureWithoutFeatures() {
        // Given
        EvaluationContext context = context(null, Options.builder().build(), null);

        // When
        FeatureResult<Boolean> result = new Evaluation(context).evaluateFeature("dark-mode", Boolean.class);

        // Then
        assertEquals(FeatureResultSource.UNKNOWN_FEATURE, result.getSource());
    }

    @Test
    @DisplayName("Leaves the passed experiment unchanged when running it")
    void leavesExperimentUnchanged() {
        // Given
        Experiment<String> experiment = twoVariationExperiment();
        EvaluationContext context = context(null, Options.builder().build(), null);

        // When
        new Evaluation(context).runExperiment(experiment, null);

        // Then
        assertEquals(twoVariationExperiment(), experiment);
        assertNull(experiment.getWeights());
        assertNull(experiment.getCoverage());
        assertNull(experiment.getRanges());
    }

    @Test
    @DisplayName("Returns not in experiment when the experiment has no variations list")
    void returnsNotInExperimentForNullVariations() {
        // Given
        Experiment<String> experiment = Experiment.<String>builder().key("exp-1").variations(null).build();
        EvaluationContext context = context(null, Options.builder().build(), null);

        // When
        ExperimentResult<String> result = new Evaluation(context).runExperiment(experiment, null);

        // Then
        assertFalse(result.getInExperiment());
        assertEquals(Integer.valueOf(-1), result.getVariationId());
        assertNull(result.getValue());
    }

    @Test
    @DisplayName("Returns not in experiment when the experiment has a single variation")
    void returnsNotInExperimentForSingleVariation() {
        // Given
        Experiment<String> experiment = Experiment.<String>builder()
                .key("exp-1")
                .variations(new ArrayList<>(Collections.singletonList("control")))
                .build();
        EvaluationContext context = context(null, Options.builder().build(), null);

        // When
        ExperimentResult<String> result = new Evaluation(context).runExperiment(experiment, null);

        // Then
        assertFalse(result.getInExperiment());
        assertEquals("control", result.getValue());
    }

    @Test
    @DisplayName("Returns not in experiment and reports nothing when the SDK is disabled")
    void returnsNotInExperimentWhenDisabled() {
        // Given
        RecordingTrackingCallback tracking = new RecordingTrackingCallback();
        EvaluationContext context = context(null,
                Options.builder().enabled(false).trackingCallBackWithUser(tracking).build(), null);

        // When
        ExperimentResult<String> result = new Evaluation(context).runExperiment(twoVariationExperiment(), null);

        // Then
        assertFalse(result.getInExperiment());
        assertTrue(tracking.experimentKeys.isEmpty());
    }

    @Test
    @DisplayName("Returns cyclic prerequisite when two features require each other")
    void returnsCyclicPrerequisiteForMutualPrerequisites() {
        // Given
        EvaluationContext context = context(CYCLIC_FEATURES, Options.builder().build(), null);

        // When
        FeatureResult<Boolean> result = new Evaluation(context).evaluateFeature("feature-a", Boolean.class);

        // Then
        assertEquals(FeatureResultSource.CYCLIC_PREREQUISITE, result.getSource());
        assertNull(result.getValue());
    }

    @Test
    @DisplayName("Returns cyclic prerequisite when a feature requires itself")
    void returnsCyclicPrerequisiteForSelfPrerequisite() {
        // Given
        EvaluationContext context = context("{"
                + "'feature-a': {'defaultValue': false, 'rules': ["
                + "  {'parentConditions': [{'id': 'feature-a', 'condition': {'value': true}}], 'force': true}"
                + "]}}", Options.builder().build(), null);

        // When
        FeatureResult<Boolean> result = new Evaluation(context).evaluateFeature("feature-a", Boolean.class);

        // Then
        assertEquals(FeatureResultSource.CYCLIC_PREREQUISITE, result.getSource());
    }

    @Test
    @DisplayName("Does not treat sibling prerequisites on the same parent as a cycle")
    void doesNotTreatSiblingPrerequisitesAsCycle() {
        // Given
        EvaluationContext context = context("{"
                + "'parent': {'defaultValue': true},"
                + "'child': {'defaultValue': false, 'rules': [{'parentConditions': ["
                + "  {'id': 'parent', 'condition': {'value': true}},"
                + "  {'id': 'parent', 'condition': {'value': true}}"
                + "], 'force': true}]}}", Options.builder().build(), null);

        // When
        FeatureResult<Boolean> result = new Evaluation(context).evaluateFeature("child", Boolean.class);

        // Then
        assertEquals(FeatureResultSource.FORCE, result.getSource());
        assertEquals(Boolean.TRUE, result.getValue());
    }

    @Test
    @DisplayName("Blocks the feature when a gating prerequisite is not met")
    void blocksFeatureWhenGatingPrerequisiteFails() {
        // Given
        EvaluationContext context = context(prerequisiteFeatures(false, true), Options.builder().build(), null);

        // When
        FeatureResult<String> result = new Evaluation(context).evaluateFeature("child", String.class);

        // Then
        assertEquals(FeatureResultSource.PREREQUISITE, result.getSource());
        assertNull(result.getValue());
    }

    @Test
    @DisplayName("Skips only the rule when a non-gating prerequisite is not met")
    void skipsRuleWhenNonGatingPrerequisiteFails() {
        // Given
        EvaluationContext context = context(prerequisiteFeatures(false, false), Options.builder().build(), null);

        // When
        FeatureResult<String> result = new Evaluation(context).evaluateFeature("child", String.class);

        // Then
        assertEquals(FeatureResultSource.DEFAULT_VALUE, result.getSource());
        assertEquals("fallback", result.getValue());
    }

    @Test
    @DisplayName("Applies the rule when its prerequisite is met")
    void appliesRuleWhenPrerequisiteIsMet() {
        // Given
        EvaluationContext context = context(prerequisiteFeatures(true, true), Options.builder().build(), null);

        // When
        FeatureResult<String> result = new Evaluation(context).evaluateFeature("child", String.class);

        // Then
        assertEquals(FeatureResultSource.FORCE, result.getSource());
        assertEquals("forced", result.getValue());
    }

    @Test
    @DisplayName("Excludes the user from an experiment whose prerequisite is not met")
    void excludesUserWhenExperimentPrerequisiteFails() {
        // Given
        RecordingTrackingCallback tracking = new RecordingTrackingCallback();
        EvaluationContext context = context("{'parent': {'defaultValue': false}}",
                Options.builder().trackingCallBackWithUser(tracking).build(), null);
        Experiment<String> experiment = experimentRequiringParent();

        // When
        ExperimentResult<String> result = new Evaluation(context).runExperiment(experiment, null);

        // Then
        assertFalse(result.getInExperiment());
        assertTrue(tracking.experimentKeys.isEmpty());
    }

    @Test
    @DisplayName("Includes the user in an experiment whose prerequisite is met")
    void includesUserWhenExperimentPrerequisiteIsMet() {
        // Given
        EvaluationContext context = context("{'parent': {'defaultValue': true}}", Options.builder().build(), null);
        Experiment<String> experiment = experimentRequiringParent();

        // When
        ExperimentResult<String> result = new Evaluation(context).runExperiment(experiment, null);

        // Then
        assertTrue(result.getInExperiment());
    }

    @Test
    @DisplayName("Returns results in the order of the given keys")
    void evaluateFeaturesPreservesKeyOrder() {
        // Given
        EvaluationContext context = context("{"
                + "'feature-a': {'defaultValue': 'a'},"
                + "'feature-b': {'defaultValue': 'b'},"
                + "'feature-c': {'defaultValue': 'c'}}", Options.builder().build(), null);

        // When
        Map<String, FeatureResult<String>> results = new Evaluation(context)
                .evaluateFeatures(Arrays.asList("feature-c", "feature-a", "feature-b"), String.class);

        // Then
        assertEquals(Arrays.asList("feature-c", "feature-a", "feature-b"), new ArrayList<>(results.keySet()));
        assertEquals("c", results.get("feature-c").getValue());
    }

    @Test
    @DisplayName("Evaluates a duplicated key once")
    void evaluateFeaturesEvaluatesDuplicateKeysOnce() {
        // Given
        RecordingFeatureUsageCallback usage = new RecordingFeatureUsageCallback();
        EvaluationContext context = context("{"
                + "'feature-a': {'defaultValue': 'a'},"
                + "'feature-b': {'defaultValue': 'b'}}",
                Options.builder().featureUsageCallbackWithUser(usage).build(), null);

        // When
        Map<String, FeatureResult<String>> results = new Evaluation(context)
                .evaluateFeatures(Arrays.asList("feature-b", "feature-a", "feature-b"), String.class);

        // Then
        assertEquals(Arrays.asList("feature-b", "feature-a"), new ArrayList<>(results.keySet()));
        assertEquals(Arrays.asList("feature-b", "feature-a"), usage.featureKeys);
    }

    @Test
    @DisplayName("Returns an empty map for a null key list")
    void evaluateFeaturesReturnsEmptyMapForNull() {
        // Given
        EvaluationContext context = context("{'feature-a': {'defaultValue': 'a'}}", Options.builder().build(), null);

        // When
        Map<String, FeatureResult<String>> results = new Evaluation(context).evaluateFeatures(null, String.class);

        // Then
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("Returns an empty map for an empty key list")
    void evaluateFeaturesReturnsEmptyMapForEmptyList() {
        // Given
        EvaluationContext context = context("{'feature-a': {'defaultValue': 'a'}}", Options.builder().build(), null);

        // When
        Map<String, FeatureResult<String>> results = new Evaluation(context)
                .evaluateFeatures(Collections.<String>emptyList(), String.class);

        // Then
        assertTrue(results.isEmpty());
    }

    @Test
    @DisplayName("Evaluates each feature of a batch afresh after a prerequisite cycle")
    void evaluateFeaturesRecoversAfterCycle() {
        // Given
        EvaluationContext context = context("{" + CYCLIC_FEATURE_DEFINITIONS + ", 'plain': {'defaultValue': 'p'}}",
                Options.builder().build(), null);

        // When
        Map<String, FeatureResult<Object>> results = new Evaluation(context)
                .evaluateFeatures(Arrays.asList("feature-a", "plain"), Object.class);

        // Then
        assertEquals(FeatureResultSource.CYCLIC_PREREQUISITE, results.get("feature-a").getSource());
        assertEquals(FeatureResultSource.DEFAULT_VALUE, results.get("plain").getSource());
        assertEquals("p", results.get("plain").getValue());
    }

    @Test
    @DisplayName("Applies a URL feature override when URL overrides are allowed")
    void appliesUrlOverrideWhenAllowed() {
        // Given
        EvaluationContext context = context("{'dark-mode': {'defaultValue': false}}",
                Options.builder().allowUrlOverrides(true).url("http://localhost/?gb~dark-mode=on").build(), null);

        // When
        FeatureResult<Boolean> result = new Evaluation(context).evaluateFeature("dark-mode", Boolean.class);

        // Then
        assertEquals(FeatureResultSource.URL_OVERRIDE, result.getSource());
        assertEquals(Boolean.TRUE, result.getValue());
    }

    @Test
    @DisplayName("Ignores a URL feature override when URL overrides are not allowed")
    void ignoresUrlOverrideWhenNotAllowed() {
        // Given
        EvaluationContext context = context("{'dark-mode': {'defaultValue': false}}",
                Options.builder().url("http://localhost/?gb~dark-mode=on").build(), null);

        // When
        FeatureResult<Boolean> result = new Evaluation(context).evaluateFeature("dark-mode", Boolean.class);

        // Then
        assertEquals(FeatureResultSource.DEFAULT_VALUE, result.getSource());
        assertEquals(Boolean.FALSE, result.getValue());
    }

    @Test
    @DisplayName("Ignores a URL feature override when the allow flag is null")
    void ignoresUrlOverrideWhenFlagIsNull() {
        // Given
        Options options = Options.builder().url("http://localhost/?gb~dark-mode=on").build();
        options.setAllowUrlOverrides(null);
        EvaluationContext context = context("{'dark-mode': {'defaultValue': false}}", options, null);

        // When
        FeatureResult<Boolean> result = new Evaluation(context).evaluateFeature("dark-mode", Boolean.class);

        // Then
        assertEquals(FeatureResultSource.DEFAULT_VALUE, result.getSource());
    }

    @Test
    @DisplayName("Ignores an unparseable URL feature override")
    void ignoresUnparseableUrlOverride() {
        // Given
        EvaluationContext context = context("{'dark-mode': {'defaultValue': false}}",
                Options.builder().allowUrlOverrides(true).url("http://localhost/?gb~dark-mode=maybe").build(), null);

        // When
        FeatureResult<Boolean> result = new Evaluation(context).evaluateFeature("dark-mode", Boolean.class);

        // Then
        assertEquals(FeatureResultSource.DEFAULT_VALUE, result.getSource());
    }

    @Test
    @DisplayName("Ignores a malformed page URL")
    void ignoresMalformedPageUrl() {
        // Given
        EvaluationContext context = context("{'dark-mode': {'defaultValue': false}}",
                Options.builder().allowUrlOverrides(true).url("not a url ?gb~dark-mode=on").build(), null);

        // When
        FeatureResult<Boolean> result = new Evaluation(context).evaluateFeature("dark-mode", Boolean.class);

        // Then
        assertEquals(FeatureResultSource.DEFAULT_VALUE, result.getSource());
    }

    @Test
    @DisplayName("Prefers a user forced feature value over a URL override")
    void prefersForcedValueOverUrlOverride() {
        // Given
        Options options = Options.builder().allowUrlOverrides(true).url("http://localhost/?gb~dark-mode=on").build();
        UserContext user = UserContext.builder()
                .attributes(attributes("user-1"))
                .forcedFeatureValues(Collections.<String, Object>singletonMap("dark-mode", false))
                .build();
        EvaluationContext context = new EvaluationContext(
                globalContext("{'dark-mode': {'defaultValue': false}}"), user, new EvaluationContext.StackContext(), options);

        // When
        FeatureResult<Boolean> result = new Evaluation(context).evaluateFeature("dark-mode", Boolean.class);

        // Then
        assertEquals(FeatureResultSource.OVERRIDE, result.getSource());
        assertEquals(Boolean.FALSE, result.getValue());
    }

    @Test
    @DisplayName("Forces an experiment variation from the query string")
    void forcesExperimentVariationFromQueryString() {
        // Given
        EvaluationContext context = context(null,
                Options.builder().allowUrlOverrides(true).url("http://localhost/?exp-1=1").build(), null);

        // When
        ExperimentResult<String> result = new Evaluation(context).runExperiment(twoVariationExperiment(), null);

        // Then
        assertEquals(Integer.valueOf(1), result.getVariationId());
        assertEquals("treatment", result.getValue());
        assertFalse(result.getHashUsed());
    }

    @Test
    @DisplayName("Reports a remote evaluation exposure once per tracker")
    void reportsRemoteEvaluationTrackOncePerTracker() {
        // Given
        RecordingTrackingCallback tracking = new RecordingTrackingCallback();
        EvaluationContext context = context(null,
                Options.builder().trackingCallBackWithUser(tracking).build(), new ExperimentTracker());
        TrackData<String> track = new TrackData<>(twoVariationExperiment(), remoteResult());

        // When
        new Evaluation(context).fireRemoteEvaluationTracks(Arrays.asList(track, track));
        new Evaluation(context).fireRemoteEvaluationTracks(Collections.singletonList(track));

        // Then
        assertEquals(Collections.singletonList("exp-1"), tracking.experimentKeys);
    }

    @Test
    @DisplayName("Skips malformed remote evaluation tracks and reports the well-formed ones")
    void skipsMalformedRemoteEvaluationTracks() {
        // Given
        RecordingTrackingCallback tracking = new RecordingTrackingCallback();
        EvaluationContext context = context(null,
                Options.builder().trackingCallBackWithUser(tracking).build(), null);
        Experiment<String> withoutKey = Experiment.<String>builder()
                .variations(new ArrayList<>(Arrays.asList("control", "treatment")))
                .build();
        Experiment<String> withoutVariations = Experiment.<String>builder().key("exp-2").variations(null).build();
        List<TrackData<String>> tracks = Arrays.asList(
                null,
                new TrackData<String>(null, remoteResult()),
                new TrackData<String>(twoVariationExperiment(), null),
                new TrackData<>(withoutKey, remoteResult()),
                new TrackData<>(withoutVariations, remoteResult()),
                new TrackData<>(twoVariationExperiment(), remoteResult()));

        // When
        new Evaluation(context).fireRemoteEvaluationTracks(tracks);

        // Then
        assertEquals(Collections.singletonList("exp-1"), tracking.experimentKeys);
    }

    @Test
    @DisplayName("Ignores a null remote evaluation track list")
    void ignoresNullRemoteEvaluationTracks() {
        // Given
        RecordingTrackingCallback tracking = new RecordingTrackingCallback();
        EvaluationContext context = context(null,
                Options.builder().trackingCallBackWithUser(tracking).build(), null);

        // When
        new Evaluation(context).fireRemoteEvaluationTracks(null);

        // Then
        assertTrue(tracking.experimentKeys.isEmpty());
    }

    @Test
    @DisplayName("Does not propagate a throwing tracking callback for remote evaluation tracks")
    void doesNotPropagateThrowingCallbackForRemoteTracks() {
        // Given
        EvaluationContext context = context(null,
                Options.builder().trackingCallBackWithUser(new ThrowingTrackingCallback()).build(), null);
        List<TrackData<String>> tracks = Collections.singletonList(
                new TrackData<>(twoVariationExperiment(), remoteResult()));

        // When
        Evaluation evaluation = new Evaluation(context);

        // Then
        assertDoesNotThrow(() -> evaluation.fireRemoteEvaluationTracks(tracks));
    }

    @Test
    @DisplayName("Reports a forced rule's remote evaluation tracks when the rule applies")
    void reportsForceRuleTracks() {
        // Given
        RecordingTrackingCallback tracking = new RecordingTrackingCallback();
        EvaluationContext context = context("{'dark-mode': {'defaultValue': false, 'rules': [{'force': true, 'tracks': [{"
                        + "'experiment': {'key': 'remote-exp', 'variations': [false, true]},"
                        + "'result': {'variationId': 1, 'inExperiment': true, 'hashAttribute': 'id', 'hashValue': 'user-1'}"
                        + "}]}]}}",
                Options.builder().trackingCallBackWithUser(tracking).build(), null);

        // When
        FeatureResult<Boolean> result = new Evaluation(context).evaluateFeature("dark-mode", Boolean.class);

        // Then
        assertEquals(FeatureResultSource.FORCE, result.getSource());
        assertEquals(Collections.singletonList("remote-exp"), tracking.experimentKeys);
    }

    private static String prerequisiteFeatures(boolean parentValue, boolean gate) {
        return "{"
                + "'parent': {'defaultValue': " + parentValue + "},"
                + "'child': {'defaultValue': 'fallback', 'rules': [{'parentConditions': ["
                + "  {'id': 'parent', 'condition': {'value': true}, 'gate': " + gate + "}"
                + "], 'force': 'forced'}]}}";
    }

    private static Experiment<String> twoVariationExperiment() {
        return Experiment.<String>builder()
                .key("exp-1")
                .variations(new ArrayList<>(Arrays.asList("control", "treatment")))
                .build();
    }

    private static Experiment<String> experimentRequiringParent() {
        ParentCondition parentCondition = new ParentCondition(
                "parent", JsonParser.parseString("{\"value\": true}").getAsJsonObject(), null);
        return Experiment.<String>builder()
                .key("exp-1")
                .variations(new ArrayList<>(Arrays.asList("control", "treatment")))
                .parentConditions(new ArrayList<>(Collections.singletonList(parentCondition)))
                .build();
    }

    private static ExperimentResult<String> remoteResult() {
        return ExperimentResult.<String>builder()
                .inExperiment(true)
                .variationId(1)
                .value("treatment")
                .hashAttribute("id")
                .hashValue("user-1")
                .build();
    }

    private static EvaluationContext context(String featuresJson, Options options, ExperimentTracker tracker) {
        return context(featuresJson, options, tracker, "user-1");
    }

    private static EvaluationContext context(String featuresJson, Options options, ExperimentTracker tracker, String userId) {
        UserContext user = UserContext.builder().attributes(attributes(userId)).build();
        EvaluationContext context = new EvaluationContext(
                globalContext(featuresJson), user, new EvaluationContext.StackContext(), options);
        context.setExperimentTracker(tracker);
        return context;
    }

    private static GlobalContext globalContext(String featuresJson) {
        return GlobalContext.builder()
                .features(featuresJson == null ? null : TransformationUtil.transformFeatures(featuresJson.replace('\'', '"')))
                .build();
    }

    private static JsonObject attributes(String userId) {
        return JsonParser.parseString("{\"id\": \"" + userId + "\"}").getAsJsonObject();
    }

    private static final class RecordingTrackingCallback implements TrackingCallbackWithUser {
        private final List<String> experimentKeys = new ArrayList<>();

        @Override
        public <T> void onTrack(Experiment<T> experiment, ExperimentResult<T> experimentResult, UserContext userContext) {
            experimentKeys.add(experiment.getKey());
        }
    }

    private static final class ThrowingTrackingCallback implements TrackingCallbackWithUser {
        @Override
        public <T> void onTrack(Experiment<T> experiment, ExperimentResult<T> experimentResult, UserContext userContext) {
            throw new IllegalStateException("tracking failed");
        }
    }

    private static final class RecordingFeatureUsageCallback implements FeatureUsageCallbackWithUser {
        private final List<String> featureKeys = new ArrayList<>();

        @Override
        public <T> void onFeatureUsage(String featureKey, FeatureResult<T> result, UserContext userContext) {
            featureKeys.add(featureKey);
        }
    }

    private static final class ThrowingFeatureUsageCallback implements FeatureUsageCallbackWithUser {
        @Override
        public <T> void onFeatureUsage(String featureKey, FeatureResult<T> result, UserContext userContext) {
            throw new IllegalStateException("usage failed");
        }
    }
}
