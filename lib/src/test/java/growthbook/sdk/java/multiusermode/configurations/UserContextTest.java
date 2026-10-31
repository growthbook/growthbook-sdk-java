package growthbook.sdk.java.multiusermode.configurations;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserContextTest {

    @Test
    @DisplayName("Converts attributes given as a map into a JSON object")
    void convertsAttributesMapIntoJsonObject() {
        // Given
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("userId", "user-123");
        attributes.put("country", "US");
        attributes.put("loggedIn", true);
        attributes.put("age", 42);

        // When
        UserContext context = UserContext.builder().attributesMap(attributes).build();

        // Then
        assertEquals(json("{\"userId\":\"user-123\",\"country\":\"US\",\"loggedIn\":true,\"age\":42}"),
                context.getAttributes());
    }

    @Test
    @DisplayName("A null attributes map gives empty attributes")
    void nullAttributesMapGivesEmptyAttributes() {
        // When
        UserContext context = UserContext.builder().attributesMap(null).build();

        // Then
        assertEquals(new JsonObject(), context.getAttributes());
    }

    @Test
    @DisplayName("Accepts a map whose values are all strings")
    void acceptsStringValuedAttributesMap() {
        // Given
        Map<String, String> attributes = new HashMap<>();
        attributes.put("userId", "user-123");
        attributes.put("country", "US");

        // When
        UserContext context = UserContext.builder().attributesMap(attributes).build();

        // Then
        assertEquals(json("{\"userId\":\"user-123\",\"country\":\"US\"}"), context.getAttributes());
    }

    @Test
    @DisplayName("A null attributes JSON object gives empty attributes")
    void nullAttributesJsonObjectGivesEmptyAttributes() {
        // When
        UserContext context = UserContext.builder().attributes(null).build();

        // Then
        assertEquals(new JsonObject(), context.getAttributes());
    }

    @Test
    @DisplayName("A null attributes map leaves attributes unset so later attributes JSON applies")
    void nullAttributesMapLetsLaterAttributesJsonApply() {
        // When
        UserContext context = UserContext.builder()
                .attributesMap(null)
                .attributesJson("{\"x\":1}")
                .build();

        // Then
        assertEquals(json("{\"x\":1}"), context.getAttributes());
    }

    @Test
    @DisplayName("Keeps attributes given as a JSON object")
    void keepsAttributesGivenAsJsonObject() {
        // Given
        JsonObject attributes = json("{\"userId\":\"user-123\"}");

        // When
        UserContext context = UserContext.builder().attributes(attributes).build();

        // Then
        assertEquals(json("{\"userId\":\"user-123\"}"), context.getAttributes());
    }

    private static JsonObject json(String value) {
        return JsonParser.parseString(value).getAsJsonObject();
    }
}
