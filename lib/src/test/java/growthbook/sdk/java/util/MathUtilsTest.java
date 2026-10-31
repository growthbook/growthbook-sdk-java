package growthbook.sdk.java.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class MathUtilsTest {

    @ParameterizedTest(name = "clamp({0}, {1}, {2}) = {3}")
    @CsvSource({
            "5, 1, 10, 5",
            "0, 1, 10, 1",
            "10, 1, 3, 3",
            "1, 1, 3, 1",
            "3, 1, 3, 3"
    })
    @DisplayName("Clamps a value into the inclusive range")
    void clampsValueIntoInclusiveRange(double value, double lower, double upper, double expected) {
        // When
        double result = MathUtils.clamp(value, lower, upper);

        // Then
        assertEquals(expected, result);
    }

    @ParameterizedTest(name = "fnv1a_32(\"{0}\") = {1}")
    @CsvSource({
            "\u00e9, 1812687940",
            "\uD83D\uDE00, 3409036472",
            "abc, 440920331"
    })
    @DisplayName("Hashes UTF-16 code units with FNV-1a like the JavaScript SDK")
    void hashesUtf16CodeUnitsWithFnv1aLikeJavaScript(String input, long expected) {
        // When
        long hash = MathUtils.fnv1a_32(input);

        // Then
        assertEquals(expected, hash);
    }
}
