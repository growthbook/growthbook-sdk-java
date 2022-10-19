package growthbook.sdk.java.services;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import growthbook.sdk.java.TestHelpers.TestCasesJsonHelper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ConditionEvaluatorTest {

    TestCasesJsonHelper helper = TestCasesJsonHelper.getInstance();

    @Test
    void test_evaluateCondition_returnsFalseIfWrongShape() {
        ConditionEvaluator evaluator = new ConditionEvaluator();

        String attributes = "{\"name\": \"world\"}";
        String condition = "[\"$not\": { \"name\": \"hello\" }]";

        assertFalse(evaluator.evaluateCondition(attributes, condition));
    }

//    @Test
//    void test_evaluateCondition() {
//        ConditionEvaluator evaluator = new ConditionEvaluator();
//
//        JsonArray testCases = helper.evalConditionTestCases();
//
//        testCases.forEach(jsonElement -> {
//            JsonArray testCase = (JsonArray) jsonElement;
//
//            String testDescription = testCase.get(0).getAsString();
//
//            // Get attributes and conditions as JSON objects then convert them to a JSON string
//            String attributes = testCase.get(1).getAsJsonObject().toString();
//            String condition = testCase.get(2).getAsJsonObject().toString();
//
//            Boolean expected = testCase.get(3).getAsBoolean();
//
//            assertEquals(
//                    expected,
//                    evaluator.evaluateCondition(attributes, condition),
//                    testDescription
//            );
//        });
//    }

    @Test
    void test_isOperator() {
        ConditionEvaluator evaluator = new ConditionEvaluator();

        JsonObject attributes = GrowthBookJsonUtils.getInstance().gson
                .fromJson("{\"name\": \"world\"}", JsonObject.class);
        JsonObject condition = GrowthBookJsonUtils.getInstance().gson
                .fromJson("{\"$not\": { \"name\": \"hello\" }}", JsonObject.class);

        assertTrue(evaluator.isOperator(condition));
        assertFalse(evaluator.isOperator(attributes));
    }

    @Test
    void test_getPath() {
        ConditionEvaluator evaluator = new ConditionEvaluator();

        JsonElement attributes = GrowthBookJsonUtils.getInstance().gson
                .fromJson("{ \"name\": \"sarah\", \"job\": { \"title\": \"developer\" } }", JsonElement.class);

        assertEquals("sarah", ((JsonElement) evaluator.getPath(attributes, "name")).getAsString());
        assertEquals("developer", ((JsonElement) evaluator.getPath(attributes, "job.title")).getAsString());
        assertNull(evaluator.getPath(attributes, "job.company"));
    }

    @Test
    void test_getType() {
        ConditionEvaluator evaluator = new ConditionEvaluator();

        Gson gson = GrowthBookJsonUtils.getInstance().gson;

        assertEquals(ConditionEvaluator.DataType.NULL, evaluator.getType(gson.fromJson("null", JsonElement.class)));
        assertEquals(ConditionEvaluator.DataType.ARRAY, evaluator.getType(gson.fromJson("[1]", JsonElement.class)));
        assertEquals(ConditionEvaluator.DataType.OBJECT, evaluator.getType(gson.fromJson("{ \"foo\": 2}", JsonElement.class)));
        assertEquals(ConditionEvaluator.DataType.BOOLEAN, evaluator.getType(gson.fromJson("true", JsonElement.class)));
        assertEquals(ConditionEvaluator.DataType.NUMBER, evaluator.getType(gson.fromJson("1337", JsonElement.class)));
        assertEquals(ConditionEvaluator.DataType.STRING, evaluator.getType(gson.fromJson("\"hello\"", JsonElement.class)));
    }

    @Test
    void test_Operator_fromString() {
        assertNull(ConditionEvaluator.Operator.fromString("$foo"));
        assertEquals(ConditionEvaluator.Operator.NIN, ConditionEvaluator.Operator.fromString("$nin"));
    }

}
