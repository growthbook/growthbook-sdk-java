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
