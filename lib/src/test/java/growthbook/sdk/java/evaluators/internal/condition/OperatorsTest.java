package growthbook.sdk.java.evaluators.internal.condition;

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

class OperatorsTest {

    private static final JsonObject SAVED_GROUPS = json("{"
            + "\"ids\": [\"u1\", \"u2\"],"
            + "\"typed\": {\"type\": \"list\", \"attributeKey\": \"id\", \"values\": [\"u1\"]},"
            + "\"cond\": {\"type\": \"condition\", \"condition\": {\"id\": \"u1\"}},"
            + "\"future\": {\"type\": \"segment\"},"
            + "\"badList\": {\"type\": \"list\", \"values\": \"u1\"}"
            + "}");

    @ParameterizedTest(name = "{0} on {1} -> {2}")
    @CsvSource(delimiter = '|', value = {
            "{\"v\": {\"$gte\": 0}}            | {\"v\": -0.0}             | true",
            "{\"v\": {\"$lte\": -0.0}}         | {\"v\": 0}                | true",
            "{\"v\": {\"$gt\": -0.0}}          | {\"v\": 0}                | false",
            "{\"v\": {\"$lt\": 0}}             | {\"v\": -0.0}             | false",
            "{\"v\": {\"$gt\": 16777216}}      | {\"v\": 16777217}         | true",
            "{\"v\": {\"$lt\": 16777217}}      | {\"v\": 16777216}         | true",
            "{\"v\": {\"$gt\": 9007199254740990}} | {\"v\": 9007199254740991} | true",
            "{\"v\": {\"$gte\": 1}}            | {\"v\": 1.0}              | true",
            "{\"v\": {\"$lt\": \"9\"}}         | {\"v\": \"10\"}           | true",
            "{\"v\": {\"$gt\": \"9\"}}         | {\"v\": \"10\"}           | false",
            "{\"v\": {\"$gte\": \"abc\"}}      | {\"v\": \"abc\"}          | true",
            "{\"v\": {\"$gt\": 9}}             | {\"v\": \"10\"}           | false",
            "{\"v\": {\"$lt\": \"9\"}}         | {\"v\": 1}                | false",
            "{\"v\": {\"$gte\": 0}}            | {\"v\": false}            | false",
            "{\"v\": {\"$lte\": true}}         | {\"v\": true}             | false",
            "{\"v\": {\"$lte\": 0}}            | {}                        | false",
            "{\"v\": {\"$lte\": 0}}            | {\"v\": null}             | false",
            "{\"v\": {\"$gte\": 0}}            | {\"v\": [1]}              | false",
    })
    @DisplayName("Orders numbers and strings like JavaScript and never matches mixed types")
    void ordersNumbersAndStringsLikeJavaScript(String condition, String attributes, String expected) {
        // Given
        Boolean wanted = expectedResult(expected);

        // When
        Boolean result = evaluate(condition, attributes);

        // Then
        assertEquals(wanted, result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"$gt", "$gte", "$lt", "$lte"})
    @DisplayName("Never matches a NaN attribute")
    void neverMatchesNanAttribute(String operator) {
        // Given
        JsonObject attributes = new JsonObject();
        attributes.add("v", new JsonPrimitive(Double.NaN));
        Cond condition = CondParser.parse(json("{\"v\": {\"" + operator + "\": 0}}"));

        // When
        Boolean result = condition.eval(attributes, new CondScope(null));

        // Then
        assertEquals(Boolean.FALSE, result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"$gt", "$gte", "$lt", "$lte"})
    @DisplayName("Never matches a NaN operand")
    void neverMatchesNanOperand(String operator) {
        // Given
        JsonObject operators = new JsonObject();
        operators.add(operator, new JsonPrimitive(Double.NaN));
        JsonObject condition = new JsonObject();
        condition.add("v", operators);

        // When
        Boolean result = CondParser.parse(condition).eval(json("{\"v\": 1}"), new CondScope(null));

        // Then
        assertEquals(Boolean.FALSE, result);
    }

    @ParameterizedTest(name = "{0} on {1} -> {2}")
    @CsvSource(delimiter = '|', value = {
            "{\"v\": {\"$eq\": 1}}         | {\"v\": 1.0}       | true",
            "{\"v\": {\"$eq\": 0}}         | {\"v\": -0.0}      | true",
            "{\"v\": {\"$eq\": 1}}         | {\"v\": \"1\"}     | false",
            "{\"v\": {\"$eq\": true}}      | {\"v\": 1}         | false",
            "{\"v\": {\"$eq\": null}}      | {}                 | true",
            "{\"v\": {\"$eq\": null}}      | {\"v\": 0}         | false",
            "{\"v\": {\"$eq\": [1]}}       | {\"v\": [1]}       | false",
            "{\"v\": {\"$eq\": {\"a\": 1}}} | {\"v\": {\"a\": 1}} | false",
            "{\"v\": {\"$ne\": \"a\"}}     | {}                 | true",
            "{\"v\": {\"$ne\": \"a\"}}     | {\"v\": \"a\"}     | false",
            "{\"v\": {\"$ne\": 1}}         | {\"v\": \"1\"}     | true",
    })
    @DisplayName("Compares $eq and $ne strictly, without type coercion")
    void comparesEqualityStrictly(String condition, String attributes, String expected) {
        // Given
        Boolean wanted = expectedResult(expected);

        // When
        Boolean result = evaluate(condition, attributes);

        // Then
        assertEquals(wanted, result);
    }

    @ParameterizedTest(name = "{0} on {1} -> {2}")
    @CsvSource(delimiter = '|', value = {
            "{\"v\": {\"$regex\": \"^ab\"}}      | {\"v\": \"abc\"} | true",
            "{\"v\": {\"$regex\": \"b\"}}        | {\"v\": \"abc\"} | true",
            "{\"v\": {\"$regex\": \"^AB\"}}      | {\"v\": \"abc\"} | false",
            "{\"v\": {\"$regexi\": \"^AB\"}}     | {\"v\": \"abc\"} | true",
            "{\"v\": {\"$notRegex\": \"^ab\"}}   | {\"v\": \"abc\"} | false",
            "{\"v\": {\"$notRegex\": \"^AB\"}}   | {\"v\": \"abc\"} | true",
            "{\"v\": {\"$notRegexi\": \"^AB\"}}  | {\"v\": \"abc\"} | false",
            "{\"v\": {\"$notRegexi\": \"^AB\"}}  | {\"v\": \"xyz\"} | true",
            "{\"v\": {\"$regex\": \"[\"}}        | {\"v\": \"[\"}   | false",
            "{\"v\": {\"$regexi\": \"[\"}}       | {\"v\": \"[\"}   | false",
            "{\"v\": {\"$notRegex\": \"[\"}}     | {\"v\": \"[\"}   | true",
            "{\"v\": {\"$notRegexi\": \"[\"}}    | {\"v\": \"[\"}   | true",
            "{\"v\": {\"$regex\": \"1\"}}        | {\"v\": 1}       | false",
            "{\"v\": {\"$regexi\": \"true\"}}    | {\"v\": true}    | false",
            "{\"v\": {\"$notRegex\": \"1\"}}     | {\"v\": 1}       | true",
            "{\"v\": {\"$notRegexi\": \"1\"}}    | {\"v\": [\"1\"]} | true",
            "{\"v\": {\"$regex\": \".*\"}}       | {}               | false",
            "{\"v\": {\"$notRegex\": \"x\"}}     | {}               | true",
            "{\"v\": {\"$regex\": 1}}            | {\"v\": \"1\"}   | false",
            "{\"v\": {\"$notRegex\": 1}}         | {\"v\": \"1\"}   | true",
    })
    @DisplayName("Matches regexes only on a string attribute with a valid string pattern, and negated regexes otherwise")
    void matchesRegexesOnlyOnStringsWithValidPatterns(String condition, String attributes, String expected) {
        // Given
        Boolean wanted = expectedResult(expected);

        // When
        Boolean result = evaluate(condition, attributes);

        // Then
        assertEquals(wanted, result);
    }

    @ParameterizedTest(name = "{0} on {1} -> {2}")
    @CsvSource(delimiter = '|', value = {
            "{\"v\": {\"$vlt\": \"1.10.0\"}}        | {\"v\": \"1.2.3\"}          | true",
            "{\"v\": {\"$vgt\": \"1.10.0\"}}        | {\"v\": \"1.2.3\"}          | false",
            "{\"v\": {\"$vlt\": \"1.0.0\"}}         | {\"v\": \"1.0.0-beta\"}     | true",
            "{\"v\": {\"$vlt\": \"1.0.0-beta\"}}    | {\"v\": \"1.0.0-alpha\"}    | true",
            "{\"v\": {\"$vlt\": \"1.0.0-rc.10\"}}   | {\"v\": \"1.0.0-rc.2\"}     | true",
            "{\"v\": {\"$veq\": \"1.2.3\"}}         | {\"v\": \"v1.2.3+build.7\"} | true",
            "{\"v\": {\"$vgte\": \"1.2.3\"}}        | {\"v\": \"1.2.3\"}          | true",
            "{\"v\": {\"$vlte\": \"1.2.3\"}}        | {\"v\": \"1.2.4\"}          | false",
            "{\"v\": {\"$vne\": \"1.2.3\"}}         | {\"v\": \"1.2.4\"}          | true",
            "{\"v\": {\"$vne\": \"1.2.3\"}}         | {\"v\": \"1.2.3\"}          | false",
            "{\"v\": {\"$vgt\": 9}}                 | {\"v\": 10}                 | true",
            "{\"v\": {\"$veq\": 1000}}              | {\"v\": 1e3}                | true",
            "{\"v\": {\"$veq\": 1}}                 | {\"v\": \"1\"}              | false",
            "{\"v\": {\"$vne\": 1}}                 | {\"v\": \"1\"}              | true",
            "{\"v\": {\"$vgt\": \"1.0.0\"}}         | {\"v\": true}               | false",
            "{\"v\": {\"$vgte\": \"1.0.0\"}}        | {\"v\": true}               | false",
            "{\"v\": {\"$vlt\": \"1.0.0\"}}         | {\"v\": true}               | false",
            "{\"v\": {\"$vlte\": \"1.0.0\"}}        | {\"v\": true}               | false",
            "{\"v\": {\"$veq\": \"1.0.0\"}}         | {\"v\": true}               | false",
            "{\"v\": {\"$vne\": \"1.0.0\"}}         | {\"v\": true}               | true",
            "{\"v\": {\"$veq\": \"0.0.0\"}}         | {}                          | false",
            "{\"v\": {\"$vne\": \"0.0.0\"}}         | {}                          | true",
            "{\"v\": {\"$vlte\": \"9.9.9\"}}        | {\"v\": null}               | false",
            "{\"v\": {\"$veq\": true}}              | {\"v\": true}               | false",
            "{\"v\": {\"$vne\": true}}              | {\"v\": true}               | true",
            "{\"v\": {\"$vgte\": null}}             | {}                          | false",
            "{\"v\": {\"$vne\": [\"1\"]}}           | {\"v\": [\"1\"]}            | true",
    })
    @DisplayName("Compares versions by parts, and only $vne matches non-orderable operands")
    void comparesVersions(String condition, String attributes, String expected) {
        // Given
        Boolean wanted = expectedResult(expected);

        // When
        Boolean result = evaluate(condition, attributes);

        // Then
        assertEquals(wanted, result);
    }

    @ParameterizedTest(name = "{0} on {1} -> {2}")
    @CsvSource(delimiter = '|', value = {
            "{\"v\": {\"$elemMatch\": {\"$eq\": 0}}}                  | {\"v\": [0]}                              | true",
            "{\"v\": {\"$elemMatch\": {\"$eq\": \"\"}}}               | {\"v\": [\"\"]}                           | true",
            "{\"v\": {\"$elemMatch\": {\"$eq\": false}}}              | {\"v\": [false]}                          | true",
            "{\"v\": {\"$elemMatch\": {\"$exists\": false}}}          | {\"v\": [null]}                           | false",
            "{\"v\": {\"$elemMatch\": {\"$eq\": null}}}               | {\"v\": [null, null]}                     | false",
            "{\"v\": {\"$elemMatch\": {\"$gt\": 5}}}                  | {\"v\": [1, 6]}                           | true",
            "{\"v\": {\"$elemMatch\": {\"$gt\": 5}}}                  | {\"v\": [1, 2]}                           | false",
            "{\"v\": {\"$elemMatch\": {\"$gt\": 5}}}                  | {\"v\": 6}                                | false",
            "{\"v\": {\"$elemMatch\": {\"$gt\": 5}}}                  | {}                                        | false",
            "{\"v\": {\"$elemMatch\": {\"name\": \"x\"}}}             | {\"v\": [{\"name\": \"y\"}, {\"name\": \"x\"}]} | true",
            "{\"v\": {\"$elemMatch\": {\"name\": \"x\"}}}             | {\"v\": [{\"name\": \"y\"}]}              | false",
            "{\"v\": {\"$elemMatch\": {\"$or\": [{\"name\": \"x\"}]}}} | {\"v\": [{\"name\": \"x\"}]}             | true",
            "{\"v\": {\"$elemMatch\": {\"$foo\": 1}}}                 | {\"v\": [1]}                              | null",
            "{\"v\": {\"$elemMatch\": {\"$foo\": 1}}}                 | {\"v\": []}                               | false",
    })
    @DisplayName("Tests $elemMatch on each non-null element, including falsy ones")
    void testsElemMatchOnNonNullElements(String condition, String attributes, String expected) {
        // Given
        Boolean wanted = expectedResult(expected);

        // When
        Boolean result = evaluate(condition, attributes);

        // Then
        assertEquals(wanted, result);
    }

    @ParameterizedTest(name = "{0} on {1} -> {2}")
    @CsvSource(delimiter = '|', value = {
            "{\"v\": {\"$all\": [\"a\", \"c\"]}}    | {\"v\": [\"a\", \"b\", \"c\"]} | true",
            "{\"v\": {\"$all\": [\"a\", \"x\"]}}    | {\"v\": [\"a\", \"b\"]}        | false",
            "{\"v\": {\"$all\": []}}                | {\"v\": []}                    | true",
            "{\"v\": {\"$all\": [\"a\"]}}           | {\"v\": \"a\"}                 | false",
            "{\"v\": {\"$all\": [\"a\"]}}           | {}                             | false",
            "{\"v\": {\"$all\": \"a\"}}             | {\"v\": [\"a\"]}               | false",
            "{\"v\": {\"$all\": [\"A\"]}}           | {\"v\": [\"a\"]}               | false",
            "{\"v\": {\"$alli\": [\"A\", \"b\"]}}   | {\"v\": [\"a\", \"B\"]}        | true",
            "{\"v\": {\"$alli\": [\"A\", \"z\"]}}   | {\"v\": [\"a\", \"B\"]}        | false",
            "{\"v\": {\"$all\": [{\"$gt\": 5}]}}    | {\"v\": [1, 7]}                | true",
            "{\"v\": {\"$all\": [{\"$foo\": 1}]}}   | {\"v\": [1]}                   | null",
            "{\"v\": {\"$all\": [{\"$foo\": 1}, \"x\"]}} | {\"v\": [1]}              | false",
    })
    @DisplayName("Requires every $all item to match some element of the attribute array")
    void requiresEveryAllItemToMatch(String condition, String attributes, String expected) {
        // Given
        Boolean wanted = expectedResult(expected);

        // When
        Boolean result = evaluate(condition, attributes);

        // Then
        assertEquals(wanted, result);
    }

    @ParameterizedTest(name = "{0} on {1} -> {2}")
    @CsvSource(delimiter = '|', value = {
            "{\"v\": {\"$size\": 2}}            | {\"v\": [1, 2]}  | true",
            "{\"v\": {\"$size\": 0}}            | {\"v\": []}      | true",
            "{\"v\": {\"$size\": 3}}            | {\"v\": [1, 2]}  | false",
            "{\"v\": {\"$size\": {\"$gt\": 1}}} | {\"v\": [1, 2]}  | true",
            "{\"v\": {\"$size\": 1}}            | {\"v\": \"a\"}   | false",
            "{\"v\": {\"$size\": 0}}            | {}               | false",
    })
    @DisplayName("Tests $size against the length of an array attribute only")
    void testsSizeOfArrayAttributesOnly(String condition, String attributes, String expected) {
        // Given
        Boolean wanted = expectedResult(expected);

        // When
        Boolean result = evaluate(condition, attributes);

        // Then
        assertEquals(wanted, result);
    }

    @ParameterizedTest(name = "{0} on {1} -> {2}")
    @CsvSource(delimiter = '|', value = {
            "{\"v\": {\"$in\": [\"a\", \"b\"]}}    | {\"v\": \"a\"}            | true",
            "{\"v\": {\"$in\": [\"a\"]}}           | {\"v\": \"A\"}            | false",
            "{\"v\": {\"$ini\": [\"a\"]}}          | {\"v\": \"A\"}            | true",
            "{\"v\": {\"$in\": [\"x\", \"b\"]}}    | {\"v\": [\"a\", \"b\"]}   | true",
            "{\"v\": {\"$in\": [\"x\"]}}           | {\"v\": [\"a\", \"b\"]}   | false",
            "{\"v\": {\"$in\": [1]}}               | {\"v\": 1.0}              | true",
            "{\"v\": {\"$in\": [1]}}               | {\"v\": \"1\"}            | false",
            "{\"v\": {\"$in\": [null]}}            | {}                        | true",
            "{\"v\": {\"$in\": [[1]]}}             | {\"v\": [1]}              | false",
            "{\"v\": {\"$in\": \"a\"}}             | {\"v\": \"a\"}            | false",
            "{\"v\": {\"$nin\": [\"a\"]}}          | {\"v\": \"b\"}            | true",
            "{\"v\": {\"$nin\": [\"a\"]}}          | {}                        | true",
            "{\"v\": {\"$nin\": [\"a\"]}}          | {\"v\": [\"b\", \"a\"]}   | false",
            "{\"v\": {\"$nini\": [\"A\"]}}         | {\"v\": \"a\"}            | false",
            "{\"v\": {\"$nini\": [\"A\"]}}         | {\"v\": \"b\"}            | true",
            "{\"v\": {\"$nin\": \"a\"}}            | {\"v\": \"b\"}            | false",
    })
    @DisplayName("Tests membership with arrays on either side")
    void testsMembershipWithArraysOnEitherSide(String condition, String attributes, String expected) {
        // Given
        Boolean wanted = expectedResult(expected);

        // When
        Boolean result = evaluate(condition, attributes);

        // Then
        assertEquals(wanted, result);
    }

    @ParameterizedTest(name = "{0} on {1} -> {2}")
    @CsvSource(delimiter = '|', value = {
            "{\"id\": {\"$inGroup\": \"ids\"}}         | {\"id\": \"u1\"}          | true",
            "{\"id\": {\"$inGroup\": \"ids\"}}         | {\"id\": \"u3\"}          | false",
            "{\"id\": {\"$inGroup\": \"ids\"}}         | {\"id\": [\"x\", \"u2\"]} | true",
            "{\"id\": {\"$notInGroup\": \"ids\"}}      | {\"id\": \"u3\"}          | true",
            "{\"id\": {\"$notInGroup\": \"ids\"}}      | {}                        | true",
            "{\"id\": {\"$inGroup\": \"missing\"}}     | {\"id\": \"u1\"}          | false",
            "{\"id\": {\"$notInGroup\": \"missing\"}}  | {\"id\": \"u1\"}          | true",
            "{\"id\": {\"$inGroup\": \"typed\"}}       | {\"id\": \"u1\"}          | true",
            "{\"id\": {\"$notInGroup\": \"typed\"}}    | {\"id\": \"u1\"}          | false",
            "{\"id\": {\"$inGroup\": \"cond\"}}        | {\"id\": \"u1\"}          | false",
            "{\"id\": {\"$notInGroup\": \"cond\"}}     | {\"id\": \"u1\"}          | false",
            "{\"id\": {\"$inGroup\": \"badList\"}}     | {\"id\": \"u1\"}          | false",
            "{\"id\": {\"$notInGroup\": \"badList\"}}  | {\"id\": \"u1\"}          | false",
            "{\"id\": {\"$inGroup\": \"future\"}}      | {\"id\": \"u1\"}          | null",
            "{\"id\": {\"$notInGroup\": \"future\"}}   | {\"id\": \"u1\"}          | null",
            "{\"id\": {\"$inGroup\": 1}}               | {\"id\": 1}               | false",
    })
    @DisplayName("Tests saved group membership by id")
    void testsSavedGroupMembership(String condition, String attributes, String expected) {
        // Given
        Boolean wanted = expectedResult(expected);

        // When
        Boolean result = evaluate(condition, attributes);

        // Then
        assertEquals(wanted, result);
    }

    @ParameterizedTest(name = "{0} on {1} -> {2}")
    @CsvSource(delimiter = '|', value = {
            "{\"v\": {\"$exists\": true}}     | {\"v\": 0}      | true",
            "{\"v\": {\"$exists\": true}}     | {\"v\": null}   | false",
            "{\"v\": {\"$exists\": true}}     | {}              | false",
            "{\"v\": {\"$exists\": false}}    | {}              | true",
            "{\"v\": {\"$exists\": false}}    | {\"v\": \"\"}   | false",
            "{\"v\": {\"$type\": \"string\"}} | {\"v\": \"a\"}  | true",
            "{\"v\": {\"$type\": \"number\"}} | {\"v\": \"1\"}  | false",
            "{\"v\": {\"$type\": \"array\"}}  | {\"v\": []}     | true",
            "{\"v\": {\"$type\": \"object\"}} | {\"v\": {}}     | true",
            "{\"v\": {\"$type\": \"null\"}}   | {}              | true",
            "{\"v\": {\"$type\": \"boolean\"}} | {\"v\": false} | true",
    })
    @DisplayName("Tests presence with $exists and the JSON type with $type")
    void testsPresenceAndType(String condition, String attributes, String expected) {
        // Given
        Boolean wanted = expectedResult(expected);

        // When
        Boolean result = evaluate(condition, attributes);

        // Then
        assertEquals(wanted, result);
    }

    @ParameterizedTest(name = "{0} on {1} -> {2}")
    @CsvSource(delimiter = '|', value = {
            "{\"v\": {\"$not\": {\"$gt\": 5}}}           | {\"v\": 3} | true",
            "{\"v\": {\"$not\": {\"$gt\": 5}}}           | {\"v\": 6} | false",
            "{\"v\": {\"$not\": 5}}                      | {\"v\": 5} | false",
            "{\"v\": {\"$not\": 5}}                      | {\"v\": 4} | true",
            "{\"v\": {\"$not\": {\"$foo\": 1}}}          | {\"v\": 1} | null",
            "{\"v\": {\"$foo\": 1}}                      | {\"v\": 1} | null",
            "{\"v\": {\"$foo\": 1, \"$eq\": 2}}          | {\"v\": 1} | false",
            "{\"v\": {\"$foo\": 1, \"$eq\": 1}}          | {\"v\": 1} | null",
            "{\"v\": {}}                                 | {\"v\": 1} | true",
    })
    @DisplayName("Negates a value test with $not and keeps unknown operators unknown")
    void negatesValueTestsAndKeepsUnknownOperatorsUnknown(String condition, String attributes, String expected) {
        // Given
        Boolean wanted = expectedResult(expected);

        // When
        Boolean result = evaluate(condition, attributes);

        // Then
        assertEquals(wanted, result);
    }

    @ParameterizedTest
    @ValueSource(strings = {"$or", "$nor", "$and", "$savedGroup"})
    @DisplayName("Never matches a condition operator used on a value")
    void neverMatchesConditionOperatorOnValue(String operator) {
        // Given
        Cond condition = CondParser.parse(json("{\"v\": {\"" + operator + "\": [{\"$eq\": 1}]}}"));

        // When
        Boolean result = condition.eval(json("{\"v\": 1}"), new CondScope(SAVED_GROUPS));

        // Then
        assertEquals(Boolean.FALSE, result);
    }

    @ParameterizedTest(name = "{0} with {1} is {2}")
    @CsvSource(delimiter = '|', value = {
            "{\"v\": {\"$type\": [\"string\"]}}         | {\"v\": \"x\"} | false",
            "{\"v\": {\"$type\": {}}}                   | {\"v\": \"x\"} | false",
            "{\"v\": {\"$type\": null}}                 | {}            | false",
            "{\"v\": {\"$not\": {\"$type\": [\"string\"]}}} | {\"v\": \"x\"} | true",
    })
    @DisplayName("Matches $type only against a string operand, like strict equality in JS")
    void matchesTypeOnlyAgainstStringOperand(String condition, String attributes, boolean expected) {
        // Given
        Cond parsed = CondParser.parse(json(condition));

        // When
        Boolean result = parsed.eval(json(attributes), new CondScope(null));

        // Then
        assertEquals(expected, result);
    }

    @ParameterizedTest(name = "{0} with {1} is {2}")
    @CsvSource(delimiter = '|', value = {
            "{\"v\": {\"$exists\": 1}}     | {\"v\": \"x\"} | true",
            "{\"v\": {\"$exists\": \"yes\"}} | {}            | false",
            "{\"v\": {\"$exists\": \"\"}}    | {}            | true",
            "{\"v\": {\"$exists\": null}}  | {}            | true",
            "{\"v\": {\"$exists\": 0}}     | {\"v\": 1}    | false",
            "{\"v\": {\"$exists\": []}}    | {\"v\": 1}    | true",
    })
    @DisplayName("Reads the $exists operand with JavaScript truthiness")
    void readsExistsOperandWithJavaScriptTruthiness(String condition, String attributes, boolean expected) {
        // Given
        Cond parsed = CondParser.parse(json(condition));

        // When
        Boolean result = parsed.eval(json(attributes), new CondScope(null));

        // Then
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("Fails on an $elemMatch condition that is not an object only once an element is reached")
    void failsOnNonObjectElemMatchOnlyWhenElementReached() {
        // Given
        Cond condition = CondParser.parse(json("{\"v\": {\"$elemMatch\": 5}}"));
        CondScope scope = new CondScope(null);

        // When
        Boolean empty = condition.eval(json("{\"v\": []}"), scope);
        Boolean onlyNulls = condition.eval(json("{\"v\": [null]}"), scope);
        Boolean notArray = condition.eval(json("{\"v\": \"x\"}"), scope);

        // Then
        assertFalse(empty);
        assertFalse(onlyNulls);
        assertFalse(notArray);
        assertThrows(MalformedConditionException.class, () -> condition.eval(json("{\"v\": [\"x\"]}"), scope));
    }

    @Test
    @DisplayName("Parses a known operator into a test of the given operand")
    void parsesKnownOperator() {
        // Given
        ValueTest test = Operators.parse("$gt", new JsonPrimitive(5));
        CondScope scope = new CondScope(null);

        // When
        Boolean above = test.test(new JsonPrimitive(6), scope);
        Boolean below = test.test(new JsonPrimitive(4), scope);

        // Then
        assertTrue(above);
        assertFalse(below);
    }

    @Test
    @DisplayName("Parses an unknown operator into an unknown result")
    void parsesUnknownOperatorAsUnknown() {
        // Given
        ValueTest test = Operators.parse("$between", JsonParser.parseString("[1, 2]"));

        // When
        Boolean result = test.test(new JsonPrimitive(1), new CondScope(null));

        // Then
        assertNull(result);
    }

    private static Boolean evaluate(String condition, String attributes) {
        return CondParser.parse(json(condition)).eval(json(attributes), new CondScope(SAVED_GROUPS));
    }

    private static Boolean expectedResult(String expected) {
        return "null".equals(expected) ? null : Boolean.valueOf(expected);
    }

    private static JsonObject json(String text) {
        return JsonParser.parseString(text).getAsJsonObject();
    }
}
