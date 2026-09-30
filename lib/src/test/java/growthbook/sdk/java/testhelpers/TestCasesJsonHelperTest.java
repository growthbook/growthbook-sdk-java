package growthbook.sdk.java.testhelpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.FileReader;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class TestCasesJsonHelperTest {

    @Test
    void getInstance_createASingleInstance() {
        TestCasesJsonHelper first = TestCasesJsonHelper.getInstance();
        TestCasesJsonHelper second = TestCasesJsonHelper.getInstance();

        assertEquals(first.toString(), second.toString());
    }

    @Test
    void getTestCases_returnsTestCasesAsJson() throws IOException {
        JsonObject testCases = TestCasesJsonHelper.getInstance().getTestCases();
        JsonObject source;
        try (FileReader reader = new FileReader("src/test/resources/cases/source.json")) {
            source = new Gson().fromJson(reader, JsonObject.class);
        }

        assertNotNull(testCases);
        assertEquals(source.get("specVersion").getAsString(), testCases.get("specVersion").getAsString());
    }

    @Test
    void getTestCases_includesJavaCasesAndOmitsExclusions() {
        JsonObject testCases = TestCasesJsonHelper.getInstance().getTestCases();

        assertTrue(testCases.getAsJsonArray("evalCondition").toString().contains("$notRegex - pass"));
        assertEquals(0, testCases.getAsJsonArray("contextualBandit").size());
        assertEquals(0, testCases.getAsJsonObject("savedGroupReferencesV2").getAsJsonArray("feature").size());
    }
}
