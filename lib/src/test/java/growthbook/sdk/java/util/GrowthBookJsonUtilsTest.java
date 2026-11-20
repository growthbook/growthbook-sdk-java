package growthbook.sdk.java.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonPrimitive;
import growthbook.sdk.java.model.BucketRange;
import growthbook.sdk.java.model.DataType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;

class GrowthBookJsonUtilsTest {

    private final Gson gson = GrowthBookJsonUtils.getInstance().gson;

    @Test
    @DisplayName("Serializes a namespace as an [id, start, end] tuple")
    @SuppressWarnings("deprecation")
    void serializesNamespaceAsTuple() {
        // Given
        growthbook.sdk.java.model.Namespace namespace = growthbook.sdk.java.model.Namespace.builder()
                .id("pricing").rangeStart(0.0).rangeEnd(0.6).build();

        // When
        String json = gson.toJson(namespace);

        // Then
        assertEquals("[\"pricing\",0.0,0.6]", json);
    }

    @Test
    @DisplayName("Deserializes a namespace from an [id, start, end] tuple")
    @SuppressWarnings("deprecation")
    void deserializesNamespaceFromTuple() {
        // When
        growthbook.sdk.java.model.Namespace namespace =
                gson.fromJson("[\"pricing\",0.0,0.6]", growthbook.sdk.java.model.Namespace.class);

        // Then
        assertEquals("pricing", namespace.getId());
        assertEquals(0.0, namespace.getRangeStart());
        assertEquals(0.6, namespace.getRangeEnd());
    }

    @Test
    @DisplayName("Serializes a bucket range as a [start, end] tuple")
    void serializesBucketRangeAsTuple() {
        // Given
        BucketRange range = new BucketRange(0.3, 0.7);

        // When
        String json = gson.toJson(range);

        // Then
        assertEquals("[0.3,0.7]", json);
    }

    @Test
    @DisplayName("Deserializes a bucket range from a [start, end] tuple")
    void deserializesBucketRangeFromTuple() {
        // When
        BucketRange range = gson.fromJson("[0.3,0.7]", BucketRange.class);

        // Then
        assertEquals(0.3, range.getRangeStart());
        assertEquals(0.7, range.getRangeEnd());
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(value = {
            "null|NULL",
            "[1]|ARRAY",
            "{\"foo\": 2}|OBJECT",
            "true|BOOLEAN",
            "1337|NUMBER",
            "\"hello\"|STRING"
    }, delimiter = '|')
    @DisplayName("Maps a parsed JSON element to its data type")
    void mapsJsonElementToDataType(String json, DataType expected) {
        // Given
        JsonElement element = gson.fromJson(json, JsonElement.class);

        // When
        DataType type = GrowthBookJsonUtils.getElementType(element);

        // Then
        assertEquals(expected, type);
    }

    @Test
    @DisplayName("Maps the JSON null singleton to the null data type")
    void mapsJsonNullSingletonToNullDataType() {
        // When
        DataType type = GrowthBookJsonUtils.getElementType(JsonNull.INSTANCE);

        // Then
        assertEquals(DataType.NULL, type);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("parsedJsonValues")
    @DisplayName("Unwraps a parsed JSON value into the matching plain Java value")
    void unwrapsParsedJsonValue(String json, Object expected) {
        // Given
        JsonElement element = gson.fromJson(json, JsonElement.class);

        // When
        Object unwrapped = GrowthBookJsonUtils.unwrap(element);

        // Then
        assertEquals(expected, unwrapped);
    }

    static Stream<Arguments> parsedJsonValues() {
        return Stream.of(
                Arguments.of("\"hello\"", "hello"),
                Arguments.of("true", Boolean.TRUE),
                Arguments.of("42", 42),
                Arguments.of("-42", -42),
                Arguments.of("1e2", 100),
                Arguments.of("3000000000", 3_000_000_000L),
                Arguments.of("-3000000000", -3_000_000_000L),
                Arguments.of("99999999999999999999", new BigDecimal("99999999999999999999")),
                Arguments.of("1.5", 1.5),
                Arguments.of("0.12345678901234567890", new BigDecimal("0.12345678901234567890"))
        );
    }

    @Test
    @DisplayName("Unwraps a parsed JSON null into null")
    void unwrapsParsedJsonNullIntoNull() {
        // Given
        JsonElement element = gson.fromJson("null", JsonElement.class);

        // When
        Object unwrapped = GrowthBookJsonUtils.unwrap(element);

        // Then
        assertNull(unwrapped);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("primitiveNumbers")
    @DisplayName("Unwraps a JSON number built from a standard Java number into that same number")
    void unwrapsStandardJavaNumberUnchanged(Number number) {
        // When
        Object unwrapped = GrowthBookJsonUtils.unwrap(new JsonPrimitive(number));

        // Then
        assertSame(number, unwrapped);
    }

    static Stream<Number> primitiveNumbers() {
        return Stream.of(7, 7.5f, 7.25, 7L, new BigDecimal("7.125"), new BigInteger("7"));
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("customNumbers")
    @DisplayName("Unwraps a JSON number built from a custom Number type by its value")
    void unwrapsCustomNumberTypeByValue(Number number, Number expected) {
        // When
        Object unwrapped = GrowthBookJsonUtils.unwrap(new JsonPrimitive(number));

        // Then
        assertEquals(expected, unwrapped);
    }

    static Stream<Arguments> customNumbers() {
        return Stream.of(
                Arguments.of(new AtomicInteger(12), 12),
                Arguments.of(new AtomicLong(5_000_000_000L), 5_000_000_000L)
        );
    }

    @Test
    @DisplayName("Returns a JSON array or a non-JSON object from unwrap unchanged")
    void returnsArrayAndNonJsonObjectUnchanged() {
        // Given
        JsonArray array = gson.fromJson("[1,2]", JsonArray.class);
        Object plain = Collections.singletonList("item");

        // When
        Object unwrappedArray = GrowthBookJsonUtils.unwrap(array);
        Object unwrappedPlain = GrowthBookJsonUtils.unwrap(plain);

        // Then
        assertSame(array, unwrappedArray);
        assertSame(plain, unwrappedPlain);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("objectsToJson")
    @DisplayName("Converts a Java value into the equivalent JSON element")
    void convertsJavaValueToJsonElement(Object value, String expectedJson) {
        // When
        JsonElement element = GrowthBookJsonUtils.getJsonElementForObject(value);

        // Then
        assertEquals(gson.fromJson(expectedJson, JsonElement.class), element);
    }

    static Stream<Arguments> objectsToJson() {
        return Stream.of(
                Arguments.of("text", "\"text\""),
                Arguments.of(1.5f, "1.5"),
                Arguments.of(3, "3"),
                Arguments.of(2.25, "2.25"),
                Arguments.of(4_000_000_000L, "4000000000"),
                Arguments.of(new BigDecimal("0.1"), "0.1"),
                Arguments.of(Arrays.asList("a", "b"), "[\"a\",\"b\"]"),
                Arguments.of(Collections.singletonMap("enabled", true), "{\"enabled\":true}")
        );
    }

    @Test
    @DisplayName("Returns null when the value cannot be serialized to JSON")
    void returnsNullWhenValueCannotBeSerialized() {
        // Given
        Object notSerializable = Collections.singletonList(Double.NaN);

        // When
        JsonElement element = GrowthBookJsonUtils.getJsonElementForObject(notSerializable);

        // Then
        assertNull(element);
    }
}
