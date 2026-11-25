package growthbook.sdk.java.evaluators;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.google.gson.JsonElement;
import growthbook.sdk.java.evaluators.internal.condition.CondScope;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class ConditionTest {

    private static final JsonElement ATTRIBUTES = json("{\"country\":\"UA\",\"age\":30}");

    @ParameterizedTest(name = "{0} -> {1}")
    @CsvSource({
            "$or, OR",
            "$nor, NOR",
            "$and, AND",
            "$not, NOT"
    })
    @DisplayName("Finds the logical operator for its condition key")
    @SuppressWarnings("deprecation")
    void findsLogicalOperatorForConditionKey(String key, String expectedName) {
        // When
        Condition condition = Condition.fromValue(key);

        // Then
        assertEquals(Condition.valueOf(expectedName), condition);
        assertEquals(key, condition.getConditionValue());
    }

    @ParameterizedTest(name = "\"{0}\"")
    @ValueSource(strings = {"country", "$in", "$OR", ""})
    @DisplayName("Finds no logical operator for an attribute path or another operator")
    @SuppressWarnings("deprecation")
    void findsNoLogicalOperatorForOtherKeys(String key) {
        // When
        Condition condition = Condition.fromValue(key);

        // Then
        assertNull(condition);
    }

    @ParameterizedTest(name = "{0} {1} -> {2}")
    @MethodSource("operatorEvaluations")
    @DisplayName("Combines the operand conditions against the attributes like the logical operator")
    @SuppressWarnings("deprecation")
    void combinesOperandsLikeLogicalOperator(String key, String operandJson, Boolean expected) {
        // Given
        Condition condition = Condition.fromValue(key);

        // When
        Boolean result = condition.apply(ATTRIBUTES, json(operandJson), new CondScope(null));

        // Then
        assertEquals(expected, result);
    }

    static Stream<Arguments> operatorEvaluations() {
        return Stream.of(
                Arguments.of("$or", "[{\"country\":\"US\"},{\"age\":30}]", true),
                Arguments.of("$or", "[{\"country\":\"US\"},{\"age\":31}]", false),
                Arguments.of("$nor", "[{\"country\":\"US\"},{\"age\":31}]", true),
                Arguments.of("$nor", "[{\"country\":\"UA\"}]", false),
                Arguments.of("$and", "[{\"country\":\"UA\"},{\"age\":30}]", true),
                Arguments.of("$and", "[{\"country\":\"UA\"},{\"age\":31}]", false),
                Arguments.of("$not", "{\"country\":\"US\"}", true),
                Arguments.of("$not", "{\"country\":\"UA\"}", false)
        );
    }

    @Test
    @DisplayName("Keeps an unsupported operand unknown under negation")
    @SuppressWarnings("deprecation")
    void keepsUnsupportedOperandUnknownUnderNegation() {
        // Given
        JsonElement unsupported = json("{\"country\":{\"$unsupportedOperator\":1}}");

        // When
        Boolean result = Condition.NOT.apply(ATTRIBUTES, unsupported, new CondScope(null));

        // Then
        assertNull(result);
    }

    private static JsonElement json(String json) {
        return GrowthBookJsonUtils.getInstance().gson.fromJson(json, JsonElement.class);
    }
}
