package growthbook.sdk.java;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import growthbook.sdk.java.testhelpers.TestCasesJsonHelper;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;

class ConditionEvaluatorTest {

    final TestCasesJsonHelper helper = TestCasesJsonHelper.getInstance();
    final PrintStream originalErrorOutputStream = System.err;
    final ByteArrayOutputStream errContent = new ByteArrayOutputStream();

    static final String[] expectedExceptionStrings = {
            "Expected BEGIN_ARRAY but was NUMBER at path $",
            "java.util.regex.PatternSyntaxException: Dangling meta character '?' near index 3"
    };

    @BeforeEach
    public void setUpErrorStream() {
        System.setErr(new PrintStream(errContent));
    }

    @AfterEach
    public void restoreErrorStreams() {
        System.setErr(originalErrorOutputStream);
    }

    @Test
    void test_evaluateCondition_testCases() {
        ArrayList<String> passedTests = new ArrayList<>();
        ArrayList<String> failedTests = new ArrayList<>();

        ArrayList<Integer> failingIndexes = new ArrayList<>();

        ConditionEvaluator evaluator = new ConditionEvaluator();

        JsonArray testCases = helper.evalConditionTestCases();

        for (int i = 0; i < testCases.size(); i++) {
            resetErrorOutputStream();

            JsonElement jsonElement = testCases.get(i);
            JsonArray testCase = (JsonArray) jsonElement;
            String testDescription = testCase.get(0).getAsString();

            // Get attributes and conditions as JSON objects then convert them to a JSON string
            String condition = testCase.get(1).getAsJsonObject().toString();
            String attributes = testCase.get(2).getAsJsonObject().toString();
            boolean expected = testCase.get(3).getAsBoolean();
            JsonObject savedGroups = null;
            if (testCase.size() > 4) {
                savedGroups = testCase.get(4).getAsJsonObject();
            }

            JsonObject attributesJson = GrowthBookJsonUtils.getInstance().gson.fromJson(attributes, JsonObject.class);
            JsonObject conditionJson = GrowthBookJsonUtils.getInstance().gson.fromJson(condition, JsonObject.class);

            boolean evaluationResult = evaluator.evaluateCondition(attributesJson, conditionJson, savedGroups);

            if (unexpectedExceptionOccurred(errContent.toString())) {
                failingIndexes.add(i);
                failedTests.add(String.format("Unexpected Exception: %s", testDescription));
                continue;
            }

            if (expected == evaluationResult) {
                passedTests.add(testDescription);
            } else {
                failingIndexes.add(i);
                failedTests.add(testDescription);
            }
        }

        System.out.printf("\n\n\nFailed tests = %s / %s . Failing = %s", failedTests.size(), testCases.size(), failedTests);
        System.out.printf("\n\n\nFailing indexes = %s", failingIndexes);

        assertEquals(0, failedTests.size(), "There are failing tests");
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

    private boolean unexpectedExceptionOccurred(String stacktrace) {
        if (stacktrace.isEmpty()) {
            return false;
        }
        for (String expectedExceptionSubString : expectedExceptionStrings) {
            if (stacktrace.contains(expectedExceptionSubString)) {
                return false;
            }
        }
        System.out.println(stacktrace);
        return true;
    }

    private void resetErrorOutputStream() {
        errContent.reset();
    }
}
