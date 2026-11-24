package growthbook.sdk.java.multiusermode.configurations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class UserContextTest {

    @Test
    void attributes_fromMap_convertsToJsonObject() {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put("userId", "user-123");
        attributes.put("country", "US");
        attributes.put("loggedIn", true);
        attributes.put("age", 42);

        UserContext context = UserContext.builder()
                .attributesMap(attributes)
                .build();

        JsonObject result = context.getAttributes();
        assertEquals("user-123", result.get("userId").getAsString());
        assertEquals("US", result.get("country").getAsString());
        assertTrue(result.get("loggedIn").getAsBoolean());
        assertEquals(42, result.get("age").getAsInt());
    }

    @Test
    void attributes_fromNullMap_yieldsEmptyJsonObject() {
        Map<String, ?> attributes = null;

        UserContext context = UserContext.builder()
                .attributesMap(attributes)
                .build();

        assertEquals(0, context.getAttributes().size());
    }

    @Test
    void attributes_fromStringValuedMap_isAccepted() {
        // Map.of(...) infers Map<String, String>; the wildcard overload must accept it.
        Map<String, String> attributes = new HashMap<>();
        attributes.put("userId", "user-123");
        attributes.put("country", "US");

        UserContext context = UserContext.builder()
                .attributesMap(attributes)
                .build();

        JsonObject result = context.getAttributes();
        assertEquals("user-123", result.get("userId").getAsString());
        assertEquals("US", result.get("country").getAsString());
    }

    @Test
    void attributes_nullLiteral_remainsUnambiguousAndCompiles() {
        // A separate attributesMap(...) method (rather than an attributes(Map) overload)
        // keeps the existing attributes(null) call site compiling and unambiguous.
        UserContext context = UserContext.builder()
                .attributes(null)
                .build();

        assertEquals(0, context.getAttributes().size());
    }

    @Test
    void attributes_nullMap_thenAttributesJson_isApplied() {
        // A null map must leave attributes unset so a subsequent attributesJson(...) applies,
        // mirroring attributes((JsonObject) null).
        UserContext context = UserContext.builder()
                .attributesMap(null)
                .attributesJson("{\"x\":1}")
                .build();

        assertEquals(1, context.getAttributes().get("x").getAsInt());
    }

    @Test
    void attributes_fromJsonObject_stillWorks() {
        JsonObject attributes = new JsonObject();
        attributes.addProperty("userId", "user-123");

        UserContext context = UserContext.builder()
                .attributes(attributes)
                .build();

        assertEquals("user-123", context.getAttributes().get("userId").getAsString());
    }

    @Test
    @DisplayName("A copied context takes attributes from an explicit JSON update")
    void copiedContextTakesAttributesFromJsonUpdate() {
        // Given
        UserContext original = UserContext.builder()
                .attributesJson("{\"id\":\"1\"}")
                .url("https://example.com/page")
                .build();

        // When
        UserContext copy = original.toBuilder()
                .attributesJson("{\"id\":\"2\"}")
                .build();

        // Then
        assertEquals("2", copy.getAttributes().get("id").getAsString());
        assertEquals("{\"id\":\"2\"}", copy.getAttributesJson());
        assertEquals("https://example.com/page", copy.getUrl());
        assertEquals("1", original.getAttributes().get("id").getAsString());
    }

    @Test
    @DisplayName("A copied context keeps the original attributes when they are not updated")
    void copiedContextKeepsOriginalAttributesWithoutUpdate() {
        // Given
        JsonObject attributes = new JsonObject();
        attributes.addProperty("id", "1");
        UserContext original = UserContext.builder()
                .attributes(attributes)
                .attributesJson("{\"id\":\"ignored\"}")
                .build();

        // When
        UserContext copy = original.toBuilder().build();

        // Then
        assertEquals("1", copy.getAttributes().get("id").getAsString());
        assertEquals("{\"id\":\"ignored\"}", copy.getAttributesJson());
    }

    @Test
    @DisplayName("A copied context keeps explicitly set attributes over a later JSON update")
    void copiedContextKeepsExplicitAttributesOverJsonUpdate() {
        // Given
        UserContext original = UserContext.builder()
                .attributesJson("{\"id\":\"1\"}")
                .build();
        JsonObject explicit = new JsonObject();
        explicit.addProperty("id", "3");

        // When
        UserContext copy = original.toBuilder()
                .attributes(explicit)
                .attributesJson("{\"id\":\"2\"}")
                .build();

        // Then
        assertEquals("3", copy.getAttributes().get("id").getAsString());
    }

    @Test
    @DisplayName("A copied context updated with invalid JSON gets empty attributes")
    void copiedContextUpdatedWithInvalidJsonGetsEmptyAttributes() {
        // Given
        UserContext original = UserContext.builder()
                .attributesJson("{\"id\":\"1\"}")
                .build();

        // When
        UserContext copy = original.toBuilder()
                .attributesJson("{not json")
                .build();

        // Then
        assertEquals(0, copy.getAttributes().size());
    }

    @Test
    @DisplayName("A copied context updated with null JSON gets empty attributes")
    void copiedContextUpdatedWithNullJsonGetsEmptyAttributes() {
        // Given
        UserContext original = UserContext.builder()
                .attributesJson("{\"id\":\"1\"}")
                .build();

        // When
        UserContext copy = original.toBuilder()
                .attributesJson(null)
                .build();

        // Then
        assertEquals(0, copy.getAttributes().size());
        assertNull(copy.getAttributesJson());
    }
}
