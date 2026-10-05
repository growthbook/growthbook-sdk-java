package growthbook.sdk.java.testhelpers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.FileReader;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class TestCasesJsonHelperTest {

    private static JsonObject json(String value) {
        return new Gson().fromJson(value, JsonObject.class);
    }

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

    @Test
    void appendCases_addsNewCasesAndSuites() {
        JsonObject base = json("{\"feature\": [[\"upstream\", true]]}");

        TestCasesJsonHelper.appendCases(base, json("{\"feature\": [[\"java\", true]], \"nested\": {\"run\": [[\"new\", 1]]}}"));

        assertEquals(2, base.getAsJsonArray("feature").size());
        assertEquals(1, base.getAsJsonObject("nested").getAsJsonArray("run").size());
    }

    @Test
    void appendCases_rejectsCaseThatShadowsUpstream() {
        JsonObject base = json("{\"feature\": [[\"same name\", true]]}");

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> TestCasesJsonHelper.appendCases(base, json("{\"feature\": [[\"same name\", false]]}")));
        assertTrue(error.getMessage().contains("shadows"));
    }

    @Test
    void applyExclusions_removesCasesAndClearsNestedSuites() {
        JsonObject corpus = json("{\"feature\": [[\"keep\", 1], [\"drop\", 2]], \"v2\": {\"run\": [[\"a\", 1]]}}");

        TestCasesJsonHelper.applyExclusions(corpus,
                json("{\"suites\": {\"v2\": \"unsupported\"}, \"cases\": {\"feature\": {\"drop\": \"unsupported\"}}}"));

        assertEquals("[[\"keep\",1]]", corpus.getAsJsonArray("feature").toString());
        assertEquals(0, corpus.getAsJsonObject("v2").getAsJsonArray("run").size());
    }

    @Test
    void applyExclusions_rejectsStaleCaseExclusion() {
        JsonObject corpus = json("{\"feature\": [[\"present\", true]]}");

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> TestCasesJsonHelper.applyExclusions(corpus,
                json("{\"suites\": {}, \"cases\": {\"feature\": {\"absent\": \"unsupported\"}}}")));
        assertTrue(error.getMessage().contains("Stale case exclusion"));
    }

    @Test
    void applyExclusions_rejectsUnknownSuite() {
        JsonObject corpus = json("{\"feature\": []}");

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> TestCasesJsonHelper.applyExclusions(corpus,
                json("{\"suites\": {\"missing.suite\": \"unsupported\"}, \"cases\": {}}")));
        assertTrue(error.getMessage().contains("Unknown excluded suite"));
    }

    @Test
    void applyExclusions_requiresReason() {
        JsonObject corpus = json("{\"feature\": [[\"present\", true]]}");

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> TestCasesJsonHelper.applyExclusions(corpus,
                json("{\"suites\": {}, \"cases\": {\"feature\": {\"present\": \" \"}}}")));
        assertTrue(error.getMessage().contains("Missing exclusion reason"));
    }
}
