package growthbook.sdk.java;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import growthbook.sdk.java.stickyBucketing.StickyAssignmentsDocument;
import growthbook.sdk.java.stickyBucketing.StickyBucketService;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import javax.annotation.Nullable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * <b>INTERNAL</b>: Implementation of for internal utility methods to support {@link growthbook.sdk.java.GrowthBook}
 */
@Slf4j
class GrowthBookUtils {
    /**
     * Hashes a string to a float between 0 and 1, or null if the hash version is unsupported.
     * Uses the simple Fowler–Noll–Vo algorithm, specifically fnv32a.
     *
     * @param stringValue Input string
     * @param hashVersion The hash version
     * @param seed        A seed value that can be used instead of the experiment key for hashing
     * @return hashed float value or null if the hash version is unsupported.
     */
    public static @Nullable Float hash(String stringValue, Integer hashVersion, String seed) {
        if (hashVersion == null) return null;

        switch (hashVersion) {
            case 1:
                return hashV1(stringValue, seed);
            case 2:
                return hashV2(stringValue, seed);
            default:
                return null;
        }
    }

    private static Float hashV1(String stringValue, String seed) {
        long hashValue = MathUtils.fnv1a_32((stringValue + seed).getBytes());
        long thousand = 1000;
        long remainder = hashValue % thousand;

        float remainderAsFloat = Float.parseFloat(String.valueOf(remainder));
        return remainderAsFloat / 1000f;
    }

    private static Float hashV2(String stringValue, String seed) {
        long first = MathUtils.fnv1a_32((seed + stringValue).getBytes());
        long second = MathUtils.fnv1a_32(String.valueOf(first).getBytes());

        long tenThousand = 10000;
        long remainder = second % tenThousand;

        float remainderAsFloat = Float.parseFloat(String.valueOf(remainder));
        return remainderAsFloat / 10000f;
    }

    /**
     * This checks if a userId is within an experiment namespace or not.
     *
     * @param userId    The user identifier
     * @param namespace Namespace to check the user identifier against
     * @return whether the user is in the namespace
     */
    public static Boolean inNameSpace(String userId, Namespace namespace) {
        Float n = hash(userId + "__", 1, namespace.getId());
        if (n == null) return false;
        return inRange(n, BucketRange
                .builder()
                .rangeStart(namespace.getRangeStart())
                .rangeEnd(namespace.getRangeEnd())
                .build()
        );
    }

    /**
     * Given a hash and bucket ranges, assign one of the bucket ranges.
     * Returns -1 if none can be found
     *
     * @param n            hash
     * @param bucketRanges list of {@link BucketRange}
     * @return index of the {@link BucketRange} list to assign
     */
    public static Integer chooseVariation(@NotNull Float n, ArrayList<BucketRange> bucketRanges) {
        for (int i = 0; i < bucketRanges.size(); i++) {
            BucketRange range = bucketRanges.get(i);
            if (inRange(n, range)) {
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
        if (urlString == null || urlString.isEmpty()) {
            return null;
        }
        try {
            URL url = new URL(urlString);
            return getQueryStringOverride(id, url, numberOfVariations);
        } catch (MalformedURLException e) {
            log.error(e.getMessage(), e);
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
        } catch (NumberFormatException e) {
            log.error(e.getMessage(), e);
            return null;
        }
    }

    // endregion Experiment for query string


    // region Forced feature for URL

    /**
     * Given a URL and a feature key, will find the raw value in the URL if it exists, otherwise returns null
     *
     * @param featureKey Feature ID/key (not prefixed with gb~)
     * @param url        Page URL to evaluate for forced feature values
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
     *
     * @param featureKey feature ID/key (not prefixed with gb~)
     * @param url        Page URL to evaluate for forced feature values
     * @return value or null
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
     *
     * @param featureKey feature ID/key (not prefixed with gb~)
     * @param url        Page URL to evaluate for forced feature values
     * @return value or null
     */
    @Nullable
    public static String getForcedStringValueFromUrl(String featureKey, URL url) {
        return getForcedFeatureRawValueForKeyFromUrl(featureKey, url);
    }

    // endregion Forced feature for URL -> String

    // region Forced feature for URL -> Float

    /**
     * Evaluate a forced float value from a URL. If the provided key is not present in the URL, return null.
     *
     * @param featureKey feature ID/key (not prefixed with gb~)
     * @param url        Page URL to evaluate for forced feature values
     * @return value or null
     */
    @Nullable
    public static Float getForcedFloatValueFromUrl(String featureKey, URL url) {
        String value = getForcedFeatureRawValueForKeyFromUrl(featureKey, url);

        if (value == null) return null;

        try {
            return Float.parseFloat(value);
        } catch (NumberFormatException e) {
            log.error(e.getMessage(), e);
            return null;
        }
    }

    // endregion Forced feature for URL -> Float


    // region Forced feature for URL -> Double

    /**
     * Evaluate a forced float value from a URL. If the provided key is not present in the URL, return null.
     *
     * @param featureKey feature ID/key (not prefixed with gb~)
     * @param url        Page URL to evaluate for forced feature values
     * @return value or null
     */
    @Nullable
    public static Double getForcedDoubleValueFromUrl(String featureKey, URL url) {
        String value = getForcedFeatureRawValueForKeyFromUrl(featureKey, url);

        if (value == null) return null;

        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            log.error(e.getMessage(), e);
            return null;
        }
    }

    // endregion Forced feature for URL -> Double


    // region Forced feature for URL -> Integer

    /**
     * Evaluate a forced integer value from a URL. If the provided key is not present in the URL, return null.
     *
     * @param featureKey feature ID/key (not prefixed with gb~)
     * @param url        Page URL to evaluate for forced feature values
     * @return value or null
     */
    @Nullable
    public static Integer getForcedIntegerValueFromUrl(String featureKey, URL url) {
        String value = getForcedFeatureRawValueForKeyFromUrl(featureKey, url);

        if (value == null) return null;

        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.error(e.getMessage(), e);
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
            log.error(e.getMessage(), e);
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
            @NotNull Float coverage,
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

    /**
     * Verifies if the provided float is within the lower (inclusive) and upper (exclusive) bounds of
     * the provided {@link BucketRange}.
     *
     * @param n     Float value to check if it's in range
     * @param range {@link BucketRange}
     * @return whether to include this hash value is within range. Returns false if either arguments are null.
     */
    public static Boolean inRange(Float n, BucketRange range) {
        if (n == null || range == null) return false;
        return n >= range.getRangeStart() && n < range.getRangeEnd();
    }

    public static Boolean isFilteredOut(List<Filter> filters, JsonObject attributeOverrides, GBContext context) {
        if (filters == null) return false;
        if (attributeOverrides == null) return false;

        return filters.stream().anyMatch(filter -> {
            String hashAttribute = filter.getAttribute();
            if (hashAttribute == null) {
                hashAttribute = "id";
            }

            JsonObject attributes = context.getAttributes();
            if (attributes == null) {
                attributes = new JsonObject();
            }
            JsonElement hashValueElement = attributes.get(hashAttribute);
            if (hashValueElement == null) return true;
            if (hashValueElement.isJsonNull()) return true;
            if (!hashValueElement.isJsonPrimitive()) return true;

            JsonPrimitive hashValuePrimitive = hashValueElement.getAsJsonPrimitive();

            String hashValue = hashValuePrimitive.getAsString();
            if (hashValue == null || hashValue.equals("")) return true;

            Integer hashVersion = filter.getHashVersion();
            if (hashVersion == null) {
                hashVersion = 2;
            }

            Float n = GrowthBookUtils.hash(hashValue, hashVersion, filter.getSeed());
            if (n == null) return true;

            List<BucketRange> ranges = filter.getRanges();
            if (ranges == null) return true;

            return ranges.stream().noneMatch(range -> GrowthBookUtils.inRange(n, range));
        });
    }

    public static Boolean isIncludedInRollout(
            JsonObject attributeOverrides,
            String seed,
            String hashAttribute,
            String fallbackAttribute,
            @Nullable BucketRange range,
            @Nullable Float coverage,
            @Nullable Integer hashVersion,
            GBContext context
    ) {
        if (range == null && coverage == null) return true;

        if (hashVersion == null) {
            hashVersion = 1;
        }
        HashAttributeAndHashValue hashAttributeAndHashValue = GrowthBookUtils
                .getHashAttribute(context, hashAttribute, fallbackAttribute, attributeOverrides);

        Float hash = GrowthBookUtils.hash(
                hashAttributeAndHashValue.getHashValue(),
                hashVersion,
                seed
        );

        if (hash == null) return false;

        if (range != null) {
            return GrowthBookUtils.inRange(hash, range);
        } else if (coverage != null) {
            return hash <= coverage;
        } else {
            return true;
        }
    }

    public static void refreshStickyBuckets(GBContext context,
                                            String featuresDataModel,
                                            JsonObject attributeOverrides) {
        StickyBucketService stickyBucketService = context.getStickyBucketService();
        if (stickyBucketService == null) {
            return;
        }
        Map<String, String> stickyBucketAttributes =
                getStickyBucketAttributes(
                        context,
                        featuresDataModel,
                        attributeOverrides
                );

        context.setStickyBucketAssignmentDocs(
                stickyBucketService.getAllAssignments(
                        stickyBucketAttributes
                )
        );
    }

    public static Map<String, String> getStickyBucketAttributes(GBContext context,
                                                                String featuresDataModel,
                                                                JsonObject attributeOverrides) {
        Map<String, String> attributes = new HashMap<>();


        if (context.getStickyBucketIdentifierAttributes() != null) {
            context.setStickyBucketIdentifierAttributes(deriveStickyBucketIdentifierAttributes(context, featuresDataModel));

            for (String attr : context.getStickyBucketIdentifierAttributes()) {
                HashAttributeAndHashValue hashAttribute = getHashAttribute(
                        context,
                        attr,
                        null,
                        attributeOverrides
                );
                attributes.put(attr, hashAttribute.getHashValue());
            }
        }

        return attributes;
    }

    private static @Nullable <ValueType> List<String> deriveStickyBucketIdentifierAttributes
            (GBContext context,
             String featureDataModel
            ) {
        Set<String> attributes = new HashSet<>();

        JsonObject jsonObject = GrowthBookJsonUtils.getInstance()
                .gson.fromJson(featureDataModel, JsonObject.class);

        JsonElement featuresJsonElement = jsonObject.get("features");
        JsonElement features = featuresJsonElement != null ? featuresJsonElement : context.getFeatures();

        if (features != null) {
            for (String id : features.getAsJsonObject().keySet()) {
                Feature<ValueType> feature = GrowthBookJsonUtils
                        .getInstance()
                        .gson
                        .fromJson(features.getAsJsonObject().get(id), Feature.class);

                if (feature != null && feature.getRules() != null) {

                    for (FeatureRule<ValueType> rule : feature.getRules()) {
                        ArrayList<ValueType> variations = rule.getVariations();

                        if (variations != null && !variations.isEmpty()) {
                            String attr = rule.getHashAttribute() != null ? rule.getHashAttribute() : "id";
                            attributes.add(attr);

                            String fallbackAttribute = rule.getFallbackAttribute();
                            if (fallbackAttribute != null) {
                                attributes.add(fallbackAttribute);
                            }
                        }
                    }
                }
            }
        }

        return new ArrayList<>(attributes);
    }

    public static Map<String, String> getStickyBucketAssignments(
            GBContext context,
            @Nullable String expHashAttribute,
            @Nullable String expFallbackAttribute,
            JsonObject attributeOverrides
    ) {
        Map<String, String> mergedAssignments = new HashMap<>();

        Map<String, StickyAssignmentsDocument> stickyAssignmentsDocuments = context.getStickyBucketAssignmentDocs();
        if (context.getStickyBucketAssignmentDocs() == null) {
            return mergedAssignments;
        }

        HashAttributeAndHashValue hashAttributeAndHashValueWithoutFallbackPass = getHashAttribute(
                context,
                expHashAttribute,
                null,
                attributeOverrides
        );
        String hashKey = hashAttributeAndHashValueWithoutFallbackPass.getHashAttribute() + "||"
                + hashAttributeAndHashValueWithoutFallbackPass.getHashValue();

        HashAttributeAndHashValue hashAttributeAndHashValueWithFallbackAttribute = getHashAttribute(
                context,
                null,
                expFallbackAttribute,
                attributeOverrides
        );
        String fallBackKey = hashAttributeAndHashValueWithFallbackAttribute.getHashValue().isEmpty()
                ? null
                : hashAttributeAndHashValueWithFallbackAttribute.getHashAttribute()
                + "||"
                + hashAttributeAndHashValueWithFallbackAttribute.getHashValue();

        if (attributeOverrides.get(expFallbackAttribute) != null) {
            String leftOperand = stickyAssignmentsDocuments.get(
                    expFallbackAttribute + "||" + attributeOverrides.get(expFallbackAttribute).getAsString()
            ) == null
                    ? null
                    : stickyAssignmentsDocuments.get(
                    expFallbackAttribute + "||" + attributeOverrides.get(expFallbackAttribute).getAsString()
            ).getAttributeValue();

            if (!Objects.equals(leftOperand, attributeOverrides.get(expFallbackAttribute).getAsString())) {
                context.setStickyBucketAssignmentDocs(new HashMap<>());
            }
        }

        if (context.getStickyBucketAssignmentDocs() != null) {
            context.getStickyBucketAssignmentDocs().entrySet().stream()
                    .map(Map.Entry::getValue)
                    .forEach(it -> mergedAssignments.putAll(it.getAssignments()));
        }

        if (fallBackKey != null) {
            if (stickyAssignmentsDocuments.get(fallBackKey) != null) {
                mergedAssignments.putAll(stickyAssignmentsDocuments.get(fallBackKey).getAssignments());
            }
        }

        if (stickyAssignmentsDocuments.get(hashKey) != null) {
            mergedAssignments.putAll(stickyAssignmentsDocuments.get(hashKey).getAssignments());
        }

        return mergedAssignments;
    }

    public static StickyBucketVariation getStickyBucketVariation(
            GBContext context,
            String experimentKey,
            @Nullable String experimentHashAttribute,
            @Nullable String experimentFallbackAttribute,
            JsonObject attributeOverrides,
            @Nullable Integer experimentBucketVersion,
            @Nullable Integer minExperimentBucketVersion,
            @Nullable List<VariationMeta> meta
    ) {
        if (experimentBucketVersion == null) {
            experimentBucketVersion = 0;
        }
        if (minExperimentBucketVersion == null) {
            minExperimentBucketVersion = 0;
        }
        if (meta == null) {
            meta = new ArrayList<>();
        }
        String id = getStickyBucketExperimentKey(experimentKey, experimentBucketVersion);
        Map<String, String> assignments = getStickyBucketAssignments(
                context,
                experimentHashAttribute,
                experimentFallbackAttribute,
                attributeOverrides);

        if (minExperimentBucketVersion > 0) {
            for (int i = 0; i <= minExperimentBucketVersion; i++) {
                String blockedKey = getStickyBucketExperimentKey(experimentKey, i);
                if (assignments.containsKey(blockedKey)) {
                    return new StickyBucketVariation(-1, true);
                }
            }
        }

        String variationKey = assignments.get(id);
        if (variationKey == null) {
            return new StickyBucketVariation(-1, null);
        }
        int variationIndex = findVariationIndex(meta, variationKey);

        if (variationIndex != -1) {
            return new StickyBucketVariation(variationIndex, null);
        } else {
            return new StickyBucketVariation(-1, null);
        }
    }

    private static int findVariationIndex(List<VariationMeta> meta, String variationKey) {
        for (int i = 0; i < meta.size(); i++) {
            if (meta.get(i).getKey() != null) {
                if (meta.get(i).getKey().equals(variationKey)) {
                    return i;
                }
            }
        }
        return -1;
    }

    public static String getStickyBucketExperimentKey(
            String experimentKey,
            @Nullable Integer experimentBucketVersion
    ) {
        if (experimentBucketVersion == null) {
            experimentBucketVersion = 0;
        }
        return experimentKey + "__" + experimentBucketVersion;
    }

    public static GeneratedStickyBucketAssignmentDocModel generateStickyBucketAssignmentDoc(
            GBContext context,
            String attributeName,
            String attributeValue,
            Map<String, String> assignments
    ) {
        String key = attributeName + "||" + attributeValue;
        Map<String, String> existingAssignments = new HashMap<>();
        if (context.getStickyBucketAssignmentDocs() != null) {
            if (context.getStickyBucketAssignmentDocs().get(key) != null) {
                existingAssignments = context.getStickyBucketAssignmentDocs().get(key).getAssignments();
            }
        }
        Map<String, String> newAssignments = new HashMap<>(existingAssignments);

        newAssignments.putAll(assignments);
        boolean changed = !newAssignments.equals(existingAssignments);
        StickyAssignmentsDocument stickyAssignmentsDocument = new StickyAssignmentsDocument(attributeName, attributeValue, newAssignments);
        return new GeneratedStickyBucketAssignmentDocModel(key, stickyAssignmentsDocument, changed);
    }

    public static HashAttributeAndHashValue getHashAttribute(
            GBContext context,
            @Nullable String attr,
            @Nullable String fallbackAttribute,
            JsonObject attributeOverrides
    ) {
        String hashAttribute = attr != null ? attr : "id";
        String hashValue = "";

        if (attributeOverrides.get(hashAttribute) != null && !attributeOverrides.get(hashAttribute).isJsonNull()) {
            hashValue = attributeOverrides.get(hashAttribute).getAsString();
        } else if (context.getAttributes().get(hashAttribute) != null && !context.getAttributes().get(hashAttribute).isJsonNull()) {
            hashValue = context.getAttributes().get(hashAttribute).getAsString();
        }

        if (hashValue.isEmpty() && fallbackAttribute != null) {
            if (attributeOverrides.get(fallbackAttribute) != null && !attributeOverrides.get(fallbackAttribute).isJsonNull()) {
                hashValue = attributeOverrides.get(fallbackAttribute).getAsString();
            } else if (context.getAttributes().get(fallbackAttribute) != null && !context.getAttributes().get(fallbackAttribute).isJsonNull()) {
                hashValue = context.getAttributes().get(fallbackAttribute).getAsString();
            }

            if (!hashValue.isEmpty()) {
                hashAttribute = fallbackAttribute;
            }
        }

        return new HashAttributeAndHashValue(hashAttribute, hashValue);
    }
}
