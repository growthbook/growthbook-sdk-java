package growthbook.sdk.java.evaluators.internal.bucketing;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import growthbook.sdk.java.model.BucketRange;
import growthbook.sdk.java.model.Filter;
import growthbook.sdk.java.model.HashAttributeAndHashValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BucketingTest {

    private static final double DELTA = 1e-9;

    @ParameterizedTest(name = "hash(\"{1}\", v{2}, seed \"{0}\") = {3}")
    @CsvSource({
            "'', a, 1, 0.22",
            "b, a, 1, 0.946",
            "'', 123, 1, 0.011",
            "seed, a, 2, 0.0505",
            "foo, ab, 2, 0.2575"
    })
    @DisplayName("Hashes to the known value for hash versions one and two")
    void hashesToKnownValueForSupportedVersions(String seed, String value, int version, double expected) {
        // Given
        // When
        Double result = Bucketing.hash(value, version, seed);

        // Then
        assertEquals(expected, result, DELTA);
    }

    @Test
    @DisplayName("Returns null when the hash version is missing")
    void returnsNullWhenHashVersionIsMissing() {
        // Given
        Integer hashVersion = null;

        // When
        Double result = Bucketing.hash("user-1", hashVersion, "seed");

        // Then
        assertNull(result);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 3, -1, 99})
    @DisplayName("Returns null for an unsupported hash version")
    void returnsNullForUnsupportedHashVersion(int hashVersion) {
        // Given
        // When
        Double result = Bucketing.hash("user-1", hashVersion, "seed");

        // Then
        assertNull(result);
    }

    @Test
    @DisplayName("Builds equal weights that sum to one")
    void buildsEqualWeightsThatSumToOne() {
        // Given
        int numberOfVariations = 4;

        // When
        List<Double> weights = Bucketing.getEqualWeights(numberOfVariations);

        // Then
        assertEquals(Arrays.asList(0.25, 0.25, 0.25, 0.25), weights);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -10})
    @DisplayName("Returns no equal weights for zero or a negative number of variations")
    void returnsNoEqualWeightsForZeroOrNegativeVariations(int numberOfVariations) {
        // Given
        // When
        List<Double> weights = Bucketing.getEqualWeights(numberOfVariations);

        // Then
        assertTrue(weights.isEmpty());
    }

    @Test
    @DisplayName("Uses the given weights when they match the variations and sum to one")
    void usesGivenWeightsWhenValid() {
        // Given
        List<Double> weights = Arrays.asList(0.2, 0.8);

        // When
        List<BucketRange> ranges = Bucketing.getBucketRanges(2, 1.0, weights);

        // Then
        assertRanges(ranges, 0.0, 0.2, 0.2, 1.0);
    }

    @Test
    @DisplayName("Falls back to equal weights when the weight count differs from the variation count")
    void fallsBackToEqualWeightsWhenWeightCountDiffers() {
        // Given
        List<Double> weights = Arrays.asList(0.2, 0.3, 0.5);

        // When
        List<BucketRange> ranges = Bucketing.getBucketRanges(2, 1.0, weights);

        // Then
        assertRanges(ranges, 0.0, 0.5, 0.5, 1.0);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.98, 1.02, 0.5, 2.0})
    @DisplayName("Falls back to equal weights when the weights don't sum to one")
    void fallsBackToEqualWeightsWhenWeightsDoNotSumToOne(double total) {
        // Given
        List<Double> weights = Arrays.asList(total * 0.4, total * 0.6);

        // When
        List<BucketRange> ranges = Bucketing.getBucketRanges(2, 1.0, weights);

        // Then
        assertRanges(ranges, 0.0, 0.5, 0.5, 1.0);
    }

    @Test
    @DisplayName("Keeps weights whose sum is within the tolerance of one")
    void keepsWeightsWithinTolerance() {
        // Given
        List<Double> weights = Arrays.asList(0.4, 0.605);

        // When
        List<BucketRange> ranges = Bucketing.getBucketRanges(2, 1.0, weights);

        // Then
        assertRanges(ranges, 0.0, 0.4, 0.4, 1.005);
    }

    @Test
    @DisplayName("Falls back to equal weights when the weights are missing")
    void fallsBackToEqualWeightsWhenWeightsAreMissing() {
        // Given
        List<Double> weights = null;

        // When
        List<BucketRange> ranges = Bucketing.getBucketRanges(2, 0.5, weights);

        // Then
        assertRanges(ranges, 0.0, 0.25, 0.5, 0.75);
    }

    @Test
    @DisplayName("Clamps a coverage above one to one")
    void clampsCoverageAboveOne() {
        // Given
        double coverage = 1.5;

        // When
        List<BucketRange> ranges = Bucketing.getBucketRanges(2, coverage, null);

        // Then
        assertRanges(ranges, 0.0, 0.5, 0.5, 1.0);
    }

    @Test
    @DisplayName("Clamps a negative coverage to zero")
    void clampsNegativeCoverageToZero() {
        // Given
        double coverage = -0.5;

        // When
        List<BucketRange> ranges = Bucketing.getBucketRanges(2, coverage, null);

        // Then
        assertRanges(ranges, 0.0, 0.0, 0.5, 0.5);
    }

    @Test
    @DisplayName("Returns no bucket ranges for zero variations")
    void returnsNoBucketRangesForZeroVariations() {
        // Given
        int numberOfVariations = 0;

        // When
        List<BucketRange> ranges = Bucketing.getBucketRanges(numberOfVariations, 1.0, null);

        // Then
        assertTrue(ranges.isEmpty());
    }

    @Test
    @DisplayName("Chooses the variation whose range contains the hash")
    void choosesVariationWhoseRangeContainsHash() {
        // Given
        List<BucketRange> ranges = Arrays.asList(range(0.0, 0.25), range(0.5, 0.75));

        // When
        int variation = Bucketing.chooseVariation(0.5, ranges);

        // Then
        assertEquals(1, variation);
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.25, 0.3, 0.75, 0.99})
    @DisplayName("Chooses no variation when the hash falls in no range")
    void choosesNoVariationWhenHashFallsInNoRange(double n) {
        // Given
        List<BucketRange> ranges = Arrays.asList(range(0.0, 0.25), range(0.5, 0.75));

        // When
        int variation = Bucketing.chooseVariation(n, ranges);

        // Then
        assertEquals(-1, variation);
    }

    @Test
    @DisplayName("Chooses no variation when there are no ranges")
    void choosesNoVariationWhenThereAreNoRanges() {
        // Given
        List<BucketRange> ranges = Collections.emptyList();

        // When
        int variation = Bucketing.chooseVariation(0.1, ranges);

        // Then
        assertEquals(-1, variation);
    }

    @ParameterizedTest(name = "{0} in [0.2, 0.6) = {1}")
    @CsvSource({
            "0.2, true",
            "0.4, true",
            "0.6, false",
            "0.1999, false",
            "0.5999, true"
    })
    @DisplayName("Treats the range start as inclusive and the end as exclusive")
    void treatsRangeStartAsInclusiveAndEndAsExclusive(double n, boolean expected) {
        // Given
        BucketRange range = range(0.2, 0.6);

        // When
        boolean result = Bucketing.inRange(n, range);

        // Then
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("Is not in range when the hash is missing")
    void isNotInRangeWhenHashIsMissing() {
        // Given
        BucketRange range = range(0.0, 1.0);

        // When
        boolean result = Bucketing.inRange(null, range);

        // Then
        assertFalse(result);
    }

    @Test
    @DisplayName("Is not in range when the range is missing")
    void isNotInRangeWhenRangeIsMissing() {
        // Given
        Double n = 0.5;

        // When
        boolean result = Bucketing.inRange(n, null);

        // Then
        assertFalse(result);
    }

    @ParameterizedTest(name = "user {0} in {1} [{2}, {3}) = {4}")
    @CsvSource({
            "1, namespace1, 0, 0.4, false",
            "1, namespace1, 0.4, 1, true",
            "2, namespace1, 0, 0.4, false",
            "2, namespace1, 0.4, 1, true"
    })
    @DisplayName("Places a user in the namespace range their hash falls in")
    void placesUserInNamespaceRangeTheirHashFallsIn(String hashValue, String namespaceId, double start, double end, boolean expected) {
        // Given
        // When
        boolean result = Bucketing.inNamespace(hashValue, namespaceId, start, end);

        // Then
        assertEquals(expected, result);
    }

    @Test
    @DisplayName("Includes a user whose namespace hash equals the range start and excludes one at the range end")
    void includesNamespaceHashAtStartAndExcludesAtEnd() {
        // Given
        double n = Bucketing.hash("user-1__", 1, "namespace-a");

        // When
        boolean atStart = Bucketing.inNamespace("user-1", "namespace-a", n, n + 0.001);
        boolean atEnd = Bucketing.inNamespace("user-1", "namespace-a", n - 0.001, n);

        // Then
        assertTrue(atStart);
        assertFalse(atEnd);
    }

    @Test
    @DisplayName("Does not filter out a user when there are no filters")
    void doesNotFilterOutWhenNoFilters() {
        // Given
        JsonObject attributes = attributes("{\"id\": \"user-1\"}");

        // When
        boolean filteredOut = Bucketing.isFilteredOut(null, attributes);

        // Then
        assertFalse(filteredOut);
    }

    @Test
    @DisplayName("Does not filter out a user when there are no attributes")
    void doesNotFilterOutWhenNoAttributes() {
        // Given
        List<Filter> filters = Collections.singletonList(filter("seed", null, null, range(0.0, 0.0)));

        // When
        boolean filteredOut = Bucketing.isFilteredOut(filters, null);

        // Then
        assertFalse(filteredOut);
    }

    @Test
    @DisplayName("Does not filter out a user when the filter list is empty")
    void doesNotFilterOutWhenFilterListIsEmpty() {
        // Given
        JsonObject attributes = attributes("{\"id\": \"user-1\"}");

        // When
        boolean filteredOut = Bucketing.isFilteredOut(Collections.<Filter>emptyList(), attributes);

        // Then
        assertFalse(filteredOut);
    }

    @Test
    @DisplayName("Filters out a user whose hash attribute is missing")
    void filtersOutUserWhoseHashAttributeIsMissing() {
        // Given
        JsonObject attributes = attributes("{\"id\": \"user-1\"}");
        List<Filter> filters = Collections.singletonList(filter("seed", "deviceId", null, range(0.0, 1.0)));

        // When
        boolean filteredOut = Bucketing.isFilteredOut(filters, attributes);

        // Then
        assertTrue(filteredOut);
    }

    @Test
    @DisplayName("Filters out a user whose hash falls in none of the ranges")
    void filtersOutUserWhoseHashFallsInNoRange() {
        // Given
        JsonObject attributes = attributes("{\"id\": \"user-1\"}");
        double n = Bucketing.hash("user-1", 2, "seed");
        List<Filter> filters = Collections.singletonList(
                filter("seed", null, 2, range(0.0, n), range(n + 0.0001, 1.0)));

        // When
        boolean filteredOut = Bucketing.isFilteredOut(filters, attributes);

        // Then
        assertTrue(filteredOut);
    }

    @Test
    @DisplayName("Filters out a user when a filter has no ranges")
    void filtersOutUserWhenFilterHasNoRanges() {
        // Given
        JsonObject attributes = attributes("{\"id\": \"user-1\"}");
        List<Filter> filters = Collections.singletonList(filter("seed", null, 2));

        // When
        boolean filteredOut = Bucketing.isFilteredOut(filters, attributes);

        // Then
        assertTrue(filteredOut);
    }

    @Test
    @DisplayName("Keeps a user whose hash falls in one of the ranges")
    void keepsUserWhoseHashFallsInOneOfTheRanges() {
        // Given
        JsonObject attributes = attributes("{\"deviceId\": \"device-1\"}");
        double n = Bucketing.hash("device-1", 2, "seed");
        List<Filter> filters = Collections.singletonList(
                filter("seed", "deviceId", 2, range(0.0, 0.0), range(n, n + 0.0001)));

        // When
        boolean filteredOut = Bucketing.isFilteredOut(filters, attributes);

        // Then
        assertFalse(filteredOut);
    }

    @Test
    @DisplayName("Filters out a user excluded by any one of several filters")
    void filtersOutUserExcludedByAnyFilter() {
        // Given
        JsonObject attributes = attributes("{\"id\": \"user-1\"}");
        List<Filter> filters = Arrays.asList(
                filter("seed-a", null, 2, range(0.0, 1.0)),
                filter("seed-b", null, 2, range(0.0, 0.0)));

        // When
        boolean filteredOut = Bucketing.isFilteredOut(filters, attributes);

        // Then
        assertTrue(filteredOut);
    }

    @Test
    @DisplayName("Hashes filters with version two by default")
    void hashesFiltersWithVersionTwoByDefault() {
        // Given
        JsonObject attributes = attributes("{\"id\": \"user-1\"}");
        double v2 = Bucketing.hash("user-1", 2, "seed");
        double v1 = Bucketing.hash("user-1", 1, "seed");
        BucketRange aroundV2 = range(v2, v2 + 0.0001);
        List<Filter> filters = Collections.singletonList(filter("seed", null, null, aroundV2));

        // When
        boolean filteredOut = Bucketing.isFilteredOut(filters, attributes);

        // Then
        assertFalse(Bucketing.inRange(v1, aroundV2));
        assertFalse(filteredOut);
    }

    @Test
    @DisplayName("Includes everyone in a rollout without a range or a coverage")
    void includesEveryoneWithoutRangeOrCoverage() {
        // Given
        JsonObject attributes = new JsonObject();

        // When
        boolean included = Bucketing.isIncludedInRollout(attributes, "seed", null, null, null, null, null);

        // Then
        assertTrue(included);
    }

    @Test
    @DisplayName("Includes nobody in a rollout with zero coverage and no range")
    void includesNobodyWithZeroCoverageAndNoRange() {
        // Given
        JsonObject attributes = attributes("{\"id\": \"user-1\"}");

        // When
        boolean included = Bucketing.isIncludedInRollout(attributes, "seed", null, null, null, 0.0, null);

        // Then
        assertFalse(included);
    }

    @Test
    @DisplayName("Excludes a user from a rollout when their hash attribute is missing")
    void excludesUserFromRolloutWhenHashAttributeIsMissing() {
        // Given
        JsonObject attributes = attributes("{\"id\": \"user-1\"}");

        // When
        boolean included = Bucketing.isIncludedInRollout(attributes, "seed", "deviceId", null, null, 1.0, 2);

        // Then
        assertFalse(included);
    }

    @Test
    @DisplayName("Excludes a user from a rollout when the hash version is unsupported")
    void excludesUserFromRolloutWhenHashVersionIsUnsupported() {
        // Given
        JsonObject attributes = attributes("{\"id\": \"user-1\"}");

        // When
        boolean included = Bucketing.isIncludedInRollout(attributes, "seed", null, null, null, 1.0, 3);

        // Then
        assertFalse(included);
    }

    @Test
    @DisplayName("Includes a user in a rollout only when their hash is below the coverage")
    void includesUserOnlyWhenHashIsBelowCoverage() {
        // Given
        JsonObject attributes = attributes("{\"id\": \"user-1\"}");
        double n = Bucketing.hash("user-1", 2, "seed");

        // When
        boolean atCoverage = Bucketing.isIncludedInRollout(attributes, "seed", null, null, null, n, 2);
        boolean justAbove = Bucketing.isIncludedInRollout(attributes, "seed", null, null, null, n + 0.0001, 2);

        // Then
        assertFalse(atCoverage);
        assertTrue(justAbove);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2500, 5000, 9999, 10000})
    @DisplayName("Includes exactly k hash buckets for a coverage of k over ten thousand")
    void includesExactlyKBucketsForCoverageOfKOverTenThousand(int k) {
        // Given
        double coverage = k / 10000.0;
        int included = 0;
        int expected = 0;

        // When
        for (int i = 0; i < 2000; i++) {
            String userId = "user-" + i;
            JsonObject attributes = new JsonObject();
            attributes.addProperty("id", userId);
            int bucket = (int) Math.round(Bucketing.hash(userId, 2, "rollout-seed") * 10000);
            if (bucket < k) {
                expected++;
            }
            if (Bucketing.isIncludedInRollout(attributes, "rollout-seed", null, null, null, coverage, 2)) {
                included++;
            }
        }

        // Then
        assertEquals(expected, included);
    }

    @Test
    @DisplayName("Uses the range instead of the coverage when both are given")
    void usesRangeInsteadOfCoverage() {
        // Given
        JsonObject attributes = attributes("{\"id\": \"user-1\"}");
        double n = Bucketing.hash("user-1", 1, "seed");

        // When
        boolean inRange = Bucketing.isIncludedInRollout(attributes, "seed", null, null, range(n, n + 0.001), 0.0, null);
        boolean outOfRange = Bucketing.isIncludedInRollout(attributes, "seed", null, null, range(0.0, n), 1.0, null);

        // Then
        assertTrue(inRange);
        assertFalse(outOfRange);
    }

    @Test
    @DisplayName("Hashes a rollout with version one by default")
    void hashesRolloutWithVersionOneByDefault() {
        // Given
        JsonObject attributes = attributes("{\"id\": \"user-1\"}");
        double v1 = Bucketing.hash("user-1", 1, "seed");
        double v2 = Bucketing.hash("user-1", 2, "seed");
        BucketRange aroundV1 = range(v1, v1 + 0.00005);

        // When
        boolean included = Bucketing.isIncludedInRollout(attributes, "seed", null, null, aroundV1, null, null);

        // Then
        assertFalse(Bucketing.inRange(v2, aroundV1));
        assertTrue(included);
    }

    @Test
    @DisplayName("Uses the fallback attribute in a rollout when the hash attribute is missing")
    void usesFallbackAttributeInRollout() {
        // Given
        JsonObject attributes = attributes("{\"deviceId\": \"device-1\"}");
        double n = Bucketing.hash("device-1", 1, "seed");

        // When
        boolean included = Bucketing.isIncludedInRollout(attributes, "seed", "id", "deviceId", range(n, n + 0.001), null, null);

        // Then
        assertTrue(included);
    }

    @Test
    @DisplayName("Uses the id attribute when no hash attribute is given")
    void usesIdAttributeByDefault() {
        // Given
        JsonObject attributes = attributes("{\"id\": \"user-1\", \"deviceId\": \"device-1\"}");

        // When
        HashAttributeAndHashValue result = Bucketing.getHashAttribute(null, null, attributes);

        // Then
        assertEquals(new HashAttributeAndHashValue("id", "user-1"), result);
    }

    @Test
    @DisplayName("Uses the primary attribute when it is present, even with a fallback")
    void usesPrimaryAttributeWhenPresent() {
        // Given
        JsonObject attributes = attributes("{\"id\": \"user-1\", \"deviceId\": \"device-1\"}");

        // When
        HashAttributeAndHashValue result = Bucketing.getHashAttribute("id", "deviceId", attributes);

        // Then
        assertEquals(new HashAttributeAndHashValue("id", "user-1"), result);
    }

    @Test
    @DisplayName("Uses the fallback attribute when the primary is missing and the fallback present")
    void usesFallbackWhenPrimaryMissing() {
        // Given
        JsonObject attributes = attributes("{\"deviceId\": \"device-1\"}");

        // When
        HashAttributeAndHashValue result = Bucketing.getHashAttribute("id", "deviceId", attributes);

        // Then
        assertEquals(new HashAttributeAndHashValue("deviceId", "device-1"), result);
    }

    @Test
    @DisplayName("Uses the fallback attribute when the primary counts as missing")
    void usesFallbackWhenPrimaryCountsAsMissing() {
        // Given
        JsonObject attributes = attributes("{\"id\": \"\", \"deviceId\": \"device-1\"}");

        // When
        HashAttributeAndHashValue result = Bucketing.getHashAttribute("id", "deviceId", attributes);

        // Then
        assertEquals(new HashAttributeAndHashValue("deviceId", "device-1"), result);
    }

    @Test
    @DisplayName("Keeps the primary attribute with an empty value when both are missing")
    void keepsPrimaryAttributeWhenBothMissing() {
        // Given
        JsonObject attributes = attributes("{\"companyId\": \"company-1\"}");

        // When
        HashAttributeAndHashValue result = Bucketing.getHashAttribute("id", "deviceId", attributes);

        // Then
        assertEquals(new HashAttributeAndHashValue("id", ""), result);
    }

    @Test
    @DisplayName("Returns an empty hash value when there are no attributes")
    void returnsEmptyHashValueWhenNoAttributes() {
        // Given
        JsonObject attributes = null;

        // When
        HashAttributeAndHashValue result = Bucketing.getHashAttribute("id", "deviceId", attributes);

        // Then
        assertEquals(new HashAttributeAndHashValue("id", ""), result);
    }

    @ParameterizedTest(name = "{0} -> \"{1}\"")
    @MethodSource("hashableValues")
    @DisplayName("Converts strings and finite non-zero numbers to the value that gets hashed")
    void convertsStringsAndNumbersToHashValue(JsonElement value, String expected) {
        // Given
        // When
        String result = Bucketing.toHashValue(value);

        // Then
        assertEquals(expected, result);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("missingValues")
    @DisplayName("Treats zero, booleans, null, arrays, objects and non-finite numbers as missing")
    void treatsNonHashableValuesAsMissing(JsonElement value) {
        // Given
        // When
        String result = Bucketing.toHashValue(value);

        // Then
        assertEquals("", result);
    }

    static Stream<Arguments> hashableValues() {
        return Stream.of(
                Arguments.of(new JsonPrimitive("user-1"), "user-1"),
                Arguments.of(new JsonPrimitive("0"), "0"),
                Arguments.of(new JsonPrimitive(1.0), "1"),
                Arguments.of(new JsonPrimitive(42), "42"),
                Arguments.of(new JsonPrimitive(1.5), "1.5"),
                Arguments.of(new JsonPrimitive(-3), "-3"),
                Arguments.of(JsonParser.parseString("123"), "123")
        );
    }

    static Stream<Arguments> missingValues() {
        return Stream.of(
                Arguments.of((JsonElement) null),
                Arguments.of(JsonNull.INSTANCE),
                Arguments.of(new JsonPrimitive("")),
                Arguments.of(new JsonPrimitive(0)),
                Arguments.of(new JsonPrimitive(0.0)),
                Arguments.of(new JsonPrimitive(true)),
                Arguments.of(new JsonPrimitive(false)),
                Arguments.of(new JsonPrimitive(Double.NaN)),
                Arguments.of(new JsonPrimitive(Double.POSITIVE_INFINITY)),
                Arguments.of(new JsonPrimitive(Double.NEGATIVE_INFINITY)),
                Arguments.of(JsonParser.parseString("[\"user-1\"]")),
                Arguments.of(JsonParser.parseString("{\"id\": \"user-1\"}"))
        );
    }

    private static JsonObject attributes(String json) {
        return JsonParser.parseString(json).getAsJsonObject();
    }

    private static BucketRange range(double start, double end) {
        return BucketRange.builder().rangeStart(start).rangeEnd(end).build();
    }

    private static Filter filter(String seed, String attribute, Integer hashVersion, BucketRange... ranges) {
        return Filter.builder()
                .seed(seed)
                .attribute(attribute)
                .hashVersion(hashVersion)
                .ranges(Arrays.asList(ranges))
                .build();
    }

    private static void assertRanges(List<BucketRange> ranges, double... bounds) {
        assertEquals(bounds.length / 2, ranges.size());
        for (int i = 0; i < ranges.size(); i++) {
            assertEquals(bounds[2 * i], ranges.get(i).getRangeStart(), DELTA);
            assertEquals(bounds[2 * i + 1], ranges.get(i).getRangeEnd(), DELTA);
        }
    }
}
