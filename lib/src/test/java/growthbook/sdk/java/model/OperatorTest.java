package growthbook.sdk.java.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OperatorTest {

    @ParameterizedTest(name = "{0}")
    @EnumSource(Operator.class)
    @DisplayName("Parses every operator from its raw string")
    void parsesEveryOperatorFromRawString(Operator operator) {
        // When
        Operator parsed = Operator.fromString(operator.toString());

        // Then
        assertEquals(operator, parsed);
    }

    @Test
    @DisplayName("Parses $nin as NIN")
    void parsesNinAsNin() {
        // When
        Operator parsed = Operator.fromString("$nin");

        // Then
        assertEquals(Operator.NIN, parsed);
    }

    @ParameterizedTest(name = "\"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"$foo", "nin", "$NIN"})
    @DisplayName("Returns null for an unknown operator")
    void returnsNullForUnknownOperator(String raw) {
        // When
        Operator parsed = Operator.fromString(raw);

        // Then
        assertNull(parsed);
    }
}
