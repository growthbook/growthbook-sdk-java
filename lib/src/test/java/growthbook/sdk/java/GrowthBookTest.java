package growthbook.sdk.java;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import growthbook.sdk.java.callback.ExperimentRunCallback;
import growthbook.sdk.java.callback.FeatureUsageCallback;
import growthbook.sdk.java.callback.TrackingCallback;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.FeatureResultSource;
import growthbook.sdk.java.model.GBContext;
import growthbook.sdk.java.multiusermode.util.TransformationUtil;
import growthbook.sdk.java.testhelpers.PaperCupsConfig;
import growthbook.sdk.java.testhelpers.TestCasesJsonHelper;
import growthbook.sdk.java.testhelpers.TestContext;
import growthbook.sdk.java.util.ForcedVariationsUtils;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

class GrowthBookTest {

    private static final GrowthBookJsonUtils JSON = GrowthBookJsonUtils.getInstance();

    private static final String URL_FEATURES = "{"
            + "\"banner_text\":{\"defaultValue\":\"Welcome!\",\"rules\":[{\"condition\":{\"country\":\"france\"},\"force\":\"Bienvenue !\"}]},"
            + "\"dark_mode\":{\"defaultValue\":false,\"rules\":[{\"condition\":{\"loggedIn\":true},\"force\":true,\"coverage\":0.5,\"hashAttribute\":\"id\"}]},"
            + "\"donut_price\":{\"defaultValue\":2.5,\"rules\":[{\"condition\":{\"employee\":true},\"force\":0}]},"
            + "\"meal_overrides_gluten_free\":{\"defaultValue\":{\"meal_type\":\"standard\",\"dessert\":\"Strawberry Cheesecake\"},"
            + "\"rules\":[{\"condition\":{\"dietaryRestrictions\":{\"$elemMatch\":{\"$eq\":\"gluten_free\"}}},"
            + "\"force\":{\"meal_type\":\"gf\",\"dessert\":\"French Vanilla Ice Cream\"}}]}"
            + "}";
    private static final String MEAL_OVERRIDE_PARAM =
            "gb~meal_overrides_gluten_free=%7B%22meal_type%22%3A%20%22gf%22%2C%20%22dessert%22%3A%20%22French%20Vanilla%20Ice%20Cream%22%7D";
    private static final String BANNER_OVERRIDE_PARAM =
            "gb~banner_text=Hello%2C%20everyone!%20I%20hope%20you%20are%20all%20doing%20well!";

    @ParameterizedTest(name = "{0}")
    @MethodSource("featureCases")
    @DisplayName("Evaluates the shared feature spec cases")
    void evaluatesSharedFeatureSpecCases(String description, JsonArray testCase) {
        // Given
        GrowthBook subject = featureCaseSubject(testCase.get(1).getAsJsonObject());

        // When
        FeatureResult<Object> result = subject.evalFeature(testCase.get(2).getAsString(), Object.class);

        // Then
        assertMatchesExpectedFeatureResult(testCase.get(3).getAsJsonObject(), result);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("savedGroupReferencesV2FeatureCases")
    @DisplayName("Evaluates the shared savedGroupReferencesV2 feature spec cases")
    void evaluatesSavedGroupReferencesV2FeatureSpecCases(String description, JsonArray testCase) {
        // Given
        GrowthBook subject = featureCaseSubject(testCase.get(1).getAsJsonObject());

        // When
        FeatureResult<Object> result = subject.evalFeature(testCase.get(2).getAsString(), Object.class);

        // Then
        assertMatchesExpectedFeatureResult(testCase.get(3).getAsJsonObject(), result);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("runCases")
    @DisplayName("Runs the shared experiment spec cases")
    void runsSharedExperimentSpecCases(String description, JsonArray testCase) {
        // Given
        GrowthBook subject = runCaseSubject(testCase.get(1).getAsJsonObject());
        Experiment<Object> experiment = runCaseExperiment(testCase.get(2).getAsJsonObject());

        // When
        ExperimentResult<Object> result = subject.run(experiment);

        // Then
        assertMatchesExpectedRunResult(testCase, result);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("savedGroupReferencesV2RunCases")
    @DisplayName("Runs the shared savedGroupReferencesV2 experiment spec cases")
    void runsSavedGroupReferencesV2ExperimentSpecCases(String description, JsonArray testCase) {
        // Given
        GrowthBook subject = runCaseSubject(testCase.get(1).getAsJsonObject());
        Experiment<Object> experiment = runCaseExperiment(testCase.get(2).getAsJsonObject());

        // When
        ExperimentResult<Object> result = subject.run(experiment);

        // Then
        assertMatchesExpectedRunResult(testCase, result);
    }

    @Test
    @DisplayName("Passes the feature key and result to the feature usage callback")
    void passesFeatureKeyAndResultToFeatureUsageCallback() {
        // Given
        FeatureUsageCallback featureUsageCallback = mock(FeatureUsageCallback.class);
        GrowthBook subject = new GrowthBook(GBContext.builder()
                .featuresJson(demoFeatures())
                .featureUsageCallback(featureUsageCallback)
                .build());
        ArgumentCaptor<FeatureResult<?>> captor = ArgumentCaptor.forClass(FeatureResult.class);

        // When
        String value = subject.getFeatureValue("h1-title", "unknown feature key");

        // Then
        verify(featureUsageCallback).onFeatureUsage(eq("h1-title"), captor.capture());
        assertEquals("Welcome to our app!!!", value);
        assertEquals("Welcome to our app!!!", captor.getValue().getValue());
        assertEquals(FeatureResultSource.DEFAULT_VALUE, captor.getValue().getSource());
    }

    @Test
    @DisplayName("Preserves custom fields on the experiment returned by feature evaluation")
    void preservesExperimentCustomFields() {
        // Given
        String features = "{\"signup-copy\":{\"defaultValue\":false,\"rules\":[{\"key\":\"signup-copy-exp\","
                + "\"variations\":[false,true],\"coverage\":1,\"hashAttribute\":\"id\","
                + "\"customFields\":{\"cfl_ticket\":\"TICKET-1\",\"cfl_owner\":\"team-1\"}}]}}";
        GrowthBook subject = new GrowthBook(GBContext.builder()
                .featuresJson(features)
                .attributesJson("{\"id\":\"user-1\"}")
                .build());

        // When
        FeatureResult<Boolean> result = subject.evalFeature("signup-copy", Boolean.class);

        // Then
        assertEquals(FeatureResultSource.EXPERIMENT, result.getSource());
        assertEquals("signup-copy-exp", result.getExperiment().getKey());
        assertEquals("TICKET-1", result.getExperiment().getCustomField("cfl_ticket"));
        assertEquals("team-1", result.getExperiment().getCustomField("cfl_owner"));
    }

    @Test
    @DisplayName("Notifies every subscribed experiment run callback")
    void notifiesEverySubscribedExperimentRunCallback() {
        // Given
        GrowthBook subject = new GrowthBook();
        ExperimentRunCallback callback1 = mock(ExperimentRunCallback.class);
        ExperimentRunCallback callback2 = mock(ExperimentRunCallback.class);
        Experiment<String> experiment = Experiment.<String>builder().build();
        subject.subscribe(callback1);
        subject.subscribe(callback2);

        // When
        ExperimentResult<String> result = subject.run(experiment);

        // Then
        verify(callback1).onRun(experiment, result);
        verify(callback2).onRun(experiment, result);
    }

    @Test
    @DisplayName("Notifies experiment run callbacks once when the same experiment runs repeatedly")
    void notifiesExperimentRunCallbacksOnceForRepeatedRuns() {
        // Given
        GrowthBook subject = new GrowthBook();
        ExperimentRunCallback callback = mock(ExperimentRunCallback.class);
        Experiment<String> experiment = Experiment.<String>builder().key("experiment-1").build();
        subject.subscribe(callback);

        // When
        subject.run(experiment);
        subject.run(experiment);
        subject.run(experiment);

        // Then
        verify(callback, times(1)).onRun(any(), any());
    }

    @Test
    @DisplayName("A keyless and an empty-key experiment each notify their own callback")
    void keylessAndEmptyKeyExperimentsEachNotifyCallback() {
        // Given
        GrowthBook subject = new GrowthBook();
        ExperimentRunCallback callback = mock(ExperimentRunCallback.class);
        Experiment<String> keyless = Experiment.<String>builder().build();
        Experiment<String> emptyKey = Experiment.<String>builder().key("").build();
        subject.subscribe(callback);

        // When
        ExperimentResult<String> keylessResult = subject.run(keyless);
        ExperimentResult<String> emptyKeyResult = subject.run(emptyKey);

        // Then
        verify(callback).onRun(keyless, keylessResult);
        verify(callback).onRun(emptyKey, emptyKeyResult);
        verify(callback, times(2)).onRun(any(), any());
    }

    @Test
    @DisplayName("A keyless experiment notifies callbacks once across repeated runs")
    void keylessExperimentNotifiesCallbacksOnceAcrossRepeatedRuns() {
        // Given
        GrowthBook subject = new GrowthBook();
        ExperimentRunCallback callback = mock(ExperimentRunCallback.class);
        Experiment<String> keyless = Experiment.<String>builder().build();
        subject.subscribe(callback);

        // When
        subject.run(keyless);
        subject.run(keyless);
        subject.run(keyless);

        // Then
        verify(callback, times(1)).onRun(any(), any());
    }

    @Test
    @DisplayName("Notifies experiment run callbacks once per distinct experiment when runs interleave")
    void notifiesExperimentRunCallbacksOncePerDistinctExperiment() {
        // Given
        GrowthBook subject = new GrowthBook(GBContext.builder().attributesJson("{\"id\":\"user-1\"}").build());
        ExperimentRunCallback callback = mock(ExperimentRunCallback.class);
        Experiment<String> experiment1 = Experiment.<String>builder()
                .key("experiment-1")
                .variations(new ArrayList<>(Arrays.asList("a", "b")))
                .build();
        Experiment<String> experiment2 = Experiment.<String>builder()
                .key("experiment-2")
                .variations(new ArrayList<>(Arrays.asList("a", "b")))
                .build();
        subject.subscribe(callback);

        // When
        subject.run(experiment1);
        subject.run(experiment2);
        subject.run(experiment1);

        // Then
        verify(callback).onRun(eq(experiment1), any());
        verify(callback).onRun(eq(experiment2), any());
        verify(callback, times(2)).onRun(any(), any());
    }

    @Test
    @DisplayName("Notifies experiment run callbacks again when the assigned variation changes")
    void notifiesExperimentRunCallbacksWhenAssignedVariationChanges() {
        // Given
        GrowthBook subject = new GrowthBook(GBContext.builder().attributesJson("{\"id\":\"user-1\"}").build());
        ExperimentRunCallback callback = mock(ExperimentRunCallback.class);
        Experiment<String> forcedToFirst = Experiment.<String>builder()
                .key("experiment-1")
                .variations(new ArrayList<>(Arrays.asList("a", "b")))
                .force(0)
                .build();
        Experiment<String> forcedToSecond = Experiment.<String>builder()
                .key("experiment-1")
                .variations(new ArrayList<>(Arrays.asList("a", "b")))
                .force(1)
                .build();
        subject.subscribe(callback);

        // When
        ExperimentResult<String> first = subject.run(forcedToFirst);
        ExperimentResult<String> second = subject.run(forcedToSecond);
        subject.run(forcedToSecond);

        // Then
        assertEquals("a", first.getValue());
        assertEquals("b", second.getValue());
        verify(callback).onRun(forcedToFirst, first);
        verify(callback).onRun(forcedToSecond, second);
        verify(callback, times(2)).onRun(any(), any());
    }

    @Test
    @DisplayName("Fires the tracking callback once per unique experiment assignment across repeated runs")
    void firesTrackingCallbackOncePerAssignment() {
        // Given
        TrackingCallback trackingCallback = mock(TrackingCallback.class);
        GrowthBook subject = new GrowthBook(GBContext.builder()
                .attributesJson("{\"id\":\"1\"}")
                .enabled(true)
                .trackingCallback(trackingCallback)
                .build());
        Experiment<Integer> experiment = Experiment.<Integer>builder()
                .key("experiment-1")
                .variations(new ArrayList<>(Arrays.asList(0, 1)))
                .build();

        // When
        subject.run(experiment);
        subject.run(experiment);
        subject.run(experiment);

        // Then
        verify(trackingCallback, times(1)).onTrack(any(), any());
    }

    @Test
    @DisplayName("Fires the tracking callback once for each distinct experiment assignment")
    void firesTrackingCallbackPerDistinctAssignment() {
        // Given
        TrackingCallback trackingCallback = mock(TrackingCallback.class);
        GrowthBook subject = new GrowthBook(GBContext.builder()
                .attributesJson("{\"id\":\"1\"}")
                .enabled(true)
                .trackingCallback(trackingCallback)
                .build());
        Experiment<Integer> experiment1 = Experiment.<Integer>builder()
                .key("experiment-1")
                .variations(new ArrayList<>(Arrays.asList(0, 1)))
                .build();
        Experiment<Integer> experiment2 = Experiment.<Integer>builder()
                .key("experiment-2")
                .variations(new ArrayList<>(Arrays.asList(0, 1)))
                .build();

        // When
        subject.run(experiment1);
        subject.run(experiment2);
        subject.run(experiment1);
        subject.run(experiment2);

        // Then
        verify(trackingCallback).onTrack(eq(experiment1), any());
        verify(trackingCallback).onTrack(eq(experiment2), any());
        verify(trackingCallback, times(2)).onTrack(any(), any());
    }

    @Test
    @DisplayName("Stops notifying experiment run callbacks after destroy")
    void stopsNotifyingExperimentRunCallbacksAfterDestroy() {
        // Given
        GrowthBook subject = new GrowthBook();
        ExperimentRunCallback callback = mock(ExperimentRunCallback.class);
        Experiment<String> before = Experiment.<String>builder().key("experiment-1").build();
        Experiment<String> after = Experiment.<String>builder().key("experiment-2").build();
        subject.subscribe(callback);
        ExperimentResult<String> beforeResult = subject.run(before);

        // When
        subject.destroy();
        subject.run(after);

        // Then
        verify(callback).onRun(before, beforeResult);
        verifyNoMoreInteractions(callback);
    }

    @Test
    @DisplayName("A feature forced on for matching attributes is on")
    void featureForcedOnForMatchingAttributesIsOn() {
        // Given
        GrowthBook subject = new GrowthBook(GBContext.builder()
                .featuresJson(demoFeatures())
                .attributesJson("{ \"user_group\": \"subscriber\", \"beta_users\": true }")
                .build());

        // When
        FeatureResult<Object> feature = subject.evalFeature("new-feature", Object.class);

        // Then
        assertEquals(FeatureResultSource.FORCE, feature.getSource());
        assertTrue(feature.isOn());
        assertFalse(feature.isOff());
    }

    @Test
    @DisplayName("A feature whose rules do not match is off")
    void featureWithoutMatchingRulesIsOff() {
        // Given
        GrowthBook subject = new GrowthBook(GBContext.builder()
                .featuresJson(demoFeatures())
                .attributesJson("{ \"user_group\": \"standard\", \"beta_users\": false }")
                .build());

        // When
        boolean on = subject.isOn("new-feature");
        boolean off = subject.isOff("new-feature");

        // Then
        assertFalse(on);
        assertTrue(off);
    }

    @Test
    @DisplayName("isOn returns the same answer on repeated calls")
    void isOnIsStableAcrossRepeatedCalls() {
        // Given
        GrowthBook subject = new GrowthBook(GBContext.builder()
                .features(TransformationUtil.transformFeatures("{\"flag\":{\"defaultValue\":true}}"))
                .build());

        // When
        boolean first = subject.isOn("flag");
        boolean second = subject.isOn("flag");

        // Then
        assertTrue(first);
        assertTrue(second);
    }

    @Test
    @DisplayName("getFeatureValue returns a boolean forced by a rule")
    void getFeatureValueReturnsForcedBoolean() {
        // Given
        GrowthBook subject = demoSubject("{ \"user_group\": \"subscriber\", \"beta_users\": true }");

        // When
        Boolean result = subject.getFeatureValue("new-feature", false);

        // Then
        assertTrue(result);
    }

    @ParameterizedTest(name = "beta_users = {0}")
    @CsvSource({
            "true, Beta users",
            "false, We're safe"
    })
    @DisplayName("getFeatureValue returns the string chosen by the rules")
    void getFeatureValueReturnsStringChosenByRules(boolean betaUser, String expected) {
        // Given
        GrowthBook subject = demoSubject("{ \"user_group\": \"subscriber\", \"beta_users\": " + betaUser + " }");

        // When
        String result = subject.getFeatureValue("covid-banner-text", "nope");

        // Then
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("getFeatureValue returns a forced number as a double and as an integer")
    void getFeatureValueReturnsForcedNumberAsDoubleAndInteger() {
        // Given
        GrowthBook subject = demoSubject("{ \"admin\": true }");

        // When
        Double asDouble = subject.getFeatureValue("demo", Double.valueOf(999));
        Integer asInteger = subject.getFeatureValue("demo", 999);

        // Then
        assertEquals(1.0, asDouble);
        assertEquals(1, asInteger);
    }

    @Test
    @DisplayName("getFeatureValue returns a float default value from the feature")
    void getFeatureValueReturnsFloat() {
        // Given
        GrowthBook subject = demoSubject("{ \"user\": \"standard\" }");

        // When
        Float result = subject.getFeatureValue("price", 0.00f);

        // Then
        assertEquals(10.99f, result);
    }

    @Test
    @DisplayName("getFeatureValue with an object default returns a value that serializes to the configured object")
    void getFeatureValueWithObjectDefaultReturnsConfiguredObject() {
        // Given
        GrowthBook subject = demoSubject("{ \"user\": \"standard\" }");
        Object defaultConfig = new PaperCupsConfig("token-1", "Default title", true);

        // When
        Object result = subject.getFeatureValue("papercups-config", defaultConfig);

        // Then
        PaperCupsConfig config = JSON.gson.fromJson(JSON.gson.toJson(result), PaperCupsConfig.class);
        assertEquals("Welcome to GrowthBook Cloud", config.title);
        assertFalse(config.showAgentAvailability);
    }

    @Test
    @DisplayName("getFeatureValue deserializes the value into the given class")
    void getFeatureValueDeserializesIntoGivenClass() {
        // Given
        GrowthBook subject = demoSubject("{ \"user\": \"standard\" }");
        PaperCupsConfig defaultConfig = new PaperCupsConfig("token-1", "Default title", true);

        // When
        PaperCupsConfig result = subject.getFeatureValue("papercups-config", defaultConfig, PaperCupsConfig.class);

        // Then
        assertEquals("Welcome to GrowthBook Cloud", result.title);
        assertFalse(result.showAgentAvailability);
    }

    @Test
    @DisplayName("getFeatureValue reads a value from encrypted features")
    void getFeatureValueReadsEncryptedFeatures() {
        // Given
        String encryptedFeatures = "7rvPA94JEsqRo9yPZsdsXg==.bJ8vtYvX+ur3cEUFVkYo1OyWb98oLnMlpeoO0Hs4YPc0EVb7oKX4KNz+Yt6GUMBsieXqtL7oaYzX+kMayZEtV+3bhyDYnS9QBrvalnfxbLExjtnsy8g0pPQHU/P/DPIzO0F+pphcahRfi+3AMTnIreqvkqrcX+MyOwHN56lqEs23Vp4Rsq2qDow/LZmn5kpwMNhMY0DBq7jC+lh2Oyly0g==";
        GrowthBook subject = new GrowthBook(GBContext.builder()
                .attributesJson("{\"country\": \"mexico\", \"device\": \"android\"}")
                .featuresJson(encryptedFeatures)
                .encryptionKey("BhB1wORFmZLTDjbvstvS8w==")
                .build());

        // When
        String result = subject.getFeatureValue("greeting", "hello");

        // Then
        assertEquals("hola", result);
    }

    @Test
    @DisplayName("getFeatureValue returns the default value for an unknown feature")
    void getFeatureValueReturnsDefaultForUnknownFeature() {
        // Given
        GrowthBook subject = new GrowthBook(GBContext.builder().featuresJson("{}").attributesJson("{}").build());
        PaperCupsConfig defaultConfig = new PaperCupsConfig("token-1", "Default title", true);

        // When / Then
        assertEquals("fallback", subject.getFeatureValue("missing", "fallback"));
        assertEquals(10.0f, subject.getFeatureValue("missing", 10.0f));
        assertEquals(99, subject.getFeatureValue("missing", 99));
        assertEquals(Double.valueOf(101), subject.getFeatureValue("missing", Double.valueOf(101)));
        assertSame(defaultConfig, subject.getFeatureValue("missing", defaultConfig, PaperCupsConfig.class));
    }

    @ParameterizedTest(name = "attribute id = {0}, member = {1}")
    @CsvSource({
            "user-1, true",
            "user-3, false"
    })
    @DisplayName("evaluateCondition resolves saved groups from the context")
    void evaluateConditionResolvesSavedGroupsFromContext(String id, boolean expected) {
        // Given
        GrowthBook subject = new GrowthBook(GBContext.builder()
                .savedGroupsJson("{\"grp_beta\":[\"user-1\",\"user-2\"]}")
                .build());

        // When
        boolean result = subject.evaluateCondition("{\"id\":\"" + id + "\"}", "{\"id\":{\"$inGroup\":\"grp_beta\"}}");

        // Then
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("evaluateCondition returns false for a condition that is not a JSON object")
    void evaluateConditionReturnsFalseForMalformedCondition() {
        // Given
        GrowthBook subject = new GrowthBook();

        // When
        boolean result = subject.evaluateCondition("{\"name\": \"world\"}", "[\"$not\": { \"name\": \"hello\" }]");

        // Then
        assertFalse(result);
    }

    @ParameterizedTest(name = "gb~dark_mode={0}")
    @CsvSource({
            "true, false, true",
            "on, false, true",
            "1, false, true",
            "false, true, false",
            "off, true, false",
            "0, true, false"
    })
    @DisplayName("A URL override forces a boolean value")
    void urlOverrideForcesBooleanValue(String urlValue, boolean defaultValue, boolean expected) {
        // Given
        GrowthBook subject = urlOverrideSubject("gb~dark_mode=" + urlValue);

        // When
        Boolean result = subject.getFeatureValue("dark_mode", defaultValue);

        // Then
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("A URL override forces an integer value")
    void urlOverrideForcesIntegerValue() {
        // Given
        GrowthBook subject = urlOverrideSubject("gb~donut_price=2");

        // When
        Integer result = subject.getFeatureValue("donut_price", 999);

        // Then
        assertEquals(2, result);
    }

    @Test
    @DisplayName("A URL override forces a float value")
    void urlOverrideForcesFloatValue() {
        // Given
        GrowthBook subject = urlOverrideSubject("gb~donut_price=3.33");

        // When
        Float result = subject.getFeatureValue("donut_price", 9999f);

        // Then
        assertEquals(3.33f, result);
    }

    @Test
    @DisplayName("A URL override forces a decoded string value")
    void urlOverrideForcesStringValue() {
        // Given
        GrowthBook subject = urlOverrideSubject(BANNER_OVERRIDE_PARAM);

        // When
        String result = subject.getFeatureValue("banner_text", "???");

        // Then
        assertEquals("Hello, everyone! I hope you are all doing well!", result);
    }

    @Test
    @DisplayName("A URL override forces a JSON value deserialized into the default value's class")
    void urlOverrideForcesJsonValueIntoDefaultValueClass() {
        // Given
        GrowthBook subject = urlOverrideSubject(MEAL_OVERRIDE_PARAM);

        // When
        MealOrder result = (MealOrder) subject.getFeatureValue("meal_overrides_gluten_free", new MealOrder(MealType.STANDARD, "Donut"));

        // Then
        assertEquals(MealType.GLUTEN_FREE, result.mealType);
        assertEquals("French Vanilla Ice Cream", result.dessert);
    }

    @Test
    @DisplayName("A URL override forces a JSON value that can be read as a string")
    void urlOverrideForcesJsonValueReadableAsString() {
        // Given
        GrowthBook subject = urlOverrideSubject(MEAL_OVERRIDE_PARAM);

        // When
        String result = subject.getFeatureValue("meal_overrides_gluten_free", "{\"meal_type\": \"standard\", \"dessert\": \"Donut\"}");

        // Then
        MealOrder order = JSON.gson.fromJson(result, MealOrder.class);
        assertEquals(MealType.GLUTEN_FREE, order.mealType);
        assertEquals("French Vanilla Ice Cream", order.dessert);
    }

    @Test
    @DisplayName("A URL override JSON value that does not fit the default value's class yields the default value")
    void urlOverrideJsonValueNotFittingDefaultClassYieldsDefault() {
        // Given
        GrowthBook subject = urlOverrideSubject(MEAL_OVERRIDE_PARAM);
        UnrelatedConfig defaultValue = new UnrelatedConfig();

        // When
        Object result = subject.getFeatureValue("meal_overrides_gluten_free", defaultValue);

        // Then
        assertEquals(defaultValue, result);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("urlOverrideEvalFeatureCases")
    @DisplayName("evalFeature reports a URL override as the source of the forced value")
    void evalFeatureReportsUrlOverrideSource(String featureKey, String urlParam, Class<?> valueType, Object expected) {
        // Given
        GrowthBook subject = urlOverrideSubject(urlParam);

        // When
        FeatureResult<?> result = subject.evalFeature(featureKey, valueType);

        // Then
        assertEquals(expected, result.getValue());
        assertEquals(FeatureResultSource.URL_OVERRIDE, result.getSource());
    }

    private static Stream<Arguments> urlOverrideEvalFeatureCases() {
        return Stream.of(
                Arguments.of("dark_mode", "gb~dark_mode=true", Boolean.class, true),
                Arguments.of("banner_text", BANNER_OVERRIDE_PARAM, String.class, "Hello, everyone! I hope you are all doing well!"),
                Arguments.of("donut_price", "gb~donut_price=3.33", Float.class, 3.33f),
                Arguments.of("donut_price", "gb~donut_price=4", Integer.class, 4)
        );
    }

    private static GrowthBook urlOverrideSubject(String query) {
        return new GrowthBook(GBContext.builder()
                .featuresJson(URL_FEATURES)
                .attributesJson("{}")
                .url("http://localhost:8080/url-feature-force?" + query)
                .allowUrlOverrides(true)
                .build());
    }

    private static GrowthBook demoSubject(String attributes) {
        return new GrowthBook(GBContext.builder().featuresJson(demoFeatures()).attributesJson(attributes).build());
    }

    private static String demoFeatures() {
        return TestCasesJsonHelper.getInstance().getDemoFeaturesJson();
    }

    private static GrowthBook featureCaseSubject(JsonObject input) {
        JsonElement features = input.get("features");
        JsonElement attributes = input.get("attributes");
        JsonElement savedGroups = input.get("savedGroups");
        return new GrowthBook(GBContext.builder()
                .featuresJson(features == null ? null : features.toString())
                .attributesJson(attributes == null ? null : attributes.toString())
                .forcedVariationsMap(forcedVariations(input))
                .savedGroups(savedGroups == null ? null : savedGroups.getAsJsonObject())
                .build());
    }

    private static void assertMatchesExpectedFeatureResult(JsonObject expectedJson, FeatureResult<Object> result) {
        FeatureResult<?> expected = JSON.gson.fromJson(expectedJson, FeatureResult.class);
        assertEquals(expected.getValue(), result.getValue(), "value");
        assertEquals(expected.isOn(), result.isOn(), "on");
        assertEquals(expected.isOff(), result.isOff(), "off");
        assertEquals(expected.getSource(), result.getSource(), "source");
        assertEquals(expected.getRuleId(), result.getRuleId(), "ruleId");
        if (expected.getExperimentResult() != null) {
            ExperimentResult<?> expectedExperiment = expected.getExperimentResult();
            ExperimentResult<Object> actualExperiment = result.getExperimentResult();
            assertEquals(expectedExperiment.getHashValue(), actualExperiment == null ? null : actualExperiment.getHashValue(), "hashValue");
            assertEquals(expectedExperiment.getBucket(), actualExperiment == null ? null : actualExperiment.getBucket(), "bucket");
            assertEquals(expectedExperiment.getKey(), actualExperiment == null ? null : actualExperiment.getKey(), "key");
        }
    }

    private static GrowthBook runCaseSubject(JsonObject input) {
        JsonElement features = input.get("features");
        JsonElement attributes = input.get("attributes");
        JsonElement savedGroups = input.get("savedGroups");
        TestContext testContext = JSON.gson.fromJson(input, TestContext.class);
        return new GrowthBook(GBContext.builder()
                .featuresJson(features == null ? "{}" : features.toString())
                .attributesJson(attributes == null ? "null" : attributes.toString())
                .forcedVariationsMap(forcedVariations(input))
                .isQaMode(testContext.qaMode)
                .enabled(testContext.enabled)
                .url(testContext.url)
                .savedGroups(savedGroups == null ? null : savedGroups.getAsJsonObject())
                .build());
    }

    private static Experiment<Object> runCaseExperiment(JsonObject experimentJson) {
        Experiment<Object> experiment = JSON.gson.fromJson(experimentJson, new TypeToken<Experiment<Object>>() {}.getType());
        if (experimentJson.has("condition") && experimentJson.get("condition").isJsonObject()) {
            experiment.setConditionJson(experimentJson.getAsJsonObject("condition"));
        }
        return experiment;
    }

    private static void assertMatchesExpectedRunResult(JsonArray testCase, ExperimentResult<Object> result) {
        assertEquals(JSON.gson.fromJson(testCase.get(3), Object.class), result.getValue(), "value");
        assertEquals(testCase.get(4).getAsBoolean(), result.getInExperiment(), "inExperiment");
        assertEquals(testCase.get(5).getAsBoolean(), result.getHashUsed(), "hashUsed");
    }

    private static Map<String, Integer> forcedVariations(JsonObject input) {
        HashMap<String, Object> raw = JSON.gson.fromJson(input.get("forcedVariations"),
                new TypeToken<HashMap<String, Object>>() {}.getType());
        return ForcedVariationsUtils.normalize(raw);
    }

    private static Stream<Arguments> featureCases() {
        return cases(TestCasesJsonHelper.getInstance().featureTestCases());
    }

    private static Stream<Arguments> savedGroupReferencesV2FeatureCases() {
        return cases(TestCasesJsonHelper.getInstance().getTestCases()
                .getAsJsonObject("savedGroupReferencesV2").getAsJsonArray("feature"));
    }

    private static Stream<Arguments> runCases() {
        return cases(TestCasesJsonHelper.getInstance().runTestCases());
    }

    private static Stream<Arguments> savedGroupReferencesV2RunCases() {
        return cases(TestCasesJsonHelper.getInstance().getTestCases()
                .getAsJsonObject("savedGroupReferencesV2").getAsJsonArray("run"));
    }

    private static Stream<Arguments> cases(JsonArray cases) {
        return StreamSupport.stream(cases.spliterator(), false)
                .map(JsonElement::getAsJsonArray)
                .map(testCase -> Arguments.of(testCase.get(0).getAsString(), testCase));
    }

    private enum MealType {
        @SerializedName("standard")
        STANDARD,

        @SerializedName("gf")
        GLUTEN_FREE
    }

    private static final class MealOrder {
        @SerializedName("meal_type")
        private final MealType mealType;

        @SerializedName("dessert")
        private final String dessert;

        MealOrder(MealType mealType, String dessert) {
            this.mealType = mealType;
            this.dessert = dessert;
        }
    }

    private static final class UnrelatedConfig {
        @SerializedName("foo")
        private final String foo = "FOO!";

        @Override
        public boolean equals(Object obj) {
            return obj instanceof UnrelatedConfig && Objects.equals(((UnrelatedConfig) obj).foo, this.foo);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(foo);
        }
    }
}
