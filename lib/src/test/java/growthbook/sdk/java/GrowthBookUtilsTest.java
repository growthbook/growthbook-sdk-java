package growthbook.sdk.java;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.google.gson.JsonArray;
import com.google.gson.reflect.TypeToken;
import growthbook.sdk.java.testhelpers.TestCasesJsonHelper;
import org.junit.jupiter.api.Test;
import javax.annotation.Nullable;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;


class GrowthBookUtilsTest {
    final TestCasesJsonHelper helper = TestCasesJsonHelper.getInstance();

    @Test
    void providesTestCaseData() {
        System.out.printf("Providing test cases: %s", helper.getTestCases());
        assertNotNull(helper.getTestCases());
    }

    @Test
    void test_hashFowlerNollVoAlgo() {
        JsonArray hnvCases = helper.getHNVTestCases();

        hnvCases.forEach(jsonElement -> {
            JsonArray kv = (JsonArray) jsonElement;

            String seed = kv.get(0).getAsString();
            String input = kv.get(1).getAsString();

            Integer hashVersion = kv.get(2).getAsInt();

            Float expected = null;
            if (!kv.get(3).isJsonNull()) {
                // In the case of unsupported hash versions, this method returns null
                expected = kv.get(3).getAsFloat();
            }

            assertEquals(expected, GrowthBookUtils.hash(input, hashVersion, seed));
        });
    }

    @Test
    void test_inNameSpace() {
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
    void test_chooseVariation() {
        JsonArray testCases = helper.getChooseVariationTestCases();

        testCases.forEach(jsonElement -> {
            JsonArray testCase = (JsonArray) jsonElement;

            String testDescription = testCase.get(0).getAsString();

            // Float input (1st arg)
            Float input = testCase.get(1).getAsFloat();

            // Bucket Range input (2nd arg)
            JsonArray bucketRangeTuples = testCase.get(2).getAsJsonArray();
            Type bucketRangeListType = new TypeToken<ArrayList<BucketRange>>() {}.getType();
            ArrayList<BucketRange> bucketRanges = GrowthBookJsonUtils.getInstance().gson.fromJson(bucketRangeTuples, bucketRangeListType);

            Integer expected = testCase.get(3).getAsInt();

            assertEquals(
                    expected,
                    GrowthBookUtils.chooseVariation(input, bucketRanges),
                    String.format("canChooseVariation %s", testDescription)
            );
        });
    }

    @Test
    void test_getEqualWeights() {
        JsonArray testCases = helper.getEqualWeightsTestCases();

        testCases.forEach(jsonElement -> {
            JsonArray testCase = (JsonArray) jsonElement;

            // 1st arg
            int numberOfVariations = testCase.get(0).getAsInt();

            // Expected
            JsonArray expectedArray = testCase.get(1).getAsJsonArray();
            Type floatListType = new TypeToken<List<Float>>() {}.getType();
            List<Float> expected = GrowthBookJsonUtils.getInstance().gson.fromJson(expectedArray, floatListType);

            ArrayList<Float> result = GrowthBookUtils.getEqualWeights(numberOfVariations);

            assertEquals(expected, result);
        });
    }

    @Test
    void test_getQueryStringOverride() {
        JsonArray testCases = helper.getQueryStringOverrideTestCases();

        testCases.forEach(jsonElement -> {
            JsonArray testCase = (JsonArray) jsonElement;
            String testDescription = testCase.get(0).getAsString();

//            System.out.printf("----- Evaluating getQueryStringOverride test case: %s", testDescription);

            // 1st arg -> id
            String id = testCase.get(1).getAsString();

            // 2nd arg -> URL
            String urlString = testCase.get(2).getAsString();

            // 3rd arg -> number of variations
            Integer numberOfVariations = testCase.get(3).getAsInt();

            Integer expected = testCase.get(4).isJsonNull() ? null : testCase.get(4).getAsInt();

            assertEquals(
                    expected,
                    GrowthBookUtils.getQueryStringOverride(id, urlString, numberOfVariations),
                    String.format("Failing test: getQueryStringOverride: %s", testDescription)
            );
        });
    }

    @Test
    void test_getBucketRanges() {
        JsonArray testCases = helper.getBucketRangeTestCases();

        Type floatList = new TypeToken<List<Float>>() {}.getType();
        Type bucketRangeList = new TypeToken<List<BucketRange>>() {}.getType();

        testCases.forEach(jsonElement -> {
            JsonArray testCase = (JsonArray) jsonElement;
            String testDescription = testCase.get(0).getAsString();

            JsonArray arguments = testCase.get(1).getAsJsonArray();
            // 1st - variations
            int variations = arguments.get(0).getAsInt();
            // 2nd - coverage
            float coverage = arguments.get(1).getAsFloat();
            // 3rd - weights or null
            @Nullable ArrayList<Float> weights = null;
            if (arguments.get(2).isJsonArray()) {
                weights = GrowthBookJsonUtils.getInstance().gson.fromJson(arguments.get(2), floatList);
            }
            // expected return value
            List<BucketRange> expected = GrowthBookJsonUtils.getInstance().gson.fromJson(testCase.get(2), bucketRangeList);

            // Results
            List<BucketRange> results = GrowthBookUtils.getBucketRanges(variations, coverage, weights);

            for (int i = 0; i < expected.size(); i++) {
                BucketRange expectedBucketRange = expected.get(i);
                BucketRange resultingBucketRange = results.get(i);
                String error = String.format("%s : expected bucket %s does not equal %s", testDescription, expectedBucketRange, resultingBucketRange);

                assertEquals(expectedBucketRange, resultingBucketRange, error);
            }
        });
    }
}
