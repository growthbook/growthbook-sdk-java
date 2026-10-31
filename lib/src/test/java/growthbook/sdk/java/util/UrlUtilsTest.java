package growthbook.sdk.java.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

class UrlUtilsTest {

    @Test
    @DisplayName("Parses URL-encoded query parameters into decoded key/value pairs")
    void parsesUrlEncodedQueryParameters() {
        // Given
        String query = "gb~meal_overrides_gluten_free=%7B%22meal_type%22%3A%20%22gf%22%2C%20%22dessert%22%3A%20%22French%20Vanilla%20Ice%20Cream%22%7D"
                + "&gb~dark_mode=on&gb~donut_price=3.33&gb~banner_text=Hello%2C%20everyone!%20I%20hope%20you%20are%20all%20doing%20well!";

        // When
        Map<String, String> result = UrlUtils.parseQueryString(query);

        // Then
        assertEquals(4, result.size());
        assertEquals("Hello, everyone! I hope you are all doing well!", result.get("gb~banner_text"));
        assertEquals("3.33", result.get("gb~donut_price"));
        assertEquals("on", result.get("gb~dark_mode"));
        assertEquals("{\"meal_type\": \"gf\", \"dessert\": \"French Vanilla Ice Cream\"}", result.get("gb~meal_overrides_gluten_free"));
    }

    @Test
    @DisplayName("A parameter without a value maps to an empty string and an unnamed parameter is skipped")
    void parameterWithoutValueMapsToEmptyStringAndUnnamedParameterIsSkipped() {
        // Given
        String query = "flag&=ignored&a=b=c";

        // When
        Map<String, String> result = UrlUtils.parseQueryString(query);

        // Then
        assertEquals(2, result.size());
        assertEquals("", result.get("flag"));
        assertEquals("b=c", result.get("a"));
    }

    @ParameterizedTest(name = "query \"{0}\"")
    @NullAndEmptySource
    @DisplayName("A null or empty query string parses to an empty map")
    void nullOrEmptyQueryParsesToEmptyMap(String query) {
        // When
        Map<String, String> result = UrlUtils.parseQueryString(query);

        // Then
        assertTrue(result.isEmpty());
    }
}
