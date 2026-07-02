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
    void padLeftZeros() {
        String input = "1.10.20";
        String result = StringUtils.paddedVersionString(input);

        assertEquals("00001-00010-00020-~", result);
    }
}
