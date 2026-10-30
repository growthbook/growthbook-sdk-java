package growthbook.sdk.java;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import growthbook.sdk.java.model.BucketRange;
import growthbook.sdk.java.model.HashAttributeAndHashValue;
import growthbook.sdk.java.model.Namespace;
import growthbook.sdk.java.testhelpers.TestCasesJsonHelper;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import growthbook.sdk.java.util.GrowthBookUtils;
import growthbook.sdk.java.util.MathUtils;
import org.junit.jupiter.api.Test;
import javax.annotation.Nullable;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


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

            Double expected = null;
            if (!kv.get(3).isJsonNull()) {
                // In the case of unsupported hash versions, this method returns null
                expected = kv.get(3).getAsDouble();
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

            // Double input (1st arg)
            Double input = testCase.get(1).getAsDouble();

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
            Type doubleListType = new TypeToken<List<Double>>() {}.getType();
            List<Double> expected = GrowthBookJsonUtils.getInstance().gson.fromJson(expectedArray, doubleListType);

            ArrayList<Double> result = GrowthBookUtils.getEqualWeights(numberOfVariations);

            assertEquals(expected.size(), result.size());
            for (int i = 0; i < expected.size(); i++) {
                assertEquals(roundLikeSpec(expected.get(i)), roundLikeSpec(result.get(i)));
            }
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

        Type doubleList = new TypeToken<List<Double>>() {}.getType();

        testCases.forEach(jsonElement -> {
            JsonArray testCase = (JsonArray) jsonElement;
            String testDescription = testCase.get(0).getAsString();

            JsonArray arguments = testCase.get(1).getAsJsonArray();
            // 1st - variations
            int variations = arguments.get(0).getAsInt();
            // 2nd - coverage
            double coverage = arguments.get(1).getAsDouble();
            // 3rd - weights or null
            @Nullable ArrayList<Double> weights = null;
            if (arguments.get(2).isJsonArray()) {
                weights = GrowthBookJsonUtils.getInstance().gson.fromJson(arguments.get(2), doubleList);
            }
            // expected return value
            JsonArray expected = testCase.get(2).getAsJsonArray();

            // Results
            List<BucketRange> results = GrowthBookUtils.getBucketRanges(variations, coverage, weights);

            assertEquals(expected.size(), results.size(), testDescription);
            for (int i = 0; i < expected.size(); i++) {
                JsonArray expectedBucketRange = expected.get(i).getAsJsonArray();
                BucketRange resultingBucketRange = results.get(i);
                String error = String.format("%s : expected bucket %s does not equal %s", testDescription, expectedBucketRange, resultingBucketRange);

                assertEquals(roundLikeSpec(expectedBucketRange.get(0).getAsDouble()), roundLikeSpec(resultingBucketRange.getRangeStart()), error);
                assertEquals(roundLikeSpec(expectedBucketRange.get(1).getAsDouble()), roundLikeSpec(resultingBucketRange.getRangeEnd()), error);
            }
        });
    }

    @Test
    void test_getBucketRanges_matchesJsExactly() throws IOException {
        JsonArray vectors;
        try (FileReader reader = new FileReader("src/test/resources/bucket-ranges-js-reference.json")) {
            vectors = GrowthBookJsonUtils.getInstance().gson.fromJson(reader, JsonArray.class);
        }
        Type doubleList = new TypeToken<ArrayList<Double>>() {}.getType();

        vectors.forEach(jsonElement -> {
            JsonObject vector = jsonElement.getAsJsonObject();
            ArrayList<Double> weights = GrowthBookJsonUtils.getInstance().gson.fromJson(vector.get("weights"), doubleList);
            JsonArray expected = vector.getAsJsonArray("ranges");

            List<BucketRange> results = GrowthBookUtils.getBucketRanges(
                    vector.get("numVariations").getAsInt(),
                    vector.get("coverage").getAsDouble(),
                    weights
            );

            assertEquals(expected.size(), results.size());
            for (int i = 0; i < expected.size(); i++) {
                JsonArray range = expected.get(i).getAsJsonArray();
                assertEquals(range.get(0).getAsDouble(), results.get(i).getRangeStart(), vector.toString());
                assertEquals(range.get(1).getAsDouble(), results.get(i).getRangeEnd(), vector.toString());
            }
        });
    }

    // The JS SDK compares getBucketRange and getEqualWeights cases after Math.floor(n * 1e8) / 1e8
    private static double roundLikeSpec(double n) {
        return Math.floor(n * 1e8) / 1e8;
    }

    @Test
    void test_mergeMaps_twoMapOverwritesWithoutMutatingInputs() {
        Map<String, Integer> base = new HashMap<>();
        base.put("shared", 1);
        base.put("baseOnly", 7);

        Map<String, Integer> overrides = new HashMap<>();
        overrides.put("shared", 2);
        overrides.put("overrideOnly", 9);

        Map<String, Integer> merged = GrowthBookUtils.mergeMaps(base, overrides);

        assertEquals(Integer.valueOf(2), merged.get("shared"));
        assertEquals(Integer.valueOf(7), merged.get("baseOnly"));
        assertEquals(Integer.valueOf(9), merged.get("overrideOnly"));
        assertEquals(Integer.valueOf(1), base.get("shared"));
        assertEquals(Integer.valueOf(2), overrides.get("shared"));
    }

    @Test
    void test_mergeMaps_handlesNullAndEmptyInputs() {
        Map<String, Integer> mergedFromNull = GrowthBookUtils.mergeMaps((Map<String, Integer>) null, null);
        Map<String, Integer> mergedFromEmpty = GrowthBookUtils.mergeMaps(Collections.<String, Integer>emptyMap(), null);

        assertEquals(Collections.emptyMap(), mergedFromNull);
        assertEquals(Collections.emptyMap(), mergedFromEmpty);
    }

    @Test
    void test_mergeMaps_listOverloadPreservesOverrideOrder() {
        Map<String, Integer> first = Collections.singletonMap("key", 1);
        Map<String, Integer> second = Collections.singletonMap("key", 2);
        Map<String, Integer> third = Collections.singletonMap("other", 3);

        Map<String, Integer> merged = GrowthBookUtils.mergeMaps(Arrays.asList(first, null, second, third));

        assertEquals(Integer.valueOf(2), merged.get("key"));
        assertEquals(Integer.valueOf(3), merged.get("other"));
    }

    @Test
    void fnv1a_hashesUtf16CodeUnitsLikeJs() {
        // Expected values are the JS SDK's FNV-1a over charCodeAt
        assertEquals(1812687940L, MathUtils.fnv1a_32("\u00e9"));
        // An astral character is two surrogate code units
        assertEquals(3409036472L, MathUtils.fnv1a_32("\uD83D\uDE00"));
        assertEquals(440920331L, MathUtils.fnv1a_32("abc"));
        assertEquals(Double.valueOf(0.2851), GrowthBookUtils.hash("ren\u00e9e", 2, "seed"));
        assertEquals(Double.valueOf(0.429), GrowthBookUtils.hash("\uD83D\uDE00", 1, "seed"));
    }

    @Test
    void getHashAttribute_usesOnlyNonEmptyStringsAndNonZeroNumbers() {
        String[] missing = {"true", "false", "0", "0.0", "\"\"", "null", "[]", "[1]", "{}", "{\"a\":1}"};
        for (String value : missing) {
            JsonObject attributes = GrowthBookJsonUtils.getInstance().gson
                    .fromJson("{\"id\": " + value + ", \"deviceId\": \"d1\"}", JsonObject.class);

            HashAttributeAndHashValue withFallback = GrowthBookUtils.getHashAttribute("id", "deviceId", attributes);
            assertEquals("deviceId", withFallback.getHashAttribute(), value);
            assertEquals("d1", withFallback.getHashValue(), value);

            HashAttributeAndHashValue withoutFallback = GrowthBookUtils.getHashAttribute("id", null, attributes);
            assertEquals("", withoutFallback.getHashValue(), value);
        }
    }

    @Test
    void getHashAttribute_formatsNumbersLikeJsString() {
        JsonObject attributes = GrowthBookJsonUtils.getInstance().gson
                .fromJson("{\"a\": 1.0, \"b\": 1.5, \"c\": -2}", JsonObject.class);

        assertEquals("1", GrowthBookUtils.getHashAttribute("a", null, attributes).getHashValue());
        assertEquals("1.5", GrowthBookUtils.getHashAttribute("b", null, attributes).getHashValue());
        assertEquals("-2", GrowthBookUtils.getHashAttribute("c", null, attributes).getHashValue());
    }

    @Test
    void isIncludedInRollout_excludesAUserWhoseHashAttributeIsNotHashable() {
        JsonObject attributes = GrowthBookJsonUtils.getInstance().gson
                .fromJson("{\"id\": {}, \"deviceId\": true}", JsonObject.class);

        assertEquals(false, GrowthBookUtils.isIncludedInRollout(attributes, "s", "id", "deviceId", null, 1.0, 2));
        attributes.addProperty("deviceId", "d1");
        assertTrue(GrowthBookUtils.isIncludedInRollout(attributes, "s", "id", "deviceId", null, 1.0, 2));
    }
}
