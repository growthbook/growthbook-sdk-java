package growthbook.sdk.java.evaluators;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import growthbook.sdk.java.testhelpers.TestCasesJsonHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConditionEvaluatorTest {

    private static final String PATH_ATTRIBUTES = "{\"name\": \"user-1\", \"job\": {\"title\": \"developer\"},"
            + " \"x\": [[1, 2]], \"s\": \"ab\", \"a\": {\"\": 1}, \"b\": 2}";

    private final ConditionEvaluator evaluator = new ConditionEvaluator();

    @ParameterizedTest(name = "{0}")
    @MethodSource("evalConditionCases")
    @DisplayName("Evaluates the shared evalCondition spec cases")
    void evaluatesSharedSpecCases(String description, JsonObject condition, JsonObject attributes,
                                  boolean expected, JsonObject savedGroups) {
        // Given
        // When
        boolean result = evaluator.evaluateCondition(attributes, condition, savedGroups);

        // Then
        assertEquals(expected, result);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("savedGroupReferencesV2Cases")
    @DisplayName("Evaluates the shared savedGroupReferencesV2 evalCondition spec cases")
    void evaluatesSavedGroupReferencesV2SpecCases(String description, JsonObject condition, JsonObject attributes,
                                                  boolean expected, JsonObject savedGroups) {
        // Given
        // When
        boolean result = evaluator.evaluateCondition(attributes, condition, savedGroups);

        // Then
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("Recognises an object whose keys are operators as an operator object")
    void recognisesOperatorObject() {
        // Given
        JsonObject operatorObject = parse("{\"$not\": {\"name\": \"hello\"}}");
        JsonObject plainObject = parse("{\"name\": \"world\"}");

        // When
        Boolean operator = evaluator.isOperatorObject(operatorObject);
        Boolean plain = evaluator.isOperatorObject(plainObject);

        // Then
        assertTrue(operator);
        assertFalse(plain);
    }

    @Test
    @DisplayName("Keeps the 0.11.0 return types of isOperatorObject and getPath for binary compatibility")
    void keepsBinaryCompatibleReturnTypes() throws Exception {
        // Given
        Class<ConditionEvaluator> type = ConditionEvaluator.class;

        // When
        Class<?> isOperatorObjectType = type.getMethod("isOperatorObject", JsonElement.class).getReturnType();
        Class<?> getPathType = type.getMethod("getPath", JsonElement.class, String.class).getReturnType();

        // Then
        assertEquals(Boolean.class, isOperatorObjectType);
        assertEquals(Object.class, getPathType);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', value = {
            "name      | \"user-1\"",
            "job.title | \"developer\"",
            "x.0.0     | 1",
            "x.0.length | 2",
            "a.        | 1"
    })
    @DisplayName("Reads nested keys, array indexes, array length and a trailing empty-string key like the JS SDK")
    void readsPath(String path, String expectedJson) {
        // Given
        JsonObject attributes = parse(PATH_ATTRIBUTES);

        // When
        Object value = evaluator.getPath(attributes, path);

        // Then
        assertEquals(JsonParser.parseString(expectedJson), value);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"job.company", "x.1", "x.00", "x.-1", "s.length", "x.0.0.", "b."})
    @DisplayName("Returns null for a path that does not resolve")
    void returnsNullForUnresolvedPath(String path) {
        // Given
        JsonObject attributes = parse(PATH_ATTRIBUTES);

        // When
        Object value = evaluator.getPath(attributes, path);

        // Then
        assertNull(value);
    }

    @ParameterizedTest(name = "{1} for {0} -> {2}")
    @CsvSource(delimiter = '|', value = {
            "{} | '{\"userAgent\": {\"$notRegex\": \"(Mobile|Tablet)\"}}' | true",
            "{} | '{\"userAgent\": {\"$notRegexi\": \"(mobile|tablet)\"}}' | true"
    })
    @DisplayName("Matches $notRegex and $notRegexi for a missing attribute")
    void matchesNegatedRegexForMissingAttribute(String attributes, String condition, boolean expected) {
        // Given
        // When
        boolean result = evaluate(attributes, condition, null);

        // Then
        assertEquals(expected, result);
    }

    @ParameterizedTest(name = "{1} for {0} -> {2}")
    @CsvSource(delimiter = '|', value = {
            "{\"a\": 1}      | {\"a\": {\"$eq\": \"abc\"}}                           | false",
            "{\"a\": 1}      | {\"a\": {\"$not\": {\"$eq\": \"abc\"}}}               | true",
            "{\"a\": [1, 2]} | {\"a\": {\"$elemMatch\": {\"$eq\": \"abc\"}}}         | false",
            "{\"a\": [1, 2]} | {\"a\": {\"$not\": {\"$elemMatch\": {\"$eq\": \"abc\"}}}} | true"
    })
    @DisplayName("Evaluates $eq with a non-numeric value against a number as false, so $not inverts it")
    void evaluatesEqualWithMismatchedTypesAsFalse(String attributes, String condition, boolean expected) {
        // Given
        // When
        boolean result = evaluate(attributes, condition, null);

        // Then
        assertEquals(expected, result);
    }

    @ParameterizedTest(name = "{1} for {0} -> {2}")
    @CsvSource(delimiter = '|', value = {
            "{\"a\": 1}   | {\"a\": {\"$eq\": \"1\"}} | false",
            "{\"a\": \"1\"} | {\"a\": {\"$eq\": 1}}     | false",
            "{\"a\": \"1\"} | {\"a\": {\"$ne\": 1}}     | true",
            "{\"a\": 1}   | {\"a\": {\"$eq\": 1}}     | true",
            "{\"a\": 1}   | {\"a\": {\"$eq\": 1.0}}   | true",
            "{\"a\": 1}   | {\"a\": {\"$eq\": 2}}     | false"
    })
    @DisplayName("Does not coerce numeric strings in $eq and $ne")
    void doesNotCoerceNumericStringsInEquality(String attributes, String condition, boolean expected) {
        // Given
        // When
        boolean result = evaluate(attributes, condition, null);

        // Then
        assertEquals(expected, result);
    }

    @ParameterizedTest(name = "{1} for {0} -> {2}")
    @CsvSource(delimiter = '|', value = {
            "{\"a\": 16777217} | {\"a\": {\"$eq\": 16777216}}  | false",
            "{\"a\": 16777217} | {\"a\": {\"$eq\": 16777217}}  | true",
            "{\"a\": 16777217} | {\"a\": {\"$ne\": 16777216}}  | true",
            "{\"a\": 16777217} | {\"a\": {\"$gt\": 16777216}}  | true",
            "{\"a\": 16777217} | {\"a\": {\"$lte\": 16777216}} | false"
    })
    @DisplayName("Compares numbers beyond float precision with double precision, like the JS SDK")
    void comparesNumbersWithDoublePrecision(String attributes, String condition, boolean expected) {
        // Given
        // When
        boolean result = evaluate(attributes, condition, null);

        // Then
        assertEquals(expected, result);
    }

    @ParameterizedTest(name = "{1} for {0} -> {2}")
    @CsvSource(delimiter = '|', value = {
            "{\"a\": {\"x\": 1, \"y\": 2}} | {\"a\": {\"y\": 2, \"x\": 1}} | true",
            "{\"a\": {\"x\": 1}}         | {\"a\": {\"x\": 2}}         | false",
            "{\"a\": [1, 2]}             | {\"a\": [1, 2]}             | true",
            "{\"a\": [1, 2]}             | {\"a\": [2, 1]}             | false"
    })
    @DisplayName("Matches object conditions regardless of key order and array conditions in order")
    void matchesObjectsStructurallyAndArraysInOrder(String attributes, String condition, boolean expected) {
        // Given
        // When
        boolean result = evaluate(attributes, condition, null);

        // Then
        assertEquals(expected, result);
    }

    @ParameterizedTest(name = "{1} for {0} -> {2}")
    @CsvSource(delimiter = '|', value = {
            "{\"a\": 123}     | {\"a\": \"123\"}  | false",
            "{\"a\": true}    | {\"a\": \"true\"} | false",
            "{\"a\": \"123\"} | {\"a\": \"123\"}  | true",
            "{\"a\": 12}      | {\"a\": \"123\"}  | false",
            "{}               | {\"a\": \"123\"}  | false"
    })
    @DisplayName("Does not coerce attributes to strings for implicit equality, like the JS SDK 2.0.0")
    void doesNotCoerceImplicitEqualityToString(String attributes, String condition, boolean expected) {
        // Given
        // When
        boolean result = evaluate(attributes, condition, null);

        // Then
        assertEquals(expected, result);
    }

    @ParameterizedTest(name = "{1} for {0} -> {2}")
    @CsvSource(delimiter = '|', value = {
            "{\"a\": -0.0} | {\"a\": {\"$gte\": 0}} | true",
            "{\"a\": -0.0} | {\"a\": {\"$lte\": 0}} | true",
            "{\"a\": -0.0} | {\"a\": {\"$gt\": 0}}  | false",
            "{\"a\": -0.0} | {\"a\": {\"$lt\": 0}}  | false"
    })
    @DisplayName("Treats -0.0 and 0.0 as equal in comparisons, like the JS SDK")
    void treatsNegativeZeroAsZero(String attributes, String condition, boolean expected) {
        // Given
        // When
        boolean result = evaluate(attributes, condition, null);

        // Then
        assertEquals(expected, result);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"$gte", "$lte", "$gt", "$lt"})
    @DisplayName("Never matches a NaN attribute against a numeric bound, like the JS SDK")
    void neverMatchesNaNAgainstNumericBound(String operator) {
        // Given
        JsonObject attributes = new JsonObject();
        attributes.addProperty("a", Double.NaN);
        JsonObject condition = parse("{\"a\": {\"" + operator + "\": 0}}");

        // When
        boolean result = evaluator.evaluateCondition(attributes, condition, null);

        // Then
        assertFalse(result);
    }

    @ParameterizedTest(name = "{1} for {0} -> {2}")
    @CsvSource(delimiter = '|', value = {
            "{}                | {\"level\": {\"$ne\": \"senior\"}} | true",
            "{\"level\": null} | {\"level\": {\"$ne\": \"senior\"}} | true",
            "{\"level\": null} | {\"level\": {\"$ne\": null}}       | false"
    })
    @DisplayName("Treats a missing or null attribute as not equal to a value but equal to null in $ne")
    void evaluatesNotEqualForMissingOrNullAttribute(String attributes, String condition, boolean expected) {
        // Given
        // When
        boolean result = evaluate(attributes, condition, null);

        // Then
        assertEquals(expected, result);
    }

    @ParameterizedTest(name = "{1} for {0} -> {3}")
    @CsvSource(delimiter = '|', value = {
            "{\"x\": [[1]]} | {\"x\": {\"$elemMatch\": {\"$or\": [{\"0\": 1}]}}}       |  | true",
            "{\"x\": [[1]]} | {\"x\": {\"$elemMatch\": {\"$and\": [{\"length\": 1}]}}} |  | true",
            "{\"x\": [[1]]} | {\"x\": {\"$elemMatch\": {\"$or\": [{\"0\": 2}]}}}       |  | false",
            "{\"x\": [[1]]} | {\"x\": {\"$elemMatch\": {\"$or\": [{\"0.\": 1}]}}}      |  | false",
            "{\"x\": [{\"a\": {\"\": 1}}]} | {\"x\": {\"$elemMatch\": {\"$or\": [{\"a.\": 1}]}}} |  | true",
            "{\"x\": [[1]]} | {\"x\": {\"$elemMatch\": {\"$savedGroup\": {\"id\": \"g\"}}}} "
                    + "| {\"g\": {\"type\": \"list\", \"attributeKey\": \"0\", \"values\": [1]}} | true"
    })
    @DisplayName("Reads a nested element's index, length and empty-string keys in a logical $elemMatch")
    void readsElementKeysInLogicalElemMatch(String attributes, String condition, String savedGroups, boolean expected) {
        // Given
        // When
        boolean result = evaluate(attributes, condition, savedGroups);

        // Then
        assertEquals(expected, result);
    }

    @ParameterizedTest(name = "{1} for {0} -> {2}")
    @CsvSource(delimiter = '|', value = {
            "{\"x\": 1e3}                   | {\"x\": {\"$veq\": 1000}}                    | true",
            "{\"x\": 1e3}                   | {\"x\": {\"$vne\": 1000.0}}                  | false",
            "{\"y\": 1.50}                  | {\"y\": {\"$veq\": 1.5}}                     | true",
            "{\"x\": 5.9604644775390625e-8} | {\"x\": {\"$vgt\": 5.960464477539062e-8}}    | true",
            "{\"x\": 5.9604644775390625e-8} | {\"x\": {\"$veq\": 5.960464477539063e-8}}    | true"
    })
    @DisplayName("Formats numeric version operands like JS String(number)")
    void formatsNumericVersionsLikeJs(String attributes, String condition, boolean expected) {
        // Given
        // When
        boolean result = evaluate(attributes, condition, null);

        // Then
        assertEquals(expected, result);
    }

    @ParameterizedTest(name = "{1} for {0} -> {2}")
    @CsvSource(delimiter = '|', value = {
            "{\"x\": \"\u00c9\"} | {\"x\": {\"$regexi\": \"^\u00e9$\"}}    | true",
            "{\"x\": \"\u00c9\"} | {\"x\": {\"$notRegexi\": \"^\u00e9$\"}} | false",
            "{\"x\": \"\u00c9\"} | {\"x\": {\"$regex\": \"^\u00e9$\"}}     | false"
    })
    @DisplayName("Folds non-ASCII case in $regexi and $notRegexi, like a JS /i regex")
    void foldsUnicodeCaseInCaseInsensitiveRegex(String attributes, String condition, boolean expected) {
        // Given
        // When
        boolean result = evaluate(attributes, condition, null);

        // Then
        assertEquals(expected, result);
    }

    @ParameterizedTest(name = "{1} for {0} -> {2}")
    @CsvSource(delimiter = '|', value = {
            "{\"a\": [1]}      | {\"a\": {\"$eq\": [1]}}          | false",
            "{\"a\": [1]}      | {\"a\": {\"$ne\": [1]}}          | true",
            "{\"o\": {\"x\": 1}} | {\"o\": {\"$eq\": {\"x\": 1}}} | false",
            "{\"o\": {\"x\": 1}} | {\"o\": {\"$ne\": {\"x\": 1}}} | true"
    })
    @DisplayName("Never matches an array or object operand with $eq, like JS ===")
    void neverMatchesArrayOrObjectOperandWithEqual(String attributes, String condition, boolean expected) {
        // Given
        // When
        boolean result = evaluate(attributes, condition, null);

        // Then
        assertEquals(expected, result);
    }

    @ParameterizedTest(name = "{1} for {0} -> {2}")
    @CsvSource(delimiter = '|', value = {
            "{\"a\": [[1]]}      | {\"a\": {\"$in\": [[1]]}}           | false",
            "{\"a\": [[1]]}      | {\"a\": {\"$nin\": [[1]]}}          | true",
            "{\"o\": {\"x\": 1}} | {\"o\": {\"$ini\": [{\"x\": 1}]}}  | false",
            "{\"o\": {\"x\": 1}} | {\"o\": {\"$nini\": [{\"x\": 1}]}} | true"
    })
    @DisplayName("Never matches an array or object item with $in, $nin, $ini and $nini")
    void neverMatchesArrayOrObjectItemInMembership(String attributes, String condition, boolean expected) {
        // Given
        // When
        boolean result = evaluate(attributes, condition, null);

        // Then
        assertEquals(expected, result);
    }

    @ParameterizedTest(name = "{1} for {0} -> {2}")
    @CsvSource(delimiter = '|', value = {
            "{\"x\": \"10\"}   | {\"x\": {\"$regex\": 1}}        | false",
            "{\"x\": \"10\"}   | {\"x\": {\"$regexi\": 1}}       | false",
            "{\"t\": \"true\"} | {\"t\": {\"$regex\": true}}     | false",
            "{\"x\": \"10\"}   | {\"x\": {\"$regex\": [\"1\"]}}  | false"
    })
    @DisplayName("Never matches $regex or $regexi with a non-string pattern")
    void neverMatchesRegexWithNonStringPattern(String attributes, String condition, boolean expected) {
        // Given
        // When
        boolean result = evaluate(attributes, condition, null);

        // Then
        assertEquals(expected, result);
    }

    @ParameterizedTest(name = "{1} for {0} -> {3}")
    @CsvSource(delimiter = '|', value = {
            "{\"n\": 1}     | {\"n\": {\"$in\": [1.0]}}             |  | true",
            "{\"n\": 1.0}   | {\"n\": {\"$in\": [1]}}               |  | true",
            "{\"n\": [2.0]} | {\"n\": {\"$in\": [2]}}               |  | true",
            "{\"n\": \"1\"} | {\"n\": {\"$in\": [1]}}               |  | false",
            "{\"n\": 1}     | {\"n\": {\"$inGroup\": \"nums\"}}     | {\"nums\": [1.0, 2]} | true",
            "{\"n\": 1}     | {\"$savedGroup\": {\"id\": \"typed\"}} "
                    + "| {\"typed\": {\"type\": \"list\", \"attributeKey\": \"n\", \"values\": [1.0]}} | true"
    })
    @DisplayName("Compares numbers in lists and saved groups by value, so 1 matches 1.0")
    void comparesListNumbersByValue(String attributes, String condition, String savedGroups, boolean expected) {
        // Given
        // When
        boolean result = evaluate(attributes, condition, savedGroups);

        // Then
        assertEquals(expected, result);
    }

    @ParameterizedTest(name = "{1} for {0} -> {3}")
    @CsvSource(delimiter = '|', value = {
            "{\"a\": null} | {\"a\": {\"$exists\": true}}      |              | false",
            "{\"a\": null} | {\"a\": {\"$exists\": false}}     |              | true",
            "{}            | {\"a\": {\"$notInGroup\": \"g\"}} | {\"g\": [1]} | true"
    })
    @DisplayName("Treats a null attribute as missing")
    void treatsNullAttributeAsMissing(String attributes, String condition, String savedGroups, boolean expected) {
        // Given
        // When
        boolean result = evaluate(attributes, condition, savedGroups);

        // Then
        assertEquals(expected, result);
    }

    static Stream<Arguments> evalConditionCases() {
        return specCases(TestCasesJsonHelper.getInstance().evalConditionTestCases());
    }

    static Stream<Arguments> savedGroupReferencesV2Cases() {
        return specCases(TestCasesJsonHelper.getInstance().getTestCases()
                .getAsJsonObject("savedGroupReferencesV2").getAsJsonArray("evalCondition"));
    }

    private static Stream<Arguments> specCases(JsonArray cases) {
        List<Arguments> arguments = new ArrayList<>();
        for (JsonElement element : cases) {
            JsonArray testCase = element.getAsJsonArray();
            arguments.add(Arguments.of(
                    testCase.get(0).getAsString(),
                    testCase.get(1).getAsJsonObject().deepCopy(),
                    testCase.get(2).getAsJsonObject().deepCopy(),
                    testCase.get(3).getAsBoolean(),
                    testCase.size() > 4 ? testCase.get(4).getAsJsonObject().deepCopy() : null));
        }
        return arguments.stream();
    }

    private boolean evaluate(String attributes, String condition, String savedGroups) {
        return evaluator.evaluateCondition(parse(attributes), parse(condition), savedGroups == null ? null : parse(savedGroups));
    }

    private static JsonObject parse(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }
}
