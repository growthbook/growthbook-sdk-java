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
        assertEvalConditionCasesPass(helper.evalConditionTestCases());
    }

    @Test
    void test_savedGroupReferencesV2_evaluateCondition_testCases() {
        assertEvalConditionCasesPass(helper.getTestCases().getAsJsonObject("savedGroupReferencesV2").getAsJsonArray("evalCondition"));
    }

    private void assertEvalConditionCasesPass(JsonArray testCases) {
        ArrayList<String> passedTests = new ArrayList<>();
        ArrayList<String> failedTests = new ArrayList<>();

        ArrayList<Integer> failingIndexes = new ArrayList<>();

        ConditionEvaluator evaluator = new ConditionEvaluator();

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
    @DisplayName("$eq does not coerce numeric strings, and $ne matches them")
    void test_equalOperatorDoesNotParseNumericStrings() {
        // Spec 0.10.0: an attribute of a different type than the condition value never equals it.
        ConditionEvaluator evaluator = new ConditionEvaluator();

        assertFalse(evaluator.evaluateCondition(parse("{\"a\":1}"), parse("{\"a\":{\"$eq\":\"1\"}}"), null));
        assertFalse(evaluator.evaluateCondition(parse("{\"a\":\"1\"}"), parse("{\"a\":{\"$eq\":1}}"), null));
        assertTrue(evaluator.evaluateCondition(parse("{\"a\":\"1\"}"), parse("{\"a\":{\"$ne\":1}}"), null));
        assertTrue(evaluator.evaluateCondition(parse("{\"a\":1}"), parse("{\"a\":{\"$eq\":1}}"), null));
        assertTrue(evaluator.evaluateCondition(parse("{\"a\":1}"), parse("{\"a\":{\"$eq\":1.0}}"), null));
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
    @DisplayName("implicit equality does not coerce to string, matching the JS SDK 2.0.0")
    void test_implicitEqualityDoesNotCoerceToString() {
        // Spec 0.10.0: a numeric or boolean attribute no longer matches its string form.
        ConditionEvaluator evaluator = new ConditionEvaluator();

        assertFalse(evaluator.evaluateCondition(parse("{\"a\":123}"), parse("{\"a\":\"123\"}"), null));
        assertFalse(evaluator.evaluateCondition(parse("{\"a\":true}"), parse("{\"a\":\"true\"}"), null));
        assertTrue(evaluator.evaluateCondition(parse("{\"a\":\"123\"}"), parse("{\"a\":\"123\"}"), null));
        assertFalse(evaluator.evaluateCondition(parse("{\"a\":12}"), parse("{\"a\":\"123\"}"), null));
        assertFalse(evaluator.evaluateCondition(parse("{}"), parse("{\"a\":\"123\"}"), null));
    }

    @Test
    @DisplayName("comparison operators treat -0.0 and 0.0 as equal, like the JS SDK")
    void test_comparisonTreatsNegativeZeroAsZero() {
        // Double.compare orders -0.0 below 0.0, so it made `-0.0 $gte 0` false. Native comparison
        // on doubles keeps the precision that matters here while matching the other SDKs.
        ConditionEvaluator evaluator = new ConditionEvaluator();

        assertTrue(evaluator.evaluateCondition(parse("{\"a\":-0.0}"), parse("{\"a\":{\"$gte\":0}}"), null));
        assertTrue(evaluator.evaluateCondition(parse("{\"a\":-0.0}"), parse("{\"a\":{\"$lte\":0}}"), null));
        assertFalse(evaluator.evaluateCondition(parse("{\"a\":-0.0}"), parse("{\"a\":{\"$gt\":0}}"), null));
        assertFalse(evaluator.evaluateCondition(parse("{\"a\":-0.0}"), parse("{\"a\":{\"$lt\":0}}"), null));
    }

    @Test
    @DisplayName("comparison operators keep double precision beyond 2^24")
    void test_comparisonKeepsDoublePrecision() {
        // Guards the compareDoubles helper from being narrowed back to float.
        ConditionEvaluator evaluator = new ConditionEvaluator();

        assertTrue(evaluator.evaluateCondition(
                parse("{\"a\":16777217}"), parse("{\"a\":{\"$gt\":16777216}}"), null));
    }

    @Test
    @DisplayName("a NaN numeric attribute never matches a numeric bound, like the JS SDK")
    void test_comparisonWithNaNAttributeNeverMatches() {
        // JSON has no NaN literal, so a NaN attribute can only be supplied programmatically. In the
        // reference JS SDK every <, >, <=, >= against NaN is false; compareDoubles alone would report
        // NaN as equal to a finite number and let $gte/$lte match.
        ConditionEvaluator evaluator = new ConditionEvaluator();
        JsonObject nanAttribute = new JsonObject();
        nanAttribute.addProperty("a", Double.NaN);

        assertFalse(evaluator.evaluateCondition(nanAttribute, parse("{\"a\":{\"$gte\":0}}"), null));
        assertFalse(evaluator.evaluateCondition(nanAttribute, parse("{\"a\":{\"$lte\":0}}"), null));
        assertFalse(evaluator.evaluateCondition(nanAttribute, parse("{\"a\":{\"$gt\":0}}"), null));
        assertFalse(evaluator.evaluateCondition(nanAttribute, parse("{\"a\":{\"$lt\":0}}"), null));
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
    @DisplayName("getPath reads an array's own index and length keys, like the JS SDK")
    void test_getPathReadsArrayIndexAndLength() {
        ConditionEvaluator evaluator = new ConditionEvaluator();
        JsonElement attributes = condition("{\"x\": [[1, 2]], \"s\": \"ab\"}");

        assertEquals(1, ((JsonElement) Objects.requireNonNull(evaluator.getPath(attributes, "x.0.0"))).getAsInt());
        assertEquals(2, ((JsonElement) Objects.requireNonNull(evaluator.getPath(attributes, "x.0.length"))).getAsInt());
        assertNull(evaluator.getPath(attributes, "x.1"));
        assertNull(evaluator.getPath(attributes, "x.00"));
        assertNull(evaluator.getPath(attributes, "x.-1"));
        assertNull(evaluator.getPath(attributes, "s.length"));
        assertNull(evaluator.getPath(attributes, "x.0.0."));
    }

    @Test
    @DisplayName("getPath keeps a trailing empty segment, which reads an empty-string key")
    void test_getPathKeepsTrailingEmptySegment() {
        ConditionEvaluator evaluator = new ConditionEvaluator();
        JsonObject attributes = condition("{\"a\": {\"\": 1}, \"b\": 2, \"x\": [{\"a\": {\"\": 1}}]}");

        assertEquals(1, ((JsonElement) Objects.requireNonNull(evaluator.getPath(attributes, "a."))).getAsInt());
        assertNull(evaluator.getPath(attributes, "b."));
        assertTrue(evaluator.evaluateCondition(attributes, condition("{\"x\": {\"$elemMatch\": {\"$or\": [{\"a.\": 1}]}}}"), null));
    }

    @Test
    @DisplayName("A logical $elemMatch reads a nested array element's index keys")
    void test_logicalElemMatchOnNestedArrays() {
        ConditionEvaluator evaluator = new ConditionEvaluator();
        JsonObject attributes = condition("{\"x\": [[1]]}");

        assertTrue(evaluator.evaluateCondition(attributes, condition("{\"x\": {\"$elemMatch\": {\"$or\": [{\"0\": 1}]}}}"), null));
        assertTrue(evaluator.evaluateCondition(attributes, condition("{\"x\": {\"$elemMatch\": {\"$and\": [{\"length\": 1}]}}}"), null));
        assertFalse(evaluator.evaluateCondition(attributes, condition("{\"x\": {\"$elemMatch\": {\"$or\": [{\"0\": 2}]}}}"), null));
        // "0." reads key "" of element 1, which a number doesn't have
        assertFalse(evaluator.evaluateCondition(attributes, condition("{\"x\": {\"$elemMatch\": {\"$or\": [{\"0.\": 1}]}}}"), null));

        JsonObject savedGroups = condition("{\"g\": {\"type\": \"list\", \"attributeKey\": \"0\", \"values\": [1]}}");
        assertTrue(evaluator.evaluateCondition(attributes, condition("{\"x\": {\"$elemMatch\": {\"$savedGroup\": {\"id\": \"g\"}}}}"), savedGroups));
    }

    @Test
    @DisplayName("Numeric version operands are formatted like JS String(number)")
    void test_numericVersionUsesJsNumberFormatting() {
        ConditionEvaluator evaluator = new ConditionEvaluator();
        JsonObject attributes = condition("{\"x\": 1e3, \"y\": 1.50}");

        assertTrue(evaluator.evaluateCondition(attributes, condition("{\"x\": {\"$veq\": 1000}}"), null));
        assertFalse(evaluator.evaluateCondition(attributes, condition("{\"x\": {\"$vne\": 1000.0}}"), null));
        assertTrue(evaluator.evaluateCondition(attributes, condition("{\"y\": {\"$veq\": 1.5}}"), null));

        // 2^-24 is "5.960464477539063e-8" in JS, so it's greater than 5.960464477539062e-8
        JsonObject powerOfTwo = condition("{\"x\": 5.9604644775390625e-8}");
        assertTrue(evaluator.evaluateCondition(powerOfTwo, condition("{\"x\": {\"$vgt\": 5.960464477539062e-8}}"), null));
        assertTrue(evaluator.evaluateCondition(powerOfTwo, condition("{\"x\": {\"$veq\": 5.960464477539063e-8}}"), null));
    }

    @Test
    @DisplayName("$regexi folds non-ASCII case, like a JS /i regex")
    void test_caseInsensitiveRegexFoldsUnicode() {
        ConditionEvaluator evaluator = new ConditionEvaluator();
        JsonObject attributes = condition("{\"x\": \"\u00c9\"}");

        assertTrue(evaluator.evaluateCondition(attributes, condition("{\"x\": {\"$regexi\": \"^\u00e9$\"}}"), null));
        assertFalse(evaluator.evaluateCondition(attributes, condition("{\"x\": {\"$notRegexi\": \"^\u00e9$\"}}"), null));
        assertFalse(evaluator.evaluateCondition(attributes, condition("{\"x\": {\"$regex\": \"^\u00e9$\"}}"), null));
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

    @Test
    @DisplayName("$eq and $ne never match an array or object operand, like JS ===")
    void test_equalityWithArrayOrObjectOperand() {
        ConditionEvaluator evaluator = new ConditionEvaluator();
        JsonObject attributes = condition("{\"a\": [1], \"o\": {\"x\": 1}}");

        assertFalse(evaluator.evaluateCondition(attributes, condition("{\"a\": {\"$eq\": [1]}}"), null));
        assertTrue(evaluator.evaluateCondition(attributes, condition("{\"a\": {\"$ne\": [1]}}"), null));
        assertFalse(evaluator.evaluateCondition(attributes, condition("{\"o\": {\"$eq\": {\"x\": 1}}}"), null));
        assertTrue(evaluator.evaluateCondition(attributes, condition("{\"o\": {\"$ne\": {\"x\": 1}}}"), null));
    }

    @Test
    @DisplayName("$in, $nin, $ini and $nini never match an array or object item")
    void test_membershipWithArrayOrObjectItems() {
        ConditionEvaluator evaluator = new ConditionEvaluator();
        JsonObject attributes = condition("{\"a\": [[1]], \"o\": {\"x\": 1}}");

        assertFalse(evaluator.evaluateCondition(attributes, condition("{\"a\": {\"$in\": [[1]]}}"), null));
        assertTrue(evaluator.evaluateCondition(attributes, condition("{\"a\": {\"$nin\": [[1]]}}"), null));
        assertFalse(evaluator.evaluateCondition(attributes, condition("{\"o\": {\"$ini\": [{\"x\": 1}]}}"), null));
        assertTrue(evaluator.evaluateCondition(attributes, condition("{\"o\": {\"$nini\": [{\"x\": 1}]}}"), null));
    }

    @Test
    @DisplayName("$regex and $regexi with a non-string pattern never match")
    void test_regexWithNonStringPattern() {
        ConditionEvaluator evaluator = new ConditionEvaluator();
        JsonObject attributes = condition("{\"x\": \"10\", \"t\": \"true\"}");

        assertFalse(evaluator.evaluateCondition(attributes, condition("{\"x\": {\"$regex\": 1}}"), null));
        assertFalse(evaluator.evaluateCondition(attributes, condition("{\"x\": {\"$regexi\": 1}}"), null));
        assertFalse(evaluator.evaluateCondition(attributes, condition("{\"t\": {\"$regex\": true}}"), null));
        assertFalse(evaluator.evaluateCondition(attributes, condition("{\"x\": {\"$regex\": [\"1\"]}}"), null));
    }

    @Test
    @DisplayName("Numbers in lists and saved groups compare by value, so 1 matches 1.0")
    void test_membershipComparesNumbersByValue() {
        ConditionEvaluator evaluator = new ConditionEvaluator();
        JsonObject savedGroups = condition("{\"nums\": [1.0, 2], \"typed\": {\"type\": \"list\", \"attributeKey\": \"n\", \"values\": [1.0]}}");

        assertTrue(evaluator.evaluateCondition(condition("{\"n\": 1}"), condition("{\"n\": {\"$in\": [1.0]}}"), null));
        assertTrue(evaluator.evaluateCondition(condition("{\"n\": 1.0}"), condition("{\"n\": {\"$in\": [1]}}"), null));
        assertTrue(evaluator.evaluateCondition(condition("{\"n\": [2.0]}"), condition("{\"n\": {\"$in\": [2]}}"), null));
        assertFalse(evaluator.evaluateCondition(condition("{\"n\": \"1\"}"), condition("{\"n\": {\"$in\": [1]}}"), null));
        assertTrue(evaluator.evaluateCondition(condition("{\"n\": 1}"), condition("{\"n\": {\"$inGroup\": \"nums\"}}"), savedGroups));
        assertTrue(evaluator.evaluateCondition(condition("{\"n\": 1}"), condition("{\"$savedGroup\": {\"id\": \"typed\"}}"), savedGroups));
    }

    @Test
    @DisplayName("A null attribute does not exist, like a missing one")
    void test_existsTreatsNullAsMissing() {
        ConditionEvaluator evaluator = new ConditionEvaluator();

        assertFalse(evaluator.evaluateCondition(condition("{\"a\": null}"), condition("{\"a\": {\"$exists\": true}}"), null));
        assertTrue(evaluator.evaluateCondition(condition("{\"a\": null}"), condition("{\"a\": {\"$exists\": false}}"), null));
        assertTrue(evaluator.evaluateCondition(condition("{}"), condition("{\"a\": {\"$notInGroup\": \"g\"}}"), condition("{\"g\": [1]}")));
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
