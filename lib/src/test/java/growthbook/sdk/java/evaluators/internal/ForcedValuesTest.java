package growthbook.sdk.java.evaluators.internal;

import com.google.gson.JsonObject;
import growthbook.sdk.java.model.BucketRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ForcedValuesTest {

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "TRUE, true",
            "on, true",
            "1, true",
            "false, false",
            "OFF, false",
            "0, false"
    })
    @DisplayName("Parses true, on, 1 and false, off, 0 as a forced boolean regardless of case")
    void parsesBooleanValues(String raw, boolean expected) throws MalformedURLException {
        // Given
        URL url = new URL("http://localhost/?gb~flag=" + raw);

        // When
        Object value = ForcedValues.getForcedValueFromUrl("flag", url, Boolean.class);

        // Then
        assertEquals(expected, value);
    }

    @ParameterizedTest(name = "{1} from {0}")
    @MethodSource("parsedValues")
    @DisplayName("Parses a forced string, integer, float or double value into the requested type")
    void parsesTypedValues(String query, Class<?> type, Object expected) throws MalformedURLException {
        // Given
        URL url = new URL("http://localhost/?" + query);

        // When
        Object value = ForcedValues.getForcedValueFromUrl("key", url, type);

        // Then
        assertEquals(expected, value);
    }

    @Test
    @DisplayName("Deserializes a forced JSON object value into the requested type")
    void parsesJsonObjectValue() throws MalformedURLException {
        // Given
        URL url = new URL("http://localhost/?gb~config=%7B%22color%22%3A%22blue%22%2C%22size%22%3A3%7D");
        JsonObject expected = new JsonObject();
        expected.addProperty("color", "blue");
        expected.addProperty("size", 3);

        // When
        Object value = ForcedValues.getForcedValueFromUrl("config", url, JsonObject.class);

        // Then
        assertEquals(expected, value);
    }

    @ParameterizedTest(name = "{1} from {0}")
    @MethodSource("unparseableOrAbsentValues")
    @DisplayName("Returns null for an absent, unprefixed or unparseable forced value")
    void returnsNullForAbsentOrUnparseableValue(String url, Class<?> type) throws MalformedURLException {
        // Given
        URL pageUrl = new URL(url);

        // When
        Object value = ForcedValues.getForcedValueFromUrl("key", pageUrl, type);

        // Then
        assertNull(value);
    }

    @Test
    @DisplayName("Returns null for an empty array that a registered type adapter cannot read")
    void returnsNullWhenTypeAdapterRejectsEmptyArray() throws MalformedURLException {
        // Given
        URL url = new URL("http://localhost/?gb~range=%5B%5D");

        // When
        Object value = ForcedValues.getForcedValueFromUrl("range", url, BucketRange.class);

        // Then
        assertNull(value);
    }

    @Test
    @DisplayName("Returns null for a JSON shape that a registered type adapter cannot read")
    void returnsNullWhenTypeAdapterRejectsShape() throws MalformedURLException {
        // Given
        URL url = new URL("http://localhost/?gb~range=%7B%22start%22%3A0%7D");

        // When
        Object value = ForcedValues.getForcedValueFromUrl("range", url, BucketRange.class);

        // Then
        assertNull(value);
    }

    @ParameterizedTest(name = "exp-1={0} -> {0}")
    @ValueSource(ints = {0, 1})
    @DisplayName("Returns the variation index forced through the query string when it is in range")
    void returnsQueryStringOverrideInRange(int variation) {
        // Given
        String url = "http://localhost/?exp-1=" + variation;

        // When
        Integer override = ForcedValues.getQueryStringOverride("exp-1", url, 2);

        // Then
        assertEquals(Integer.valueOf(variation), override);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "http://localhost/?exp-1=2",
            "http://localhost/?exp-1=-1",
            "http://localhost/?exp-1=one",
            "http://localhost/?other-exp=1",
            "not a url"
    })
    @DisplayName("Returns null for an out-of-range, non-integer or absent forced variation or a malformed URL")
    void returnsNullForInvalidQueryStringOverride(String url) {
        // Given
        // When
        Integer override = ForcedValues.getQueryStringOverride("exp-1", url, 2);

        // Then
        assertNull(override);
    }

    @ParameterizedTest(name = "\"{0}\"")
    @NullAndEmptySource
    @DisplayName("Returns null for a null or empty page URL")
    void returnsNullForMissingUrl(String url) {
        // Given
        // When
        Integer override = ForcedValues.getQueryStringOverride("exp-1", url, 2);

        // Then
        assertNull(override);
    }

    static Stream<Arguments> parsedValues() {
        return Stream.of(
                Arguments.of("gb~key=hello%20world", String.class, "hello world"),
                Arguments.of("gb~key=42", Integer.class, 42),
                Arguments.of("gb~key=0.5", Float.class, 0.5f),
                Arguments.of("gb~key=9.99", Double.class, 9.99)
        );
    }

    static Stream<Arguments> unparseableOrAbsentValues() {
        return Stream.of(
                Arguments.of("http://localhost/?gb~key=yes", Boolean.class),
                Arguments.of("http://localhost/?gb~key=4.2", Integer.class),
                Arguments.of("http://localhost/?gb~key=half", Float.class),
                Arguments.of("http://localhost/?gb~key=cheap", Double.class),
                Arguments.of("http://localhost/?gb~key=%7Bnot-json", JsonObject.class),
                Arguments.of("http://localhost/?gb~other=true", Boolean.class),
                Arguments.of("http://localhost/", String.class),
                Arguments.of("http://localhost/?key=true", Boolean.class)
        );
    }
}
