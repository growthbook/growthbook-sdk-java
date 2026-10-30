package growthbook.sdk.java.evaluators;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ConditionEvaluatorPathTest {

    private final ConditionEvaluator evaluator = new ConditionEvaluator();

    private static JsonObject attrs(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    @Test
    @DisplayName("Verify: a numeric segment indexes into an array")
    void resolvesArrayIndexToElement() {
        // Given
        JsonObject attributes = attrs("{\"tags\":[\"a\",\"b\"]}");
        // When
        Object first = evaluator.getPath(attributes, "tags.0");
        Object second = evaluator.getPath(attributes, "tags.1");
        // Then
        assertEquals("a", ((JsonElement) first).getAsString());
        assertEquals("b", ((JsonElement) second).getAsString());
    }

    @Test
    @DisplayName("Verify: an out-of-range array index resolves to null")
    void outOfRangeIndexIsNull() {
        // Given
        JsonObject attributes = attrs("{\"tags\":[\"a\",\"b\"]}");
        // When / Then
        assertNull(evaluator.getPath(attributes, "tags.2"));
        assertNull(evaluator.getPath(attributes, "tags.99"));
    }

    @Test
    @DisplayName("Verify: a negative array index resolves to null")
    void negativeIndexIsNull() {
        assertNull(evaluator.getPath(attrs("{\"tags\":[\"a\"]}"), "tags.-1"));
    }

    @Test
    @DisplayName("Verify: a non-numeric segment on an array resolves to null")
    void nonNumericSegmentOnArrayIsNull() {
        assertNull(evaluator.getPath(attrs("{\"tags\":[\"a\"]}"), "tags.name"));
    }

    @Test
    @DisplayName("Verify: an index that overflows int resolves to null")
    void overflowingIndexIsNull() {
        assertNull(evaluator.getPath(attrs("{\"tags\":[\"a\"]}"), "tags.99999999999999999999"));
    }

    @Test
    @DisplayName("Verify: a nested object-array-value path resolves")
    void nestedObjectArrayValuePath() {
        // Given
        JsonObject attributes = attrs("{\"a\":{\"b\":[{\"c\":1},{\"c\":2}]}}");
        // When
        Object result = evaluator.getPath(attributes, "a.b.1.c");
        // Then
        assertEquals(2, ((JsonElement) result).getAsInt());
    }

    @Test
    @DisplayName("Verify: indexing into a non-array scalar resolves to null")
    void indexIntoScalarIsNull() {
        assertNull(evaluator.getPath(attrs("{\"x\":\"str\"}"), "x.0"));
    }
}
