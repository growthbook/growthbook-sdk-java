package growthbook.sdk.java;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class StringUtilsTest {

    @Test
    void padLeftZeros() {
        String input = "1.10.20";
        String result = StringUtils.paddedVersionString(input);

        assertEquals("00001-00010-00020-~", result);
    }
}
