package growthbook.sdk.java.evaluators.internal;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import growthbook.sdk.java.util.UrlUtils;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Map;

/**
 * Values forced through the page URL: experiment variations ({@code ?my-test=1}) and feature
 * values ({@code ?gb~my-feature=true}). Pure functions, safe for concurrent use; a value that
 * cannot be parsed is logged and treated as absent.
 */
@Slf4j
public final class ForcedValues {

    private static final String FEATURE_KEY_PREFIX = "gb~";

    private ForcedValues() {
    }

    /**
     * This checks if an experiment variation is being forced via a URL query string.
     * This may not be applicable for all SDKs (e.g. mobile).
     * <p>
     * As an example, if the id is my-test and url is <a href="http://localhost/?my-test=1">...</a>,
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
     * Same as {@link #getQueryStringOverride(String, String, Integer)} for an already parsed URL.
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

    /**
     * Evaluate a forced feature value from a URL, parsed as {@code valueTypeClass}: a boolean,
     * string, integer, float or double, or otherwise JSON deserialized into that type.
     *
     * @param featureKey     feature ID/key (not prefixed with gb~)
     * @param url            Page URL to evaluate for forced feature values
     * @param valueTypeClass the type to parse the value as
     * @return value, or null when the key is absent or the value can't be parsed as that type
     */
    @Nullable
    public static Object getForcedValueFromUrl(String featureKey, URL url, Class<?> valueTypeClass) {
        if (valueTypeClass.equals(Boolean.class)) {
            return getForcedBooleanValueFromUrl(featureKey, url);
        }
        if (valueTypeClass.equals(String.class)) {
            return getForcedStringValueFromUrl(featureKey, url);
        }
        if (valueTypeClass.equals(Integer.class)) {
            return getForcedIntegerValueFromUrl(featureKey, url);
        }
        if (valueTypeClass.equals(Float.class)) {
            return getForcedFloatValueFromUrl(featureKey, url);
        }
        if (valueTypeClass.equals(Double.class)) {
            return getForcedDoubleValueFromUrl(featureKey, url);
        }
        return getForcedSerializableValueFromUrl(featureKey, url, valueTypeClass, GrowthBookJsonUtils.getInstance().gson);
    }

    /**
     * Evaluate a forced boolean value from a URL: {@code true}/{@code 1}/{@code on} and
     * {@code false}/{@code 0}/{@code off}, case-insensitive.
     *
     * @param featureKey feature ID/key (not prefixed with gb~)
     * @param url        Page URL to evaluate for forced feature values
     * @return value, or null when the key is absent or the value is not a boolean
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

    /**
     * Evaluate a forced string value from a URL.
     *
     * @param featureKey feature ID/key (not prefixed with gb~)
     * @param url        Page URL to evaluate for forced feature values
     * @return value, or null when the key is absent
     */
    @Nullable
    public static String getForcedStringValueFromUrl(String featureKey, URL url) {
        return getForcedFeatureRawValueForKeyFromUrl(featureKey, url);
    }

    /**
     * Evaluate a forced float value from a URL.
     *
     * @param featureKey feature ID/key (not prefixed with gb~)
     * @param url        Page URL to evaluate for forced feature values
     * @return value, or null when the key is absent or the value is not a number
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

    /**
     * Evaluate a forced double value from a URL.
     *
     * @param featureKey feature ID/key (not prefixed with gb~)
     * @param url        Page URL to evaluate for forced feature values
     * @return value, or null when the key is absent or the value is not a number
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

    /**
     * Evaluate a forced integer value from a URL.
     *
     * @param featureKey feature ID/key (not prefixed with gb~)
     * @param url        Page URL to evaluate for forced feature values
     * @return value, or null when the key is absent or the value is not an integer
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

    /**
     * Evaluate a forced JSON value from a URL, deserialized into {@code valueTypeClass}.
     *
     * @param featureKey     feature ID/key (not prefixed with gb~)
     * @param url            Page URL to evaluate for forced feature values
     * @param valueTypeClass the type to deserialize into
     * @param gson           the Gson instance to deserialize with
     * @param <T>    the value type
     * @return value, or null when the key is absent or the value cannot be deserialized
     */
    @Nullable
    public static <T> T getForcedSerializableValueFromUrl(String featureKey, URL url, Class<T> valueTypeClass, Gson gson) {
        String value = getForcedFeatureRawValueForKeyFromUrl(featureKey, url);

        if (value == null) return null;

        try {
            return gson.fromJson(value, valueTypeClass);
        } catch (JsonParseException e) {
            log.error(e.getMessage(), e);
            return null;
        }
    }

    private static @Nullable String getForcedFeatureRawValueForKeyFromUrl(String featureKey, URL url) {
        Map<String, String> queryMap = UrlUtils.parseQueryString(url.getQuery());
        return queryMap.get(FEATURE_KEY_PREFIX + featureKey);
    }
}
