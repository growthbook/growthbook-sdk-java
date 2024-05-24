package growthbook.sdk.java;

import growthbook.sdk.java.stickyBucketing.StickyBucketService;
import javax.annotation.Nullable;

interface IGrowthBook {

    <ValueType> ExperimentResult<ValueType> run(Experiment<ValueType> experiment);

    void subscribe(ExperimentRunCallback callback);

    void destroy();


    // region Features

    <ValueType> FeatureResult<ValueType> evalFeature(String key, Class<ValueType> valueTypeClass);

    /**
     * Call this with the JSON string returned from API.
     *
     * @param featuresJsonString features JSON from the GrowthBook API
     */
    void setFeatures(String featuresJsonString);

    /**
     * Update the user's attributes
     *
     * @param attributesJsonString user attributes JSON
     */
    void setAttributes(String attributesJsonString);

    void setOwnStickyBucketService(@Nullable StickyBucketService stickyBucketService);

    void setInMemoryStickyBucketService();

    Boolean isOn(String featureKey);

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

    // endregion Features

    // region Conditions

    Boolean evaluateCondition(String attributesJsonString, String conditionJsonString);

    void featuresAPIModelSuccessfully(String featuresDataModel);

    // endregion Conditions

    // TODO: getAllResults (not required)
}
