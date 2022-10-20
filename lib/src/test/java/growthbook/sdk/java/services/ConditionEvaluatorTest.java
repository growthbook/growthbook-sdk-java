package growthbook.sdk.java.services;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import growthbook.sdk.java.TestHelpers.TestCasesJsonHelper;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;

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

    @Test
    void test_evaluateCondition_testCases() {
        ArrayList<String> passedTests = new ArrayList<>();
        ArrayList<String> failedTests = new ArrayList<>();

        ArrayList<Integer> failingIndexes = new ArrayList<>();

        ConditionEvaluator evaluator = new ConditionEvaluator();

        JsonArray testCases = helper.evalConditionTestCases();

        for (int i = 0; i < testCases.size(); i++) {
            // Run only test at index i
//            if (i > 15) break;

            // Failing $regex=invalid fail, $elemMatch, $all
            // Failing: [17, 19, 57, 67, 70, 76]
            // Failing: 17 elemMatch, 19 missing attribute

            // Run only test at index i
//            if (i == 57) {
                JsonElement jsonElement = testCases.get(i);
                JsonArray testCase = (JsonArray) jsonElement;

                String testDescription = testCase.get(0).getAsString();

                // Get attributes and conditions as JSON objects then convert them to a JSON string
                String condition = testCase.get(1).getAsJsonObject().toString();
                String attributes = testCase.get(2).getAsJsonObject().toString();

                boolean expected = testCase.get(3).getAsBoolean();

                if (expected == evaluator.evaluateCondition(attributes, condition)) {
                    passedTests.add(testDescription);
                } else {
                    failingIndexes.add(i);
                    failedTests.add(testDescription);
                }

//            }

        }

//        System.out.printf("\n\nPassed tests: %s", passedTests);
        System.out.printf("\n\n\nFailed tests = %s / %s . Failing = %s", failedTests.size(), testCases.size(), failedTests);
        System.out.printf("\n\n\nFailing indexes = %s", failingIndexes);

        assertEquals(0, failedTests.size(), "There are failing tests");
    }

    @Test
    void test_evalCondition_partialTests() {
        ConditionEvaluator evaluator = new ConditionEvaluator();

        // TODO: Make sure this passes
//        assertTrue(evaluator.evaluateCondition("0", "{\"$gt\": 10}"));
    }

    @Test
    void test_isOperator() {
        ConditionEvaluator evaluator = new ConditionEvaluator();

        JsonObject attributes = GrowthBookJsonUtils.getInstance().gson
                .fromJson("{\"name\": \"world\"}", JsonObject.class);
        JsonObject condition = GrowthBookJsonUtils.getInstance().gson
                .fromJson("{\"$not\": { \"name\": \"hello\" }}", JsonObject.class);

        assertTrue(evaluator.isOperatorObject(condition));
        assertFalse(evaluator.isOperatorObject(attributes));
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

//    @Test
//    void test_evalOperatorCondition() {
//        ConditionEvaluator evaluator = new ConditionEvaluator();
//        Gson gson = GrowthBookJsonUtils.getInstance().gson;
//
//
////        JsonElement element = gson.fromJson("{ \"foo\": 2}", JsonElement.class);
////        assertNull(element.toString());
////        System.out.printf(ConditionEvaluator.Operator.ALL.toString());
////        evaluator.evalOperatorCondition(ConditionEvaluator.Operator.ALL, null, null);
//    }
}
