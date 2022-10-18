package growthbook.sdk.java.models;

import com.google.gson.JsonArray;
import growthbook.sdk.java.TestHelpers.TestCasesJsonHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
    void test_evaluateCondition() {
        ConditionEvaluator evaluator = new ConditionEvaluator();

        JsonArray testCases = helper.evalConditionTestCases();

        testCases.forEach(jsonElement -> {
            JsonArray testCase = (JsonArray) jsonElement;

            String testDescription = testCase.get(0).getAsString();

            // Get attributes and conditions as JSON objects then convert them to a JSON string
            String attributes = testCase.get(1).getAsJsonObject().toString();
            String condition = testCase.get(2).getAsJsonObject().toString();

            Boolean expected = testCase.get(3).getAsBoolean();

            assertEquals(
                    expected,
                    evaluator.evaluateCondition(attributes, condition),
                    testDescription
            );
        });
    }
}
