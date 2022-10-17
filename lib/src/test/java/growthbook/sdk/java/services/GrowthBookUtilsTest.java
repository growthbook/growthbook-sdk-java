package growthbook.sdk.java.services;

import com.google.gson.JsonArray;
import growthbook.sdk.java.TestHelpers.TestCasesJsonHelper;
import growthbook.sdk.java.models.Namespace;
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
    void canVerifyAUserIsInANamespace() {
        JsonArray testCases = helper.getInNamespaceTestCases();

        testCases.forEach(jsonElement -> {
            JsonArray testCase = (JsonArray) jsonElement;

            String testDescription = testCase.get(0).getAsString();
            String userId = testCase.get(1).getAsString();

            Namespace namespace = GrowthBookJsonUtils.getInstance()
                    .gson.fromJson(testCase.get(2).getAsJsonArray(), Namespace.class);
            Boolean expected = testCase.get(3).getAsBoolean();

            assertEquals(
                    expected,
                    GrowthBookUtils.inNameSpace(userId, namespace),
                    String.format("Namespace test case failure: %s", testDescription)
            );
        });
    }

    @Test
    void providesTestCaseData() {
        System.out.printf("Providing test cases: %s", helper.getTestCases());
        assertNotNull(helper.getTestCases());
    }
}
