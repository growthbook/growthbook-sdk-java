package growthbook.sdk.java;

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

import static org.junit.jupiter.api.Assertions.*;

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
            resetErrorOutputStream();

            JsonElement jsonElement = testCases.get(i);
            JsonArray testCase = (JsonArray) jsonElement;
            String testDescription = testCase.get(0).getAsString();

            // Get attributes and conditions as JSON objects then convert them to a JSON string
            String condition = testCase.get(1).getAsJsonObject().toString();
            String attributes = testCase.get(2).getAsJsonObject().toString();
            boolean expected = testCase.get(3).getAsBoolean();

            boolean evaluationResult = evaluator.evaluateCondition(attributes, condition);

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

    @Test
    void test_paddedVersionString_eq() {
        JsonArray testCases = helper.versionCompareTestCases_eq();

        for (int i = 0; i < testCases.size(); i++) {
            JsonArray test = (JsonArray) testCases.get(i);
            String version = test.get(0).getAsString();
            String otherVersion = test.get(1).getAsString();
            Boolean equals = test.get(2).getAsBoolean();

            String paddedVersion = StringUtils.paddedVersionString(version);
            String paddedOther = StringUtils.paddedVersionString(otherVersion);

            assertEquals(paddedVersion.compareTo(paddedOther) == 0, equals);
        }
    }

    @Test
    void test_paddedVersionString_lt() {
        JsonArray testCases = helper.versionCompareTestCases_lt();

        for (int i = 0; i < testCases.size(); i++) {
            JsonArray test = (JsonArray) testCases.get(i);
            String version = test.get(0).getAsString();
            String otherVersion = test.get(1).getAsString();
            Boolean equals = test.get(2).getAsBoolean();

            String paddedVersion = StringUtils.paddedVersionString(version);
            String paddedOther = StringUtils.paddedVersionString(otherVersion);

//            System.out.printf("%s < %s = %s - actual: %s\n",  paddedVersion, paddedOther, equals, paddedVersion.compareTo(paddedOther) < 0);

            assertEquals(paddedVersion.compareTo(paddedOther) < 0, equals);
        }
    }

    @Test
    void test_paddedVersionString_gt() {
        JsonArray testCases = helper.versionCompareTestCases_gt();

        for (int i = 0; i < testCases.size(); i++) {
            JsonArray test = (JsonArray) testCases.get(i);
            String version = test.get(0).getAsString();
            String otherVersion = test.get(1).getAsString();
            Boolean equals = test.get(2).getAsBoolean();

            String paddedVersion = StringUtils.paddedVersionString(version);
            String paddedOther = StringUtils.paddedVersionString(otherVersion);

//            System.out.printf("%s > %s = %s - actual: %s\n",  paddedVersion, paddedOther, equals, paddedVersion.compareTo(paddedOther) > 0);

            assertEquals(paddedVersion.compareTo(paddedOther) > 0, equals);
        }
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
        System.out.println(stacktrace.toString());
        return true;
    }

    private void resetErrorOutputStream() {
        errContent.reset();
    }
}
