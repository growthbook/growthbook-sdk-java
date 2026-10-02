package growthbook.sdk.java;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import growthbook.sdk.java.evaluators.ConditionEvaluator;
import growthbook.sdk.java.testhelpers.TestCasesJsonHelper;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import growthbook.sdk.java.util.GrowthBookJsonUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Objects;

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
    void test_notRegexOperatorsPassForMissingAttributes() {
        ConditionEvaluator evaluator = new ConditionEvaluator();

        JsonObject attributes = GrowthBookJsonUtils.getInstance().gson
                .fromJson("{}", JsonObject.class);
        JsonObject notRegexCondition = GrowthBookJsonUtils.getInstance().gson
                .fromJson("{\"userAgent\":{\"$notRegex\":\"(Mobile|Tablet)\"}}", JsonObject.class);
        JsonObject notRegexICondition = GrowthBookJsonUtils.getInstance().gson
                .fromJson("{\"userAgent\":{\"$notRegexi\":\"(mobile|tablet)\"}}", JsonObject.class);

        assertTrue(evaluator.evaluateCondition(attributes, notRegexCondition, null));
        assertTrue(evaluator.evaluateCondition(attributes, notRegexICondition, null));
    }

    @Test
    @DisplayName("$eq with a non-numeric value against a numeric attribute is false, not an error")
    void test_equalOperatorWithMismatchedTypes() {
        // Reading the condition value as a double used to throw NumberFormatException, which
        // escaped to evaluateCondition's catch and made the whole condition false — so a wrapping
        // $not could never invert it. test-cases.json has no $eq type-mismatch case to catch this.
        ConditionEvaluator evaluator = new ConditionEvaluator();
        JsonObject numericAttribute = parse("{\"a\":1}");

        assertFalse(evaluator.evaluateCondition(numericAttribute, parse("{\"a\":{\"$eq\":\"abc\"}}"), null));
        assertTrue(evaluator.evaluateCondition(numericAttribute, parse("{\"a\":{\"$not\":{\"$eq\":\"abc\"}}}"), null));

        JsonObject numericArray = parse("{\"a\":[1,2]}");
        assertFalse(evaluator.evaluateCondition(numericArray, parse("{\"a\":{\"$elemMatch\":{\"$eq\":\"abc\"}}}"), null));
        assertTrue(evaluator.evaluateCondition(
                numericArray, parse("{\"a\":{\"$not\":{\"$elemMatch\":{\"$eq\":\"abc\"}}}}"), null));
    }

    @Test
    @DisplayName("$eq keeps comparing numeric strings by value")
    void test_equalOperatorStillParsesNumericStrings() {
        // Guards the fix above from being tightened into a strict type check, which would change
        // which users match targeting rules.
        ConditionEvaluator evaluator = new ConditionEvaluator();

        assertTrue(evaluator.evaluateCondition(parse("{\"a\":1}"), parse("{\"a\":{\"$eq\":\"1\"}}"), null));
        assertTrue(evaluator.evaluateCondition(parse("{\"a\":\"1\"}"), parse("{\"a\":{\"$eq\":1}}"), null));
        assertTrue(evaluator.evaluateCondition(parse("{\"a\":1}"), parse("{\"a\":{\"$eq\":1}}"), null));
        assertFalse(evaluator.evaluateCondition(parse("{\"a\":1}"), parse("{\"a\":{\"$eq\":2}}"), null));
    }

    @Test
    @DisplayName("$eq compares numbers beyond float precision exactly")
    void test_equalOperatorKeepsDoublePrecision() {
        ConditionEvaluator evaluator = new ConditionEvaluator();

        assertFalse(evaluator.evaluateCondition(
                parse("{\"a\":16777217}"), parse("{\"a\":{\"$eq\":16777216}}"), null));
        assertTrue(evaluator.evaluateCondition(
                parse("{\"a\":16777217}"), parse("{\"a\":{\"$eq\":16777217}}"), null));
    }

    @Test
    @DisplayName("public ConditionEvaluator signatures stay binary compatible with 0.11.0")
    void test_publicSignaturesAreBinaryCompatible() throws Exception {
        // A return type is part of the JVM method descriptor, so narrowing one here is a
        // NoSuchMethodError for callers compiled against 0.11.0 — and it compiles cleanly, so
        // nothing else catches it. Reflection reads the descriptors the JVM actually links against.
        assertEquals(Boolean.class,
                ConditionEvaluator.class.getMethod("isOperatorObject", JsonElement.class).getReturnType());
        assertEquals(Object.class,
                ConditionEvaluator.class.getMethod("getPath", JsonElement.class, String.class).getReturnType());
    }

    @Test
    @DisplayName("object and array conditions match structurally, ignoring key order")
    void test_objectEqualityIgnoresKeyOrder() {
        // Changed from comparing serialized JSON (key-order sensitive) to JsonElement.equals.
        // Note this diverges from the JS SDK, which compares JSON.stringify output; pinned here so
        // the divergence is a recorded decision rather than an accident of refactoring.
        ConditionEvaluator evaluator = new ConditionEvaluator();

        assertTrue(evaluator.evaluateCondition(
                parse("{\"a\":{\"x\":1,\"y\":2}}"), parse("{\"a\":{\"y\":2,\"x\":1}}"), null));
        assertFalse(evaluator.evaluateCondition(
                parse("{\"a\":{\"x\":1}}"), parse("{\"a\":{\"x\":2}}"), null));
        assertTrue(evaluator.evaluateCondition(
                parse("{\"a\":[1,2]}"), parse("{\"a\":[1,2]}"), null));
        // Arrays stay order sensitive — only object keys are unordered.
        assertFalse(evaluator.evaluateCondition(
                parse("{\"a\":[1,2]}"), parse("{\"a\":[2,1]}"), null));
    }

    @Test
    @DisplayName("implicit equality coerces to string, matching the JS SDK")
    void test_implicitEqualityCoercesToString() {
        // The JS SDK compares `value + "" === condition` for string conditions, so a numeric or
        // boolean attribute matches its string form. The previous type-strict comparison diverged.
        ConditionEvaluator evaluator = new ConditionEvaluator();

        assertTrue(evaluator.evaluateCondition(parse("{\"a\":123}"), parse("{\"a\":\"123\"}"), null));
        assertTrue(evaluator.evaluateCondition(parse("{\"a\":true}"), parse("{\"a\":\"true\"}"), null));
        assertFalse(evaluator.evaluateCondition(parse("{\"a\":12}"), parse("{\"a\":\"123\"}"), null));
        assertFalse(evaluator.evaluateCondition(parse("{}"), parse("{\"a\":\"123\"}"), null));
    }

    private static JsonObject parse(String json) {
        return GrowthBookJsonUtils.getInstance().gson.fromJson(json, JsonObject.class);
    }

    @Test
    @DisplayName("$ne treats missing/null attributes as not equal to a value, but equal to null")
    void test_notEqualOperatorForMissingOrNullAttributes() {
        ConditionEvaluator evaluator = new ConditionEvaluator();

        JsonObject neCondition = GrowthBookJsonUtils.getInstance().gson
                .fromJson("{\"level\":{\"$ne\":\"senior\"}}", JsonObject.class);

        assertTrue(evaluator.evaluateCondition(
                GrowthBookJsonUtils.getInstance().gson.fromJson("{}", JsonObject.class), neCondition, null));
        assertTrue(evaluator.evaluateCondition(
                GrowthBookJsonUtils.getInstance().gson.fromJson("{\"level\":null}", JsonObject.class), neCondition, null));

        JsonObject neNullCondition = GrowthBookJsonUtils.getInstance().gson
                .fromJson("{\"level\":{\"$ne\":null}}", JsonObject.class);
        assertFalse(evaluator.evaluateCondition(
                GrowthBookJsonUtils.getInstance().gson.fromJson("{\"level\":null}", JsonObject.class), neNullCondition, null));
    }

    @Test
    void test_getPath() {
        ConditionEvaluator evaluator = new ConditionEvaluator();

        JsonElement attributes = GrowthBookJsonUtils.getInstance().gson
                .fromJson("{ \"name\": \"sarah\", \"job\": { \"title\": \"developer\" } }", JsonElement.class);

        // getPath is declared as Object for binary compatibility with 0.11.0; the cast documents
        // that every value it returns is a JsonElement.
        assertEquals("sarah",
                ((JsonElement) Objects.requireNonNull(evaluator.getPath(attributes, "name"))).getAsString());
        assertEquals("developer",
                ((JsonElement) Objects.requireNonNull(evaluator.getPath(attributes, "job.title"))).getAsString());
        assertNull(evaluator.getPath(attributes, "job.company"));
    }

    @Test
    @DisplayName("Numeric comparisons keep double precision, matching the JS reference SDK")
    void test_numericComparison_usesDoublePrecision() {
        // The JS SDK compares with native `<`/`>` on IEEE-754 doubles (packages/sdk-js/src/mongrule.ts).
        // Narrowing to float first collapses values that differ only beyond 2^24, so 16777217 and
        // 16777216 would compare equal and every operator below would return the wrong answer.
        ConditionEvaluator evaluator = new ConditionEvaluator();
        JsonObject attributes = GrowthBookJsonUtils.getInstance().gson
                .fromJson("{\"count\":16777217}", JsonObject.class);

        assertTrue(evaluator.evaluateCondition(attributes, condition("{\"count\":{\"$gt\":16777216}}"), null));
        assertFalse(evaluator.evaluateCondition(attributes, condition("{\"count\":{\"$lte\":16777216}}"), null));
        assertFalse(evaluator.evaluateCondition(attributes, condition("{\"count\":{\"$eq\":16777216}}"), null));
        assertTrue(evaluator.evaluateCondition(attributes, condition("{\"count\":{\"$ne\":16777216}}"), null));
    }

    private JsonObject condition(String json) {
        return GrowthBookJsonUtils.getInstance().gson.fromJson(json, JsonObject.class);
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
