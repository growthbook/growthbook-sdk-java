package growthbook.sdk.java.evaluators.internal.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ConditionReducerTest {

    private static final JsonObject ATTRIBUTES = json("{'plan': 'pro', 'age': 30}");
    private static final JsonObject SAVED_GROUPS = json("{'pros': {'type': 'list', 'attributeKey': 'plan', 'values': ['pro']},"
            + " 'regions': {'type': 'list', 'attributeKey': 'region', 'values': ['r1']}}");

    private final ConditionReducer reducer = new ConditionReducer(ATTRIBUTES, SAVED_GROUPS);

    @ParameterizedTest(name = "{0}")
    @ValueSource(booleans = {true, false})
    @DisplayName("Removes a condition that always passes")
    void removesPassingCondition(boolean isRule) {
        // Given
        JsonObject item = json("{'condition': {'plan': 'pro', 'age': {'$gt': 18}}, 'force': 1}");

        // When
        JsonElement reduced = reducer.reduce(item, isRule);

        // Then
        assertEquals(json("{'force': 1}"), reduced);
    }

    @Test
    @DisplayName("Removes a condition that passes through a saved group")
    void removesConditionPassingThroughSavedGroup() {
        // Given
        JsonObject item = json("{'condition': {'$savedGroup': {'id': 'pros'}}, 'force': 1}");

        // When
        JsonElement reduced = reducer.reduce(item, true);

        // Then
        assertEquals(json("{'force': 1}"), reduced);
    }

    @Test
    @DisplayName("Marks a rule that never passes as never matching and clears its forced value")
    void marksFailingRuleAsNeverMatching() {
        // Given
        JsonObject rule = json("{'id': 'r1', 'condition': {'plan': 'free'}, 'force': 1}");

        // When
        JsonElement reduced = reducer.reduce(rule, true);

        // Then
        JsonObject expected = json("{'id': 'r1', 'condition': {'$not': {}}}");
        expected.add("force", JsonNull.INSTANCE);
        assertEquals(expected, reduced);
    }

    @Test
    @DisplayName("Adds no forced value to a never-passing rule that has none")
    void addsNoForceToFailingRuleWithoutForce() {
        // Given
        JsonObject rule = json("{'condition': {'plan': 'free'}, 'variations': [0, 1]}");

        // When
        JsonElement reduced = reducer.reduce(rule, true);

        // Then
        assertEquals(json("{'condition': {'$not': {}}, 'variations': [0, 1]}"), reduced);
    }

    @Test
    @DisplayName("Keeps an experiment's forced value when its condition never passes")
    void keepsExperimentForceWhenNeverPassing() {
        // Given
        JsonObject experiment = json("{'key': 'e1', 'condition': {'plan': 'free'}, 'force': 1}");

        // When
        JsonElement reduced = reducer.reduce(experiment, false);

        // Then
        assertEquals(json("{'key': 'e1', 'condition': {'$not': {}}, 'force': 1}"), reduced);
    }

    @Test
    @DisplayName("Replaces an undecided condition with its pruned form")
    void replacesUndecidedConditionWithPrunedForm() {
        // Given
        JsonObject item = json("{'condition': {'plan': 'pro', 'region': 'r1', '$or': [{'age': {'$lt': 18}}, {'device': 'd1'}]}, 'force': 1}");

        // When
        JsonElement reduced = reducer.reduce(item, true);

        // Then
        assertEquals(json("{'condition': {'region': 'r1', '$or': [{'device': 'd1'}]}, 'force': 1}"), reduced);
    }

    @Test
    @DisplayName("Keeps an undecided saved group reference as written")
    void keepsUndecidedSavedGroupReference() {
        // Given
        JsonObject item = json("{'condition': {'$savedGroup': {'id': 'regions'}}, 'force': 1}");

        // When
        JsonElement reduced = reducer.reduce(item, true);

        // Then
        assertEquals(item, reduced);
    }

    @Test
    @DisplayName("Treats a nested attribute path as unknown")
    void treatsNestedPathAsUnknown() {
        // Given
        ConditionReducer nested = new ConditionReducer(json("{'account': {'plan': 'pro'}}"), new JsonObject());
        JsonObject item = json("{'condition': {'account.plan': 'pro'}}");

        // When
        JsonElement reduced = nested.reduce(item, true);

        // Then
        assertEquals(item, reduced);
    }

    @Test
    @DisplayName("Returns a copy of an item without a condition")
    void copiesItemWithoutCondition() {
        // Given
        JsonObject item = json("{'force': 1}");

        // When
        JsonElement reduced = reducer.reduce(item, true);

        // Then
        assertEquals(item, reduced);
        assertNotSame(item, reduced);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"'plan'", "null", "[{'plan': 'free'}]", "1"})
    @DisplayName("Leaves a condition that is not an object as written")
    void leavesNonObjectConditionAsWritten(String condition) {
        // Given
        JsonObject item = json("{'condition': " + condition + ", 'force': 1}");

        // When
        JsonElement reduced = reducer.reduce(item, true);

        // Then
        assertEquals(item, reduced);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"'rule'", "[1, 2]", "null", "3"})
    @DisplayName("Returns an item that is not an object as is")
    void returnsNonObjectItemAsIs(String value) {
        // Given
        JsonElement item = JsonParser.parseString(value.replace('\'', '"'));

        // When
        JsonElement reduced = reducer.reduce(item, true);

        // Then
        assertSame(item, reduced);
    }

    @Test
    @DisplayName("Never modifies the item it reduces")
    void neverModifiesItem() {
        // Given
        JsonObject rule = json("{'condition': {'plan': 'free'}, 'force': 1}");
        JsonObject before = rule.deepCopy();

        // When
        reducer.reduce(rule, true);

        // Then
        assertEquals(before, rule);
    }

    @Test
    @DisplayName("Prunes a condition to a boolean once it is decided")
    void prunesDecidedConditionToBoolean() {
        // Given
        JsonObject passing = json("{'plan': 'pro'}");
        JsonObject failing = json("{'plan': 'free', 'region': 'r1'}");

        // When
        JsonElement passed = reducer.prune(passing);
        JsonElement failed = reducer.prune(failing);

        // Then
        assertEquals(new JsonPrimitive(true), passed);
        assertEquals(new JsonPrimitive(false), failed);
    }

    @Test
    @DisplayName("Prunes an empty condition to true")
    void prunesEmptyConditionToTrue() {
        // Given
        JsonObject condition = new JsonObject();

        // When
        JsonElement pruned = reducer.prune(condition);

        // Then
        assertEquals(new JsonPrimitive(true), pruned);
    }

    @Test
    @DisplayName("Reduces a list of rules independently of each other")
    void reducesEachRuleIndependently() {
        // Given
        JsonArray rules = JsonParser.parseString("[{\"condition\": {\"plan\": \"pro\"}}, {\"condition\": {\"plan\": \"free\"}}]").getAsJsonArray();

        // When
        JsonElement first = reducer.reduce(rules.get(0), true);
        JsonElement second = reducer.reduce(rules.get(1), true);

        // Then
        assertEquals(new JsonObject(), first);
        assertEquals(json("{'condition': {'$not': {}}}"), second);
    }

    private static JsonObject json(String value) {
        return JsonParser.parseString(value.replace('\'', '"')).getAsJsonObject();
    }
}
