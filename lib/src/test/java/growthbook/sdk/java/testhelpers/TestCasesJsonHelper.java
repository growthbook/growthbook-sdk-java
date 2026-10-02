package growthbook.sdk.java.testhelpers;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Getter;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

/**
 * A helper class for working with the <a href="https://github.com/growthbook/growthbook/blob/main/packages/sdk-js/test/cases.json">test cases</a>.
 * Loads the unedited upstream {@code cases/cases.json}, adds {@code cases/java.json} and removes
 * {@code cases/exclusions.json}. See {@code src/test/resources/cases/README.md}.
 */
public class TestCasesJsonHelper implements ITestCasesJsonHelper {

    @Override
    public JsonObject getTestCases() {
        return this.testCases;
    }

    @Override
    public JsonArray evalConditionTestCases() {
        return this.testCases.get("evalCondition").getAsJsonArray();
    }

    @Override
    public JsonArray getHNVTestCases() {
        return this.testCases.get("hash").getAsJsonArray();
    }

    @Override
    public JsonArray getInNamespaceTestCases() {
        return this.testCases.get("inNamespace").getAsJsonArray();
    }

    @Override
    public JsonArray getBucketRangeTestCases() {
        return this.testCases.get("getBucketRange").getAsJsonArray();
    }

    @Override
    public JsonArray featureTestCases() {
        return this.testCases.get("feature").getAsJsonArray();
    }

    @Override
    public JsonArray runTestCases() {
        return this.testCases.get("run").getAsJsonArray();
    }

    @Override
    public JsonArray getChooseVariationTestCases() {
        return this.testCases.get("chooseVariation").getAsJsonArray();
    }

    @Override
    public JsonArray getEqualWeightsTestCases() {
        return this.testCases.get("getEqualWeights").getAsJsonArray();
    }

    @Override
    public JsonArray decryptionTestCases() {
        return this.testCases.get("decrypt").getAsJsonArray();
    }

    @Override
    public JsonArray getQueryStringOverrideTestCases() {
        return this.testCases.get("getQueryStringOverride").getAsJsonArray();
    }

    @Override
    public JsonArray getStickyBucketTestCases() {
        return this.testCases.get("stickyBucket").getAsJsonArray();
    }

    // region Initialization

    private final JsonObject testCases;

    @Getter
    private final String demoFeaturesJson;

    private static TestCasesJsonHelper instance = null;

    private TestCasesJsonHelper() {
        this.testCases = initializeTestCasesFromFile();
        this.demoFeaturesJson = initializeDemoFeaturesFromFile();
    }

    public static TestCasesJsonHelper getInstance() {
        if (instance == null) {
            instance = new TestCasesJsonHelper();
            System.out.printf("Creating a TestCasesJsonHelper instance: %s%n", instance);
        }

        return instance;
    }

    private JsonObject initializeTestCasesFromFile() {
        String casesPath = getResourceDirectoryPath() + "/cases";

        JsonObject corpus = readJsonObject(casesPath + "/cases.json");
        appendCases(corpus, readJsonObject(casesPath + "/java.json"));
        applyExclusions(corpus, readJsonObject(casesPath + "/exclusions.json"));
        return corpus;
    }

    private static JsonObject readJsonObject(String path) {
        try (FileReader reader = new FileReader(path)) {
            return new Gson().fromJson(reader, JsonObject.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Adds the Java-only cases to the upstream corpus. A Java case may not reuse an upstream
     * case name, so it can never override an upstream expectation.
     *
     * @param base      the upstream corpus, modified in place
     * @param additions suites of Java-only cases, with the same nesting as the upstream corpus
     */
    static void appendCases(JsonObject base, JsonObject additions) {
        for (Map.Entry<String, JsonElement> suite : additions.entrySet()) {
            JsonElement cases = suite.getValue();
            if (cases.isJsonObject()) {
                if (!base.has(suite.getKey())) {
                    base.add(suite.getKey(), new JsonObject());
                }
                appendCases(base.getAsJsonObject(suite.getKey()), cases.getAsJsonObject());
            } else if (cases.isJsonArray()) {
                if (!base.has(suite.getKey())) {
                    base.add(suite.getKey(), new JsonArray());
                }
                JsonArray target = base.getAsJsonArray(suite.getKey());
                for (JsonElement testCase : cases.getAsJsonArray()) {
                    String name = testCase.getAsJsonArray().get(0).getAsString();
                    if (findCaseIndex(target, name) >= 0) {
                        throw new IllegalStateException("Java case shadows an existing case: " + name);
                    }
                    target.add(testCase);
                }
            } else {
                throw new IllegalStateException("java.json must contain suites of cases, not " + suite.getKey());
            }
        }
    }

    /**
     * Removes unsupported suites and cases before tests run. Every exclusion needs a reason, and
     * an exclusion that matches nothing is an error, so the list cannot go stale.
     *
     * @param corpus     the merged corpus, modified in place
     * @param exclusions {@code {"suites": {path: reason}, "cases": {path: {name: reason}}}}, where a
     *                   path is dot-separated for nested suites, such as {@code savedGroupReferencesV2.feature}
     */
    static void applyExclusions(JsonObject corpus, JsonObject exclusions) {
        for (Map.Entry<String, JsonElement> suite : exclusions.getAsJsonObject("cases").entrySet()) {
            JsonArray target = findSuite(corpus, suite.getKey()).getAsJsonArray();
            for (Map.Entry<String, JsonElement> exclusion : suite.getValue().getAsJsonObject().entrySet()) {
                String name = exclusion.getKey();
                requireReason(exclusion.getValue(), suite.getKey() + "/" + name);
                int index = findCaseIndex(target, name);
                if (index < 0) {
                    throw new IllegalStateException("Stale case exclusion: " + suite.getKey() + "/" + name);
                }
                target.remove(index);
            }
        }
        for (Map.Entry<String, JsonElement> suite : exclusions.getAsJsonObject("suites").entrySet()) {
            requireReason(suite.getValue(), suite.getKey());
            clearSuite(findSuite(corpus, suite.getKey()));
        }
    }

    private static JsonElement findSuite(JsonObject corpus, String path) {
        JsonElement suite = corpus;
        for (String key : path.split("\\.")) {
            if (!suite.isJsonObject() || !suite.getAsJsonObject().has(key)) {
                throw new IllegalStateException("Unknown excluded suite: " + path);
            }
            suite = suite.getAsJsonObject().get(key);
        }
        return suite;
    }

    private static void clearSuite(JsonElement suite) {
        if (suite.isJsonArray()) {
            JsonArray cases = suite.getAsJsonArray();
            while (cases.size() > 0) {
                cases.remove(0);
            }
        } else if (suite.isJsonObject()) {
            for (Map.Entry<String, JsonElement> child : suite.getAsJsonObject().entrySet()) {
                clearSuite(child.getValue());
            }
        } else {
            throw new IllegalStateException("Excluded suite must contain cases");
        }
    }

    private static int findCaseIndex(JsonArray cases, String name) {
        for (int i = 0; i < cases.size(); i++) {
            JsonElement first = cases.get(i).getAsJsonArray().get(0);
            if (first.isJsonPrimitive() && first.getAsJsonPrimitive().isString() && first.getAsString().equals(name)) {
                return i;
            }
        }
        return -1;
    }

    private static void requireReason(JsonElement reason, String label) {
        if (!reason.isJsonPrimitive() || reason.getAsString().trim().isEmpty()) {
            throw new IllegalStateException("Missing exclusion reason for " + label);
        }
    }

    private String initializeDemoFeaturesFromFile() {
        String absolutePath = getResourceDirectoryPath();

        Gson gson = new Gson();
        try {
            JsonObject features = gson.fromJson(new FileReader(absolutePath + "/demo-features-001.json"), JsonObject.class);
            return features.toString();
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private String getResourceDirectoryPath() {
        Path resourceDirectory = Paths.get("src", "test", "resources");
        String absolutePath = resourceDirectory.toFile().getAbsolutePath();
        System.out.println(absolutePath);
        return absolutePath;
    }

    // endregion Initialization
}
