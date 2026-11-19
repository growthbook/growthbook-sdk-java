package growthbook.sdk.java.util;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import growthbook.sdk.java.callback.ExperimentRunCallback;
import growthbook.sdk.java.evaluators.internal.ForcedValues;
import growthbook.sdk.java.evaluators.internal.bucketing.Bucketing;
import growthbook.sdk.java.evaluators.internal.bucketing.StickyBucketPreload;
import growthbook.sdk.java.evaluators.internal.bucketing.StickyBuckets;
import growthbook.sdk.java.model.AssignedExperiment;
import growthbook.sdk.java.model.BucketRange;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.Filter;
import growthbook.sdk.java.model.GBContext;
import growthbook.sdk.java.model.GeneratedStickyBucketAssignmentDocModel;
import growthbook.sdk.java.model.HashAttributeAndHashValue;
import growthbook.sdk.java.model.Namespace;
import growthbook.sdk.java.model.StickyAssignmentsDocument;
import growthbook.sdk.java.model.StickyBucketVariation;
import growthbook.sdk.java.model.VariationMeta;
import growthbook.sdk.java.multiusermode.configurations.EvaluationContext;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
/**
 * <b>INTERNAL</b>: Implementation of for internal utility methods to support {@link growthbook.sdk.java.GrowthBook}
 *
 * <p>Hashing and bucketing delegate to {@link Bucketing}, URL overrides to {@link ForcedValues}
 * and sticky bucketing to {@link StickyBuckets}; this class keeps the public entry points.
 */
@Slf4j
public class GrowthBookUtils {
    /**
     * Hashes a string to a double between 0 and 1, or null if the hash version is unsupported.
     * Uses the simple Fowler–Noll–Vo algorithm, specifically fnv32a.
     *
     * @param stringValue Input string
     * @param hashVersion The hash version
     * @param seed        A seed value that can be used instead of the experiment key for hashing
     * @return hashed double value or null if the hash version is unsupported.
     */
    public static @Nullable Double hash(String stringValue, Integer hashVersion, String seed) {
        return Bucketing.hash(stringValue, hashVersion, seed);
    }

    /**
     * This checks if a userId is within an experiment namespace or not.
     *
     * @param userId    The user identifier
     * @param namespace Namespace to check the user identifier against
     * @return whether the user is in the namespace
     * @deprecated {@link Namespace} is deprecated in favour of {@link Filter}s; this stays while
     * experiments still carry namespaces
     */
    @Deprecated
    public static Boolean inNameSpace(String userId, Namespace namespace) {
        return Bucketing.inNamespace(userId, namespace.getId(), namespace.getRangeStart(), namespace.getRangeEnd());
    }

    /**
     * Given a hash and bucket ranges, assign one of the bucket ranges.
     * Returns -1 if none can be found
     *
     * @param n            hash
     * @param bucketRanges list of {@link BucketRange}
     * @return index of the {@link BucketRange} list to assign
     */
    public static Integer chooseVariation(@NotNull Double n, ArrayList<BucketRange> bucketRanges) {
        return Bucketing.chooseVariation(n, bucketRanges);
    }

    /**
     * Returns an array of doubles with numVariations items that are all equal and sum to 1.
     * For example, getEqualWeights(2) would return [0.5, 0.5]
     *
     * @param numberOfVariations The number of variations you would like
     * @return A list of variations
     */
    public static ArrayList<Double> getEqualWeights(Integer numberOfVariations) {
        return new ArrayList<>(Bucketing.getEqualWeights(numberOfVariations));
    }

    /**
     * This checks if an experiment variation is being forced via a URL query string.
     * See {@link ForcedValues#getQueryStringOverride(String, String, Integer)}.
     *
     * @param id                 the identifier
     * @param urlString          the desired page URL as a string
     * @param numberOfVariations the number of variations
     * @return integer or null
     */
    @Nullable
    public static Integer getQueryStringOverride(String id, String urlString, Integer numberOfVariations) {
        return ForcedValues.getQueryStringOverride(id, urlString, numberOfVariations);
    }

    /**
     * This checks if an experiment variation is being forced via a URL query string.
     * See {@link ForcedValues#getQueryStringOverride(String, URL, Integer)}.
     *
     * @param id                 the identifier
     * @param url                the desired page URL
     * @param numberOfVariations the number of variations
     * @return integer or null
     */
    @Nullable
    public static Integer getQueryStringOverride(String id, URL url, Integer numberOfVariations) {
        return ForcedValues.getQueryStringOverride(id, url, numberOfVariations);
    }

    /**
     * Evaluate a forced boolean value from a URL. If the provided key is not present in the URL, return null.
     *
     * @param featureKey feature ID/key (not prefixed with gb~)
     * @param url        Page URL to evaluate for forced feature values
     * @return value or null
     */
    @Nullable
    public static Boolean getForcedBooleanValueFromUrl(String featureKey, URL url) {
        return ForcedValues.getForcedBooleanValueFromUrl(featureKey, url);
    }

    /**
     * Evaluate a forced string value from a URL. If the provided key is not present in the URL, return null.
     *
     * @param featureKey feature ID/key (not prefixed with gb~)
     * @param url        Page URL to evaluate for forced feature values
     * @return value or null
     */
    @Nullable
    public static String getForcedStringValueFromUrl(String featureKey, URL url) {
        return ForcedValues.getForcedStringValueFromUrl(featureKey, url);
    }

    /**
     * Evaluate a forced float value from a URL. If the provided key is not present in the URL, return null.
     *
     * @param featureKey feature ID/key (not prefixed with gb~)
     * @param url        Page URL to evaluate for forced feature values
     * @return value or null
     */
    @Nullable
    public static Float getForcedFloatValueFromUrl(String featureKey, URL url) {
        return ForcedValues.getForcedFloatValueFromUrl(featureKey, url);
    }

    /**
     * Evaluate a forced double value from a URL. If the provided key is not present in the URL, return null.
     *
     * @param featureKey feature ID/key (not prefixed with gb~)
     * @param url        Page URL to evaluate for forced feature values
     * @return value or null
     */
    @Nullable
    public static Double getForcedDoubleValueFromUrl(String featureKey, URL url) {
        return ForcedValues.getForcedDoubleValueFromUrl(featureKey, url);
    }

    /**
     * Evaluate a forced integer value from a URL. If the provided key is not present in the URL, return null.
     *
     * @param featureKey feature ID/key (not prefixed with gb~)
     * @param url        Page URL to evaluate for forced feature values
     * @return value or null
     */
    @Nullable
    public static Integer getForcedIntegerValueFromUrl(String featureKey, URL url) {
        return ForcedValues.getForcedIntegerValueFromUrl(featureKey, url);
    }

    @Nullable
    public static <T> T getForcedSerializableValueFromUrl(String featureKey, URL url, Class<T> valueTypeClass, Gson gson) {
        return ForcedValues.getForcedSerializableValueFromUrl(featureKey, url, valueTypeClass, gson);
    }

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
            @NotNull Double coverage,
            @Nullable ArrayList<Double> weights
    ) {
        return new ArrayList<>(Bucketing.getBucketRanges(numberOfVariations, coverage, weights));
    }

    /**
     * Determines if a number n is within the provided range: lower bound inclusive, upper bound exclusive.
     *
     * @param n     Double value to check if it's in range
     * @param range {@link BucketRange}
     * @return whether to include this hash value is within range. Returns false if either arguments are null.
     */
    public static Boolean inRange(Double n, BucketRange range) {
        return Bucketing.inRange(n, range);
    }

    /**
     * Evaluates whether a user should be filtered out based on the given filters.
     * See {@link Bucketing#isFilteredOut(List, JsonObject)}.
     *
     * @param filters    The list of {@link Filter} objects that define filtering criteria.
     * @param attributes A {@link JsonObject} containing user attributes.
     * @return {@code true} if the user is filtered out, otherwise {@code false}.
     */
    public static Boolean isFilteredOut(List<Filter> filters, JsonObject attributes) {
        return Bucketing.isFilteredOut(filters, attributes);
    }

    /**
     * Determines if the user is part of a gradual feature rollout.
     * See {@link Bucketing#isIncludedInRollout}.
     *
     * @param attributes        JsonObject
     * @param seed              String
     * @param hashAttribute     String
     * @param fallbackAttribute String
     * @param range             BucketRange
     * @param coverage          Double
     * @param hashVersion       Integer
     * @return Boolean - check if user is included
     */
    public static Boolean isIncludedInRollout(
            JsonObject attributes,
            String seed,
            String hashAttribute,
            String fallbackAttribute,
            @Nullable BucketRange range,
            @Nullable Double coverage,
            @Nullable Integer hashVersion
    ) {
        return Bucketing.isIncludedInRollout(attributes, seed, hashAttribute, fallbackAttribute, range, coverage, hashVersion);
    }

    /**
     * Retrieves cached assignments and sets them in the context's Sticky Bucket Assignments documents.
     *
     * @param context            {@link GBContext} that contains sticky bucketing attributes and sticky bucket service
     * @param featuresDataModel  A string representing the features data model.
     * @param attributeOverrides {@link JsonObject} containing attribute overrides.
     */
    public static void refreshStickyBuckets(GBContext context,
                                            String featuresDataModel,
                                            JsonObject attributeOverrides) {
        StickyBucketPreload.refreshStickyBuckets(context, featuresDataModel, attributeOverrides);
    }

    /**
     * {@link #refreshStickyBuckets} that reports whether the stored assignments were read.
     *
     * @param context            {@link GBContext} that contains sticky bucketing attributes and sticky bucket service
     * @param featuresDataModel  A string representing the features data model.
     * @param attributeOverrides {@link JsonObject} containing attribute overrides.
     * @return {@code false} when the service failed to load assignments. The context then holds no
     * documents and evaluation buckets normally; the caller must not save those assignments, because
     * they could replace the variation the store already holds. {@code true} otherwise, including
     * when no service is configured.
     */
    public static boolean loadStickyBuckets(GBContext context,
                                            String featuresDataModel,
                                            JsonObject attributeOverrides) {
        return StickyBucketPreload.loadStickyBuckets(context, featuresDataModel, attributeOverrides);
    }

    /**
     * Supportive method to get attribute values from the context.
     *
     * @param context            {@link GBContext} The context containing user attributes.
     * @param featuresDataModel  A string representing the features data model.
     * @param attributeOverrides A {@link JsonObject} containing attribute overrides.
     * @return {@code Map<String, String>} A map of sticky bucket attributes.
     */
    public static Map<String, String> getStickyBucketAttributes(GBContext context,
                                                                String featuresDataModel,
                                                                JsonObject attributeOverrides) {
        return StickyBucketPreload.getStickyBucketAttributes(context, featuresDataModel, attributeOverrides);
    }

    /**
     * Method to get actual Sticky Bucket assignments.
     * Also, this method handles if assignments belong to the user.
     *
     * @param context              {@link EvaluationContext}
     * @param expHashAttribute     String (nullable)
     * @param expFallbackAttribute String (nullable)
     * @return {@code Map<String, String>} containing sticky bucket assignments.
     */
    public static Map<String, String> getStickyBucketAssignments(
            EvaluationContext context,
            @Nullable String expHashAttribute,
            @Nullable String expFallbackAttribute
    ) {
        return StickyBuckets.getStickyBucketAssignments(context, expHashAttribute, expFallbackAttribute);
    }

    /**
     * Method to get {@link StickyBucketVariation}: variation and versionIsBlocked.
     *
     * @param context                     {@link EvaluationContext}
     * @param experimentKey               String
     * @param experimentHashAttribute     String (nullable)
     * @param experimentFallbackAttribute String (nullable)
     * @param experimentBucketVersion     Integer (nullable)
     * @param minExperimentBucketVersion  Integer (nullable)
     * @param meta                        {@code List<}{@link VariationMeta}{@code >} (nullable)
     * @return {@link StickyBucketVariation}
     */
    public static StickyBucketVariation getStickyBucketVariation(
            EvaluationContext context,
            String experimentKey,
            @Nullable String experimentHashAttribute,
            @Nullable String experimentFallbackAttribute,
            @Nullable Integer experimentBucketVersion,
            @Nullable Integer minExperimentBucketVersion,
            @Nullable List<VariationMeta> meta
    ) {
        return StickyBuckets.getStickyBucketVariation(context, experimentKey, experimentHashAttribute,
                experimentFallbackAttribute, experimentBucketVersion, minExperimentBucketVersion, meta);
    }

    /**
     * Method to get Experiment key from cache
     *
     * @param experimentKey           String
     * @param experimentBucketVersion Integer
     * @return key in String format
     */
    public static String getStickyBucketExperimentKey(
            String experimentKey,
            @Nullable Integer experimentBucketVersion
    ) {
        return StickyBuckets.getStickyBucketExperimentKey(experimentKey, experimentBucketVersion);
    }

    /**
     * Method for generating a Sticky Bucket Assignment document.
     *
     * @param stickyBucketAssignmentDocs {@code Map<String, StickyAssignmentsDocument>}
     * @param attributeName              String
     * @param attributeValue             String
     * @param assignments                {@code Map<String, String>}
     * @return {@link GeneratedStickyBucketAssignmentDocModel}
     */
    public static GeneratedStickyBucketAssignmentDocModel generateStickyBucketAssignmentDoc(
            Map<String, StickyAssignmentsDocument> stickyBucketAssignmentDocs,
            String attributeName,
            String attributeValue,
            Map<String, String> assignments
    ) {
        return StickyBuckets.generateStickyBucketAssignmentDoc(stickyBucketAssignmentDocs, attributeName, attributeValue, assignments);
    }

    /**
     * Method for get hash value by identifier. User attribute used for hashing, defaulting to id if not set.
     *
     * @param attr              String
     * @param fallbackAttribute String
     * @param attributes        JsonObject
     * @return {@link HashAttributeAndHashValue}
     */
    public static HashAttributeAndHashValue getHashAttribute(
            @Nullable String attr,
            @Nullable String fallbackAttribute,
            JsonObject attributes
    ) {
        return Bucketing.getHashAttribute(attr, fallbackAttribute, attributes);
    }

    static String toHashValue(@Nullable JsonElement value) {
        return Bucketing.toHashValue(value);
    }

    public static <K, V> Map<K, V> mergeMaps(@Nullable List<Map<K, V>> maps) {
        if (maps == null || maps.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<K, V> merged = Collections.emptyMap();
        for (Map<K, V> map : maps) {
            merged = mergeMaps(merged, map);
        }
        return merged;
    }

    public static <K, V> Map<K, V> mergeMaps(@Nullable Map<K, V> base, @Nullable Map<K, V> overrides) {
        if (base == null || base.isEmpty()) return overrides != null ? new HashMap<>(overrides) : Collections.emptyMap();
        if (overrides == null || overrides.isEmpty()) return new HashMap<>(base);
        Map<K, V> merged = new HashMap<>(base);
        merged.putAll(overrides);
        return merged;
    }

    /** Dedup-map entry for an experiment with no key. */
    private static final String NULL_EXPERIMENT_KEY = "null-key";

    /**
     * Prefix applied to every real experiment key. A prefixed key always starts with {@code "key:"},
     * so it can never equal {@link #NULL_EXPERIMENT_KEY} whatever the experiment key is.
     */
    private static final String KEYED_EXPERIMENT_PREFIX = "key:";

    public static <T> void fireSubscriptions(Map<String, AssignedExperiment> assigned,
                                                     List<ExperimentRunCallback> callbacks,
                                                     Experiment<T> experiment,
                                                     ExperimentResult<T> result
    ) {
        // A key-less experiment needs a non-null stand-in: this dedup map may be a ConcurrentHashMap
        // (GrowthBookClient) which rejects null keys, even though GrowthBook's plain HashMap tolerates
        // them. Mapping it to "" would merge it with an experiment whose key really is "", suppressing
        // one of their callbacks; the prefix keeps the two namespaces disjoint for any key value.
        // This map is internal dedup state and is never exposed.
        String key = experiment.getKey() == null ? NULL_EXPERIMENT_KEY : KEYED_EXPERIMENT_PREFIX + experiment.getKey();

        AssignedExperiment current = new AssignedExperiment(
                experiment.getKey(),
                result.getInExperiment(),
                result.getVariationId()
        );

        // Atomically update the assignment and detect whether it actually changed.
        // Doing the compare-and-set inside compute() avoids the check-then-act race where two
        // threads with the same key both see a stale value and fire the callback twice.
        boolean[] changed = {false};
        assigned.compute(key, (k, prev) -> {
            if (prev == null
                    || !Objects.equals(prev.getInExperiment(), current.getInExperiment())
                    || !Objects.equals(prev.getVariationId(), current.getVariationId())) {
                changed[0] = true;
                return current;
            }
            return prev;
        });

        // Fire callbacks outside the map's locked region so a callback that re-enters the SDK
        // (e.g. calls run() again) cannot block or deadlock on the same map bin.
        if (changed[0]) {
            for (ExperimentRunCallback cb : callbacks) {
                try {
                    cb.onRun(experiment, result);
                } catch (Exception e) {
                    log.error(e.getMessage());
                }
            }
        }
    }
}
