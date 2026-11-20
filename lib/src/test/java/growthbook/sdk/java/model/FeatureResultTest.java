package growthbook.sdk.java.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import growthbook.sdk.java.GrowthBook;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FeatureResultTest {

    @Test
    @DisplayName("The builder and the constructor produce equal results")
    void builderAndConstructorProduceEqualResults() {
        // Given
        FeatureResult<Integer> built = FeatureResult.<Integer>builder()
                .value(100)
                .source(FeatureResultSource.DEFAULT_VALUE)
                .ruleId("rule-1")
                .build();

        // When
        FeatureResult<Integer> constructed = new FeatureResult<>(100, FeatureResultSource.DEFAULT_VALUE, null, null, "rule-1");

        // Then
        assertEquals(100, constructed.getValue());
        assertEquals(FeatureResultSource.DEFAULT_VALUE, constructed.getSource());
        assertNull(constructed.getExperiment());
        assertNull(constructed.getExperimentResult());
        assertEquals("rule-1", constructed.getRuleId());
        assertEquals(built, constructed);
    }

    @Test
    @DisplayName("The builder defaults the rule id to an empty string")
    void builderDefaultsRuleIdToEmptyString() {
        // When
        FeatureResult<String> subject = FeatureResult.<String>builder().build();

        // Then
        assertEquals("", subject.getRuleId());
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "UNKNOWN_FEATURE, unknownFeature",
            "DEFAULT_VALUE, defaultValue",
            "FORCE, force",
            "URL_OVERRIDE, urlOverride",
            "EXPERIMENT, experiment",
            "CYCLIC_PREREQUISITE, cyclicPrerequisite",
            "OVERRIDE, override",
            "PREREQUISITE, prerequisite"
    })
    @DisplayName("Writes the source as its raw string in toString and JSON")
    void writesSourceAsRawString(FeatureResultSource source, String raw) {
        // Given
        FeatureResult<Object> subject = FeatureResult.builder().source(source).build();

        // When
        String json = GrowthBookJsonUtils.getInstance().gson.toJson(subject);

        // Then
        assertEquals(raw, source.toString());
        assertEquals("{\"on\":false,\"off\":true,\"source\":\"" + raw + "\"}", json);
    }

    @ParameterizedTest(name = "{0} is on: {1}")
    @MethodSource("valuesAndOnState")
    @DisplayName("Is on for truthy values and off for falsy values")
    void isOnForTruthyValues(Object value, boolean expectedOn) {
        // Given
        FeatureResult<Object> subject = FeatureResult.builder().value(value).source(FeatureResultSource.FORCE).build();

        // When
        boolean on = subject.isOn();
        boolean off = subject.isOff();

        // Then
        assertEquals(expectedOn, on);
        assertEquals(!expectedOn, off);
    }

    @Test
    @DisplayName("A feature forced to a non-empty list is on and returns the list")
    void featureForcedToListIsOnAndReturnsList() {
        // Given
        GrowthBook growthBook = new GrowthBook(GBContext.builder()
                .featuresJson("{\"feature-1\":{\"defaultValue\":[],\"rules\":[{\"force\":[\"line1\",\"line2\"]}]}}")
                .build());

        // When
        Object value = growthBook.getFeatureValue("feature-1", new ArrayList<>());

        // Then
        assertTrue(growthBook.isOn("feature-1"));
        assertFalse(growthBook.isOff("feature-1"));
        assertEquals(Arrays.asList("line1", "line2"), value);
    }

    @Test
    @DisplayName("A feature forced to an object is on")
    void featureForcedToObjectIsOn() {
        // Given
        GrowthBook growthBook = new GrowthBook(GBContext.builder()
                .featuresJson("{\"feature-1\":{\"defaultValue\":{},\"rules\":[{\"force\":{\"theme\":\"dark\"}}]}}")
                .build());

        // When
        boolean on = growthBook.isOn("feature-1");

        // Then
        assertTrue(on);
    }

    private static Stream<Arguments> valuesAndOnState() {
        return Stream.of(
                Arguments.of(1, true),
                Arguments.of(0, false),
                Arguments.of(1.0f, true),
                Arguments.of(0.0f, false),
                Arguments.of(1.0, true),
                Arguments.of(0.0, false),
                Arguments.of("hello", true),
                Arguments.of("", false),
                Arguments.of(true, true),
                Arguments.of(false, false),
                Arguments.of(null, false),
                Arguments.of(Collections.singleton("value-1"), true),
                Arguments.of(Collections.emptyList(), true),
                Arguments.of(new JsonObject(), true),
                Arguments.of(new JsonArray(), true),
                Arguments.of(JsonNull.INSTANCE, false),
                Arguments.of(new JsonPrimitive("hello"), true),
                Arguments.of(new JsonPrimitive(""), false),
                Arguments.of(new JsonPrimitive(0), false),
                Arguments.of(new JsonPrimitive(42), true),
                Arguments.of(new JsonPrimitive(true), true),
                Arguments.of(new JsonPrimitive(false), false)
        );
    }
}
