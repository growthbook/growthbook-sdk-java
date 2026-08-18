package growthbook.sdk.java.multiusermode.configurations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
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
                .attributes(attributes)
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
                .attributes(attributes)
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
                .attributes(attributes)
                .build();

        JsonObject result = context.getAttributes();
        assertEquals("user-123", result.get("userId").getAsString());
        assertEquals("US", result.get("country").getAsString());
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
}
