package growthbook.sdk.java.testhelpers;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Getter;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * A helper class for working with the <a href="https://github.com/growthbook/growthbook/blob/main/packages/sdk-js/test/cases.json">test cases</a>.
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
        Path resourceDirectory = getResourceDirectoryPath();
        Path filePath = resourceDirectory.resolve("test-cases.json");

        Gson gson = new Gson();
        try {
            String content = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);

            return gson.fromJson(content, JsonObject.class);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private String initializeDemoFeaturesFromFile() {
        Path resourceDirectory = getResourceDirectoryPath();
        Path filePath = resourceDirectory.resolve("demo-features-001.json");

        Gson gson = new Gson();
        try {
            String content = new String(Files.readAllBytes(filePath), StandardCharsets.UTF_8);
            JsonObject features = gson.fromJson(content, JsonObject.class);
            return features.toString();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Path getResourceDirectoryPath() {
        Path resourceDirectory = Paths.get("src", "test", "resources");
        System.out.println(resourceDirectory);
        return resourceDirectory;
    }

    // endregion Initialization
}
