package growthbook.sdk.java.multiusermode.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FeatureValuesTest {

    @Test
    @DisplayName("Reads a boolean and falls back to the default for a missing value or another type")
    void readsBooleanOrDefault() {
        // Given
        Boolean defaultValue = Boolean.FALSE;

        // When
        Boolean found = FeatureValues.asBoolean(Boolean.TRUE, defaultValue);
        Boolean missing = FeatureValues.asBoolean(null, defaultValue);
        Boolean wrongType = FeatureValues.asBoolean("true", defaultValue);

        // Then
        assertEquals(Boolean.TRUE, found);
        assertEquals(defaultValue, missing);
        assertEquals(defaultValue, wrongType);
    }

    @Test
    @DisplayName("Reads a string and falls back to the default for another type")
    void readsStringOrDefault() {
        // Given
        String defaultValue = "fallback";

        // When
        String found = FeatureValues.asString("value", defaultValue);
        String wrongType = FeatureValues.asString(1.0, defaultValue);

        // Then
        assertEquals("value", found);
        assertEquals(defaultValue, wrongType);
    }

    @Test
    @DisplayName("Narrows JSON numbers, which arrive as Double or Long, to the requested type")
    void narrowsJsonNumbers() {
        // Given
        Object fromDouble = 2.75;
        Object fromLong = 3L;

        // When
        Integer integer = FeatureValues.asInteger(fromDouble, -1);
        Float floatValue = FeatureValues.asFloat(fromLong, -1f);
        Double doubleValue = FeatureValues.asDouble(fromLong, -1.0);

        // Then
        assertEquals(2, integer);
        assertEquals(3f, floatValue);
        assertEquals(3.0, doubleValue);
    }

    @Test
    @DisplayName("Falls back to the default for a number that isn't a JSON number type, or a non-number")
    void fallsBackForOtherNumberTypes() {
        // Given
        Integer defaultValue = 7;

        // When
        Integer fromInteger = FeatureValues.asInteger(5, defaultValue);
        Integer fromString = FeatureValues.asInteger("5", defaultValue);

        // Then
        assertEquals(defaultValue, fromInteger);
        assertEquals(defaultValue, fromString);
    }

    @Test
    @DisplayName("Converts a value into the requested type through its JSON form")
    void convertsThroughJson() {
        // Given
        Map<String, Object> value = Collections.<String, Object>singletonMap("title", "Welcome");

        // When
        Banner banner = FeatureValues.convert(value, null, Banner.class);

        // Then
        assertEquals("Welcome", banner.title);
    }

    @Test
    @DisplayName("Falls back to the default when the value doesn't deserialize into the type")
    void fallsBackWhenValueDoesNotDeserialize() {
        // Given
        Banner defaultValue = new Banner();

        // When
        Banner converted = FeatureValues.convert("not an object", defaultValue, Banner.class);
        Banner missing = FeatureValues.convert(null, defaultValue, Banner.class);

        // Then
        assertEquals(defaultValue, converted);
        assertEquals(defaultValue, missing);
        assertNull(defaultValue.title);
    }

    private static final class Banner {
        private String title;
    }
}
