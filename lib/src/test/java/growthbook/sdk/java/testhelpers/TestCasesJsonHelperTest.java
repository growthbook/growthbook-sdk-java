package growthbook.sdk.java.testhelpers;

import com.google.gson.JsonObject;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TestCasesJsonHelperTest {

    @Test
    @DisplayName("getInstance returns the same instance on every call")
    void getInstanceReturnsSingleInstance() {
        // When
        TestCasesJsonHelper first = TestCasesJsonHelper.getInstance();
        TestCasesJsonHelper second = TestCasesJsonHelper.getInstance();

        // Then
        assertSame(first, second);
    }

    @Test
    @DisplayName("Loads the upstream corpus at the spec version recorded in source.json")
    void loadsUpstreamCorpusAtRecordedSpecVersion() throws IOException {
        // Given
        JsonObject source;
        try (Reader reader = new InputStreamReader(
                new FileInputStream("src/test/resources/cases/source.json"), StandardCharsets.UTF_8)) {
            source = GrowthBookJsonUtils.getInstance().gson.fromJson(reader, JsonObject.class);
        }

        // When
        JsonObject testCases = TestCasesJsonHelper.getInstance().getTestCases();

        // Then
        assertEquals(source.get("specVersion").getAsString(), testCases.get("specVersion").getAsString());
    }

    @Test
    @DisplayName("The loaded corpus includes the Java-only cases and omits excluded suites")
    void includesJavaCasesAndOmitsExclusions() {
        // When
        JsonObject testCases = TestCasesJsonHelper.getInstance().getTestCases();

        // Then
        assertTrue(testCases.getAsJsonArray("evalCondition").toString().contains("$notRegex - pass"));
        assertEquals(0, testCases.getAsJsonArray("contextualBandit").size());
        assertTrue(testCases.getAsJsonObject("savedGroupReferencesV2").getAsJsonArray("feature").size() > 0);
    }

    @Test
    @DisplayName("appendCases adds new cases to existing suites and creates missing nested suites")
    void appendCasesAddsNewCasesAndSuites() {
        // Given
        JsonObject base = json("{\"feature\": [[\"upstream\", true]]}");

        // When
        TestCasesJsonHelper.appendCases(base,
                json("{\"feature\": [[\"java\", true]], \"nested\": {\"run\": [[\"new\", 1]]}}"));

        // Then
        assertEquals("[[\"upstream\",true],[\"java\",true]]", base.getAsJsonArray("feature").toString());
        assertEquals("[[\"new\",1]]", base.getAsJsonObject("nested").getAsJsonArray("run").toString());
    }

    @Test
    @DisplayName("appendCases rejects a Java case that reuses an upstream case name")
    void appendCasesRejectsCaseThatShadowsUpstream() {
        // Given
        JsonObject base = json("{\"feature\": [[\"same name\", true]]}");

        // When
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> TestCasesJsonHelper.appendCases(base, json("{\"feature\": [[\"same name\", false]]}")));

        // Then
        assertEquals("Java case shadows an existing case: same name", error.getMessage());
    }

    @Test
    @DisplayName("applyExclusions removes excluded cases and clears excluded nested suites")
    void applyExclusionsRemovesCasesAndClearsNestedSuites() {
        // Given
        JsonObject corpus = json("{\"feature\": [[\"keep\", 1], [\"drop\", 2]], \"v2\": {\"run\": [[\"a\", 1]]}}");

        // When
        TestCasesJsonHelper.applyExclusions(corpus,
                json("{\"suites\": {\"v2\": \"unsupported\"}, \"cases\": {\"feature\": {\"drop\": \"unsupported\"}}}"));

        // Then
        assertEquals("[[\"keep\",1]]", corpus.getAsJsonArray("feature").toString());
        assertEquals(0, corpus.getAsJsonObject("v2").getAsJsonArray("run").size());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidExclusions")
    @DisplayName("applyExclusions rejects an exclusion list that cannot be applied")
    void applyExclusionsRejectsInvalidExclusions(String description, String corpus, String exclusions, String expectedMessage) {
        // Given
        JsonObject corpusJson = json(corpus);

        // When
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> TestCasesJsonHelper.applyExclusions(corpusJson, json(exclusions)));

        // Then
        assertEquals(expectedMessage, error.getMessage());
    }

    static Stream<Arguments> invalidExclusions() {
        return Stream.of(
                Arguments.of("stale case exclusion",
                        "{\"feature\": [[\"present\", true]]}",
                        "{\"suites\": {}, \"cases\": {\"feature\": {\"absent\": \"unsupported\"}}}",
                        "Stale case exclusion: feature/absent"),
                Arguments.of("unknown suite",
                        "{\"feature\": []}",
                        "{\"suites\": {\"missing.suite\": \"unsupported\"}, \"cases\": {}}",
                        "Unknown excluded suite: missing.suite"),
                Arguments.of("blank reason",
                        "{\"feature\": [[\"present\", true]]}",
                        "{\"suites\": {}, \"cases\": {\"feature\": {\"present\": \" \"}}}",
                        "Missing exclusion reason for feature/present")
        );
    }

    private static JsonObject json(String value) {
        return GrowthBookJsonUtils.getInstance().gson.fromJson(value, JsonObject.class);
    }
}
