package growthbook.sdk.java.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class StringUtilsTest {

    @ParameterizedTest(name = "\"{0}\"")
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t\n"})
    @DisplayName("A null, empty or whitespace-only string is blank")
    void nullEmptyOrWhitespaceStringIsBlank(String value) {
        // When
        boolean blank = StringUtils.isBlank(value);

        // Then
        assertTrue(blank);
    }

    @Test
    @DisplayName("A string with visible characters is not blank")
    void stringWithVisibleCharactersIsNotBlank() {
        // When
        boolean blank = StringUtils.isBlank(" features ");

        // Then
        assertFalse(blank);
    }

    @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
    @CsvSource(value = {
            "1.10.20|'    1-   10-   20-~'",
            "-1|'-    1'",
            "' 5'|' 5'",
            "1.2.|'    1-    2--~'",
            "v1.2.3-rc.1+build.7|'    1-    2-    3-rc-    1'"
    }, delimiter = '|')
    @DisplayName("Pads a version string like the JavaScript SDK, keeping empty parts")
    void padsVersionStringLikeJavaScript(String input, String expected) {
        // When
        String result = StringUtils.paddedVersionString(input);

        // Then
        assertEquals(expected, result);
    }

    @ParameterizedTest(name = "\"{0}\"")
    @NullAndEmptySource
    @DisplayName("Pads a null or empty version string as version 0")
    void padsNullOrEmptyVersionAsZero(String input) {
        // When
        String result = StringUtils.paddedVersionString(input);

        // Then
        assertEquals("    0", result);
    }

    @ParameterizedTest(name = "1.2.3 < \"{0}\"")
    @ValueSource(strings = {" 5", "-1"})
    @DisplayName("A padded numeric version sorts below a version whose first part is not all digits")
    void paddedNumericVersionSortsBelowNonNumericFirstPart(String other) {
        // When
        int comparison = StringUtils.paddedVersionString("1.2.3").compareTo(StringUtils.paddedVersionString(other));

        // Then
        assertTrue(comparison < 0);
    }

    @ParameterizedTest(name = "{0} -> \"{1}\"")
    @MethodSource("javaScriptNumberStrings")
    @DisplayName("Formats a number like JavaScript's String(number)")
    void formatsNumberLikeJavaScript(double number, String expected) {
        // When
        String result = StringUtils.jsNumberToString(number);

        // Then
        assertEquals(expected, result);
    }

    static Stream<Arguments> javaScriptNumberStrings() {
        return Stream.of(
                Arguments.of(1, "1"),
                Arguments.of(1.5, "1.5"),
                Arguments.of(1e3, "1000"),
                Arguments.of(-12.25, "-12.25"),
                Arguments.of(0.1 + 0.2, "0.30000000000000004"),
                Arguments.of(123456789012345678901.0, "123456789012345680000"),
                Arguments.of(1e21, "1e+21"),
                Arguments.of(1e-7, "1e-7"),
                Arguments.of(0.000002, "0.000002"),
                Arguments.of(123e-20, "1.23e-18"),
                Arguments.of(1e23, "1e+23"),
                Arguments.of(2e23, "2e+23"),
                Arguments.of(Double.MIN_VALUE, "5e-324"),
                Arguments.of(Double.MAX_VALUE, "1.7976931348623157e+308"),
                Arguments.of(-0.0, "0"),
                Arguments.of(Double.NaN, "NaN"),
                Arguments.of(Double.NEGATIVE_INFINITY, "-Infinity"),
                Arguments.of(Double.POSITIVE_INFINITY, "Infinity"),
                Arguments.of(Math.scalb(1.0, -24), "5.960464477539063e-8"),
                Arguments.of(Math.scalb(1.0, -140), "7.174648137343064e-43"),
                Arguments.of(Math.scalb(1.0, -296), "7.854549544476363e-90")
        );
    }

    @ParameterizedTest(name = "\"{0}\" -> \"{1}\"")
    @MethodSource("normalizedStrings")
    @DisplayName("Normalizes null to an empty string and keeps any other string")
    void normalizesNullToEmptyString(String input, String expected) {
        // When
        String result = StringUtils.normalize(input);

        // Then
        assertEquals(expected, result);
    }

    static Stream<Arguments> normalizedStrings() {
        return Stream.of(
                Arguments.of(null, ""),
                Arguments.of("", ""),
                Arguments.of(" value ", " value ")
        );
    }

    @ParameterizedTest(name = "\"{0}\" to {1} -> \"{2}\"")
    @CsvSource(value = {
            "7|3|'007'",
            "42|5|'00042'",
            "123|3|'123'",
            "12345|3|'12345'",
            "''|2|'00'"
    }, delimiter = '|')
    @DisplayName("Left-pads a string with zeros up to the requested length")
    void leftPadsWithZerosToLength(String input, int length, String expected) {
        // When
        String result = StringUtils.padLeftZeros(input, length);

        // Then
        assertEquals(expected, result);
    }
}
