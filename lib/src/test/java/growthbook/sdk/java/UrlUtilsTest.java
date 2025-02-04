package growthbook.sdk.java;

import static org.junit.jupiter.api.Assertions.assertEquals;

import growthbook.sdk.java.util.UrlUtils;
import org.junit.jupiter.api.Test;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

class UrlUtilsTest {

    @Test
    void test_parseQueryString() throws MalformedURLException {
        String urlString = "http://localhost:8080/url-feature-force?gb~meal_overrides_gluten_free=%7B%22meal_type%22%3A%20%22gf%22%2C%20%22dessert%22%3A%20%22French%20Vanilla%20Ice%20Cream%22%7D&gb~dark_mode=on&gb~donut_price=3.33&gb~banner_text=Hello%2C%20everyone!%20I%20hope%20you%20are%20all%20doing%20well!";
        URL url = new URL(urlString);
        String query = url.getQuery();

        Map<String, String> result = UrlUtils.parseQueryString(query);

        assertEquals("Hello, everyone! I hope you are all doing well!", result.get("gb~banner_text"));
        assertEquals("3.33", result.get("gb~donut_price"));
        assertEquals("on", result.get("gb~dark_mode"));
        assertEquals("{\"meal_type\": \"gf\", \"dessert\": \"French Vanilla Ice Cream\"}", result.get("gb~meal_overrides_gluten_free"));
    }
}
