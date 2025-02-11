package growthbook.sdk.java;

import static org.junit.jupiter.api.Assertions.assertEquals;

import growthbook.sdk.java.util.StringUtils;
import org.junit.jupiter.api.Test;

class StringUtilsTest {

    @Test
    void padLeftZeros() {
        String input = "1.10.20";
        String result = StringUtils.paddedVersionString(input);

        assertEquals("00001-00010-00020-~", result);
    }
}
