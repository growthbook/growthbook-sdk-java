package growthbook.sdk.java.evaluators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import growthbook.sdk.java.model.ReducePayloadOptions;
import growthbook.sdk.java.testhelpers.TestCasesJsonHelper;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class PayloadReducerTest {

    @TestFactory
    @DisplayName("Verify: pruneCondition matches the shared spec cases")
    List<DynamicTest> pruneConditionSpecCases() {
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonElement element : suite("pruneCondition")) {
            JsonArray testCase = element.getAsJsonArray();
            tests.add(DynamicTest.dynamicTest(testCase.get(0).getAsString(), () -> {
                // Given
                JsonObject condition = testCase.get(1).getAsJsonObject();
                JsonObject attributes = testCase.get(2).getAsJsonObject();
                JsonObject savedGroups = testCase.size() > 4 ? testCase.get(4).getAsJsonObject() : null;

                // When
                JsonElement pruned = PayloadReducer.pruneCondition(attributes, condition, savedGroups);

                // Then
                assertEquals(testCase.get(3), pruned);
            }));
        }
        return tests;
    }

    @TestFactory
    @DisplayName("Verify: reducePayload matches the shared spec cases")
    List<DynamicTest> reducePayloadSpecCases() {
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonElement element : suite("reducePayload")) {
            JsonArray testCase = element.getAsJsonArray();
            tests.add(DynamicTest.dynamicTest(testCase.get(0).getAsString(), () -> {
                // Given
                JsonObject input = testCase.get(1).getAsJsonObject();
                JsonObject attributes = mergedAttributes(input);
                ReducePayloadOptions options = GrowthBookJsonUtils.getInstance().gson.fromJson(input, ReducePayloadOptions.class);
                JsonObject payload = input.getAsJsonObject("payload");
                String before = payload.toString();

                // When
                JsonObject reduced = PayloadReducer.reducePayload(payload, attributes, options);

                // Then
                assertEquals(testCase.get(2), reduced);
                assertEquals(before, payload.toString(), "the input payload is not modified");
            }));
        }
        return tests;
    }

    @Test
    @DisplayName("Verify: a malformed condition is returned as written")
    void malformedConditionIsReturnedAsWritten() {
        // Given
        JsonObject condition = parse("{\"$not\": null}");

        // When
        JsonElement pruned = PayloadReducer.pruneCondition(parse("{\"id\": \"1\"}"), condition, null);

        // Then
        assertEquals(condition, pruned);
    }

    @Test
    @DisplayName("Verify: a malformed condition group is kept as written instead of failing the reduction")
    void malformedSavedGroupIsKeptAsWritten() {
        // Given
        JsonObject payload = parse("{\"features\": {\"f\": {\"defaultValue\": false, \"rules\": [{\"condition\": {\"$savedGroup\": {\"id\": \"g\"}}, \"force\": true}]}},"
                + " \"savedGroups\": {\"g\": {\"type\": \"condition\", \"condition\": {\"$not\": null}}}}");

        // When
        JsonObject reduced = PayloadReducer.reducePayload(payload, parse("{\"id\": \"1\"}"), null);

        // Then
        assertEquals(payload.getAsJsonObject("savedGroups"), reduced.getAsJsonObject("savedGroups"));
        assertEquals(payload.getAsJsonObject("features"), reduced.getAsJsonObject("features"));
    }

    @Test
    @DisplayName("Verify: many condition groups sharing one large list group are all kept")
    void manyGroupsSharingALargeList() {
        // Given
        JsonArray values = new JsonArray();
        for (int i = 0; i < 50_000; i++) {
            values.add("user-" + i);
        }
        JsonObject big = new JsonObject();
        big.addProperty("type", "list");
        big.addProperty("attributeKey", "id");
        big.add("values", values);
        JsonObject savedGroups = new JsonObject();
        savedGroups.add("big", big);
        JsonArray rules = new JsonArray();
        for (int i = 0; i < 1_000; i++) {
            savedGroups.add("g" + i, parse("{\"type\": \"condition\", \"condition\": {\"$or\": [{\"$savedGroup\": {\"id\": \"big\"}}, {\"plan\": \"p" + i + "\"}]}}"));
            rules.add(parse("{\"condition\": {\"$savedGroup\": {\"id\": \"g" + i + "\"}}, \"force\": " + i + "}"));
        }
        JsonObject feature = new JsonObject();
        feature.addProperty("defaultValue", -1);
        feature.add("rules", rules);
        JsonObject features = new JsonObject();
        features.add("f", feature);
        JsonObject payload = new JsonObject();
        payload.add("features", features);
        payload.add("savedGroups", savedGroups);

        // When
        JsonObject reduced = PayloadReducer.reducePayload(payload, new JsonObject(), null);

        // Then
        assertEquals(savedGroups, reduced.getAsJsonObject("savedGroups"));
        assertEquals(features, reduced.getAsJsonObject("features"));
    }

    @Test
    @DisplayName("Verify: condition groups on a cycle, including one that references itself, are kept as written")
    void groupsOnCyclesAreKeptAsWritten() {
        // Given
        JsonObject payload = parse("{\"features\": {\"f\": {\"defaultValue\": false, \"rules\": ["
                + "{\"condition\": {\"$savedGroup\": {\"id\": \"b\"}}, \"force\": true},"
                + "{\"condition\": {\"$savedGroup\": {\"id\": \"self\"}}, \"force\": true},"
                + "{\"condition\": {\"$savedGroup\": {\"id\": \"plain\"}}, \"force\": true}]}},"
                + " \"savedGroups\": {"
                + "\"a\": {\"type\": \"condition\", \"condition\": {\"$or\": [{\"$savedGroup\": {\"id\": \"b\"}}, {\"x\": true}], \"y\": true}},"
                + "\"b\": {\"type\": \"condition\", \"condition\": {\"$not\": {\"$savedGroup\": {\"id\": \"a\"}}}},"
                + "\"self\": {\"type\": \"condition\", \"condition\": {\"$or\": [{\"$savedGroup\": {\"id\": \"self\"}}, {\"x\": true}], \"y\": true}},"
                + "\"plain\": {\"type\": \"condition\", \"condition\": {\"x\": true, \"y\": true}}}}");

        // When
        JsonObject reduced = PayloadReducer.reducePayload(payload, parse("{\"y\": true}"), null);

        // Then
        JsonObject groups = reduced.getAsJsonObject("savedGroups");
        assertEquals(payload.getAsJsonObject("savedGroups").get("a"), groups.get("a"));
        assertEquals(payload.getAsJsonObject("savedGroups").get("b"), groups.get("b"));
        assertEquals(payload.getAsJsonObject("savedGroups").get("self"), groups.get("self"));
        assertEquals(parse("{\"type\": \"condition\", \"condition\": {\"x\": true}}"), groups.get("plain"));
    }

    @Test
    @DisplayName("Verify: a list group's values never reference other groups")
    void listValuesAreNotGroupReferences() {
        // Given: list values that happen to name groups, including the group that uses the list
        JsonObject payload = parse("{\"features\": {\"f\": {\"defaultValue\": false, \"rules\": ["
                + "{\"condition\": {\"$savedGroup\": {\"id\": \"g\"}}, \"force\": true},"
                + "{\"condition\": {\"items\": {\"$elemMatch\": {\"$inGroup\": \"elem\"}}}, \"force\": true},"
                + "{\"condition\": {\"id\": {\"$inGroup\": \"legacy\"}}, \"force\": true}]}},"
                + " \"savedGroups\": {"
                + "\"g\": {\"type\": \"condition\", \"condition\": {\"$or\": [{\"id\": {\"$inGroup\": \"list\"}}, {\"x\": true}], \"y\": true}},"
                + "\"list\": {\"type\": \"list\", \"attributeKey\": \"id\", \"values\": [\"g\", \"h\"]},"
                + "\"elem\": {\"type\": \"list\", \"attributeKey\": \"id\", \"values\": [\"e\"]},"
                + "\"legacy\": [\"l\"],"
                + "\"h\": {\"type\": \"condition\", \"condition\": {\"x\": true}},"
                + "\"e\": {\"type\": \"condition\", \"condition\": {\"y\": true}},"
                + "\"l\": {\"type\": \"condition\", \"condition\": {\"y\": true}}}}");

        // When
        JsonObject reduced = PayloadReducer.reducePayload(payload, parse("{\"y\": true}"), null);

        // Then: g isn't on a cycle, so it's reduced, and groups only named in list values go
        JsonObject groups = payload.getAsJsonObject("savedGroups");
        JsonObject expected = new JsonObject();
        expected.add("g", parse("{\"type\": \"condition\", \"condition\": {\"$or\": [{\"id\": {\"$inGroup\": \"list\"}}, {\"x\": true}]}}"));
        expected.add("list", groups.get("list"));
        expected.add("elem", groups.get("elem"));
        expected.add("legacy", groups.get("legacy"));
        assertEquals(expected, reduced.getAsJsonObject("savedGroups"));
    }

    @Test
    @DisplayName("Verify: a payload without saved groups reduces to an empty savedGroups object")
    void payloadWithoutSavedGroups() {
        // Given
        JsonObject payload = parse("{\"features\": {\"g\": {\"defaultValue\": \"g\", \"rules\": [{\"condition\": {\"id\": {\"$inGroup\": \"g\"}}, \"force\": \"g\"}]}}}");

        // When
        JsonObject reduced = PayloadReducer.reducePayload(payload, new JsonObject(), null);

        // Then
        assertEquals(new JsonObject(), reduced.getAsJsonObject("savedGroups"));
        assertEquals(payload.getAsJsonObject("features"), reduced.getAsJsonObject("features"));
    }

    @Test
    @DisplayName("Verify: contextualBandits is left out of the result when the payload has none")
    void contextualBanditsLeftOutWhenAbsent() {
        // Given
        String features = "\"features\": {\"f\": {\"defaultValue\": 0, \"rules\": [{\"contextualBanditRef\": \"b\"}]}}";
        String bandit = "{\"contexts\": []}";

        // When
        JsonObject absent = PayloadReducer.reducePayload(parse("{" + features + "}"), null, null);
        JsonObject nullBandits = PayloadReducer.reducePayload(parse("{" + features + ", \"contextualBandits\": null}"), null, null);
        JsonObject emptyBandits = PayloadReducer.reducePayload(parse("{" + features + ", \"contextualBandits\": {}}"), null, null);
        JsonObject withBandit = PayloadReducer.reducePayload(parse("{" + features + ", \"contextualBandits\": {\"b\": " + bandit + "}}"), null, null);

        // Then
        assertTrue(!absent.has("contextualBandits"));
        assertTrue(!nullBandits.has("contextualBandits"));
        assertEquals(new JsonObject(), emptyBandits.get("contextualBandits"));
        assertEquals(parse("{\"b\": " + bandit + "}"), withBandit.get("contextualBandits"));
    }

    @Test
    @DisplayName("Verify: a long chain of group references doesn't overflow the stack")
    void longChainOfGroupReferences() {
        // Given
        int length = 20_000;
        JsonObject savedGroups = new JsonObject();
        for (int i = 0; i < length; i++) {
            String next = i + 1 < length ? "{\"$savedGroup\": {\"id\": \"c" + (i + 1) + "\"}}" : "{\"x\": 1}";
            savedGroups.add("c" + i, parse("{\"type\": \"condition\", \"condition\": " + next + "}"));
        }
        JsonObject payload = parse("{\"features\": {\"f\": {\"defaultValue\": false, \"rules\": ["
                + "{\"condition\": {\"items\": {\"$elemMatch\": {\"$savedGroup\": {\"id\": \"c0\"}}}}, \"force\": true}]}}}");
        payload.add("savedGroups", savedGroups);

        // When
        JsonObject reduced = PayloadReducer.reducePayload(payload, new JsonObject(), null);

        // Then
        assertEquals(savedGroups, reduced.getAsJsonObject("savedGroups"));
    }

    @Test
    @DisplayName("Verify: conditions that match like JS are removed and the rule's force kept")
    void matchingConditionsAreRemoved() {
        // Given: a logical $elemMatch on nested arrays, a numeric version written with an
        // exponent, and a case-insensitive regex on non-ASCII text, all of which match in JS
        String[][] cases = {
                {"{\"x\": [[1]]}", "{\"x\": {\"$elemMatch\": {\"$or\": [{\"0\": 1}]}}}"},
                {"{\"x\": 1e3}", "{\"x\": {\"$veq\": 1000}}"},
                {"{\"x\": \"\u00c9\"}", "{\"x\": {\"$regexi\": \"^\u00e9$\"}}"},
                {"{\"x\": 5.9604644775390625e-8}", "{\"x\": {\"$vgt\": 5.960464477539062e-8}}"},
        };
        for (String[] c : cases) {
            JsonObject payload = parse("{\"features\": {\"f\": {\"defaultValue\": false, \"rules\": [{\"force\": true, \"condition\": " + c[1] + "}]}}}");

            // When
            JsonObject reduced = PayloadReducer.reducePayload(payload, parse(c[0]), null);

            // Then
            assertEquals(parse("{\"defaultValue\": false, \"rules\": [{\"force\": true}]}"),
                    reduced.getAsJsonObject("features").get("f"), c[1]);
        }
    }

    @Test
    @DisplayName("Verify: conditions that fail like JS disable the rule")
    void failingConditionsDisableTheRule() {
        // Given: a path with a trailing empty segment, which reads key "" of element 1 in JS
        String[][] cases = {
                {"{\"x\": [[1]]}", "{\"x\": {\"$elemMatch\": {\"$or\": [{\"0.\": 1}]}}}"},
        };
        for (String[] c : cases) {
            JsonObject payload = parse("{\"features\": {\"f\": {\"defaultValue\": false, \"rules\": [{\"force\": true, \"condition\": " + c[1] + "}]}}}");

            // When
            JsonObject reduced = PayloadReducer.reducePayload(payload, parse(c[0]), null);

            // Then
            assertEquals(parse("{\"defaultValue\": false, \"rules\": [{\"force\": null, \"condition\": {\"$not\": {}}}]}"),
                    reduced.getAsJsonObject("features").get("f"), c[1]);
        }
    }

    @Test
    @DisplayName("Verify: null attributes and options mean nothing is known and nothing is filtered")
    void nullAttributesAndOptions() {
        // Given
        JsonObject payload = parse("{\"features\": {\"f\": {\"defaultValue\": 0, \"rules\": [{\"condition\": {\"id\": \"1\"}, \"force\": 1}]}}}");

        // When
        JsonObject reduced = PayloadReducer.reducePayload(payload, null, null);
        JsonElement pruned = PayloadReducer.pruneCondition(null, parse("{\"id\": \"1\"}"), null);

        // Then
        assertEquals(parse("{\"features\": {\"f\": {\"defaultValue\": 0, \"rules\": [{\"condition\": {\"id\": \"1\"}, \"force\": 1}]}},"
                + " \"experiments\": [], \"savedGroups\": {}}"), reduced);
        assertEquals(parse("{\"id\": \"1\"}"), pruned);
    }

    @Test
    @DisplayName("Verify: the reduced payload shares no objects with the input")
    void reducedPayloadIsIndependentOfInput() {
        // Given
        JsonObject payload = parse("{\"features\": {\"f\": {\"defaultValue\": {\"a\": 1}}}}");

        // When
        JsonObject reduced = PayloadReducer.reducePayload(payload, new JsonObject(), null);
        reduced.getAsJsonObject("features").getAsJsonObject("f").getAsJsonObject("defaultValue").add("b", new JsonPrimitive(2));

        // Then
        assertNotSame(payload.get("features"), reduced.get("features"));
        assertTrue(!payload.getAsJsonObject("features").getAsJsonObject("f").getAsJsonObject("defaultValue").has("b"));
    }

    private static JsonArray suite(String name) {
        return TestCasesJsonHelper.getInstance().getTestCases().getAsJsonArray(name);
    }

    /**
     * Global attributes overlaid by the user's, as the spec's client does.
     */
    private static JsonObject mergedAttributes(JsonObject input) {
        JsonObject merged = new JsonObject();
        for (String key : new String[]{"globalAttributes", "attributes"}) {
            JsonElement attributes = input.get(key);
            if (attributes != null && attributes.isJsonObject()) {
                for (Map.Entry<String, JsonElement> entry : attributes.getAsJsonObject().entrySet()) {
                    merged.add(entry.getKey(), entry.getValue());
                }
            }
        }
        return merged;
    }

    private static JsonObject parse(String json) {
        return GrowthBookJsonUtils.getInstance().gson.fromJson(json, JsonObject.class);
    }
}
