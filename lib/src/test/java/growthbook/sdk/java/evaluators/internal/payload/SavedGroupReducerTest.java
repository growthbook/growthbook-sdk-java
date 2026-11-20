package growthbook.sdk.java.evaluators.internal.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

class SavedGroupReducerTest {

    private static final JsonObject ATTRIBUTES = json("{'plan': 'pro'}");
    private static final String PRUNABLE = "{'type': 'condition', 'condition': {'plan': 'pro', 'region': 'r1'}}";

    @Test
    @DisplayName("Keeps no saved groups when the payload has none")
    void keepsNothingWithoutSavedGroups() {
        // Given
        JsonElement reduced = json("{'features': {'f': {'rules': [{'condition': {'$savedGroup': {'id': 'g1'}}}]}}}");

        // When
        JsonObject groups = new SavedGroupReducer(new JsonObject(), ATTRIBUTES).reduce(reduced);

        // Then
        assertEquals(new JsonObject(), groups);
    }

    @Test
    @DisplayName("Keeps only the saved groups the reduced payload references")
    void keepsOnlyReferencedGroups() {
        // Given
        JsonObject savedGroups = json("{'used': {'type': 'list', 'attributeKey': 'id', 'values': ['v1']},"
                + " 'unused': {'type': 'list', 'attributeKey': 'id', 'values': ['v2']}}");
        JsonElement reduced = json("{'features': {'f': {'rules': [{'condition': {'id': {'$inGroup': 'used'}}}]}}}");

        // When
        JsonObject groups = new SavedGroupReducer(savedGroups, ATTRIBUTES).reduce(reduced);

        // Then
        assertEquals(json("{'used': {'type': 'list', 'attributeKey': 'id', 'values': ['v1']}}"), groups);
    }

    @Test
    @DisplayName("Keeps no saved groups when nothing references them")
    void keepsNothingWhenUnreferenced() {
        // Given
        JsonObject savedGroups = json("{'g1': {'type': 'list', 'attributeKey': 'id', 'values': ['v1']}}");
        JsonElement reduced = json("{'features': {'f': {'defaultValue': 'g2'}}, 'experiments': []}");

        // When
        JsonObject groups = new SavedGroupReducer(savedGroups, ATTRIBUTES).reduce(reduced);

        // Then
        assertEquals(new JsonObject(), groups);
    }

    @Test
    @DisplayName("Keeps the groups referenced groups reference, transitively, in the order a recursive walk meets them")
    void keepsGroupsTransitively() {
        // Given
        JsonObject savedGroups = json("{'list': {'type': 'list', 'attributeKey': 'id', 'values': ['v1']},"
                + " 'inner': {'type': 'condition', 'condition': {'$savedGroup': {'id': 'list'}}},"
                + " 'outer': {'type': 'condition', 'condition': {'$savedGroup': {'id': 'inner'}}},"
                + " 'later': {'type': 'list', 'attributeKey': 'id', 'values': ['v2']},"
                + " 'unused': {'type': 'list', 'attributeKey': 'id', 'values': ['v3']}}");
        JsonElement reduced = JsonParser.parseString("[{\"r1\": {\"$savedGroup\": {\"id\": \"outer\"}}}, {\"r2\": {\"$inGroup\": \"later\"}}]");

        // When
        JsonObject groups = new SavedGroupReducer(savedGroups, ATTRIBUTES).reduce(reduced);

        // Then
        assertEquals(Arrays.asList("outer", "inner", "list", "later"), new ArrayList<>(groups.keySet()));
    }

    @Test
    @DisplayName("Never treats a list group's values as references")
    void listValuesAreNotReferences() {
        // Given
        JsonObject savedGroups = json("{'list': {'type': 'list', 'attributeKey': 'id', 'values': ['other']},"
                + " 'other': {'type': 'list', 'attributeKey': 'id', 'values': ['v1']}}");
        JsonElement reduced = json("{'condition': {'id': {'$inGroup': 'list'}}}");

        // When
        JsonObject groups = new SavedGroupReducer(savedGroups, ATTRIBUTES).reduce(reduced);

        // Then
        assertEquals(Collections.singletonList("list"), new ArrayList<>(groups.keySet()));
    }

    @Test
    @DisplayName("Reduces a condition group evaluated against the user")
    void reducesConditionGroupEvaluatedAgainstUser() {
        // Given
        JsonObject savedGroups = json("{'g1': " + PRUNABLE + "}");
        JsonElement reduced = json("{'condition': {'$savedGroup': {'id': 'g1'}}}");

        // When
        JsonObject groups = new SavedGroupReducer(savedGroups, ATTRIBUTES).reduce(reduced);

        // Then
        assertEquals(json("{'g1': {'type': 'condition', 'condition': {'region': 'r1'}}}"), groups);
    }

    @Test
    @DisplayName("Keeps a decided condition group as written")
    void keepsDecidedConditionGroupAsWritten() {
        // Given
        JsonObject savedGroups = json("{'passes': {'type': 'condition', 'condition': {'plan': 'pro'}},"
                + " 'fails': {'type': 'condition', 'condition': {'plan': 'free'}}}");
        JsonElement reduced = json("{'a': {'$savedGroup': {'id': 'passes'}}, 'b': {'$savedGroup': {'id': 'fails'}}}");

        // When
        JsonObject groups = new SavedGroupReducer(savedGroups, ATTRIBUTES).reduce(reduced);

        // Then
        assertEquals(savedGroups, groups);
    }

    @Test
    @DisplayName("Keeps a group evaluated inside $elemMatch and the groups it references as written")
    void keepsGroupsInsideElemMatchAsWritten() {
        // Given
        JsonObject savedGroups = json("{'item': {'type': 'condition', 'condition': {'plan': 'pro', '$savedGroup': {'id': 'nested'}}},"
                + " 'nested': " + PRUNABLE + "}");
        JsonElement reduced = json("{'condition': {'orders': {'$elemMatch': {'$savedGroup': {'id': 'item'}}}}}");

        // When
        JsonObject groups = new SavedGroupReducer(savedGroups, ATTRIBUTES).reduce(reduced);

        // Then
        assertEquals(savedGroups, groups);
    }

    @Test
    @DisplayName("Keeps a group evaluated against a prerequisite's value as written")
    void keepsGroupsInParentConditionsAsWritten() {
        // Given
        JsonObject savedGroups = json("{'g1': " + PRUNABLE + "}");
        JsonElement reduced = json("{'rules': [{'parentConditions': [{'id': 'f2', 'condition': {'value': {'$savedGroup': {'id': 'g1'}}}}]}]}");

        // When
        JsonObject groups = new SavedGroupReducer(savedGroups, ATTRIBUTES).reduce(reduced);

        // Then
        assertEquals(savedGroups, groups);
    }

    @Test
    @DisplayName("Keeps a group as written when it is evaluated both against the user and something else")
    void keepsGroupAsWrittenWhenAlsoEvaluatedAgainstOtherValues() {
        // Given
        JsonObject savedGroups = json("{'g1': " + PRUNABLE + "}");
        JsonElement reduced = json("{'a': {'$savedGroup': {'id': 'g1'}}, 'b': {'orders': {'$elemMatch': {'$savedGroup': {'id': 'g1'}}}}}");

        // When
        JsonObject groups = new SavedGroupReducer(savedGroups, ATTRIBUTES).reduce(reduced);

        // Then
        assertEquals(savedGroups, groups);
    }

    @Test
    @DisplayName("Keeps condition groups on a cycle as written")
    void keepsGroupsOnCycleAsWritten() {
        // Given
        JsonObject savedGroups = json("{'a': {'type': 'condition', 'condition': {'plan': 'pro', 'region': 'r1', '$savedGroup': {'id': 'b'}}},"
                + " 'b': {'type': 'condition', 'condition': {'plan': 'pro', 'device': 'd1', '$savedGroup': {'id': 'a'}}},"
                + " 'self': {'type': 'condition', 'condition': {'plan': 'pro', 'region': 'r1', '$or': [{'$savedGroup': {'id': 'self'}}, {'age': 1}]}}}");
        JsonElement reduced = json("{'x': {'$savedGroup': {'id': 'a'}}, 'y': {'$savedGroup': {'id': 'self'}}}");

        // When
        JsonObject groups = new SavedGroupReducer(savedGroups, ATTRIBUTES).reduce(reduced);

        // Then
        assertEquals(savedGroups, groups);
    }

    @Test
    @DisplayName("Keeps a group that is not an object or has an unknown type as written")
    void keepsUnrecognizedGroupsAsWritten() {
        // Given
        JsonObject savedGroups = json("{'legacy': ['v1', 'v2'], 'future': {'type': 'segment', 'rules': {'plan': 'pro'}}}");
        JsonElement reduced = json("{'a': {'id': {'$inGroup': 'legacy'}}, 'b': {'$savedGroup': {'id': 'future'}}}");

        // When
        JsonObject groups = new SavedGroupReducer(savedGroups, ATTRIBUTES).reduce(reduced);

        // Then
        assertEquals(savedGroups, groups);
    }

    @Test
    @DisplayName("Never modifies the saved groups it reduces")
    void neverModifiesSavedGroups() {
        // Given
        JsonObject savedGroups = json("{'g1': " + PRUNABLE + "}");
        JsonObject before = savedGroups.deepCopy();
        JsonElement reduced = json("{'condition': {'$savedGroup': {'id': 'g1'}}}");

        // When
        new SavedGroupReducer(savedGroups, ATTRIBUTES).reduce(reduced);

        // Then
        assertEquals(before, savedGroups);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', value = {
            "{'type': 'condition', 'condition': {'plan': 'pro', 'region': 'r1'}} | true",
            "{'condition': {}}                                                   | true",
            "{'type': 'list', 'values': []}                                      | false",
            "['g1']                                                              | false",
    })
    @DisplayName("Lets only non-list objects reference other groups")
    void onlyNonListObjectsCanReference(String group, boolean expected) {
        // Given
        JsonElement parsed = JsonParser.parseString(group.replace('\'', '"'));

        // When
        boolean canReference = SavedGroupReducer.canReference(parsed);

        // Then
        assertEquals(expected, canReference);
    }

    @Test
    @DisplayName("Finds the saved group ids that appear as strings anywhere in a value")
    void findsGroupIdsAnywhere() {
        // Given
        JsonObject savedGroups = json("{'g1': {}, 'g2': {}, 'g3': {}}");
        JsonElement value = json("{'g3': 1, 'a': ['g1', {'b': 'g2'}, 'other', 7, null]}");

        // When
        List<String> ids = SavedGroupReducer.groupIdsIn(value, savedGroups);

        // Then
        assertEquals(new HashSet<>(Arrays.asList("g1", "g2")), new HashSet<>(ids));
    }

    private static JsonObject json(String value) {
        return JsonParser.parseString(value.replace('\'', '"')).getAsJsonObject();
    }
}
