package growthbook.sdk.java.evaluators.internal.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;


class PayloadJsonTest {

    @Test
    @DisplayName("Returns the array under a key of an object")
    void returnsArrayField() {
        // Given
        JsonObject item = json("{'rules': [1, 2]}");

        // When
        JsonArray rules = PayloadJson.arrayField(item, "rules");

        // Then
        assertSame(item.get("rules"), rules);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"{}", "{'rules': {}}", "{'rules': 'x'}", "{'rules': null}", "[]", "'rules'"})
    @DisplayName("Returns no array when the item is not an object or the key holds no array")
    void returnsNoArrayWhenAbsent(String value) {
        // Given
        JsonElement item = JsonParser.parseString(value.replace('\'', '"'));

        // When
        JsonArray rules = PayloadJson.arrayField(item, "rules");

        // Then
        assertNull(rules);
    }

    @Test
    @DisplayName("Returns no array for a missing item")
    void returnsNoArrayForMissingItem() {
        // Given
        // When
        JsonArray rules = PayloadJson.arrayField(null, "rules");

        // Then
        assertNull(rules);
    }

    @Test
    @DisplayName("Returns an item's metadata object")
    void returnsMetadata() {
        // Given
        JsonObject item = json("{'metadata': {'projects': ['p1']}}");

        // When
        JsonObject metadata = PayloadJson.metadataOf(item);

        // Then
        assertEquals(json("{'projects': ['p1']}"), metadata);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"{}", "{'metadata': null}", "{'metadata': ['p1']}", "{'metadata': 'p1'}", "'item'"})
    @DisplayName("Returns empty metadata when an item has no metadata object")
    void returnsEmptyMetadataWhenAbsent(String value) {
        // Given
        JsonElement item = JsonParser.parseString(value.replace('\'', '"'));

        // When
        JsonObject metadata = PayloadJson.metadataOf(item);

        // Then
        assertEquals(new JsonObject(), metadata);
    }

    @Test
    @DisplayName("Returns an empty object for a missing or non-object value")
    void returnsEmptyObjectForNonObject() {
        // Given
        JsonElement array = new JsonArray();

        // When
        JsonObject fromNull = PayloadJson.objectOrEmpty(null);
        JsonObject fromJsonNull = PayloadJson.objectOrEmpty(JsonNull.INSTANCE);
        JsonObject fromArray = PayloadJson.objectOrEmpty(array);

        // Then
        assertEquals(new JsonObject(), fromNull);
        assertEquals(new JsonObject(), fromJsonNull);
        assertEquals(new JsonObject(), fromArray);
    }

    @Test
    @DisplayName("Copies an object's entries without copying their values or changing the source")
    void shallowCopiesObject() {
        // Given
        JsonObject source = json("{'a': {'b': 1}, 'c': 2}");

        // When
        JsonObject copy = PayloadJson.shallowCopy(source);
        copy.addProperty("d", 3);

        // Then
        assertNotSame(source, copy);
        assertSame(source.get("a"), copy.get("a"));
        assertEquals(json("{'a': {'b': 1}, 'c': 2}"), source);
    }


    private static JsonObject json(String value) {
        return JsonParser.parseString(value.replace('\'', '"')).getAsJsonObject();
    }
}
