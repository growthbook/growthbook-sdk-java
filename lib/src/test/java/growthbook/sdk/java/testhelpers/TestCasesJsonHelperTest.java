package growthbook.sdk.java.testhelpers;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class TestCasesJsonHelperTest {

    @Test
    void getInstance_createASingleInstance() {
        TestCasesJsonHelper first = TestCasesJsonHelper.getInstance();
        TestCasesJsonHelper second = TestCasesJsonHelper.getInstance();

        assertEquals(first.toString(), second.toString());
    }

    @Test
    void getTestCases_returnsTestCasesAsJson() {
        JsonObject testCases = TestCasesJsonHelper.getInstance().getTestCases();

        assertNotNull(testCases);
        assertEquals("0.2.3", testCases.get("specVersion").getAsString());
    }
}
