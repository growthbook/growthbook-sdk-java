package growthbook.sdk.java.evaluators.internal.condition;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DescribedTest {

    @Test
    @DisplayName("Describes a parsed condition as the JSON it came from")
    void describesParsedCondition() {
        // Given
        JsonObject source = json("{\"country\": {\"$in\": [\"AA\"]}, \"$or\": [{\"beta\": true}]}");

        // When
        String description = CondParser.parse(source).toString();

        // Then
        assertEquals(source.toString(), description);
    }

    @Test
    @DisplayName("Describes a parsed entry as a single-key object")
    void describesParsedEntry() {
        // Given
        JsonElement value = JsonParser.parseString("{\"$gt\": 18}");

        // When
        String description = CondParser.parseEntry("age", value).toString();

        // Then
        assertEquals("{\"age\":{\"$gt\":18}}", description);
    }

    @Test
    @DisplayName("Describes a parsed value test as its condition value")
    void describesParsedValue() {
        // Given
        JsonElement operators = JsonParser.parseString("{\"$gt\": 1, \"$lt\": 5}");
        JsonElement literal = new JsonPrimitive("AA");

        // When
        String operatorDescription = CondParser.parseValue(operators).toString();
        String literalDescription = CondParser.parseValue(literal).toString();

        // Then
        assertEquals("{\"$gt\":1,\"$lt\":5}", operatorDescription);
        assertEquals("\"AA\"", literalDescription);
    }

    @Test
    @DisplayName("Describes a parsed operator as a single-key operator object")
    void describesParsedOperator() {
        // Given
        JsonElement operand = JsonParser.parseString("[\"AA\", \"BB\"]");

        // When
        String description = Operators.parse("$nin", operand).toString();

        // Then
        assertEquals("{\"$nin\":[\"AA\",\"BB\"]}", description);
    }

    @Test
    @DisplayName("Delegates evaluation of a described condition to the wrapped condition")
    void delegatesConditionEvaluation() {
        // Given
        Cond described = Described.cond((attributes, scope) -> null, new JsonPrimitive("source"));
        Cond matching = Described.cond((attributes, scope) -> true, new JsonPrimitive("source"));

        // When
        Boolean unknown = described.eval(json("{}"), new CondScope(null));
        Boolean matched = matching.eval(json("{}"), new CondScope(null));

        // Then
        assertNull(unknown);
        assertTrue(matched);
        assertEquals("\"source\"", described.toString());
    }

    @Test
    @DisplayName("Delegates a described value test to the wrapped test")
    void delegatesValueTest() {
        // Given
        ValueTest described = Described.value((actual, scope) -> false, json("{\"$eq\": 1}"));

        // When
        Boolean result = described.test(new JsonPrimitive(1), new CondScope(null));

        // Then
        assertFalse(result);
        assertEquals("{\"$eq\":1}", described.toString());
    }

    @Test
    @DisplayName("Builds a single-key entry object")
    void buildsEntryObject() {
        // Given
        JsonElement value = new JsonPrimitive(5);

        // When
        JsonObject entry = Described.entry("$size", value);

        // Then
        assertEquals(json("{\"$size\": 5}"), entry);
    }

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }
}
