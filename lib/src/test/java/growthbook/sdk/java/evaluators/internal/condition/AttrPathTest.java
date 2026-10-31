package growthbook.sdk.java.evaluators.internal.condition;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class AttrPathTest {

    private static final JsonObject ATTRIBUTES = JsonParser.parseString("{"
            + "\"name\": \"placeholder\","
            + "\"address\": {\"city\": \"Testville\", \"\": \"blank-key\"},"
            + "\"tags\": [\"t0\", \"t1\", \"t2\"],"
            + "\"items\": [{\"id\": \"i0\"}, {\"id\": \"i1\"}],"
            + "\"\": \"root-blank\","
            + "\"nothing\": null"
            + "}").getAsJsonObject();

    @Test
    @DisplayName("Resolves a top-level object key")
    void resolvesTopLevelKey() {
        // Given
        AttrPath path = AttrPath.parse("name");

        // When
        JsonElement value = path.resolve(ATTRIBUTES);

        // Then
        assertEquals(new JsonPrimitive("placeholder"), value);
    }

    @Test
    @DisplayName("Resolves nested object keys separated by dots")
    void resolvesNestedKeys() {
        // Given
        AttrPath path = AttrPath.parse("address.city");

        // When
        JsonElement value = path.resolve(ATTRIBUTES);

        // Then
        assertEquals(new JsonPrimitive("Testville"), value);
    }

    @Test
    @DisplayName("Resolves a canonical array index")
    void resolvesCanonicalArrayIndex() {
        // Given
        AttrPath path = AttrPath.parse("tags.1");

        // When
        JsonElement value = path.resolve(ATTRIBUTES);

        // Then
        assertEquals(new JsonPrimitive("t1"), value);
    }

    @Test
    @DisplayName("Resolves a key of an object inside an array")
    void resolvesKeyInsideArrayElement() {
        // Given
        AttrPath path = AttrPath.parse("items.1.id");

        // When
        JsonElement value = path.resolve(ATTRIBUTES);

        // Then
        assertEquals(new JsonPrimitive("i1"), value);
    }

    @Test
    @DisplayName("Resolves the length of an array")
    void resolvesArrayLength() {
        // Given
        AttrPath path = AttrPath.parse("tags.length");

        // When
        JsonElement value = path.resolve(ATTRIBUTES);

        // Then
        assertEquals(3, value.getAsInt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"tags.01", "tags.-1", "tags.+1", "tags.3", "tags.1.0", "tags.9999999999", "tags.x", "tags. 1", "tags."})
    @DisplayName("Resolves nothing for an array key that is not a canonical in-range index or length")
    void resolvesNothingForNonOwnArrayKeys(String rawPath) {
        // Given
        AttrPath path = AttrPath.parse(rawPath);

        // When
        JsonElement value = path.resolve(ATTRIBUTES);

        // Then
        assertNull(value);
    }

    @ParameterizedTest
    @ValueSource(strings = {"name.length", "name.0", "missing", "missing.deeper", "address.zip", "nothing.deeper"})
    @DisplayName("Resolves nothing for a path that does not exist or passes through a primitive")
    void resolvesNothingForMissingPaths(String rawPath) {
        // Given
        AttrPath path = AttrPath.parse(rawPath);

        // When
        JsonElement value = path.resolve(ATTRIBUTES);

        // Then
        assertNull(value);
    }

    @Test
    @DisplayName("Keeps a trailing empty segment and reads the empty key")
    void keepsTrailingEmptySegment() {
        // Given
        AttrPath path = AttrPath.parse("address.");

        // When
        JsonElement value = path.resolve(ATTRIBUTES);

        // Then
        assertEquals(new JsonPrimitive("blank-key"), value);
    }

    @Test
    @DisplayName("Resolves the empty path to the empty key, like splitting the path in JavaScript")
    void resolvesEmptyPathToEmptyKey() {
        // Given
        AttrPath path = AttrPath.parse("");

        // When
        JsonElement withEmptyKey = path.resolve(JsonParser.parseString("{\"\": \"top-level-blank\"}"));
        JsonElement withoutEmptyKey = path.resolve(JsonParser.parseString("{\"name\": \"placeholder\"}"));

        // Then
        assertEquals(new JsonPrimitive("top-level-blank"), withEmptyKey);
        assertNull(withoutEmptyKey);
    }

    @Test
    @DisplayName("Resolves a present null as JSON null")
    void resolvesPresentNull() {
        // Given
        AttrPath path = AttrPath.parse("nothing");

        // When
        JsonElement value = path.resolve(ATTRIBUTES);

        // Then
        assertSame(JsonNull.INSTANCE, value);
    }

    @Test
    @DisplayName("Resolves nothing from null attributes")
    void resolvesNothingFromNullAttributes() {
        // Given
        AttrPath path = AttrPath.parse("name");

        // When
        JsonElement value = path.resolve(null);

        // Then
        assertNull(value);
    }

    @Test
    @DisplayName("Gives a missing attribute as JSON null")
    void givesMissingAttributeAsJsonNull() {
        // Given
        AttrPath path = AttrPath.parse("missing.deeper");

        // When
        JsonElement value = path.attributeAt(ATTRIBUTES);

        // Then
        assertSame(JsonNull.INSTANCE, value);
    }

    @Test
    @DisplayName("Gives a present attribute as its value")
    void givesPresentAttributeAsItsValue() {
        // Given
        AttrPath path = AttrPath.parse("tags.0");

        // When
        JsonElement value = path.attributeAt(ATTRIBUTES);

        // Then
        assertEquals(new JsonPrimitive("t0"), value);
    }

    @Test
    @DisplayName("Resolves the same value on every call of a parsed path")
    void resolvesRepeatedly() {
        // Given
        AttrPath path = AttrPath.parse("address.city");
        JsonObject other = JsonParser.parseString("{\"address\": {\"city\": \"Otherville\"}}").getAsJsonObject();

        // When
        JsonElement first = path.resolve(ATTRIBUTES);
        JsonElement second = path.resolve(other);
        JsonElement third = path.resolve(ATTRIBUTES);

        // Then
        assertEquals(new JsonPrimitive("Testville"), first);
        assertEquals(new JsonPrimitive("Otherville"), second);
        assertEquals(first, third);
    }
}
