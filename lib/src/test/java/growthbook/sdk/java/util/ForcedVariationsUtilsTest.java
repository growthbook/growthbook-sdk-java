package growthbook.sdk.java.util;

import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ForcedVariationsUtilsTest {
    @Test
    void normalizesSupportedNumericValues() {
        Map<String, Object> source = new HashMap<>();
        source.put("integer", 1);
        source.put("double", 1.0);
        source.put("long", 2L);
        source.put("bigDecimal", new BigDecimal("3.0"));
        source.put("jsonNumber", new JsonPrimitive(4.0));

        Map<String, Integer> result = ForcedVariationsUtils.normalize(source);

        assertEquals(Integer.valueOf(1), result.get("integer"));
        assertEquals(Integer.valueOf(1), result.get("double"));
        assertEquals(Integer.valueOf(2), result.get("long"));
        assertEquals(Integer.valueOf(3), result.get("bigDecimal"));
        assertEquals(Integer.valueOf(4), result.get("jsonNumber"));
    }

    @Test
    void ignoresUnsupportedValuesWithoutThrowing() {
        Map<String, Object> source = new HashMap<>();
        source.put("fractional", 1.5);
        source.put("text", "abc");
        source.put("boolean", true);
        source.put("object", new JsonObject());

        Map<String, Integer> result = ForcedVariationsUtils.normalize(source);

        assertFalse(result.containsKey("fractional"));
        assertFalse(result.containsKey("text"));
        assertFalse(result.containsKey("boolean"));
        assertFalse(result.containsKey("object"));
    }
}
