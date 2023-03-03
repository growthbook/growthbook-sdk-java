package growthbook.sdk.java;

import com.google.gson.Gson;

import javax.annotation.Nullable;
import java.math.BigInteger;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;

/**
 * <b>INTERNAL</b>: Implementation of for internal utility methods to support {@link growthbook.sdk.java.GrowthBook}
 */
class GrowthBookUtils {
    /**
     * Hashes a string to a float between 0 and 1.
     * Uses the simple Fowler–Noll–Vo algorithm, specifically fnv32a.
     *
     * @param stringValue Input string
     * @return hashed float value
     */
    public static Float hash(String stringValue) {
        BigInteger bigInt = MathUtils.fnv1a_32(stringValue.getBytes());
        BigInteger thousand = new BigInteger("1000");
        BigInteger remainder = bigInt.remainder(thousand);

        String remainderAsString = remainder.toString();
        float remainderAsFloat = Float.parseFloat(remainderAsString);
        return remainderAsFloat / 1000f;
    }

    /**
     * This checks if a userId is within an experiment namespace or not.
     *
     * @param userId    The user identifier
     * @param namespace Namespace to check the user identifier against
     * @return whether the user is in the namespace
     */
    public static Boolean inNameSpace(String userId, Namespace namespace) {
        Float n = hash(userId + "__" + namespace.getId());
        return n >= namespace.getRangeStart() && n < namespace.getRangeEnd();
    }

    /**
     * Given a hash and bucket ranges, assign one of the bucket ranges.
     * Returns -1 if none can be found
     *
     * @param n            hash
     * @param bucketRanges list of {@link BucketRange}
     * @return index of the {@link BucketRange} list to assign
     */
    public static Integer chooseVariation(Float n, ArrayList<BucketRange> bucketRanges) {
        for (int i = 0; i < bucketRanges.size(); i++) {
            BucketRange range = bucketRanges.get(i);
            if (n >= range.getRangeStart() && n < range.getRangeEnd()) {
                return i;
            }
        }

        return -1;
    }

    /**
     * Returns an array of floats with numVariations items that are all equal and sum to 1.
     * For example, getEqualWeights(2) would return [0.5, 0.5]
     *
     * @param numberOfVariations The number of variations you would like
     * @return A list of variations
     */
    public static ArrayList<Float> getEqualWeights(Integer numberOfVariations) {
        // Accommodate -1 number of variations
        int size = Math.max(0, numberOfVariations);
        if (size == 0) {
            return new ArrayList<>();
        }

        Float weight = 1f / numberOfVariations;
        return new ArrayList<Float>(Collections.nCopies(size, weight));
    }

    // region Experiment for query string

    /**
     * This checks if an experiment variation is being forced via a URL query string.
     * This may not be applicable for all SDKs (e.g. mobile).
     * <p>
     * As an example, if the id is my-test and url is http://localhost/?my-test=1,
     * it would return 1.
     * <p>
     * Returns null if any of these are true:
     *
     * <ul>
     *     <li>There is no query string</li>
     *     <li>The id is not a key in the query string</li>
     *     <li>The variation is not an integer</li>
     *     <li>The variation is less than 0 or greater than or equal to numVariations</li>
     * </ul>
     *
     * @param id                 the identifier
     * @param urlString          the desired page URL as a string
     * @param numberOfVariations the number of variations
     * @return integer or null
     */
    @Nullable
    public static Integer getQueryStringOverride(String id, String urlString, Integer numberOfVariations) {
        try {
            URL url = new URL(urlString);
            return getQueryStringOverride(id, url, numberOfVariations);
        } catch (MalformedURLException e) {
            return null;
        }
    }

    /**
     * This checks if an experiment variation is being forced via a URL query string.
     * This may not be applicable for all SDKs (e.g. mobile).
     * <p>
     * As an example, if the id is my-test and url is http://localhost/?my-test=1,
     * it would return 1.
     * <p>
     * Returns null if any of these are true:
     *
     * <ul>
     *     <li>There is no query string</li>
     *     <li>The id is not a key in the query string</li>
     *     <li>The variation is not an integer</li>
     *     <li>The variation is less than 0 or greater than or equal to numVariations</li>
     * </ul>
     *
     * @param id                 the identifier
     * @param url                the desired page URL
     * @param numberOfVariations the number of variations
     * @return integer or null
     */
    @Nullable
    public static Integer getQueryStringOverride(String id, URL url, Integer numberOfVariations) {
        String query = url.getQuery();
        Map<String, String> queryMap = UrlUtils.parseQueryString(query);

        String possibleValue = queryMap.get(id);
        if (possibleValue == null) {
            return null;
        }

        try {
            int variationValue = Integer.parseInt(possibleValue);
            if (variationValue < 0 || variationValue >= numberOfVariations) {
                return null;
            }

            return variationValue;
        } catch (NumberFormatException exception) {
            exception.printStackTrace();
            return null;
        }
    }

    // endregion Experiment for query string



    // region Forced feature for URL

    /**
     * Given a URL and a feature key, will find the raw value in the URL if it exists, otherwise returns null
     * @param featureKey  Feature ID/key (not prefixed with gb~)
     * @param url  Page URL to evaluate for forced feature values
     * @return forced feature value
     */
    private static @Nullable String getForcedFeatureRawValueForKeyFromUrl(String featureKey, URL url) {
        String prefixedKey = "gb~" + featureKey;
        Map<String, String> queryMap = UrlUtils.parseQueryString(url.getQuery());

        if (!queryMap.containsKey(prefixedKey)) {
            return null;
        }

        return queryMap.get(prefixedKey);
    }

    // region Forced feature for URL -> Boolean

    /**
     * Evaluate a forced boolean value from a URL. If the provided key is not present in the URL, return null.
     * @param featureKey    feature ID/key (not prefixed with gb~)
     * @param url    Page URL to evaluate for forced feature values
     * @return  value or null
     */
    @Nullable
    public static Boolean getForcedBooleanValueFromUrl(String featureKey, URL url) {
        String value = getForcedFeatureRawValueForKeyFromUrl(featureKey, url);

        if (value == null) return null;

        value = value.toLowerCase();

        if (value.equals("true") || value.equals("1") || value.equals("on")) {
            return true;
        }

        if (value.equals("false") || value.equals("0") || value.equals("off")) {
            return false;
        }

        return null;
    }

    // endregion Forced feature for URL -> Boolean

    // region Forced feature for URL -> String

    /**
     * Evaluate a forced string value from a URL. If the provided key is not present in the URL, return null.
     * @param featureKey    feature ID/key (not prefixed with gb~)
     * @param url    Page URL to evaluate for forced feature values
     * @return  value or null
     */
    @Nullable
    public static String getForcedStringValueFromUrl(String featureKey, URL url) {
        return getForcedFeatureRawValueForKeyFromUrl(featureKey, url);
    }

    // endregion Forced feature for URL -> String

    // region Forced feature for URL -> Float

    /**
     * Evaluate a forced float value from a URL. If the provided key is not present in the URL, return null.
     * @param featureKey    feature ID/key (not prefixed with gb~)
     * @param url    Page URL to evaluate for forced feature values
     * @return  value or null
     */
    @Nullable
    public static Float getForcedFloatValueFromUrl(String featureKey, URL url) {
        String value = getForcedFeatureRawValueForKeyFromUrl(featureKey, url);

        if (value == null) return null;

        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // endregion Forced feature for URL -> Float


    // region Forced feature for URL -> Double

    /**
     * Evaluate a forced float value from a URL. If the provided key is not present in the URL, return null.
     * @param featureKey    feature ID/key (not prefixed with gb~)
     * @param url    Page URL to evaluate for forced feature values
     * @return  value or null
     */
    @Nullable
    public static Double getForcedDoubleValueFromUrl(String featureKey, URL url) {
        String value = getForcedFeatureRawValueForKeyFromUrl(featureKey, url);

        if (value == null) return null;

        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // endregion Forced feature for URL -> Double


    // region Forced feature for URL -> Integer

    /**
     * Evaluate a forced integer value from a URL. If the provided key is not present in the URL, return null.
     * @param featureKey    feature ID/key (not prefixed with gb~)
     * @param url    Page URL to evaluate for forced feature values
     * @return  value or null
     */
    @Nullable
    public static Integer getForcedIntegerValueFromUrl(String featureKey, URL url) {
        String value = getForcedFeatureRawValueForKeyFromUrl(featureKey, url);

        if (value == null) return null;

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // endregion Forced feature for URL -> Integer

    // region Forced feature for URL -> Objects

    @Nullable
    public static <ValueType> ValueType getForcedSerializableValueFromUrl(String featureKey, URL url, Class<ValueType> valueTypeClass, Gson gson) {
        String value = getForcedFeatureRawValueForKeyFromUrl(featureKey, url);

        if (value == null) return null;

        try {
            return gson.fromJson(value, valueTypeClass);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // endregion Forced feature for URL -> Objects

    // endregion Forced feature for URL

    /**
     * This converts and experiment's coverage and variation weights into an array of bucket ranges.
     * Defaults to equal weights if the sum of the weight is not equal to 1 (rounded).
     *
     * @param numberOfVariations number of variations
     * @param coverage           the amount that should be covered, e.g. 0.5 is 50%
     * @param weights            List of weights. If these do not sum to 1, equal weights will be applied.
     * @return list of {@link BucketRange}
     */
    public static ArrayList<BucketRange> getBucketRanges(
            Integer numberOfVariations,
            Float coverage,
            @Nullable ArrayList<Float> weights
    ) {
        float clampedCoverage = MathUtils.clamp(coverage, 0.0f, 1.0f);

        // When the number of variations doesn't match the weights provided, ignore the weights and get equal weights.
        ArrayList<Float> adjustedWeights = weights;
        if (weights == null || numberOfVariations != weights.size()) {
            adjustedWeights = getEqualWeights(numberOfVariations);
        }

        // When the sums of the weights are not equal to 1, ignore the weights and get equal weights
        float sumOfWeights = MathUtils.sum(adjustedWeights);
        if (sumOfWeights < 0.99 || sumOfWeights > 1.01) {
            adjustedWeights = getEqualWeights(numberOfVariations);
        }

        float start = 0.0f;
        float cumulative = 0.0f;
        ArrayList<BucketRange> bucketRanges = new ArrayList<>();

        for (float weight : adjustedWeights) {
            start = cumulative;
            cumulative += weight;

            BucketRange bucketRange = BucketRange
                    .builder()
                    .rangeStart(start)
                    .rangeEnd(start + clampedCoverage * weight)
                    .build();

            bucketRanges.add(bucketRange);
        }

        return bucketRanges;
    }
}
