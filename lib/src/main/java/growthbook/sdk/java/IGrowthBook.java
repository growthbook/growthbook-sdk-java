package growthbook.sdk.java;

import growthbook.sdk.java.callback.ExperimentRunCallback;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.FeatureKey;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.stickyBucketing.StickyBucketService;
import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

interface IGrowthBook {

    <ValueType> ExperimentResult<ValueType> run(Experiment<ValueType> experiment);

    void subscribe(ExperimentRunCallback callback);

    void destroy();


    // region Features

    <ValueType> FeatureResult<ValueType> evalFeature(String key, Class<ValueType> valueTypeClass);

    /**
     * Evaluates a batch of features for the current user, reusing a single evaluation context.
     *
     * @param featureKeys    the feature keys to evaluate
     * @param valueTypeClass the expected value type (typically {@code Object.class} for mixed types)
     * @param <ValueType>    the result value type
     * @return a map from feature key to its {@link FeatureResult}
     */
    <ValueType> Map<String, FeatureResult<ValueType>> evalFeatures(List<String> featureKeys, Class<ValueType> valueTypeClass);

    /**
     * Update the user's attributes
     *
     * @param attributesJsonString user attributes JSON
     */
    void setAttributes(String attributesJsonString);

    /**
     * Setting your own implementation of StickyBucketService interface
     * @param stickyBucketService StickyBucketService
     */
    void setOwnStickyBucketService(@Nullable StickyBucketService stickyBucketService);

    /**
     * Setting default in memory implementation of StickyBucketService interface
     */
    void setInMemoryStickyBucketService();

    /**
     * Returns true if the value is a truthy value
     * @param featureKey String
     * @return true if the value is a truthy value
     */
    Boolean isOn(String featureKey);

    /**
     * Returns true if the value is a falsy value. Only the following values
     * are considered to be "falsy": null, false, "", 0.
     * @param featureKey String
     * @return Returns true if the value is a falsy value
     */
    Boolean isOff(String featureKey);

    /**
     * Get the feature value as a boolean
     *
     * @param featureKey   name of the feature
     * @param defaultValue boolean value to return
     * @return the found value or defaultValue
     */
    Boolean getFeatureValue(String featureKey, Boolean defaultValue);

    /**
     * Get the feature value as a string
     *
     * @param featureKey   name of the feature
     * @param defaultValue string value to return
     * @return the found value or defaultValue
     */
    String getFeatureValue(String featureKey, String defaultValue);

    /**
     * Get the feature value as a float
     *
     * @param featureKey   name of the feature
     * @param defaultValue float value to return
     * @return the found value or defaultValue
     */
    Float getFeatureValue(String featureKey, Float defaultValue);

    /**
     * Get the feature value as an integer
     *
     * @param featureKey   name of the feature
     * @param defaultValue integer value to return
     * @return the found value or defaultValue
     */
    Integer getFeatureValue(String featureKey, Integer defaultValue);

    /**
     * Get the feature value as a double
     *
     * @param featureKey   name of the feature
     * @param defaultValue integer value to return
     * @return the found value or defaultValue
     */
    Double getFeatureValue(String featureKey, Double defaultValue);

    /**
     * Get the feature value as an Object. This may be useful for implementations that do not use Gson.
     *
     * @param featureKey   feature identifier
     * @param defaultValue default object value
     * @return Object
     */
    Object getFeatureValue(String featureKey, Object defaultValue);

    /**
     * Get the feature value as a Gson-deserializable.
     * If your class requires a custom deserializer, use {@link #getFeatureValue(String, Object)} instead and deserialize it with your own Gson instance.
     *
     * @param featureKey              feature identifier
     * @param defaultValue            default generic class
     * @param gsonDeserializableClass the class of the generic, e.g. MyFeature.class
     * @param <ValueType>             Gson deserializable type
     * @return ValueType instance
     */
    <ValueType> ValueType getFeatureValue(String featureKey, ValueType defaultValue, Class<ValueType> gsonDeserializableClass);

    /**
     * Evaluate a feature using a type-safe {@link FeatureKey} instead of a raw string key.
     * The key carries both the feature identifier and its value type.
     *
     * <p>The returned result's {@link FeatureResult#getValue()} is the raw evaluated value; it is
     * <em>not</em> deserialized into the key's value type. To obtain a deserialized instance of a
     * complex type, use {@link #getFeatureValue(FeatureKey, Object)}.
     *
     * @param featureKey  typed feature key, e.g. {@code Features.NEW_HOME}
     * @param <ValueType> feature value type carried by the key
     * @return the feature result
     */
    <ValueType> FeatureResult<ValueType> getFeature(FeatureKey<ValueType> featureKey);

    /**
     * Returns true if the feature identified by the typed key evaluates to a truthy value.
     *
     * @param featureKey typed feature key
     * @return true if the value is truthy
     */
    Boolean isOn(FeatureKey<?> featureKey);

    /**
     * Returns true if the feature identified by the typed key evaluates to a falsy value.
     *
     * @param featureKey typed feature key
     * @return true if the value is falsy
     */
    Boolean isOff(FeatureKey<?> featureKey);

    /**
     * Get the feature value using a typed key, inferring the deserialization class from the key.
     *
     * @param featureKey   typed feature key
     * @param defaultValue value to return when the feature is missing or invalid
     * @param <ValueType>  feature value type carried by the key
     * @return the found value or defaultValue
     */
    <ValueType> ValueType getFeatureValue(FeatureKey<ValueType> featureKey, ValueType defaultValue);

    /**
     * Get a boolean feature value, defaulting to {@code false} when missing or falsy.
     *
     * @param featureKey typed boolean feature key
     * @return the found value or {@code false}
     */
    Boolean getBooleanFeature(FeatureKey<Boolean> featureKey);

    /**
     * Get a boolean feature value.
     *
     * @param featureKey   typed boolean feature key
     * @param defaultValue value to return when the feature is missing or invalid
     * @return the found value or defaultValue
     */
    Boolean getBooleanFeature(FeatureKey<Boolean> featureKey, Boolean defaultValue);

    /**
     * Get a string feature value.
     *
     * @param featureKey   typed string feature key
     * @param defaultValue value to return when the feature is missing or invalid
     * @return the found value or defaultValue
     */
    String getStringFeature(FeatureKey<String> featureKey, String defaultValue);

    /**
     * Get an integer feature value.
     *
     * @param featureKey   typed integer feature key
     * @param defaultValue value to return when the feature is missing or invalid
     * @return the found value or defaultValue
     */
    Integer getIntegerFeature(FeatureKey<Integer> featureKey, Integer defaultValue);

    /**
     * Get a double feature value.
     *
     * @param featureKey   typed double feature key
     * @param defaultValue value to return when the feature is missing or invalid
     * @return the found value or defaultValue
     */
    Double getDoubleFeature(FeatureKey<Double> featureKey, Double defaultValue);

    /**
     * Get a float feature value.
     *
     * @param featureKey   typed float feature key
     * @param defaultValue value to return when the feature is missing or invalid
     * @return the found value or defaultValue
     */
    Float getFloatFeature(FeatureKey<Float> featureKey, Float defaultValue);

    // endregion Features

    // region Conditions

    Boolean evaluateCondition(String attributesJsonString, String conditionJsonString);

    void featuresAPIModelSuccessfully(String featuresDataModel);

    // if feature enabled by environment it would be present in context
    Boolean isFeatureEnabled(String featureKey);

    // endregion Conditions

    // TODO: getAllResults (not required)
}
