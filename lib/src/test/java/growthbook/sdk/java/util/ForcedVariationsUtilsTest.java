package growthbook.sdk.java.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ForcedVariationsUtilsTest {

    @Test
    @DisplayName("Normalizes whole numbers of every supported numeric type to integer variation ids")
    void normalizesWholeNumbersToIntegerVariationIds() {
        // Given
        Map<String, Object> source = new HashMap<>();
        source.put("integer", 1);
        source.put("double", 1.0);
        source.put("long", 2L);
        source.put("bigDecimal", new BigDecimal("3.0"));
        source.put("jsonNumber", new JsonPrimitive(4.0));

        // When
        Map<String, Integer> result = ForcedVariationsUtils.normalize(source);

        // Then
        Map<String, Integer> expected = new HashMap<>();
        expected.put("integer", 1);
        expected.put("double", 1);
        expected.put("long", 2);
        expected.put("bigDecimal", 3);
        expected.put("jsonNumber", 4);
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("Drops fractional, text, boolean and object values")
    void dropsUnsupportedValues() {
        // Given
        Map<String, Object> source = new HashMap<>();
        source.put("fractional", 1.5);
        source.put("text", "abc");
        source.put("boolean", true);
        source.put("object", new JsonObject());

        // When
        Map<String, Integer> result = ForcedVariationsUtils.normalize(source);

        // Then
        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("A null source normalizes to an empty map")
    void nullSourceNormalizesToEmptyMap() {
        // When
        Map<String, Integer> result = ForcedVariationsUtils.normalize(null);

        // Then
        assertEquals(Collections.emptyMap(), result);
    }
}
