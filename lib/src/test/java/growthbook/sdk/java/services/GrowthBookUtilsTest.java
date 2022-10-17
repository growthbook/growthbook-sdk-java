package growthbook.sdk.java.services;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GrowthBookUtilsTest {
    @Test
    void canHashUsingFowlerNollVoAlgo() {
        // Test cases: https://github.com/growthbook/growthbook/blob/main/packages/sdk-js/test/cases.json#L1319-L1326
        assertEquals(0.22f, GrowthBookUtils.hash("a"));
        assertEquals(0.077f, GrowthBookUtils.hash("b"));
        assertEquals(0.946f, GrowthBookUtils.hash("ab"));
        assertEquals(0.652f, GrowthBookUtils.hash("def"));
        assertEquals(0.549f, GrowthBookUtils.hash("8952klfjas09ujkasdf"));
        assertEquals(0.563f, GrowthBookUtils.hash("___)((*\":&"));
    }
}
