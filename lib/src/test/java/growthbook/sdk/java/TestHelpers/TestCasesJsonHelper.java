package growthbook.sdk.java.TestHelpers;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.io.FileNotFoundException;
import java.io.FileReader;
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
    public JsonArray getChooseVariationTestCases() {
        return this.testCases.get("chooseVariation").getAsJsonArray();
    }

    @Override
    public JsonArray getEqualWeightsTestCases() {
        return this.testCases.get("getEqualWeights").getAsJsonArray();
    }

    // region Initialization

    private final JsonObject testCases;
    private static TestCasesJsonHelper instance = null;

    private TestCasesJsonHelper() {
        this.testCases = initializeTestCasesFromFile();
    }

    public static TestCasesJsonHelper getInstance() {
        if (instance == null) {
            instance = new TestCasesJsonHelper();
            System.out.printf("Creating a TestCasesJsonHelper instance: %s%n", instance);
        }

        return instance;
    }

    private JsonObject initializeTestCasesFromFile() {
        Path resourceDirectory = Paths.get("src","test","resources");
        String absolutePath = resourceDirectory.toFile().getAbsolutePath();
        System.out.println(absolutePath);

        Gson gson = new Gson();
        try {
            return (JsonObject) gson.fromJson(new FileReader(absolutePath + "/test-cases.json"), JsonElement.class);
        } catch (FileNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    // endregion Initialization
}
