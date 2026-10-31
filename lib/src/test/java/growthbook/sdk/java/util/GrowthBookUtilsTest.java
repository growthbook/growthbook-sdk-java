package growthbook.sdk.java.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.reflect.TypeToken;
import growthbook.sdk.java.callback.ExperimentRunCallback;
import growthbook.sdk.java.model.AssignedExperiment;
import growthbook.sdk.java.model.BucketRange;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.Filter;
import growthbook.sdk.java.model.GBContext;
import growthbook.sdk.java.model.GeneratedStickyBucketAssignmentDocModel;
import growthbook.sdk.java.model.HashAttributeAndHashValue;
import growthbook.sdk.java.model.StickyAssignmentsDocument;
import growthbook.sdk.java.model.StickyBucketVariation;
import growthbook.sdk.java.model.VariationMeta;
import growthbook.sdk.java.multiusermode.configurations.EvaluationContext;
import growthbook.sdk.java.multiusermode.configurations.GlobalContext;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.testhelpers.TestCasesJsonHelper;
import java.io.FileReader;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Type;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class GrowthBookUtilsTest {

    private static final Gson GSON = GrowthBookJsonUtils.getInstance().gson;
    private static final TestCasesJsonHelper HELPER = TestCasesJsonHelper.getInstance();
    private static final Type DOUBLE_LIST = new TypeToken<ArrayList<Double>>() {}.getType();

    @ParameterizedTest(name = "hash(\"{0}\", \"{1}\", v{2}) = {3}")
    @MethodSource("hashCases")
    @DisplayName("Hashes the seed and input like the shared conformance cases")
    void hashesLikeConformanceCases(String seed, String input, int hashVersion, Double expected) {
        // When
        Double hash = GrowthBookUtils.hash(input, hashVersion, seed);

        // Then
        assertEquals(expected, hash);
    }

    static Stream<Arguments> hashCases() {
        return arrays(HELPER.getHNVTestCases()).map(testCase -> Arguments.of(
                testCase.get(0).getAsString(),
                testCase.get(1).getAsString(),
                testCase.get(2).getAsInt(),
                testCase.get(3).isJsonNull() ? null : testCase.get(3).getAsDouble()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("inNamespaceCases")
    @DisplayName("Places a user in or out of a namespace like the shared conformance cases")
    @SuppressWarnings("deprecation")
    void placesUserInNamespaceLikeConformanceCases(
            String description, String userId, growthbook.sdk.java.model.Namespace namespace, boolean expected) {
        // When
        boolean inNamespace = GrowthBookUtils.inNameSpace(userId, namespace);

        // Then
        assertEquals(expected, inNamespace);
    }

    @SuppressWarnings("deprecation")
    static Stream<Arguments> inNamespaceCases() {
        return arrays(HELPER.getInNamespaceTestCases()).map(testCase -> Arguments.of(
                testCase.get(0).getAsString(),
                testCase.get(1).getAsString(),
                GSON.fromJson(testCase.get(2).getAsJsonArray(), growthbook.sdk.java.model.Namespace.class),
                testCase.get(3).getAsBoolean()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("chooseVariationCases")
    @DisplayName("Chooses the variation whose bucket range holds the hash like the shared conformance cases")
    void choosesVariationLikeConformanceCases(String description, double hash, ArrayList<BucketRange> ranges, int expected) {
        // When
        Integer variation = GrowthBookUtils.chooseVariation(hash, ranges);

        // Then
        assertEquals(expected, variation);
    }

    static Stream<Arguments> chooseVariationCases() {
        Type bucketRangeList = new TypeToken<ArrayList<BucketRange>>() {}.getType();
        return arrays(HELPER.getChooseVariationTestCases()).map(testCase -> Arguments.of(
                testCase.get(0).getAsString(),
                testCase.get(1).getAsDouble(),
                GSON.fromJson(testCase.get(2).getAsJsonArray(), bucketRangeList),
                testCase.get(3).getAsInt()));
    }

    @ParameterizedTest(name = "{0} variations")
    @MethodSource("equalWeightsCases")
    @DisplayName("Splits weights equally between variations like the shared conformance cases")
    void splitsEqualWeightsLikeConformanceCases(int numberOfVariations, List<Double> expected) {
        // When
        List<Double> weights = GrowthBookUtils.getEqualWeights(numberOfVariations);

        // Then
        assertEquals(roundLikeSpec(expected), roundLikeSpec(weights));
    }

    static Stream<Arguments> equalWeightsCases() {
        return arrays(HELPER.getEqualWeightsTestCases()).map(testCase -> Arguments.of(
                testCase.get(0).getAsInt(),
                GSON.fromJson(testCase.get(1).getAsJsonArray(), DOUBLE_LIST)));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("queryStringOverrideCases")
    @DisplayName("Reads a forced variation from the URL query string like the shared conformance cases")
    void readsQueryStringOverrideLikeConformanceCases(String description, String id, String url, int numberOfVariations, Integer expected) {
        // When
        Integer override = GrowthBookUtils.getQueryStringOverride(id, url, numberOfVariations);

        // Then
        assertEquals(expected, override);
    }

    static Stream<Arguments> queryStringOverrideCases() {
        return arrays(HELPER.getQueryStringOverrideTestCases()).map(testCase -> Arguments.of(
                testCase.get(0).getAsString(),
                testCase.get(1).getAsString(),
                testCase.get(2).getAsString(),
                testCase.get(3).getAsInt(),
                testCase.get(4).isJsonNull() ? null : testCase.get(4).getAsInt()));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("bucketRangeCases")
    @DisplayName("Builds bucket ranges like the shared conformance cases")
    void buildsBucketRangesLikeConformanceCases(String description, int variations, double coverage, ArrayList<Double> weights, List<Double> expectedBounds) {
        // When
        List<BucketRange> ranges = GrowthBookUtils.getBucketRanges(variations, coverage, weights);

        // Then
        assertEquals(roundLikeSpec(expectedBounds), roundLikeSpec(bounds(ranges)));
    }

    static Stream<Arguments> bucketRangeCases() {
        return arrays(HELPER.getBucketRangeTestCases()).map(testCase -> {
            JsonArray arguments = testCase.get(1).getAsJsonArray();
            ArrayList<Double> weights = arguments.get(2).isJsonArray() ? GSON.fromJson(arguments.get(2), DOUBLE_LIST) : null;
            return Arguments.of(
                    testCase.get(0).getAsString(),
                    arguments.get(0).getAsInt(),
                    arguments.get(1).getAsDouble(),
                    weights,
                    flatten(testCase.get(2).getAsJsonArray()));
        });
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("jsReferenceBucketRanges")
    @DisplayName("Builds bucket ranges identical to the JavaScript SDK's, without rounding")
    void buildsBucketRangesIdenticalToJavaScript(String vector, int variations, double coverage, ArrayList<Double> weights, List<Double> expectedBounds) {
        // When
        List<BucketRange> ranges = GrowthBookUtils.getBucketRanges(variations, coverage, weights);

        // Then
        assertEquals(expectedBounds, bounds(ranges));
    }

    static Stream<Arguments> jsReferenceBucketRanges() {
        JsonArray vectors;
        try (FileReader reader = new FileReader("src/test/resources/bucket-ranges-js-reference.json")) {
            vectors = GSON.fromJson(reader, JsonArray.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return StreamSupport.stream(vectors.spliterator(), false).map(element -> {
            JsonObject vector = element.getAsJsonObject();
            return Arguments.of(
                    vector.toString(),
                    vector.get("numVariations").getAsInt(),
                    vector.get("coverage").getAsDouble(),
                    GSON.fromJson(vector.get("weights"), DOUBLE_LIST),
                    flatten(vector.getAsJsonArray("ranges")));
        });
    }

    @Test
    @DisplayName("Merging two maps lets the second override the first without mutating either")
    void mergingTwoMapsOverridesWithoutMutatingInputs() {
        // Given
        Map<String, Integer> base = new HashMap<>();
        base.put("shared", 1);
        base.put("baseOnly", 7);
        Map<String, Integer> overrides = new HashMap<>();
        overrides.put("shared", 2);
        overrides.put("overrideOnly", 9);

        // When
        Map<String, Integer> merged = GrowthBookUtils.mergeMaps(base, overrides);

        // Then
        Map<String, Integer> expected = new HashMap<>();
        expected.put("shared", 2);
        expected.put("baseOnly", 7);
        expected.put("overrideOnly", 9);
        assertEquals(expected, merged);
        assertEquals(Integer.valueOf(1), base.get("shared"));
        assertEquals(2, base.size());
        assertEquals(Integer.valueOf(2), overrides.get("shared"));
        assertEquals(2, overrides.size());
    }

    @Test
    @DisplayName("Merging null maps gives an empty map")
    void mergingNullMapsGivesEmptyMap() {
        // When
        Map<String, Integer> merged = GrowthBookUtils.mergeMaps((Map<String, Integer>) null, null);

        // Then
        assertEquals(Collections.emptyMap(), merged);
    }

    @Test
    @DisplayName("Merging an empty map with null gives an empty map")
    void mergingEmptyMapWithNullGivesEmptyMap() {
        // When
        Map<String, Integer> merged = GrowthBookUtils.mergeMaps(Collections.<String, Integer>emptyMap(), null);

        // Then
        assertEquals(Collections.emptyMap(), merged);
    }

    @Test
    @DisplayName("Merging a list of maps applies them in order and skips null entries")
    void mergingListOfMapsAppliesInOrderAndSkipsNulls() {
        // Given
        Map<String, Integer> first = Collections.singletonMap("key", 1);
        Map<String, Integer> second = Collections.singletonMap("key", 2);
        Map<String, Integer> third = Collections.singletonMap("other", 3);

        // When
        Map<String, Integer> merged = GrowthBookUtils.mergeMaps(Arrays.asList(first, null, second, third));

        // Then
        Map<String, Integer> expected = new HashMap<>();
        expected.put("key", 2);
        expected.put("other", 3);
        assertEquals(expected, merged);
    }

    @ParameterizedTest(name = "hash(\"{0}\", v{1}) = {2}")
    @CsvSource({
            "ren\u00e9e, 2, 0.2851",
            "\uD83D\uDE00, 1, 0.429"
    })
    @DisplayName("Hashes non-ASCII input over UTF-16 code units like the JavaScript SDK")
    void hashesNonAsciiInputLikeJavaScript(String input, int hashVersion, double expected) {
        // When
        Double hash = GrowthBookUtils.hash(input, hashVersion, "seed");

        // Then
        assertEquals(Double.valueOf(expected), hash);
    }

    @ParameterizedTest(name = "id = {0}")
    @ValueSource(strings = {"true", "false", "0", "0.0", "\"\"", "null", "[]", "[1]", "{}", "{\"a\":1}"})
    @DisplayName("Falls back to the fallback attribute when the hash attribute is not a non-empty string or non-zero number")
    void fallsBackWhenHashAttributeIsNotUsable(String idJson) {
        // Given
        JsonObject attributes = GSON.fromJson("{\"id\": " + idJson + ", \"deviceId\": \"d1\"}", JsonObject.class);

        // When
        HashAttributeAndHashValue result = GrowthBookUtils.getHashAttribute("id", "deviceId", attributes);

        // Then
        assertEquals("deviceId", result.getHashAttribute());
        assertEquals("d1", result.getHashValue());
    }

    @ParameterizedTest(name = "id = {0}")
    @ValueSource(strings = {"true", "false", "0", "0.0", "\"\"", "null", "[]", "[1]", "{}", "{\"a\":1}"})
    @DisplayName("Gives an empty hash value when the hash attribute is not usable and there is no fallback")
    void givesEmptyHashValueWithoutFallback(String idJson) {
        // Given
        JsonObject attributes = GSON.fromJson("{\"id\": " + idJson + ", \"deviceId\": \"d1\"}", JsonObject.class);

        // When
        HashAttributeAndHashValue result = GrowthBookUtils.getHashAttribute("id", null, attributes);

        // Then
        assertEquals("", result.getHashValue());
    }

    @ParameterizedTest(name = "{0} -> \"{1}\"")
    @CsvSource({
            "1.0, 1",
            "1.5, 1.5",
            "-2, -2"
    })
    @DisplayName("Formats a numeric hash attribute like JavaScript's String(number)")
    void formatsNumericHashAttributeLikeJavaScript(String numberJson, String expected) {
        // Given
        JsonObject attributes = GSON.fromJson("{\"id\": " + numberJson + "}", JsonObject.class);

        // When
        HashAttributeAndHashValue result = GrowthBookUtils.getHashAttribute("id", null, attributes);

        // Then
        assertEquals(expected, result.getHashValue());
    }

    @Test
    @DisplayName("Excludes a user from a rollout when neither the hash attribute nor the fallback is hashable")
    void excludesUserFromRolloutWithoutHashableAttribute() {
        // Given
        JsonObject attributes = GSON.fromJson("{\"id\": {}, \"deviceId\": true}", JsonObject.class);

        // When
        Boolean included = GrowthBookUtils.isIncludedInRollout(attributes, "s", "id", "deviceId", null, 1.0, 2);

        // Then
        assertFalse(included);
    }

    @Test
    @DisplayName("Includes a user in a full rollout through a hashable fallback attribute")
    void includesUserInFullRolloutThroughFallbackAttribute() {
        // Given
        JsonObject attributes = GSON.fromJson("{\"id\": {}, \"deviceId\": \"d1\"}", JsonObject.class);

        // When
        Boolean included = GrowthBookUtils.isIncludedInRollout(attributes, "s", "id", "deviceId", null, 1.0, 2);

        // Then
        assertTrue(included);
    }

    @ParameterizedTest(name = "{0} -> {1}")
    @MethodSource("queryStringOverrides")
    @DisplayName("Reads a forced variation from the query string of a parsed URL")
    void readsQueryStringOverrideFromParsedUrl(String url, Integer expected) throws MalformedURLException {
        // When
        Integer variation = GrowthBookUtils.getQueryStringOverride("my-test", new URL(url), 3);

        // Then
        assertEquals(expected, variation);
    }

    static Stream<Arguments> queryStringOverrides() {
        return Stream.of(
                Arguments.of("https://example.test/?my-test=2", 2),
                Arguments.of("https://example.test/?my-test=3", null),
                Arguments.of("https://example.test/?my-test=-1", null),
                Arguments.of("https://example.test/?my-test=two", null),
                Arguments.of("https://example.test/?other=1", null),
                Arguments.of("https://example.test/", null)
        );
    }

    @ParameterizedTest(name = "gb~flag={0} -> {1}")
    @MethodSource("forcedBooleans")
    @DisplayName("Reads a forced boolean feature value from the URL")
    void readsForcedBooleanFromUrl(String rawValue, Boolean expected) throws MalformedURLException {
        // When
        Boolean value = GrowthBookUtils.getForcedBooleanValueFromUrl("flag", forcedUrl(rawValue));

        // Then
        assertEquals(expected, value);
    }

    static Stream<Arguments> forcedBooleans() {
        return Stream.of(
                Arguments.of("true", true),
                Arguments.of("ON", true),
                Arguments.of("1", true),
                Arguments.of("false", false),
                Arguments.of("Off", false),
                Arguments.of("0", false),
                Arguments.of("maybe", null)
        );
    }

    @Test
    @DisplayName("Reads a forced string feature value from the URL")
    void readsForcedStringFromUrl() throws MalformedURLException {
        // When
        String value = GrowthBookUtils.getForcedStringValueFromUrl("flag", forcedUrl("blue%20sky"));

        // Then
        assertEquals("blue sky", value);
    }

    @Test
    @DisplayName("Reads a forced float feature value from the URL")
    void readsForcedFloatFromUrl() throws MalformedURLException {
        // When
        Float value = GrowthBookUtils.getForcedFloatValueFromUrl("flag", forcedUrl("1.5"));

        // Then
        assertEquals(Float.valueOf(1.5f), value);
    }

    @Test
    @DisplayName("Reads a forced double feature value from the URL")
    void readsForcedDoubleFromUrl() throws MalformedURLException {
        // When
        Double value = GrowthBookUtils.getForcedDoubleValueFromUrl("flag", forcedUrl("2.25"));

        // Then
        assertEquals(Double.valueOf(2.25), value);
    }

    @Test
    @DisplayName("Reads a forced integer feature value from the URL")
    void readsForcedIntegerFromUrl() throws MalformedURLException {
        // When
        Integer value = GrowthBookUtils.getForcedIntegerValueFromUrl("flag", forcedUrl("42"));

        // Then
        assertEquals(Integer.valueOf(42), value);
    }

    @Test
    @DisplayName("Reads a forced JSON feature value from the URL into the requested type")
    void readsForcedSerializableValueFromUrl() throws MalformedURLException {
        // When
        Palette value = GrowthBookUtils.getForcedSerializableValueFromUrl(
                "flag", forcedUrl("%7B%22color%22%3A%22green%22%7D"), Palette.class, GSON);

        // Then
        assertNotNull(value);
        assertEquals("green", value.color);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"float", "double", "integer", "json"})
    @DisplayName("Gives no forced value when the URL value cannot be parsed as the requested type")
    void givesNoForcedValueForUnparsableUrlValue(String type) throws MalformedURLException {
        // Given
        URL url = forcedUrl("%7Bnot-a-number");

        // When
        Object value = forcedValueOfType(type, url);

        // Then
        assertNull(value);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"boolean", "string", "float", "double", "integer", "json"})
    @DisplayName("Gives no forced value when the feature key is not in the URL")
    void givesNoForcedValueWhenKeyIsAbsent(String type) throws MalformedURLException {
        // Given
        URL url = new URL("https://example.test/?gb~other=1");

        // When
        Object value = forcedValueOfType(type, url);

        // Then
        assertNull(value);
    }

    @ParameterizedTest(name = "{0} in [0.2, 0.5) -> {1}")
    @MethodSource("rangeMemberships")
    @DisplayName("A number is in a bucket range when it is at or above the start and below the end")
    void numberIsInRangeFromStartInclusiveToEndExclusive(Double n, boolean expected) {
        // When
        Boolean inRange = GrowthBookUtils.inRange(n, new BucketRange(0.2, 0.5));

        // Then
        assertEquals(expected, inRange);
    }

    static Stream<Arguments> rangeMemberships() {
        return Stream.of(
                Arguments.of(0.2, true),
                Arguments.of(0.35, true),
                Arguments.of(0.5, false),
                Arguments.of(0.1, false),
                Arguments.of(null, false)
        );
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("filterCases")
    @DisplayName("Filters a user out unless their hash attribute falls in one of the filter's ranges")
    void filtersUserOutUnlessHashFallsInRange(String description, String attributesJson, List<BucketRange> ranges, boolean expected) {
        // Given
        JsonObject attributes = GSON.fromJson(attributesJson, JsonObject.class);
        Filter filter = Filter.builder().seed("seed-1").attribute("id").ranges(ranges).hashVersion(2).build();

        // When
        Boolean filteredOut = GrowthBookUtils.isFilteredOut(Collections.singletonList(filter), attributes);

        // Then
        assertEquals(expected, filteredOut);
    }

    static Stream<Arguments> filterCases() {
        List<BucketRange> fullRange = Collections.singletonList(new BucketRange(0.0, 1.0));
        return Stream.of(
                Arguments.of("hash in the full range", "{\"id\":\"user-1\"}", fullRange, false),
                Arguments.of("no ranges", "{\"id\":\"user-1\"}", Collections.<BucketRange>emptyList(), true),
                Arguments.of("hash attribute missing", "{\"deviceId\":\"d-1\"}", fullRange, true)
        );
    }

    @Test
    @DisplayName("Collects the hash and fallback attribute values the payload's experiment rules bucket by")
    void collectsStickyBucketAttributesFromPayloadRules() {
        // Given
        GBContext context = GBContext.builder().build();
        String payload = "{\"features\":{\"checkout\":{\"defaultValue\":0,\"rules\":[{\"key\":\"exp-1\","
                + "\"variations\":[0,1],\"hashAttribute\":\"deviceId\",\"fallbackAttribute\":\"id\"}]}}}";
        JsonObject attributes = GSON.fromJson("{\"id\":\"user-1\",\"deviceId\":\"device-1\"}", JsonObject.class);

        // When
        Map<String, String> stickyAttributes = GrowthBookUtils.getStickyBucketAttributes(context, payload, attributes);

        // Then
        Map<String, String> expected = new HashMap<>();
        expected.put("deviceId", "device-1");
        expected.put("id", "user-1");
        assertEquals(expected, stickyAttributes);
    }

    @Test
    @DisplayName("Merges sticky assignments from the fallback and hash attribute documents, the hash attribute winning")
    void mergesStickyAssignmentsWithHashAttributeWinning() {
        // Given
        Map<String, StickyAssignmentsDocument> docs = new HashMap<>();
        docs.put("id||user-1", new StickyAssignmentsDocument("id", "user-1", assignments("exp-1__0", "1")));
        Map<String, String> fallbackAssignments = assignments("exp-1__0", "0");
        fallbackAssignments.put("exp-2__0", "1");
        docs.put("deviceId||device-1", new StickyAssignmentsDocument("deviceId", "device-1", fallbackAssignments));
        EvaluationContext context = stickyContext(docs);

        // When
        Map<String, String> merged = GrowthBookUtils.getStickyBucketAssignments(context, "id", "deviceId");

        // Then
        Map<String, String> expected = new HashMap<>();
        expected.put("exp-1__0", "1");
        expected.put("exp-2__0", "1");
        assertEquals(expected, merged);
    }

    @Test
    @DisplayName("Resolves the stored sticky variation to its index in the variation metadata")
    void resolvesStickyVariationToMetaIndex() {
        // Given
        Map<String, StickyAssignmentsDocument> docs = Collections.singletonMap(
                "id||user-1", new StickyAssignmentsDocument("id", "user-1", assignments("exp-1__1", "beta")));
        List<VariationMeta> meta = Arrays.asList(
                VariationMeta.builder().key("alpha").build(),
                VariationMeta.builder().key("beta").build());

        // When
        StickyBucketVariation variation = GrowthBookUtils.getStickyBucketVariation(
                stickyContext(docs), "exp-1", "id", null, 1, 0, meta);

        // Then
        assertEquals(Integer.valueOf(1), variation.getVariation());
        assertNull(variation.getVersionIsBlocked());
    }

    @Test
    @DisplayName("Blocks a user who holds an assignment for a bucket version below the minimum")
    void blocksUserWithAssignmentBelowMinimumBucketVersion() {
        // Given
        Map<String, StickyAssignmentsDocument> docs = Collections.singletonMap(
                "id||user-1", new StickyAssignmentsDocument("id", "user-1", assignments("exp-1__0", "alpha")));

        // When
        StickyBucketVariation variation = GrowthBookUtils.getStickyBucketVariation(
                stickyContext(docs), "exp-1", "id", null, 2, 1, Collections.<VariationMeta>emptyList());

        // Then
        assertEquals(Integer.valueOf(-1), variation.getVariation());
        assertEquals(Boolean.TRUE, variation.getVersionIsBlocked());
    }

    @ParameterizedTest(name = "version {0} -> {1}")
    @MethodSource("stickyExperimentKeys")
    @DisplayName("Builds the sticky bucket experiment key from the experiment key and bucket version")
    void buildsStickyBucketExperimentKey(Integer bucketVersion, String expected) {
        // When
        String key = GrowthBookUtils.getStickyBucketExperimentKey("exp-1", bucketVersion);

        // Then
        assertEquals(expected, key);
    }

    static Stream<Arguments> stickyExperimentKeys() {
        return Stream.of(
                Arguments.of(null, "exp-1__0"),
                Arguments.of(3, "exp-1__3")
        );
    }

    @Test
    @DisplayName("Generates a changed sticky document that merges new assignments into the existing ones")
    void generatesChangedStickyDocumentMergingAssignments() {
        // Given
        Map<String, StickyAssignmentsDocument> docs = Collections.singletonMap(
                "id||user-1", new StickyAssignmentsDocument("id", "user-1", assignments("exp-1__0", "alpha")));

        // When
        GeneratedStickyBucketAssignmentDocModel generated = GrowthBookUtils.generateStickyBucketAssignmentDoc(
                docs, "id", "user-1", assignments("exp-2__0", "beta"));

        // Then
        Map<String, String> expected = assignments("exp-1__0", "alpha");
        expected.put("exp-2__0", "beta");
        assertEquals("id||user-1", generated.getKey());
        assertTrue(generated.isChanged());
        assertEquals(new StickyAssignmentsDocument("id", "user-1", expected), generated.getStickyAssignmentsDocument());
    }

    @Test
    @DisplayName("Generates an unchanged sticky document when the assignments are already stored")
    void generatesUnchangedStickyDocumentForKnownAssignments() {
        // Given
        Map<String, StickyAssignmentsDocument> docs = Collections.singletonMap(
                "id||user-1", new StickyAssignmentsDocument("id", "user-1", assignments("exp-1__0", "alpha")));

        // When
        GeneratedStickyBucketAssignmentDocModel generated = GrowthBookUtils.generateStickyBucketAssignmentDoc(
                docs, "id", "user-1", assignments("exp-1__0", "alpha"));

        // Then
        assertFalse(generated.isChanged());
        assertEquals(assignments("exp-1__0", "alpha"), generated.getStickyAssignmentsDocument().getAssignments());
    }

    @Test
    @DisplayName("Merging an empty list of maps gives an empty map")
    void mergingEmptyListOfMapsGivesEmptyMap() {
        // When
        Map<String, Integer> merged = GrowthBookUtils.mergeMaps(Collections.<Map<String, Integer>>emptyList());

        // Then
        assertEquals(Collections.emptyMap(), merged);
    }

    @Test
    @DisplayName("Merging a map with an empty map gives an independent copy of the first map")
    void mergingMapWithEmptyMapGivesCopy() {
        // Given
        Map<String, Integer> base = new HashMap<>();
        base.put("key", 1);

        // When
        Map<String, Integer> merged = GrowthBookUtils.mergeMaps(base, Collections.<String, Integer>emptyMap());
        merged.put("added", 2);

        // Then
        assertEquals(Collections.singletonMap("key", 1), base);
    }

    @Test
    @DisplayName("Fires subscriptions only when an experiment's assignment changes")
    @SuppressWarnings("deprecation")
    void firesSubscriptionsOnlyWhenAssignmentChanges() {
        // Given
        Map<String, AssignedExperiment> assigned = new ConcurrentHashMap<>();
        RecordingRunCallback callback = new RecordingRunCallback();
        Experiment<String> experiment = Experiment.<String>builder().key("exp-1").build();

        // When
        GrowthBookUtils.fireSubscriptions(assigned, Collections.singletonList(callback), experiment, runResult(0));
        GrowthBookUtils.fireSubscriptions(assigned, Collections.singletonList(callback), experiment, runResult(0));
        GrowthBookUtils.fireSubscriptions(assigned, Collections.singletonList(callback), experiment, runResult(1));

        // Then
        assertEquals(Arrays.asList("exp-1:0", "exp-1:1"), callback.runs);
    }

    @Test
    @DisplayName("Fires subscriptions for a key-less experiment separately from an experiment keyed with an empty string")
    @SuppressWarnings("deprecation")
    void firesSubscriptionsForKeylessExperimentSeparately() {
        // Given
        Map<String, AssignedExperiment> assigned = new ConcurrentHashMap<>();
        RecordingRunCallback callback = new RecordingRunCallback();
        Experiment<String> keyless = Experiment.<String>builder().build();
        Experiment<String> emptyKey = Experiment.<String>builder().key("").build();

        // When
        GrowthBookUtils.fireSubscriptions(assigned, Collections.singletonList(callback), keyless, runResult(0));
        GrowthBookUtils.fireSubscriptions(assigned, Collections.singletonList(callback), emptyKey, runResult(0));

        // Then
        assertEquals(Arrays.asList("null:0", ":0"), callback.runs);
    }

    @Test
    @DisplayName("A throwing subscription callback does not stop the remaining callbacks")
    @SuppressWarnings("deprecation")
    void throwingSubscriptionCallbackDoesNotStopOthers() {
        // Given
        Map<String, AssignedExperiment> assigned = new HashMap<>();
        RecordingRunCallback recorder = new RecordingRunCallback();
        List<ExperimentRunCallback> callbacks = Arrays.asList(new ThrowingRunCallback(), recorder);
        Experiment<String> experiment = Experiment.<String>builder().key("exp-1").build();

        // When
        GrowthBookUtils.fireSubscriptions(assigned, callbacks, experiment, runResult(1));

        // Then
        assertEquals(Collections.singletonList("exp-1:1"), recorder.runs);
    }

    private static URL forcedUrl(String rawValue) throws MalformedURLException {
        return new URL("https://example.test/?gb~flag=" + rawValue);
    }

    private static Object forcedValueOfType(String type, URL url) {
        switch (type) {
            case "boolean":
                return GrowthBookUtils.getForcedBooleanValueFromUrl("flag", url);
            case "string":
                return GrowthBookUtils.getForcedStringValueFromUrl("flag", url);
            case "float":
                return GrowthBookUtils.getForcedFloatValueFromUrl("flag", url);
            case "double":
                return GrowthBookUtils.getForcedDoubleValueFromUrl("flag", url);
            case "integer":
                return GrowthBookUtils.getForcedIntegerValueFromUrl("flag", url);
            default:
                return GrowthBookUtils.getForcedSerializableValueFromUrl("flag", url, Palette.class, GSON);
        }
    }

    private static Map<String, String> assignments(String key, String value) {
        Map<String, String> assignments = new HashMap<>();
        assignments.put(key, value);
        return assignments;
    }

    private static EvaluationContext stickyContext(Map<String, StickyAssignmentsDocument> docs) {
        UserContext user = UserContext.builder()
                .attributes(GSON.fromJson("{\"id\":\"user-1\",\"deviceId\":\"device-1\"}", JsonObject.class))
                .stickyBucketAssignmentDocs(docs)
                .build();
        return new EvaluationContext(GlobalContext.builder().build(), user, Options.builder().build());
    }

    private static ExperimentResult<String> runResult(int variationId) {
        return ExperimentResult.<String>builder().inExperiment(true).variationId(variationId).build();
    }

    private static Stream<JsonArray> arrays(JsonArray testCases) {
        return StreamSupport.stream(testCases.spliterator(), false).map(JsonElement::getAsJsonArray);
    }

    private static List<Double> flatten(JsonArray rangeTuples) {
        List<Double> bounds = new ArrayList<>();
        for (JsonElement tuple : rangeTuples) {
            bounds.add(tuple.getAsJsonArray().get(0).getAsDouble());
            bounds.add(tuple.getAsJsonArray().get(1).getAsDouble());
        }
        return bounds;
    }

    private static List<Double> bounds(List<BucketRange> ranges) {
        List<Double> bounds = new ArrayList<>();
        for (BucketRange range : ranges) {
            bounds.add(range.getRangeStart());
            bounds.add(range.getRangeEnd());
        }
        return bounds;
    }

    private static List<Double> roundLikeSpec(List<Double> values) {
        List<Double> rounded = new ArrayList<>();
        for (Double value : values) {
            rounded.add(Math.floor(value * 1e8) / 1e8);
        }
        return rounded;
    }

    private static final class Palette {
        String color;
    }

    private static final class RecordingRunCallback implements ExperimentRunCallback {
        private final List<String> runs = new ArrayList<>();

        @Override
        public <T> void onRun(Experiment<T> experiment, ExperimentResult<T> experimentResult) {
            runs.add(experiment.getKey() + ":" + experimentResult.getVariationId());
        }
    }

    private static final class ThrowingRunCallback implements ExperimentRunCallback {
        @Override
        public <T> void onRun(Experiment<T> experiment, ExperimentResult<T> experimentResult) {
            throw new IllegalStateException("callback failure");
        }
    }
}
