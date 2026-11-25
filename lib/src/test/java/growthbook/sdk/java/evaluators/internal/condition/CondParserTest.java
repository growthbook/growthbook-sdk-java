package growthbook.sdk.java.evaluators.internal.condition;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CondParserTest {

    private static final CondScope NO_GROUPS = new CondScope(null);

    @Test
    @DisplayName("Does not evaluate a malformed $or once an earlier entry fails")
    void skipsMalformedOrAfterFailingEntry() {
        // Given
        Cond condition = CondParser.parse(json("{\"a\": 1, \"$or\": \"not-an-array\"}"));

        // When
        Boolean result = condition.eval(json("{\"a\": 2}"), NO_GROUPS);

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("Throws when evaluation reaches an $or that is not an array")
    void throwsWhenReachingNonArrayOr() {
        // Given
        Cond condition = CondParser.parse(json("{\"a\": 1, \"$or\": \"not-an-array\"}"));

        // When
        MalformedConditionException thrown = assertThrows(MalformedConditionException.class,
                () -> condition.eval(json("{\"a\": 1}"), NO_GROUPS));

        // Then
        assertEquals("$or needs an array of conditions", thrown.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"$or", "$nor", "$and"})
    @DisplayName("Throws when evaluation reaches a logical operator whose operand is not an array")
    void throwsWhenReachingNonArrayLogicalOperator(String operator) {
        // Given
        Cond condition = CondParser.parse(json("{\"" + operator + "\": {\"a\": 1}}"));

        // When
        MalformedConditionException thrown = assertThrows(MalformedConditionException.class,
                () -> condition.eval(json("{\"a\": 1}"), NO_GROUPS));

        // Then
        assertEquals(operator + " needs an array of conditions", thrown.getMessage());
    }

    @ParameterizedTest
    @ValueSource(strings = {"$or", "$nor", "$and"})
    @DisplayName("Throws when evaluation reaches a list entry that is not a condition object")
    void throwsWhenReachingNonObjectListEntry(String operator) {
        // Given
        Cond condition = CondParser.parse(json("{\"" + operator + "\": [5]}"));

        // When
        MalformedConditionException thrown = assertThrows(MalformedConditionException.class,
                () -> condition.eval(json("{}"), NO_GROUPS));

        // Then
        assertEquals(operator + " needs condition objects", thrown.getMessage());
    }

    @Test
    @DisplayName("Does not evaluate a malformed list entry once an earlier entry of an $or passes")
    void skipsMalformedListEntryAfterPassingOrEntry() {
        // Given
        Cond condition = CondParser.parse(json("{\"$or\": [{\"a\": 1}, 5]}"));

        // When
        Boolean result = condition.eval(json("{\"a\": 1}"), NO_GROUPS);

        // Then
        assertTrue(result);
    }

    @Test
    @DisplayName("Does not evaluate a $not with a non-object operand once an earlier entry fails")
    void skipsMalformedNotAfterFailingEntry() {
        // Given
        Cond condition = CondParser.parse(json("{\"a\": 1, \"$not\": 5}"));

        // When
        Boolean result = condition.eval(json("{\"a\": 2}"), NO_GROUPS);

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("Throws when evaluation reaches a $not with a non-object operand")
    void throwsWhenReachingNonObjectNot() {
        // Given
        Cond condition = CondParser.parse(json("{\"a\": 1, \"$not\": 5}"));

        // When
        MalformedConditionException thrown = assertThrows(MalformedConditionException.class,
                () -> condition.eval(json("{\"a\": 1}"), NO_GROUPS));

        // Then
        assertEquals("$not needs a condition object", thrown.getMessage());
    }

    @ParameterizedTest(name = "{0} on {1} -> {2}")
    @CsvSource(delimiter = '|', value = {
            "{}                                                | {\"a\": 1}  | true",
            "{\"$or\": []}                                     | {}          | true",
            "{\"$and\": []}                                    | {}          | true",
            "{\"$nor\": []}                                    | {}          | false",
            "{\"$or\": [{\"a\": 1}, {\"a\": 2}]}               | {\"a\": 2}  | true",
            "{\"$or\": [{\"a\": 1}, {\"a\": 2}]}               | {\"a\": 3}  | false",
            "{\"$and\": [{\"a\": {\"$gt\": 1}}, {\"a\": {\"$lt\": 3}}]} | {\"a\": 2} | true",
            "{\"$and\": [{\"a\": {\"$gt\": 1}}, {\"a\": {\"$lt\": 3}}]} | {\"a\": 3} | false",
            "{\"$nor\": [{\"a\": 1}, {\"a\": 2}]}              | {\"a\": 3}  | true",
            "{\"$nor\": [{\"a\": 1}, {\"a\": 2}]}              | {\"a\": 1}  | false",
            "{\"$not\": {\"a\": 1}}                            | {\"a\": 2}  | true",
            "{\"$not\": {\"a\": 1}}                            | {\"a\": 1}  | false",
    })
    @DisplayName("Combines entries with $or, $and, $nor and $not")
    void combinesEntriesWithLogicalOperators(String condition, String attributes, String expected) {
        // Given
        Cond parsed = CondParser.parse(json(condition));

        // When
        Boolean result = parsed.eval(json(attributes), NO_GROUPS);

        // Then
        assertEquals(Boolean.valueOf(expected), result);
    }

    @ParameterizedTest(name = "{0} on {1} -> {2}")
    @CsvSource(delimiter = '|', value = {
            "{\"v\": {\"$foo\": 1}}                                   | {\"v\": 1}            | null",
            "{\"$not\": {\"v\": {\"$foo\": 1}}}                       | {\"v\": 1}            | null",
            "{\"$nor\": [{\"v\": {\"$foo\": 1}}]}                     | {\"v\": 1}            | null",
            "{\"$or\": [{\"v\": {\"$foo\": 1}}, {\"w\": 1}]}          | {\"v\": 1, \"w\": 1}  | true",
            "{\"$or\": [{\"v\": {\"$foo\": 1}}, {\"w\": 1}]}          | {\"v\": 1, \"w\": 2}  | null",
            "{\"$nor\": [{\"v\": {\"$foo\": 1}}, {\"w\": 1}]}         | {\"v\": 1, \"w\": 1}  | false",
            "{\"$and\": [{\"v\": {\"$foo\": 1}}, {\"w\": 2}]}         | {\"v\": 1, \"w\": 1}  | false",
            "{\"$and\": [{\"v\": {\"$foo\": 1}}, {\"w\": 1}]}         | {\"v\": 1, \"w\": 1}  | null",
            "{\"v\": {\"$foo\": 1}, \"w\": 2}                         | {\"v\": 1, \"w\": 1}  | false",
    })
    @DisplayName("Keeps an unknown result unknown unless another entry decides it")
    void keepsUnknownResultUnknown(String condition, String attributes, String expected) {
        // Given
        Cond parsed = CondParser.parse(json(condition));

        // When
        Boolean result = parsed.eval(json(attributes), NO_GROUPS);

        // Then
        assertEquals("null".equals(expected) ? null : Boolean.valueOf(expected), result);
    }

    @ParameterizedTest(name = "{0} on {1} -> {2}")
    @CsvSource(delimiter = '|', value = {
            "{\"o\": {\"a\": 1, \"b\": [2]}}       | {\"o\": {\"b\": [2], \"a\": 1}}   | true",
            "{\"o\": {\"a\": 1}}                   | {\"o\": {\"a\": 1, \"b\": 2}}     | false",
            "{\"o\": {\"$eq\": 1, \"a\": 1}}       | {\"o\": {\"$eq\": 1, \"a\": 1}}   | true",
            "{\"o\": [1, 2]}                       | {\"o\": [1, 2]}                   | true",
            "{\"o\": [1, 2]}                       | {\"o\": [2, 1]}                   | false",
            "{\"o\": null}                         | {}                                | true",
            "{\"o\": null}                         | {\"o\": null}                     | true",
            "{\"o\": null}                         | {\"o\": 0}                        | false",
            "{\"o\": 1}                            | {\"o\": 1.0}                      | true",
            "{\"o\": 1}                            | {\"o\": \"1\"}                    | false",
            "{\"o\": \"a\"}                        | {\"o\": \"A\"}                    | false",
            "{\"o\": false}                        | {}                                | false",
            "{\"o.p\": 1}                          | {\"o\": {\"p\": 1}}               | true",
    })
    @DisplayName("Compares a literal condition value with deep, type-strict equality")
    void comparesLiteralsWithDeepStrictEquality(String condition, String attributes, String expected) {
        // Given
        Cond parsed = CondParser.parse(json(condition));

        // When
        Boolean result = parsed.eval(json(attributes), NO_GROUPS);

        // Then
        assertEquals(Boolean.valueOf(expected), result);
    }

    @Test
    @DisplayName("Treats null attributes as having no attributes")
    void treatsNullAttributesAsEmpty() {
        // Given
        Cond condition = CondParser.parse(json("{\"a\": {\"$exists\": false}, \"b\": null}"));

        // When
        Boolean result = condition.eval(null, NO_GROUPS);

        // Then
        assertTrue(result);
    }

    @Test
    @DisplayName("Parses a single entry the same way as a condition with that entry")
    void parsesSingleEntry() {
        // Given
        Cond entry = CondParser.parseEntry("$or", JsonParser.parseString("[{\"a\": 1}, {\"a\": 2}]"));

        // When
        Boolean matching = entry.eval(json("{\"a\": 2}"), NO_GROUPS);
        Boolean failing = entry.eval(json("{\"a\": 3}"), NO_GROUPS);

        // Then
        assertTrue(matching);
        assertFalse(failing);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource(delimiter = '|', value = {
            "{}                         | true",
            "{\"$gt\": 1}               | true",
            "{\"$gt\": 1, \"$lt\": 5}   | true",
            "{\"$gt\": 1, \"a\": 5}     | false",
            "{\"a\": 5}                 | false",
            "[\"$gt\"]                  | false",
            "\"$gt\"                    | false",
            "5                          | false",
    })
    @DisplayName("Recognizes an operator object only when every key starts with $")
    void recognizesOperatorObjects(String value, boolean expected) {
        // Given
        JsonElement element = JsonParser.parseString(value);

        // When
        boolean result = CondParser.isOperatorObject(element);

        // Then
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("Negates true and false and keeps unknown unknown")
    void negatesThreeValuedResults() {
        // Given
        Boolean unknown = null;

        // When
        Boolean negatedTrue = CondParser.negate(true);
        Boolean negatedFalse = CondParser.negate(false);
        Boolean negatedUnknown = CondParser.negate(unknown);

        // Then
        assertFalse(negatedTrue);
        assertTrue(negatedFalse);
        assertNull(negatedUnknown);
    }

    @Test
    @DisplayName("Parses a literal value test that ignores case only for strings when asked to")
    void parsesCaseInsensitiveLiteral() {
        // Given
        ValueTest caseInsensitive = CondParser.parseValue(new JsonPrimitive("Abc"), true);
        ValueTest caseSensitive = CondParser.parseValue(new JsonPrimitive("Abc"));

        // When
        Boolean foldedMatch = caseInsensitive.test(new JsonPrimitive("aBC"), NO_GROUPS);
        Boolean exactMismatch = caseSensitive.test(new JsonPrimitive("aBC"), NO_GROUPS);
        Boolean numberMismatch = caseInsensitive.test(new JsonPrimitive(1), NO_GROUPS);

        // Then
        assertTrue(foldedMatch);
        assertFalse(exactMismatch);
        assertFalse(numberMismatch);
    }

    @Test
    @DisplayName("Parses a condition once and evaluates it against many attribute sets")
    void reusesParsedCondition() {
        // Given
        Cond condition = CondParser.parse(json("{\"country\": {\"$in\": [\"AA\", \"BB\"]}}"));

        // When
        Boolean first = condition.eval(json("{\"country\": \"AA\"}"), NO_GROUPS);
        Boolean second = condition.eval(json("{\"country\": \"CC\"}"), NO_GROUPS);
        Boolean third = condition.eval(json("{\"country\": \"BB\"}"), NO_GROUPS);

        // Then
        assertTrue(first);
        assertFalse(second);
        assertTrue(third);
    }

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }
}
