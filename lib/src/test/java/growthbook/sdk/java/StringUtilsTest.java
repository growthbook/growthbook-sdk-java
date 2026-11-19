package growthbook.sdk.java;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import growthbook.sdk.java.util.StringUtils;
import org.junit.jupiter.api.Test;

class StringUtilsTest {
    @Test
    void isBlankReturnsTrueForNullEmptyAndWhitespace() {
        assertTrue(StringUtils.isBlank(null));
        assertTrue(StringUtils.isBlank(""));
        assertTrue(StringUtils.isBlank("   "));
    }

    @Test
    void isBlankReturnsFalseForNonBlankValues() {
        assertFalse(StringUtils.isBlank("features"));
    }

    @Test
    void paddedVersionStringPadsNumbersWithSpaces() {
        String input = "1.10.20";
        String result = StringUtils.paddedVersionString(input);

        assertEquals("    1-   10-   20-~", result);
    }

    @Test
    void paddedVersionStringKeepsEmptyPartsLikeJavaScript() {
        // Expected values are the JS SDK's paddedVersionString
        assertEquals("-    1", StringUtils.paddedVersionString("-1"));
        assertEquals(" 5", StringUtils.paddedVersionString(" 5"));
        assertEquals("    1-    2--~", StringUtils.paddedVersionString("1.2."));
        assertEquals("    1-    2-    3-rc-    1", StringUtils.paddedVersionString("v1.2.3-rc.1+build.7"));
        // A padded number sorts below " 5" and "-1", like JS, where the pad is spaces
        assertTrue(StringUtils.paddedVersionString("1.2.3").compareTo(StringUtils.paddedVersionString(" 5")) < 0);
        assertTrue(StringUtils.paddedVersionString("1.2.3").compareTo(StringUtils.paddedVersionString("-1")) < 0);
    }

    @Test
    void jsNumberToStringMatchesJavaScript() {
        // Expected values are JavaScript's String(number)
        assertEquals("1", StringUtils.jsNumberToString(1));
        assertEquals("1.5", StringUtils.jsNumberToString(1.5));
        assertEquals("1000", StringUtils.jsNumberToString(1e3));
        assertEquals("-12.25", StringUtils.jsNumberToString(-12.25));
        assertEquals("0.30000000000000004", StringUtils.jsNumberToString(0.1 + 0.2));
        assertEquals("123456789012345680000", StringUtils.jsNumberToString(123456789012345678901.0));
        assertEquals("1e+21", StringUtils.jsNumberToString(1e21));
        assertEquals("1e-7", StringUtils.jsNumberToString(1e-7));
        assertEquals("0.000002", StringUtils.jsNumberToString(0.000002));
        assertEquals("1.23e-18", StringUtils.jsNumberToString(123e-20));
        assertEquals("1e+23", StringUtils.jsNumberToString(1e23));
        assertEquals("2e+23", StringUtils.jsNumberToString(2e23));
        assertEquals("5e-324", StringUtils.jsNumberToString(Double.MIN_VALUE));
        assertEquals("1.7976931348623157e+308", StringUtils.jsNumberToString(Double.MAX_VALUE));
        assertEquals("0", StringUtils.jsNumberToString(-0.0));
        assertEquals("NaN", StringUtils.jsNumberToString(Double.NaN));
        assertEquals("-Infinity", StringUtils.jsNumberToString(Double.NEGATIVE_INFINITY));
        // Powers of two, where the shortest digits that round-trip aren't the nearest ones
        assertEquals("5.960464477539063e-8", StringUtils.jsNumberToString(Math.scalb(1.0, -24)));
        assertEquals("7.174648137343064e-43", StringUtils.jsNumberToString(Math.scalb(1.0, -140)));
        assertEquals("7.854549544476363e-90", StringUtils.jsNumberToString(Math.scalb(1.0, -296)));
    }
}
