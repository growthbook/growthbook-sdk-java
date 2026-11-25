package growthbook.sdk.java.evaluators.internal.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;

class BanditReducerTest {

    private static final ConditionReducer CONDITIONS = new ConditionReducer(json("{'plan': 'pro'}"), new JsonObject());

    @Test
    @DisplayName("Keeps only the bandits that kept rules and experiments reference")
    void keepsOnlyReferencedBandits() {
        // Given
        JsonObject bandits = json("{'b1': {'contexts': []}, 'b2': {'contexts': []}, 'b3': {'contexts': []}}");
        JsonObject features = json("{'f1': {'rules': [{'force': 1}, {'contextualBanditRef': 'b2'}]}, 'f2': {'defaultValue': 1}}");
        JsonArray experiments = array("[{'key': 'e1', 'contextualBanditRef': 'b1'}, {'key': 'e2'}]");

        // When
        JsonObject reduced = new BanditReducer(bandits, CONDITIONS).reduce(features, experiments);

        // Then
        assertEquals(json("{'b2': {'contexts': []}, 'b1': {'contexts': []}}"), reduced);
        assertEquals(Arrays.asList("b2", "b1"), new ArrayList<>(reduced.keySet()));
    }

    @Test
    @DisplayName("Ignores references that are empty, not strings or to missing bandits")
    void ignoresUnusableReferences() {
        // Given
        JsonObject bandits = json("{'b1': {'contexts': []}}");
        JsonObject features = json("{'f1': {'rules': [{'contextualBanditRef': ''}, {'contextualBanditRef': 3}, {'contextualBanditRef': 'missing'}, 'b1']}}");
        JsonArray experiments = array("[{'contextualBanditRef': null}, 'b1']");

        // When
        JsonObject reduced = new BanditReducer(bandits, CONDITIONS).reduce(features, experiments);

        // Then
        assertEquals(new JsonObject(), reduced);
    }

    @Test
    @DisplayName("Keeps no bandits when there are none")
    void keepsNothingWithoutBandits() {
        // Given
        JsonObject features = json("{'f1': {'rules': [{'contextualBanditRef': 'b1'}]}}");

        // When
        JsonObject reduced = new BanditReducer(new JsonObject(), CONDITIONS).reduce(features, new JsonArray());

        // Then
        assertEquals(new JsonObject(), reduced);
    }

    @Test
    @DisplayName("Keeps a bandit referenced several times once")
    void keepsSharedBanditOnce() {
        // Given
        JsonObject bandits = json("{'b1': {'contexts': [{'id': 'c1', 'condition': {'plan': 'free'}}]}}");
        JsonObject features = json("{'f1': {'rules': [{'contextualBanditRef': 'b1'}]}, 'f2': {'rules': [{'contextualBanditRef': 'b1'}]}}");
        JsonArray experiments = array("[{'contextualBanditRef': 'b1'}]");

        // When
        JsonObject reduced = new BanditReducer(bandits, CONDITIONS).reduce(features, experiments);

        // Then
        assertEquals(json("{'b1': {'contexts': []}}"), reduced);
    }

    @Test
    @DisplayName("Drops contexts that never pass, clears passing ones and prunes undecided ones")
    void reducesContexts() {
        // Given
        JsonObject bandits = json("{'b1': {'weights': [0.5, 0.5], 'contexts': ["
                + "{'id': 'never', 'condition': {'plan': 'free'}, 'arms': [0]},"
                + " {'id': 'always', 'condition': {'plan': 'pro'}, 'arms': [1]},"
                + " {'id': 'maybe', 'condition': {'plan': 'pro', 'region': 'r1'}, 'arms': [0, 1]},"
                + " {'id': 'unconditional', 'arms': [1, 0]}]}}");
        JsonArray experiments = array("[{'contextualBanditRef': 'b1'}]");

        // When
        JsonObject reduced = new BanditReducer(bandits, CONDITIONS).reduce(new JsonObject(), experiments);

        // Then
        assertEquals(json("{'b1': {'weights': [0.5, 0.5], 'contexts': ["
                + "{'id': 'always', 'condition': {}, 'arms': [1]},"
                + " {'id': 'maybe', 'condition': {'region': 'r1'}, 'arms': [0, 1]},"
                + " {'id': 'unconditional', 'condition': {}, 'arms': [1, 0]}]}}"), reduced);
    }

    @Test
    @DisplayName("Keeps a bandit definition that is not an object as is")
    void keepsNonObjectBanditAsIs() {
        // Given
        JsonObject bandits = json("{'b1': 'legacy', 'b2': [1, 2]}");
        JsonArray experiments = array("[{'contextualBanditRef': 'b1'}, {'contextualBanditRef': 'b2'}]");

        // When
        JsonObject reduced = new BanditReducer(bandits, CONDITIONS).reduce(new JsonObject(), experiments);

        // Then
        assertEquals(bandits, reduced);
    }

    @Test
    @DisplayName("Never modifies the bandits it reduces")
    void neverModifiesBandits() {
        // Given
        JsonObject bandits = json("{'b1': {'contexts': [{'id': 'c1', 'condition': {'plan': 'free'}}, {'id': 'c2', 'condition': {'plan': 'pro'}}]}}");
        JsonObject before = bandits.deepCopy();
        JsonArray experiments = array("[{'contextualBanditRef': 'b1'}]");

        // When
        new BanditReducer(bandits, CONDITIONS).reduce(new JsonObject(), experiments);

        // Then
        assertEquals(before, bandits);
    }

    @Test
    @DisplayName("Keeps a context that isn't an object as is instead of failing the reduction")
    void keepsNonObjectContextAsIs() {
        // Given
        JsonObject bandits = json("{'b1': {'contexts': ['x', null, {'condition': {'plan': 'free'}}, {'condition': {'plan': 'pro'}}]}}");
        JsonArray experiments = array("[{'contextualBanditRef': 'b1'}]");

        // When
        JsonObject reduced = new BanditReducer(bandits, CONDITIONS).reduce(new JsonObject(), experiments);

        // Then
        assertEquals(json("{'b1': {'contexts': ['x', null, {'condition': {}}]}}"), reduced);
    }

    private static JsonArray array(String value) {
        return JsonParser.parseString(value.replace('\'', '"')).getAsJsonArray();
    }

    private static JsonObject json(String value) {
        return JsonParser.parseString(value.replace('\'', '"')).getAsJsonObject();
    }
}
