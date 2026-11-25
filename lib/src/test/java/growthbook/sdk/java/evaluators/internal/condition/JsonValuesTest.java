package growthbook.sdk.java.evaluators.internal.condition;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import growthbook.sdk.java.model.DataType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonValuesTest {

    @ParameterizedTest(name = "{0} is falsy")
    @MethodSource("falsyValues")
    @DisplayName("Treats JavaScript-falsy values as falsy")
    void treatsJavaScriptFalsyValuesAsFalsy(String label, JsonElement value) {
        // Given
        // When
        boolean truthy = JsonValues.isTruthy(value);

        // Then
        assertFalse(truthy);
    }

    @ParameterizedTest(name = "{0} is truthy")
    @ValueSource(strings = {"true", "1", "-1", "0.5", "\"a\"", "\"0\"", "\"false\"", "{}", "[]", "{\"a\": 0}", "[0]"})
    @DisplayName("Treats objects, arrays, non-zero numbers, true and non-empty strings as truthy")
    void treatsOtherValuesAsTruthy(String value) {
        // Given
        JsonElement element = JsonParser.parseString(value);

        // When
        boolean truthy = JsonValues.isTruthy(element);

        // Then
        assertTrue(truthy);
    }

    static Stream<Arguments> falsyValues() {
        return Stream.of(
                Arguments.of("absent", null),
                Arguments.of("JSON null", JsonNull.INSTANCE),
                Arguments.of("false", new JsonPrimitive(false)),
                Arguments.of("0", new JsonPrimitive(0)),
                Arguments.of("0.0", new JsonPrimitive(0.0)),
                Arguments.of("-0.0", new JsonPrimitive(-0.0)),
                Arguments.of("NaN", new JsonPrimitive(Double.NaN)),
                Arguments.of("empty string", new JsonPrimitive("")));
    }

    @Test
    @DisplayName("Treats -0.0 and 0.0 as equal in ordered comparisons, like JavaScript")
    void treatsNegativeZeroAsEqualToZero() {
        // Given
        double negativeZero = -0.0;
        double positiveZero = 0.0;

        // When
        int forward = JsonValues.compareDoubles(negativeZero, positiveZero);
        int backward = JsonValues.compareDoubles(positiveZero, negativeZero);

        // Then
        assertEquals(0, forward);
        assertEquals(0, backward);
        assertNotEquals(0, Double.compare(negativeZero, positiveZero));
    }

    @ParameterizedTest(name = "{0} vs {1} -> {2}")
    @CsvSource({
            "1.0, 2.0, -1",
            "2.0, 1.0, 1",
            "16777217, 16777216, 1",
            "16777216, 16777217, -1",
            "-5.5, -5.5, 0",
    })
    @DisplayName("Orders doubles by their sign of difference")
    void ordersDoubles(double left, double right, int expected) {
        // Given
        double a = left;
        double b = right;

        // When
        int result = JsonValues.compareDoubles(a, b);

        // Then
        assertEquals(expected, result);
    }

    @ParameterizedTest(name = "{0} === {1} -> {2}")
    @CsvSource(delimiter = '|', value = {
            "1        | 1.0      | true",
            "0        | -0.0     | true",
            "1        | \"1\"    | false",
            "true     | true     | true",
            "true     | 1        | false",
            "\"a\"    | \"a\"    | true",
            "\"a\"    | \"A\"    | false",
            "null     | null     | true",
            "null     | 0        | false",
            "[1]      | [1]      | false",
            "{}       | {}       | false",
    })
    @DisplayName("Compares strictly, with no coercion and never equal arrays or objects")
    void comparesStrictly(String actual, String expected, boolean result) {
        // Given
        JsonElement left = JsonParser.parseString(actual);
        JsonElement right = JsonParser.parseString(expected);

        // When
        boolean equal = JsonValues.strictEquals(left, right);

        // Then
        assertEquals(result, equal);
    }

    @Test
    @DisplayName("Never equals a missing value")
    void neverEqualsMissingValue() {
        // Given
        JsonElement missing = null;

        // When
        boolean equalToNull = JsonValues.strictEquals(missing, JsonNull.INSTANCE);

        // Then
        assertFalse(equalToNull);
    }

    @Test
    @DisplayName("Keeps precision for doubles beyond 2^24 in equality")
    void keepsPrecisionInEquality() {
        // Given
        JsonPrimitive above = new JsonPrimitive(16777217);
        JsonPrimitive base = new JsonPrimitive(16777216);

        // When
        boolean equal = JsonValues.primitivesEqual(above, base);

        // Then
        assertFalse(equal);
    }

    @ParameterizedTest(name = "{0} in {1} (ignore case {2}) -> {3}")
    @CsvSource(delimiter = '|', value = {
            "\"a\"            | [\"a\", \"b\"]  | false | true",
            "\"A\"            | [\"a\", \"b\"]  | false | false",
            "\"A\"            | [\"a\", \"b\"]  | true  | true",
            "1                | [1.0]           | false | true",
            "1                | [\"1\"]         | true  | false",
            "null             | [null]          | false | true",
            "null             | [\"a\"]         | false | false",
            "[\"x\", \"b\"]   | [\"a\", \"b\"]  | false | true",
            "[\"x\", \"y\"]   | [\"a\", \"b\"]  | false | false",
            "[]               | [\"a\"]         | false | false",
            "[1]              | [[1]]           | false | false",
            "{\"a\": 1}       | [{\"a\": 1}]    | false | false",
            "\"a\"            | []              | false | false",
    })
    @DisplayName("Tests membership of a value, or any element of an array value, in an array")
    void testsMembership(String actual, String expected, boolean caseInsensitive, boolean result) {
        // Given
        JsonElement value = JsonParser.parseString(actual);
        JsonArray candidates = JsonParser.parseString(expected).getAsJsonArray();

        // When
        boolean contained = JsonValues.isIn(value, candidates, caseInsensitive);

        // Then
        assertEquals(result, contained);
    }

    @Test
    @DisplayName("Finds nothing for a missing value")
    void findsNothingForMissingValue() {
        // Given
        JsonArray candidates = JsonParser.parseString("[null]").getAsJsonArray();

        // When
        boolean contained = JsonValues.isIn(null, candidates, false);

        // Then
        assertFalse(contained);
    }

    @ParameterizedTest(name = "{0} vs {1} -> {2}")
    @CsvSource(delimiter = '|', value = {
            "1        | 2       | NUMBER",
            "\"a\"    | \"b\"   | STRING",
            "1        | \"1\"   | ",
            "true     | true    | ",
            "null     | null    | ",
            "[1]      | [1]     | ",
    })
    @DisplayName("Gives the shared orderable type of two values, or none")
    void givesOrderableType(String actual, String expected, DataType type) {
        // Given
        JsonElement left = JsonParser.parseString(actual);
        JsonElement right = JsonParser.parseString(expected);

        // When
        DataType result = JsonValues.orderableType(left, right);

        // Then
        assertEquals(type, result);
    }

    @Test
    @DisplayName("Gives no orderable type for a missing value")
    void givesNoOrderableTypeForMissingValue() {
        // Given
        JsonElement expected = new JsonPrimitive(1);

        // When
        DataType result = JsonValues.orderableType(null, expected);

        // Then
        assertNull(result);
    }

    @ParameterizedTest(name = "{0} -> string {2}, boolean {3}")
    @MethodSource("primitiveKinds")
    @DisplayName("Recognizes strings and booleans only among primitives of that type")
    void recognizesStringsAndBooleans(String label, JsonElement value, boolean expectedString, boolean expectedBoolean) {
        // Given
        // When
        boolean isString = JsonValues.isString(value);
        boolean isBoolean = JsonValues.isBoolean(value);

        // Then
        assertEquals(expectedString, isString);
        assertEquals(expectedBoolean, isBoolean);
    }

    static Stream<Arguments> primitiveKinds() {
        return Stream.of(
                Arguments.of("string \"true\"", new JsonPrimitive("true"), true, false),
                Arguments.of("boolean true", new JsonPrimitive(true), false, true),
                Arguments.of("number", new JsonPrimitive(1), false, false),
                Arguments.of("absent", null, false, false),
                Arguments.of("JSON null", JsonNull.INSTANCE, false, false),
                Arguments.of("array", new JsonArray(), false, false));
    }

    @ParameterizedTest(name = "{0} -> {2}")
    @MethodSource("booleanCandidates")
    @DisplayName("Gives the boolean a value holds, or null for anything else")
    void givesBooleanOrNull(String label, JsonElement value, Boolean expected) {
        // Given
        // When
        Boolean held = JsonValues.asBoolean(value);

        // Then
        assertEquals(expected, held);
    }

    static Stream<Arguments> booleanCandidates() {
        return Stream.of(
                Arguments.of("false", new JsonPrimitive(false), Boolean.FALSE),
                Arguments.of("true", new JsonPrimitive(true), Boolean.TRUE),
                Arguments.of("string \"false\"", new JsonPrimitive("false"), null),
                Arguments.of("0", new JsonPrimitive(0), null),
                Arguments.of("object", new JsonObject(), null),
                Arguments.of("absent", null, null));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "s, text",
            "n, ",
            "z, ",
            "absent, ",
    })
    @DisplayName("Gives a string field, or null when it is absent or not a string")
    void givesStringFieldOrNull(String key, String expected) {
        // Given
        JsonObject object = JsonParser.parseString("{\"s\": \"text\", \"n\": 1, \"z\": null}").getAsJsonObject();

        // When
        String value = JsonValues.stringField(object, key);

        // Then
        assertEquals(expected, value);
    }
}
