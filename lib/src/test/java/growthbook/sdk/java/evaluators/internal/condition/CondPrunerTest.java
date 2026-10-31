package growthbook.sdk.java.evaluators.internal.condition;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

class CondPrunerTest {

    private static final JsonObject SAVED_GROUPS = json("{"
            + "\"listGroup\": {\"type\": \"list\", \"attributeKey\": \"id\", \"values\": [\"u1\", \"u2\"]},"
            + "\"condGroup\": {\"type\": \"condition\", \"condition\": {\"country\": \"AA\", \"beta\": true}},"
            + "\"decidedGroup\": {\"type\": \"condition\", \"condition\": {\"country\": \"AA\"}},"
            + "\"selfRef\": {\"type\": \"condition\", \"condition\": {\"$savedGroup\": {\"id\": \"selfRef\"}}},"
            + "\"future\": {\"type\": \"segment\"}"
            + "}");

    @Test
    @DisplayName("Keeps an unknown attribute whose condition value is the boolean false as written")
    void keepsUnknownAttributeComparedToFalse() {
        // Given
        JsonObject condition = json("{\"beta\": false}");

        // When
        JsonElement pruned = CondPruner.pruneCondition(json("{\"country\": \"AA\"}"), condition, null);

        // Then
        assertEquals(json("{\"beta\": false}"), pruned);
    }

    @ParameterizedTest(name = "{0} with {1} -> {2}")
    @CsvSource(delimiter = '|', value = {
            "{\"beta\": false}                         | {\"beta\": false}                      | true",
            "{\"beta\": false}                         | {\"beta\": true}                       | false",
            "{\"a\": null}                             | {\"a\": null}                          | true",
            "{\"country\": \"AA\", \"beta\": true}     | {\"country\": \"BB\"}                  | false",
            "{\"country\": \"AA\"}                     | {\"country\": \"AA\"}                  | true",
            "{}                                        | {}                                     | true",
            "{\"$or\": []}                             | {}                                     | true",
            "{\"$and\": []}                            | {}                                     | true",
            "{\"$or\": [{\"country\": \"AA\"}, {\"beta\": true}]}  | {\"country\": \"AA\"}      | true",
            "{\"$or\": [{\"country\": \"BB\"}, {\"country\": \"CC\"}]} | {\"country\": \"AA\"}  | false",
            "{\"$and\": [{\"country\": \"BB\"}, {\"beta\": true}]} | {\"country\": \"AA\"}      | false",
            "{\"$and\": [{\"country\": \"AA\"}, {\"n\": {\"$gt\": 1}}]} | {\"country\": \"AA\", \"n\": 2} | true",
            "{\"$nor\": [{\"country\": \"AA\"}, {\"beta\": true}]} | {\"country\": \"AA\"}      | false",
            "{\"$nor\": [{\"country\": \"BB\"}]}       | {\"country\": \"AA\"}                  | true",
            "{\"$not\": {\"country\": \"AA\"}}         | {\"country\": \"AA\"}                  | false",
            "{\"$savedGroup\": {\"id\": \"listGroup\"}} | {\"id\": \"u2\"}                      | true",
            "{\"$savedGroup\": {\"id\": \"decidedGroup\"}} | {\"country\": \"BB\"}              | false",
            "{\"$savedGroup\": {\"id\": \"selfRef\"}}  | {}                                     | false",
    })
    @DisplayName("Decides a condition whose result does not depend on unknown attributes")
    void decidesConditionsIndependentOfUnknownAttributes(String condition, String attributes, boolean expected) {
        // Given
        JsonObject parsed = json(condition);

        // When
        JsonElement pruned = CondPruner.pruneCondition(json(attributes), parsed, SAVED_GROUPS);

        // Then
        assertEquals(new JsonPrimitive(expected), pruned);
    }

    @ParameterizedTest(name = "{0} with {1} -> {2}")
    @CsvSource(delimiter = '|', value = {
            "{\"country\": \"AA\", \"beta\": true}                  | {\"country\": \"AA\"} | {\"beta\": true}",
            "{\"$or\": [{\"country\": \"BB\"}, {\"beta\": true}]}   | {\"country\": \"AA\"} | {\"$or\": [{\"beta\": true}]}",
            "{\"$and\": [{\"country\": \"AA\"}, {\"beta\": true}]}  | {\"country\": \"AA\"} | {\"$and\": [{\"beta\": true}]}",
            "{\"$nor\": [{\"country\": \"BB\"}, {\"beta\": true}]}  | {\"country\": \"AA\"} | {\"$nor\": [{\"beta\": true}]}",
            "{\"$not\": {\"country\": \"AA\", \"beta\": true}}      | {\"country\": \"AA\"} | {\"$not\": {\"beta\": true}}",
            "{\"$or\": [{\"country\": \"AA\", \"beta\": false}, {\"country\": \"BB\"}]} | {\"country\": \"AA\"} | {\"$or\": [{\"beta\": false}]}",
    })
    @DisplayName("Drops decided branches and keeps the undecided ones")
    void dropsDecidedBranches(String condition, String attributes, String expected) {
        // Given
        JsonObject parsed = json(condition);

        // When
        JsonElement pruned = CondPruner.pruneCondition(json(attributes), parsed, SAVED_GROUPS);

        // Then
        assertEquals(json(expected), pruned);
    }

    @ParameterizedTest(name = "{0} with {1} kept")
    @CsvSource(delimiter = '|', value = {
            "{\"address.city\": \"Testville\"}            | {\"address\": {\"city\": \"Testville\"}}",
            "{\"tags.length\": 0}                         | {\"tags\": []}",
            "{\"country\": {\"$foo\": 1}}                 | {\"country\": \"AA\"}",
            "{\"id\": {\"$inGroup\": \"future\"}}         | {\"id\": \"u1\"}",
            "{\"$savedGroup\": {\"id\": \"condGroup\"}}   | {\"country\": \"AA\"}",
            "{\"$savedGroup\": {\"id\": \"listGroup\"}}   | {\"country\": \"AA\"}",
            "{\"$savedGroup\": {\"id\": \"future\"}}      | {\"id\": \"u1\"}",
            "{\"$not\": {\"beta\": true}}                 | {\"country\": \"AA\"}",
    })
    @DisplayName("Keeps an entry as written while its result is unknown")
    void keepsUnknownEntriesAsWritten(String condition, String attributes) {
        // Given
        JsonObject parsed = json(condition);

        // When
        JsonElement pruned = CondPruner.pruneCondition(json(attributes), parsed, SAVED_GROUPS);

        // Then
        assertEquals(json(condition), pruned);
    }

    @Test
    @DisplayName("Returns a malformed condition as written once pruning reaches the malformed part")
    void returnsMalformedConditionAsWritten() {
        // Given
        JsonObject condition = json("{\"country\": \"AA\", \"$or\": \"not-an-array\"}");

        // When
        JsonElement pruned = CondPruner.pruneCondition(json("{\"country\": \"AA\"}"), condition, null);

        // Then
        assertSame(condition, pruned);
    }

    @Test
    @DisplayName("Returns a condition with a non-object $or entry as written")
    void returnsConditionWithNonObjectListEntryAsWritten() {
        // Given
        JsonObject condition = json("{\"$or\": [{\"beta\": true}, 5]}");

        // When
        JsonElement pruned = CondPruner.pruneCondition(json("{}"), condition, null);

        // Then
        assertSame(condition, pruned);
    }

    @Test
    @DisplayName("Decides a condition before reaching a malformed part when an earlier entry fails")
    void decidesBeforeReachingMalformedPart() {
        // Given
        JsonObject condition = json("{\"country\": \"AA\", \"$or\": \"not-an-array\"}");

        // When
        JsonElement pruned = CondPruner.pruneCondition(json("{\"country\": \"BB\"}"), condition, null);

        // Then
        assertEquals(new JsonPrimitive(false), pruned);
    }

    @Test
    @DisplayName("Does not modify the condition it prunes")
    void doesNotModifyCondition() {
        // Given
        JsonObject condition = json("{\"country\": \"AA\", \"$or\": [{\"country\": \"BB\"}, {\"beta\": true}]}");
        JsonObject original = condition.deepCopy();

        // When
        CondPruner.pruneCondition(json("{\"country\": \"AA\"}"), condition, null);

        // Then
        assertEquals(original, condition);
    }

    @ParameterizedTest(name = "{0} with {1} -> {2}")
    @CsvSource(delimiter = '|', value = {
            "listGroup     | {\"id\": \"u1\"}                        | true",
            "listGroup     | {\"id\": \"u9\"}                        | false",
            "condGroup     | {\"country\": \"AA\", \"beta\": true}   | true",
            "condGroup     | {\"country\": \"BB\"}                   | false",
            "missingGroup  | {\"id\": \"u1\"}                        | false",
            "selfRef       | {}                                      | false",
    })
    @DisplayName("Decides a saved group whose result does not depend on unknown attributes")
    void decidesSavedGroups(String groupId, String attributes, boolean expected) {
        // Given
        JsonObject known = json(attributes);

        // When
        JsonElement pruned = CondPruner.pruneSavedGroup(known, groupId, SAVED_GROUPS);

        // Then
        assertEquals(new JsonPrimitive(expected), pruned);
    }

    @Test
    @DisplayName("Gives a condition group's condition minus its decided branches while it is undecided")
    void prunesUndecidedConditionGroup() {
        // Given
        JsonObject known = json("{\"country\": \"AA\"}");

        // When
        JsonElement pruned = CondPruner.pruneSavedGroup(known, "condGroup", SAVED_GROUPS);

        // Then
        assertEquals(json("{\"beta\": true}"), pruned);
    }

    @Test
    @DisplayName("Gives nothing for a list group whose attribute is unknown")
    void givesNothingForListGroupWithUnknownAttribute() {
        // Given
        JsonObject known = json("{\"country\": \"AA\"}");

        // When
        JsonElement pruned = CondPruner.pruneSavedGroup(known, "listGroup", SAVED_GROUPS);

        // Then
        assertNull(pruned);
    }

    @Test
    @DisplayName("Gives nothing for a saved group of an unknown type")
    void givesNothingForUnknownGroupType() {
        // Given
        JsonObject known = json("{\"id\": \"u1\"}");

        // When
        JsonElement pruned = CondPruner.pruneSavedGroup(known, "future", SAVED_GROUPS);

        // Then
        assertNull(pruned);
    }

    @Test
    @DisplayName("Gives nothing for a condition group whose condition is malformed")
    void givesNothingForMalformedConditionGroup() {
        // Given
        JsonObject groups = json("{\"bad\": {\"type\": \"condition\", \"condition\": {\"$and\": 5}}}");

        // When
        JsonElement pruned = CondPruner.pruneSavedGroup(json("{}"), "bad", groups);

        // Then
        assertNull(pruned);
    }

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }
}
