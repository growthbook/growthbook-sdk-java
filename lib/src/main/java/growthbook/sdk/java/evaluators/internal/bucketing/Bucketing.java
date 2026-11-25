package growthbook.sdk.java.evaluators.internal.bucketing;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import growthbook.sdk.java.model.BucketRange;
import growthbook.sdk.java.model.Filter;
import growthbook.sdk.java.model.HashAttributeAndHashValue;
import growthbook.sdk.java.util.MathUtils;
import growthbook.sdk.java.util.StringUtils;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Hashing and bucket assignment: hashes, bucket ranges, namespaces, filters and rollouts.
 * Pure functions over their arguments, safe for concurrent use.
 */
public final class Bucketing {

    private static final String DEFAULT_HASH_ATTRIBUTE = "id";

    private Bucketing() {
    }

    /**
     * Hashes a string to a double between 0 and 1 with fnv32a.
     *
     * @param stringValue the value to hash
     * @param hashVersion {@code 1} or {@code 2}
     * @param seed        mixed into the hash, usually the experiment key
     * @return the hash, or {@code null} if the hash version is missing or unsupported
     */
    @Nullable
    public static Double hash(String stringValue, @Nullable Integer hashVersion, String seed) {
        if (hashVersion == null) {
            return null;
        }
        switch (hashVersion) {
            case 1:
                return (MathUtils.fnv1a_32(stringValue + seed) % 1000) / 1000.0;
            case 2:
                long first = MathUtils.fnv1a_32(seed + stringValue);
                return (MathUtils.fnv1a_32(String.valueOf(first)) % 10000) / 10000.0;
            default:
                return null;
        }
    }

    /**
     * @param hashValue   the user's hash value
     * @param namespaceId the namespace id
     * @param rangeStart  the start of the experiment's part of the namespace, inclusive
     * @param rangeEnd    the end of the experiment's part of the namespace, exclusive
     * @return whether the user falls in the experiment's part of the namespace
     */
    public static boolean inNamespace(String hashValue, String namespaceId, double rangeStart, double rangeEnd) {
        Double n = hash(hashValue + "__", 1, namespaceId);
        return n != null && n >= rangeStart && n < rangeEnd;
    }

    /**
     * @param n            the hash
     * @param bucketRanges one range per variation
     * @return the index of the range the hash falls in, or {@code -1} if none
     */
    public static int chooseVariation(double n, List<BucketRange> bucketRanges) {
        for (int i = 0; i < bucketRanges.size(); i++) {
            if (inRange(n, bucketRanges.get(i))) {
                return i;
            }
        }
        return -1;
    }

    /**
     * @param numberOfVariations the number of variations; zero or negative yields an empty list
     * @return equal weights that sum to 1, e.g. {@code [0.5, 0.5]} for 2
     */
    public static List<Double> getEqualWeights(int numberOfVariations) {
        if (numberOfVariations <= 0) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Collections.nCopies(numberOfVariations, 1.0 / numberOfVariations));
    }

    /**
     * Converts an experiment's coverage and variation weights into one bucket range per variation.
     * Weights that don't match the number of variations, or don't sum to 1 (±0.01), are replaced
     * by equal weights.
     *
     * @param numberOfVariations the number of variations
     * @param coverage           the share of users included, clamped to 0..1
     * @param weights            the variation weights, or {@code null} for equal weights
     * @return the bucket ranges
     */
    public static List<BucketRange> getBucketRanges(int numberOfVariations, double coverage, @Nullable List<Double> weights) {
        double clampedCoverage = MathUtils.clamp(coverage, 0.0, 1.0);

        List<Double> adjustedWeights = weights;
        if (weights == null || weights.size() != numberOfVariations) {
            adjustedWeights = getEqualWeights(numberOfVariations);
        }
        double sumOfWeights = MathUtils.sum(adjustedWeights);
        if (sumOfWeights < 0.99 || sumOfWeights > 1.01) {
            adjustedWeights = getEqualWeights(numberOfVariations);
        }

        List<BucketRange> bucketRanges = new ArrayList<>();
        double cumulative = 0.0;
        for (double weight : adjustedWeights) {
            double start = cumulative;
            cumulative += weight;
            bucketRanges.add(BucketRange.builder()
                    .rangeStart(start)
                    .rangeEnd(start + clampedCoverage * weight)
                    .build());
        }
        return bucketRanges;
    }

    /**
     * @param n     the hash
     * @param range the range, start inclusive and end exclusive
     * @return whether the hash is in the range; false if either is {@code null}
     */
    public static boolean inRange(@Nullable Double n, @Nullable BucketRange range) {
        return n != null && range != null && n >= range.getRangeStart() && n < range.getRangeEnd();
    }

    /**
     * A user is filtered out when any filter excludes them: their hash attribute is missing, or
     * their hash falls in none of the filter's ranges.
     *
     * @param filters    the filters, or {@code null} for none
     * @param attributes the user attributes, or {@code null}
     * @return whether the user is filtered out; false when there are no filters or attributes
     */
    public static boolean isFilteredOut(@Nullable List<Filter> filters, @Nullable JsonObject attributes) {
        if (filters == null || attributes == null) {
            return false;
        }
        for (Filter filter : filters) {
            if (excludes(filter, attributes)) {
                return true;
            }
        }
        return false;
    }

    private static boolean excludes(Filter filter, JsonObject attributes) {
        String hashAttribute = filter.getAttribute() != null ? filter.getAttribute() : DEFAULT_HASH_ATTRIBUTE;
        String hashValue = toHashValue(attributes.get(hashAttribute));
        if (hashValue.isEmpty()) {
            return true;
        }
        int hashVersion = filter.getHashVersion() != null ? filter.getHashVersion() : 2;
        Double n = hash(hashValue, hashVersion, filter.getSeed());
        List<BucketRange> ranges = filter.getRanges();
        if (n == null || ranges == null) {
            return true;
        }
        for (BucketRange range : ranges) {
            if (inRange(n, range)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Whether the user is in a gradual rollout. Without a range or a coverage everyone is; with a
     * zero coverage and no range nobody is. With a coverage, the user is included when their hash
     * is below it, so a coverage of k/10000 includes exactly k hash buckets.
     *
     * @param attributes        the user attributes
     * @param seed              mixed into the hash
     * @param hashAttribute     the attribute to hash, {@code id} when {@code null}
     * @param fallbackAttribute the attribute to hash when the first is missing, or {@code null}
     * @param range             the included range, or {@code null} to use the coverage
     * @param coverage          the included share, or {@code null}
     * @param hashVersion       the hash version, 1 when {@code null}
     * @return whether the user is included; false when their hash attribute is missing
     */
    public static boolean isIncludedInRollout(
            JsonObject attributes,
            String seed,
            @Nullable String hashAttribute,
            @Nullable String fallbackAttribute,
            @Nullable BucketRange range,
            @Nullable Double coverage,
            @Nullable Integer hashVersion
    ) {
        if (range == null && coverage == null) {
            return true;
        }
        if (range == null && coverage == 0) {
            return false;
        }

        String hashValue = getHashAttribute(hashAttribute, fallbackAttribute, attributes).getHashValue();
        if (hashValue == null || hashValue.isEmpty()) {
            return false;
        }

        Double hash = hash(hashValue, hashVersion != null ? hashVersion : 1, seed);
        if (hash == null) {
            return false;
        }
        return range != null ? inRange(hash, range) : hash < coverage;
    }

    /**
     * Picks the attribute to hash: {@code attr} ({@code id} when {@code null}), or the fallback
     * when the first one is missing and the fallback is present.
     *
     * @param attr              the hash attribute
     * @param fallbackAttribute the fallback attribute, or {@code null}
     * @param attributes        the user attributes, or {@code null}
     * @return the chosen attribute and its hash value; an empty value when both are missing
     */
    public static HashAttributeAndHashValue getHashAttribute(
            @Nullable String attr,
            @Nullable String fallbackAttribute,
            @Nullable JsonObject attributes
    ) {
        String hashAttribute = attr != null ? attr : DEFAULT_HASH_ATTRIBUTE;
        String hashValue = attributes != null ? toHashValue(attributes.get(hashAttribute)) : "";

        if (hashValue.isEmpty() && fallbackAttribute != null && attributes != null) {
            String fallbackValue = toHashValue(attributes.get(fallbackAttribute));
            if (!fallbackValue.isEmpty()) {
                return new HashAttributeAndHashValue(fallbackAttribute, fallbackValue);
            }
        }
        return new HashAttributeAndHashValue(hashAttribute, hashValue);
    }

    /**
     * Converts an attribute value to the string that gets hashed, like the JS SDK: a non-empty
     * string as is, and a finite, non-zero number formatted like JS {@code String(number)}, so
     * {@code 1.0} hashes as {@code "1"}. Any other value, including {@code 0}, booleans, null,
     * arrays and objects, counts as missing.
     *
     * @param value the attribute value, or null when the attribute is absent
     * @return the value to hash, or an empty string when the attribute counts as missing
     */
    public static String toHashValue(@Nullable JsonElement value) {
        if (value == null || !value.isJsonPrimitive()) {
            return "";
        }
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        if (primitive.isString()) {
            return primitive.getAsString();
        }
        if (primitive.isNumber()) {
            double number = primitive.getAsDouble();
            if (number == 0 || Double.isNaN(number) || Double.isInfinite(number)) {
                return "";
            }
            return StringUtils.jsNumberToString(number);
        }
        return "";
    }
}
