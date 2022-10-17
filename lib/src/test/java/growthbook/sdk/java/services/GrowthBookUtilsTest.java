package growthbook.sdk.java.services;

import com.google.gson.JsonArray;
import growthbook.sdk.java.TestHelpers.TestCasesJsonHelper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class GrowthBookUtilsTest {
    TestCasesJsonHelper helper = TestCasesJsonHelper.getInstance();

    @Test
    void canHashUsingFowlerNollVoAlgo() {
        JsonArray hnvCases = helper.getHNVTestCases();

        hnvCases.forEach(jsonElement -> {
            JsonArray kv = (JsonArray) jsonElement;

            String input = kv.get(0).getAsString();
            Float expected = kv.get(1).getAsFloat();

            assertEquals(expected, GrowthBookUtils.hash(input));
        });
    }

    @Test
    void providesTestCaseData() {
        System.out.printf("Providing test cases: %s", helper.getTestCases());
        assertNotNull(helper.getTestCases());
    }
}
