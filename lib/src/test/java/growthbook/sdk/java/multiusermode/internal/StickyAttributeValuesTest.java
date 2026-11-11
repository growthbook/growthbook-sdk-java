package growthbook.sdk.java.multiusermode.internal;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import growthbook.sdk.java.util.GrowthBookUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StickyAttributeValuesTest {

    @Test
    @DisplayName("Formats identifier values the way evaluation formats hash values")
    void formatsValuesLikeEvaluationHashValues() {
        // Given
        List<JsonElement> values = Arrays.asList(new JsonPrimitive(1.0), new JsonPrimitive(1.5), new JsonPrimitive(1e21),
                new JsonPrimitive("abc"), new JsonPrimitive(""), new JsonPrimitive(0), new JsonPrimitive(true),
                JsonNull.INSTANCE, new JsonArray(), new JsonObject(), null);

        for (JsonElement value : values) {
            JsonObject attributes = new JsonObject();
            attributes.add("id", value);

            // When
            String sticky = StickyAttributeValues.hashValue(value);
            String evaluation = GrowthBookUtils.getHashAttribute("id", null, attributes).getHashValue();

            // Then
            assertEquals(evaluation, sticky, "value " + value);
        }
    }

    @Test
    @DisplayName("Leaves out identifiers whose value cannot be a hash value")
    void leavesOutValuesThatCannotBeHashed() {
        // Given
        JsonObject attributes = new JsonObject();
        attributes.addProperty("id", 1.0);
        attributes.addProperty("deviceId", 1.5);
        attributes.addProperty("loggedIn", true);
        attributes.addProperty("company", "");

        // When
        Map<String, String> values = StickyAttributeValues.of(attributes,
                Arrays.asList("id", "deviceId", "loggedIn", "company", "missing"));

        // Then
        Map<String, String> expected = new HashMap<>();
        expected.put("id", "1");
        expected.put("deviceId", "1.5");
        assertEquals(expected, values);
    }
}
