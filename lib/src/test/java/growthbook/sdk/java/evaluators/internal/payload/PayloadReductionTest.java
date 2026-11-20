package growthbook.sdk.java.evaluators.internal.payload;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import growthbook.sdk.java.model.PayloadFilters;
import growthbook.sdk.java.model.ReducePayloadOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;

class PayloadReductionTest {

    private static final String FULL_PAYLOAD = "{'status': 200, 'dateUpdated': '2000-01-01T00:00:00Z', 'extra': {'nested': [1, 2]},"
            + " 'features': {"
            + "   'a': {'defaultValue': 0, 'metadata': {'projects': ['p1']}, 'rules': ["
            + "     {'condition': {'plan': 'pro'}, 'force': 1},"
            + "     {'condition': {'plan': 'free'}, 'force': 2},"
            + "     {'condition': {'$savedGroup': {'id': 'g1'}}, 'force': 3},"
            + "     {'contextualBanditRef': 'b1', 'parentConditions': [{'id': 'pre', 'condition': {'value': true}}]}]},"
            + "   'pre': {'defaultValue': true, 'metadata': {'projects': ['p2']}},"
            + "   'other': {'defaultValue': 1, 'metadata': {'projects': ['p2']}, 'rules': [{'condition': {'$savedGroup': {'id': 'g2'}}, 'force': 1}]}},"
            + " 'experiments': ["
            + "   {'key': 'e1', 'metadata': {'projects': ['p1']}, 'condition': {'plan': 'free'}, 'contextualBanditRef': 'b2'},"
            + "   {'key': 'e2', 'metadata': {'projects': ['p2']}, 'contextualBanditRef': 'b3'}],"
            + " 'savedGroups': {"
            + "   'g1': {'type': 'condition', 'condition': {'plan': 'pro', 'region': 'r1'}},"
            + "   'g2': {'type': 'list', 'attributeKey': 'id', 'values': ['v1']}},"
            + " 'contextualBandits': {"
            + "   'b1': {'contexts': [{'id': 'c1', 'condition': {'plan': 'free'}}, {'id': 'c2', 'condition': {'plan': 'pro'}}]},"
            + "   'b2': {'contexts': []},"
            + "   'b3': {'contexts': []}}}";

    private static final JsonObject ATTRIBUTES = json("{'plan': 'pro'}");
    private static final ReducePayloadOptions ONLY_P1 = ReducePayloadOptions.builder()
            .include(PayloadFilters.builder().projects(Collections.singletonList("p1")).build())
            .build();

    @Test
    @DisplayName("Reduces features, experiments, bandits and saved groups together")
    void reducesWholePayload() {
        // Given
        JsonObject payload = json(FULL_PAYLOAD);

        // When
        JsonObject reduced = PayloadReduction.reduce(payload, ATTRIBUTES, ONLY_P1);

        // Then
        JsonObject expected = json("{'status': 200, 'dateUpdated': '2000-01-01T00:00:00Z', 'extra': {'nested': [1, 2]},"
                + " 'features': {"
                + "   'a': {'defaultValue': 0, 'metadata': {'projects': ['p1']}, 'rules': ["
                + "     {'force': 1},"
                + "     {'condition': {'$not': {}}, 'force': null},"
                + "     {'condition': {'$savedGroup': {'id': 'g1'}}, 'force': 3},"
                + "     {'contextualBanditRef': 'b1', 'parentConditions': [{'id': 'pre', 'condition': {'value': true}}]}]},"
                + "   'pre': {'defaultValue': true, 'metadata': {'projects': ['p2']}}},"
                + " 'experiments': ["
                + "   {'key': 'e1', 'metadata': {'projects': ['p1']}, 'condition': {'$not': {}}, 'contextualBanditRef': 'b2'}],"
                + " 'savedGroups': {'g1': {'type': 'condition', 'condition': {'region': 'r1'}}},"
                + " 'contextualBandits': {"
                + "   'b1': {'contexts': [{'id': 'c2', 'condition': {}}]},"
                + "   'b2': {'contexts': []}}}");
        assertEquals(expected, reduced);
    }

    @Test
    @DisplayName("Never modifies the input payload")
    void neverModifiesInput() {
        // Given
        JsonObject payload = json(FULL_PAYLOAD);
        JsonObject before = payload.deepCopy();
        JsonObject attributes = ATTRIBUTES.deepCopy();

        // When
        PayloadReduction.reduce(payload, attributes, ONLY_P1);

        // Then
        assertEquals(before, payload);
        assertEquals(ATTRIBUTES, attributes);
    }

    @Test
    @DisplayName("Keeps the payload's other keys")
    void keepsOtherKeys() {
        // Given
        JsonObject payload = json("{'status': 200, 'dateUpdated': '2000-01-01T00:00:00Z', 'encryptionKey': null, 'custom': {'k': ['v']}}");

        // When
        JsonObject reduced = PayloadReduction.reduce(payload, ATTRIBUTES, new ReducePayloadOptions());

        // Then
        assertEquals(json("{'status': 200, 'dateUpdated': '2000-01-01T00:00:00Z', 'encryptionKey': null, 'custom': {'k': ['v']},"
                + " 'features': {}, 'experiments': [], 'savedGroups': {}}"), reduced);
    }

    @Test
    @DisplayName("Returns empty features, experiments and saved groups for an empty payload")
    void reducesEmptyPayload() {
        // Given
        JsonObject payload = new JsonObject();

        // When
        JsonObject reduced = PayloadReduction.reduce(payload, new JsonObject(), new ReducePayloadOptions());

        // Then
        assertEquals(json("{'features': {}, 'experiments': [], 'savedGroups': {}}"), reduced);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"'features': 'x', 'experiments': {}, 'savedGroups': []", "'features': null, 'experiments': null, 'savedGroups': null"})
    @DisplayName("Treats malformed top-level sections as empty")
    void treatsMalformedSectionsAsEmpty(String sections) {
        // Given
        JsonObject payload = json("{" + sections + "}");

        // When
        JsonObject reduced = PayloadReduction.reduce(payload, new JsonObject(), new ReducePayloadOptions());

        // Then
        assertEquals(json("{'features': {}, 'experiments': [], 'savedGroups': {}}"), reduced);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"{}", "{'contextualBandits': null}", "{'contextualBandits': false}", "{'contextualBandits': 0}", "{'contextualBandits': ''}"})
    @DisplayName("Leaves contextualBandits out when the input has no truthy one")
    void leavesBanditsOutWhenFalsy(String payload) {
        // Given
        JsonObject input = json(payload);

        // When
        JsonObject reduced = PayloadReduction.reduce(input, new JsonObject(), new ReducePayloadOptions());

        // Then
        assertFalse(reduced.has("contextualBandits"));
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"{'contextualBandits': {}}", "{'contextualBandits': {'b1': {'contexts': []}}}"})
    @DisplayName("Keeps contextualBandits when the input has a truthy one, even when none is referenced")
    void keepsBanditsWhenTruthy(String payload) {
        // Given
        JsonObject input = json(payload);

        // When
        JsonObject reduced = PayloadReduction.reduce(input, new JsonObject(), new ReducePayloadOptions());

        // Then
        assertEquals(new JsonObject(), reduced.get("contextualBandits"));
    }

    @Test
    @DisplayName("Keeps everything when no filters are set and nothing is known")
    void keepsEverythingWithoutFiltersOrAttributes() {
        // Given
        JsonObject payload = json("{'features': {'a': {'rules': [{'condition': {'plan': 'pro'}, 'force': 1}]}, 'b': {'defaultValue': 2}},"
                + " 'experiments': [{'key': 'e1', 'condition': {'plan': 'pro'}}],"
                + " 'savedGroups': {'g1': {'type': 'list', 'attributeKey': 'id', 'values': ['v1']}}}");

        // When
        JsonObject reduced = PayloadReduction.reduce(payload, new JsonObject(), new ReducePayloadOptions());

        // Then
        assertEquals(payload.get("features"), reduced.get("features"));
        assertEquals(payload.get("experiments"), reduced.get("experiments"));
        assertEquals(new JsonObject(), reduced.get("savedGroups"));
    }

    @Test
    @DisplayName("Adds the prerequisites of kept experiments after the matching features")
    void addsExperimentPrerequisitesAfterMatchingFeatures() {
        // Given
        JsonObject payload = json("{'features': {'pre': {'defaultValue': true, 'metadata': {'projects': ['p2']}},"
                + " 'a': {'defaultValue': 1, 'metadata': {'projects': ['p1']}}},"
                + " 'experiments': [{'key': 'e1', 'metadata': {'projects': ['p1']}, 'parentConditions': [{'id': 'pre', 'condition': {'value': true}}]}]}");

        // When
        JsonObject reduced = PayloadReduction.reduce(payload, new JsonObject(), ONLY_P1);

        // Then
        assertEquals(Arrays.asList("a", "pre"), new ArrayList<>(reduced.getAsJsonObject("features").keySet()));
    }

    @Test
    @DisplayName("Applies the filters to prerequisites when asked, except for holdouts")
    void filtersPrerequisitesWhenAsked() {
        // Given
        JsonObject payload = json("{'features': {'pre': {'defaultValue': true, 'metadata': {'projects': ['p2']}},"
                + " '$holdout:h1': {'defaultValue': 'holdoutcontrol', 'metadata': {'projects': ['p2']}}},"
                + " 'experiments': [{'key': 'e1', 'metadata': {'projects': ['p1']}, 'parentConditions': [{'id': 'pre'}, {'id': '$holdout:h1'}]}]}");
        ReducePayloadOptions options = ReducePayloadOptions.builder()
                .include(PayloadFilters.builder().projects(Collections.singletonList("p1")).build())
                .prerequisites(ReducePayloadOptions.Prerequisites.FILTER)
                .build();

        // When
        JsonObject reduced = PayloadReduction.reduce(payload, new JsonObject(), options);

        // Then
        assertEquals(Collections.singletonList("$holdout:h1"), new ArrayList<>(reduced.getAsJsonObject("features").keySet()));
    }

    @Test
    @DisplayName("Keeps the saved groups that kept bandits reference")
    void keepsGroupsReferencedByBandits() {
        // Given
        JsonObject payload = json("{'experiments': [{'key': 'e1', 'contextualBanditRef': 'b1'}],"
                + " 'contextualBandits': {'b1': {'contexts': [{'id': 'c1', 'condition': {'id': {'$inGroup': 'g1'}}}]}},"
                + " 'savedGroups': {'g1': {'type': 'list', 'attributeKey': 'id', 'values': ['v1']},"
                + " 'g2': {'type': 'list', 'attributeKey': 'id', 'values': ['v2']}}}");

        // When
        JsonObject reduced = PayloadReduction.reduce(payload, new JsonObject(), new ReducePayloadOptions());

        // Then
        assertEquals(json("{'g1': {'type': 'list', 'attributeKey': 'id', 'values': ['v1']}}"), reduced.get("savedGroups"));
    }

    @Test
    @DisplayName("Returns a result that shares no objects with the input")
    void resultIsIndependentOfInput() {
        // Given
        JsonObject payload = json("{'extra': {'k': 'v'}, 'features': {'a': {'defaultValue': {'x': 1}}}}");

        // When
        JsonObject reduced = PayloadReduction.reduce(payload, new JsonObject(), new ReducePayloadOptions());
        reduced.getAsJsonObject("extra").addProperty("k", "changed");
        reduced.getAsJsonObject("features").getAsJsonObject("a").getAsJsonObject("defaultValue").addProperty("x", 2);

        // Then
        assertEquals(json("{'extra': {'k': 'v'}, 'features': {'a': {'defaultValue': {'x': 1}}}}"), payload);
        assertTrue(reduced.has("savedGroups"));
    }

    private static JsonObject json(String value) {
        return JsonParser.parseString(value.replace('\'', '"')).getAsJsonObject();
    }
}
