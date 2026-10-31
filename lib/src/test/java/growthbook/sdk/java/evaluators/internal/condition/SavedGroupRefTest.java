package growthbook.sdk.java.evaluators.internal.condition;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SavedGroupRefTest {

    private static final JsonObject SAVED_GROUPS = json("{"
            + "\"listGroup\": {\"type\": \"list\", \"attributeKey\": \"id\", \"values\": [\"u1\", \"u2\"]},"
            + "\"keylessList\": {\"type\": \"list\", \"values\": [\"u1\"]},"
            + "\"badValues\": {\"type\": \"list\", \"attributeKey\": \"id\", \"values\": \"u1\"},"
            + "\"condGroup\": {\"type\": \"condition\", \"condition\": {\"country\": \"AA\"}},"
            + "\"badCondition\": {\"type\": \"condition\", \"condition\": [\"country\"]},"
            + "\"malformedCond\": {\"type\": \"condition\", \"condition\": {\"$or\": 5}},"
            + "\"cycleA\": {\"type\": \"condition\", \"condition\": {\"$savedGroup\": {\"id\": \"cycleB\"}}},"
            + "\"cycleB\": {\"type\": \"condition\", \"condition\": {\"$savedGroup\": {\"id\": \"cycleA\"}}},"
            + "\"nested\": {\"type\": \"condition\", \"condition\": {\"$savedGroup\": {\"id\": \"listGroup\"}}},"
            + "\"future\": {\"type\": \"segment\"},"
            + "\"untyped\": {\"values\": [\"u1\"]},"
            + "\"bareArray\": [\"u1\"]"
            + "}");

    @ParameterizedTest(name = "{0} with {1} -> {2}")
    @CsvSource(delimiter = '|', value = {
            "{\"id\": \"listGroup\"}                             | {\"id\": \"u1\"}                    | true",
            "{\"id\": \"listGroup\"}                             | {\"id\": \"u9\"}                    | false",
            "{\"id\": \"listGroup\"}                             | {\"id\": [\"u9\", \"u2\"]}          | true",
            "{\"id\": \"listGroup\"}                             | {}                                  | false",
            "{\"id\": \"listGroup\", \"attributeKey\": \"alt\"}  | {\"id\": \"u9\", \"alt\": \"u1\"}   | true",
            "{\"id\": \"listGroup\", \"attributeKey\": \"alt\"}  | {\"id\": \"u1\", \"alt\": \"u9\"}   | false",
            "{\"id\": \"keylessList\", \"attributeKey\": \"id\"} | {\"id\": \"u1\"}                    | true",
            "{\"id\": \"keylessList\"}                           | {\"id\": \"u1\"}                    | false",
            "{\"id\": \"badValues\"}                             | {\"id\": \"u1\"}                    | false",
            "{\"id\": \"condGroup\"}                             | {\"country\": \"AA\"}               | true",
            "{\"id\": \"condGroup\"}                             | {\"country\": \"BB\"}               | false",
            "{\"id\": \"condGroup\", \"attributeKey\": \"other\"} | {\"country\": \"AA\"}              | true",
            "{\"id\": \"badCondition\"}                          | {\"country\": \"AA\"}               | false",
            "{\"id\": \"nested\"}                                | {\"id\": \"u2\"}                    | true",
            "{\"id\": \"cycleA\"}                                | {}                                  | false",
            "{\"id\": \"missingGroup\"}                          | {\"id\": \"u1\"}                    | false",
            "{\"id\": \"bareArray\"}                             | {\"id\": \"u1\"}                    | false",
            "{\"id\": \"listGroup\", \"attributeKey\": 5}        | {\"id\": \"u1\"}                    | false",
            "{\"id\": \"listGroup\", \"attributeKey\": null}     | {\"id\": \"u1\"}                    | false",
            "{\"id\": 5}                                         | {\"id\": \"u1\"}                    | false",
            "{}                                                  | {\"id\": \"u1\"}                    | false",
            "{\"id\": \"future\"}                                | {\"id\": \"u1\"}                    | null",
            "{\"id\": \"untyped\"}                               | {\"id\": \"u1\"}                    | null",
    })
    @DisplayName("Resolves a $savedGroup reference to the group's result, no match, or unknown")
    void resolvesSavedGroupReferences(String reference, String attributes, String expected) {
        // Given
        Cond condition = CondParser.parse(json("{\"$savedGroup\": " + reference + "}"));

        // When
        Boolean result = condition.eval(json(attributes), new CondScope(SAVED_GROUPS));

        // Then
        assertEquals("null".equals(expected) ? null : Boolean.valueOf(expected), result);
    }

    @ParameterizedTest(name = "{0}")
    @CsvSource(delimiter = '|', value = {
            "\"listGroup\"",
            "[\"listGroup\"]",
            "null",
    })
    @DisplayName("Matches nobody for a reference that is not an object")
    void matchesNobodyForNonObjectReference(String reference) {
        // Given
        JsonElement parsed = JsonParser.parseString(reference);

        // When
        Boolean result = SavedGroupRef.resolve(parsed, new CondScope(SAVED_GROUPS)).eval(json("{\"id\": \"u1\"}"));

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("Matches nobody for a missing reference")
    void matchesNobodyForMissingReference() {
        // Given
        CondScope scope = new CondScope(SAVED_GROUPS);

        // When
        Boolean result = SavedGroupRef.resolve(null, scope).eval(json("{\"id\": \"u1\"}"));

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("Matches nobody for a group already being resolved")
    void matchesNobodyForGroupAlreadyVisited() {
        // Given
        CondScope scope = new CondScope(SAVED_GROUPS).visiting("listGroup");

        // When
        Boolean result = SavedGroupRef.resolve(json("{\"id\": \"listGroup\"}"), scope).eval(json("{\"id\": \"u1\"}"));

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("Throws when evaluation reaches a malformed condition group")
    void throwsForMalformedConditionGroup() {
        // Given
        Cond condition = CondParser.parse(json("{\"$savedGroup\": {\"id\": \"malformedCond\"}}"));

        // When
        Executable evaluation = () -> condition.eval(json("{}"), new CondScope(SAVED_GROUPS));

        // Then
        assertThrows(MalformedConditionException.class, evaluation);
    }

    @Test
    @DisplayName("Prunes a list group to its result only when its attribute is a known top-level attribute")
    void prunesListGroupOnlyForKnownAttribute() {
        // Given
        SavedGroupRef group = SavedGroupRef.resolve(json("{\"id\": \"listGroup\"}"), new CondScope(SAVED_GROUPS));
        SavedGroupRef dotted = SavedGroupRef.resolve(json("{\"id\": \"listGroup\", \"attributeKey\": \"user.id\"}"), new CondScope(SAVED_GROUPS));

        // When
        JsonElement known = group.prune(json("{\"id\": \"u2\"}"));
        JsonElement unknown = group.prune(json("{\"country\": \"AA\"}"));
        JsonElement nested = dotted.prune(json("{\"user\": {\"id\": \"u2\"}}"));

        // Then
        assertEquals(new JsonPrimitive(true), known);
        assertNull(unknown);
        assertNull(nested);
    }

    @Test
    @DisplayName("Prunes an unrecognized reference to no match and an unknown group type to nothing")
    void prunesDecidedReferences() {
        // Given
        CondScope scope = new CondScope(SAVED_GROUPS);

        // When
        JsonElement missing = SavedGroupRef.resolve(json("{\"id\": \"missingGroup\"}"), scope).prune(json("{}"));
        JsonElement future = SavedGroupRef.resolve(json("{\"id\": \"future\"}"), scope).prune(json("{}"));

        // Then
        assertEquals(new JsonPrimitive(false), missing);
        assertNull(future);
    }

    @Test
    @DisplayName("Gives $inGroup values for an unknown id, a bare array and a typed list")
    void givesInGroupValues() {
        // Given
        JsonElement bareArray = SAVED_GROUPS.get("bareArray");
        JsonElement typedList = SAVED_GROUPS.get("listGroup");

        // When
        JsonArray forUnknownId = SavedGroupRef.inGroupValues(null);
        JsonArray forBareArray = SavedGroupRef.inGroupValues(bareArray);
        JsonArray forTypedList = SavedGroupRef.inGroupValues(typedList);

        // Then
        assertEquals(new JsonArray(), forUnknownId);
        assertSame(bareArray, forBareArray);
        assertEquals(json("{\"v\": [\"u1\", \"u2\"]}").get("v"), forTypedList);
    }

    @Test
    @DisplayName("Gives no $inGroup values for a condition group, malformed list or other entry")
    void givesNoInGroupValuesForOtherEntries() {
        // Given
        JsonElement conditionGroup = SAVED_GROUPS.get("condGroup");

        // When
        JsonArray values = SavedGroupRef.inGroupValues(conditionGroup);

        // Then
        assertNull(values);
        assertNull(SavedGroupRef.inGroupValues(SAVED_GROUPS.get("badValues")));
        assertNull(SavedGroupRef.inGroupValues(SAVED_GROUPS.get("future")));
        assertNull(SavedGroupRef.inGroupValues(new JsonPrimitive("u1")));
    }

    @Test
    @DisplayName("Recognizes only object entries with a type other than list or condition as a new group type")
    void recognizesNewGroupTypes() {
        // Given
        JsonElement future = SAVED_GROUPS.get("future");

        // When
        boolean isNew = SavedGroupRef.isNewGroupType(future);

        // Then
        assertTrue(isNew);
        assertTrue(SavedGroupRef.isNewGroupType(SAVED_GROUPS.get("untyped")));
        assertFalse(SavedGroupRef.isNewGroupType(SAVED_GROUPS.get("listGroup")));
        assertFalse(SavedGroupRef.isNewGroupType(SAVED_GROUPS.get("condGroup")));
        assertFalse(SavedGroupRef.isNewGroupType(SAVED_GROUPS.get("bareArray")));
        assertFalse(SavedGroupRef.isNewGroupType(null));
    }

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }
}
