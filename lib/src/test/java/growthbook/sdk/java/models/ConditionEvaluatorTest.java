package growthbook.sdk.java.models;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import growthbook.sdk.java.TestHelpers.TestCasesJsonHelper;
import growthbook.sdk.java.services.GrowthBookJsonUtils;
import org.junit.jupiter.api.Test;

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
}
