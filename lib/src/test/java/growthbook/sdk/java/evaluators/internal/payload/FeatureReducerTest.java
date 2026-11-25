package growthbook.sdk.java.evaluators.internal.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import growthbook.sdk.java.model.PayloadFilters;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

class FeatureReducerTest {

    private static final PayloadFilter ONLY_P1 = new PayloadFilter(PayloadFilters.builder().projects(Collections.singletonList("p1")).build(), null);
    private static final PayloadFilter NO_FILTER = new PayloadFilter(null, null);

    @Test
    @DisplayName("Keeps only the features the filters match")
    void keepsMatchingFeatures() {
        // Given
        JsonObject features = json("{'a': {'defaultValue': 1, 'metadata': {'projects': ['p1']}},"
                + " 'b': {'defaultValue': 2, 'metadata': {'projects': ['p2']}},"
                + " 'c': {'defaultValue': 3, 'metadata': {'allProjects': true}}}");
        FeatureReducer reducer = reducer(features, ONLY_P1, false);

        // When
        reducer.addMatching();

        // Then
        assertEquals(json("{'a': {'defaultValue': 1, 'metadata': {'projects': ['p1']}},"
                + " 'c': {'defaultValue': 3, 'metadata': {'allProjects': true}}}"), reducer.features());
    }

    @Test
    @DisplayName("Adds the prerequisites of a kept feature even when the filters drop them")
    void addsPrerequisitesOfKeptFeatures() {
        // Given
        JsonObject features = json("{'a': {'metadata': {'projects': ['p1']}, 'rules': [{'parentConditions': [{'id': 'b', 'condition': {'value': true}}], 'force': 1}]},"
                + " 'b': {'metadata': {'projects': ['p2']}, 'rules': [{'parentConditions': [{'id': 'c', 'condition': {'value': true}}], 'force': true}]},"
                + " 'c': {'metadata': {'projects': ['p3']}, 'defaultValue': true},"
                + " 'd': {'metadata': {'projects': ['p4']}, 'defaultValue': true}}");
        FeatureReducer reducer = reducer(features, ONLY_P1, false);

        // When
        reducer.addMatching();

        // Then
        assertEquals(Arrays.asList("a", "b", "c"), keys(reducer.features()));
    }

    @Test
    @DisplayName("Adds the prerequisites of a kept experiment even when the filters drop them")
    void addsPrerequisitesOfKeptExperiments() {
        // Given
        JsonObject features = json("{'a': {'metadata': {'projects': ['p2']}, 'defaultValue': true},"
                + " 'b': {'metadata': {'projects': ['p2']}, 'defaultValue': true}}");
        JsonObject experiment = json("{'key': 'e1', 'parentConditions': [{'id': 'a', 'condition': {'value': true}}]}");
        FeatureReducer reducer = reducer(features, ONLY_P1, false);

        // When
        reducer.addMatching();
        reducer.addParents(experiment);

        // Then
        assertEquals(Collections.singletonList("a"), keys(reducer.features()));
    }

    @Test
    @DisplayName("Adds only holdout prerequisites when prerequisites are filtered")
    void addsOnlyHoldoutsWhenFilteringPrerequisites() {
        // Given
        JsonObject features = json("{'a': {'metadata': {'projects': ['p1']}, 'rules': [{'parentConditions': ["
                + "{'id': 'b', 'condition': {'value': true}}, {'id': '$holdout:h1', 'condition': {'value': 'holdoutcontrol'}}], 'force': 1}]},"
                + " 'b': {'metadata': {'projects': ['p2']}, 'defaultValue': true},"
                + " '$holdout:h1': {'metadata': {'projects': ['p2']}, 'defaultValue': 'holdoutcontrol'}}");
        FeatureReducer reducer = reducer(features, ONLY_P1, true);

        // When
        reducer.addMatching();

        // Then
        assertEquals(Arrays.asList("a", "$holdout:h1"), keys(reducer.features()));
    }

    @Test
    @DisplayName("Adds only holdout prerequisites of an experiment when prerequisites are filtered")
    void addsOnlyHoldoutsOfExperimentWhenFilteringPrerequisites() {
        // Given
        JsonObject features = json("{'b': {'metadata': {'projects': ['p2']}, 'defaultValue': true},"
                + " '$holdout:h1': {'metadata': {'projects': ['p2']}, 'defaultValue': 'holdoutcontrol'}}");
        JsonObject experiment = json("{'key': 'e1', 'parentConditions': [{'id': 'b'}, {'id': '$holdout:h1'}]}");
        FeatureReducer reducer = reducer(features, ONLY_P1, true);

        // When
        reducer.addParents(experiment);

        // Then
        assertEquals(Collections.singletonList("$holdout:h1"), keys(reducer.features()));
    }

    @Test
    @DisplayName("Terminates on a cycle of prerequisites and keeps each feature once")
    void terminatesOnPrerequisiteCycle() {
        // Given
        JsonObject features = json("{'a': {'metadata': {'projects': ['p1']}, 'rules': [{'parentConditions': [{'id': 'b'}], 'force': 1}]},"
                + " 'b': {'metadata': {'projects': ['p2']}, 'rules': [{'parentConditions': [{'id': 'c'}], 'force': 2}]},"
                + " 'c': {'metadata': {'projects': ['p2']}, 'rules': [{'parentConditions': [{'id': 'a'}, {'id': 'c'}], 'force': 3}]}}");
        FeatureReducer reducer = reducer(features, ONLY_P1, false);

        // When
        reducer.addMatching();

        // Then
        assertEquals(features, reducer.features());
    }

    @Test
    @DisplayName("Ignores prerequisites that are missing, falsy or have no string id")
    void ignoresUnusablePrerequisites() {
        // Given
        JsonObject features = json("{'a': {'rules': [{'parentConditions': [{'id': 'missing'}, {'id': 'empty'}, {'id': 7}, {}, 'b', null], 'force': 1}]},"
                + " 'empty': null}");
        FeatureReducer reducer = reducer(features, ONLY_P1, false);

        // When
        reducer.addParents(features.getAsJsonObject("a").getAsJsonArray("rules").get(0));

        // Then
        assertEquals(new JsonObject(), reducer.features());
    }

    @Test
    @DisplayName("Ignores an item without prerequisites")
    void ignoresItemWithoutPrerequisites() {
        // Given
        FeatureReducer reducer = reducer(json("{'a': {'defaultValue': 1}}"), NO_FILTER, false);

        // When
        reducer.addParents(json("{'key': 'e1'}"));
        reducer.addParents(json("{'key': 'e2', 'parentConditions': {'id': 'a'}}"));
        reducer.addParents(JsonParser.parseString("\"a\""));

        // Then
        assertEquals(new JsonObject(), reducer.features());
    }

    @Test
    @DisplayName("Drops a rule scoped to other projects from a feature in the included projects")
    void dropsRulesScopedToOtherProjects() {
        // Given
        JsonObject features = json("{'a': {'metadata': {'projects': ['p1']}, 'rules': ["
                + "{'metadata': {'projects': ['p2']}, 'force': 1},"
                + " {'metadata': {'projects': ['p1']}, 'force': 2},"
                + " {'force': 3}]}}");
        FeatureReducer reducer = reducer(features, ONLY_P1, false);

        // When
        reducer.addMatching();

        // Then
        assertEquals(json("{'a': {'metadata': {'projects': ['p1']}, 'rules': ["
                + "{'metadata': {'projects': ['p1']}, 'force': 2}, {'force': 3}]}}"), reducer.features());
    }

    @Test
    @DisplayName("Keeps every rule of a prerequisite outside the included projects")
    void keepsRulesOfPrerequisiteOutsideIncludedProjects() {
        // Given
        JsonObject features = json("{'a': {'metadata': {'projects': ['p1']}, 'rules': [{'parentConditions': [{'id': 'b'}], 'force': 1}]},"
                + " 'b': {'metadata': {'projects': ['p2']}, 'rules': [{'metadata': {'projects': ['p2']}, 'force': true}]}}");
        FeatureReducer reducer = reducer(features, ONLY_P1, false);

        // When
        reducer.addMatching();

        // Then
        assertEquals(features.get("b"), reducer.features().get("b"));
    }

    @Test
    @DisplayName("Does not add the prerequisites of a dropped rule")
    void skipsPrerequisitesOfDroppedRules() {
        // Given
        JsonObject features = json("{'a': {'metadata': {'projects': ['p1']}, 'rules': [{'metadata': {'projects': ['p2']}, 'parentConditions': [{'id': 'b'}], 'force': 1}]},"
                + " 'b': {'metadata': {'projects': ['p2']}, 'defaultValue': true}}");
        FeatureReducer reducer = reducer(features, ONLY_P1, false);

        // When
        reducer.addMatching();

        // Then
        assertEquals(Collections.singletonList("a"), keys(reducer.features()));
    }

    @Test
    @DisplayName("Keeps every rule when no projects are included")
    void keepsEveryRuleWithoutIncludedProjects() {
        // Given
        JsonObject features = json("{'a': {'rules': [{'metadata': {'projects': ['p2']}, 'force': 1}]}}");
        FeatureReducer reducer = reducer(features, NO_FILTER, false);

        // When
        reducer.addMatching();

        // Then
        assertEquals(features, reducer.features());
    }

    @Test
    @DisplayName("Reduces the conditions of kept rules against the known attributes")
    void reducesRuleConditions() {
        // Given
        JsonObject features = json("{'a': {'rules': [{'condition': {'plan': 'pro'}, 'force': 1},"
                + " {'condition': {'plan': 'free'}, 'force': 2}, {'condition': {'region': 'r1'}, 'force': 3}]}}");
        FeatureReducer reducer = new FeatureReducer(features, NO_FILTER, new ConditionReducer(json("{'plan': 'pro'}"), new JsonObject()), false);

        // When
        reducer.addMatching();

        // Then
        JsonObject expected = json("{'a': {'rules': [{'force': 1}, {'condition': {'$not': {}}}, {'condition': {'region': 'r1'}, 'force': 3}]}}");
        expected.getAsJsonObject("a").getAsJsonArray("rules").get(1).getAsJsonObject().add("force", JsonNull.INSTANCE);
        assertEquals(expected, reducer.features());
    }

    @Test
    @DisplayName("Keeps a feature without rules as written")
    void keepsFeatureWithoutRulesAsWritten() {
        // Given
        JsonObject features = json("{'a': {'defaultValue': {'x': 1}, 'rules': 'none'}}");
        FeatureReducer reducer = reducer(features, NO_FILTER, false);

        // When
        reducer.addMatching();

        // Then
        assertEquals(features, reducer.features());
    }

    @Test
    @DisplayName("Keeps no features when there are none")
    void keepsNothingWithoutFeatures() {
        // Given
        FeatureReducer reducer = reducer(new JsonObject(), NO_FILTER, false);

        // When
        reducer.addMatching();
        reducer.addParents(json("{'parentConditions': [{'id': 'a'}]}"));

        // Then
        assertEquals(new JsonObject(), reducer.features());
    }

    @Test
    @DisplayName("Never modifies the features it reduces")
    void neverModifiesFeatures() {
        // Given
        JsonObject features = json("{'a': {'metadata': {'projects': ['p1']}, 'rules': [{'metadata': {'projects': ['p2']}, 'force': 1},"
                + " {'condition': {'plan': 'free'}, 'force': 2}]}}");
        JsonObject before = features.deepCopy();
        FeatureReducer reducer = new FeatureReducer(features, ONLY_P1, new ConditionReducer(json("{'plan': 'pro'}"), new JsonObject()), false);

        // When
        reducer.addMatching();

        // Then
        assertEquals(before, features);
    }

    @Test
    @DisplayName("Follows a chain of ten thousand prerequisites without overflowing the stack")
    void followsLongPrerequisiteChain() {
        // Given
        int length = 10_000;
        JsonObject features = new JsonObject();
        for (int i = 0; i < length; i++) {
            JsonObject feature = new JsonObject();
            if (i < length - 1) {
                feature = json("{'rules': [{'parentConditions': [{'id': 'f" + (i + 1) + "', 'condition': {'value': true}}], 'force': true}]}");
            }
            features.add("f" + i, feature);
        }
        FeatureReducer reducer = reducer(features, NO_FILTER, false);

        // When
        reducer.addMatching();

        // Then
        assertEquals(keys(features), keys(reducer.features()));
    }

    private static FeatureReducer reducer(JsonObject features, PayloadFilter filter, boolean filterPrerequisites) {
        return new FeatureReducer(features, filter, new ConditionReducer(new JsonObject(), new JsonObject()), filterPrerequisites);
    }

    private static List<String> keys(JsonObject object) {
        return new ArrayList<>(object.keySet());
    }

    private static JsonObject json(String value) {
        return JsonParser.parseString(value.replace('\'', '"')).getAsJsonObject();
    }
}
