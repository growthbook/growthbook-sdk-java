package growthbook.sdk.java;

import growthbook.sdk.java.models.Experiment;
import growthbook.sdk.java.models.ExperimentResult;
import growthbook.sdk.java.models.ExperimentRunCallback;
import growthbook.sdk.java.models.FeatureResult;

interface IGrowthBook {

    <ValueType>ExperimentResult<ValueType> run(Experiment<ValueType> experiment);

    void subscribe(ExperimentRunCallback callback);

    void destroy();


    // region Features

    <ValueType> FeatureResult<ValueType> evalFeature(String key);

    /**
     * Call this with the JSON string returned from API.
     * @param featuresJsonString features JSON from the GrowthBook API
     */
    void setFeatures(String featuresJsonString);

    Boolean isOn(String featureKey);
    Boolean isOff(String featureKey);

    /**
     * Get the feature value as a boolean
     * @param featureKey name of the feature
     * @param defaultValue boolean value to return
     * @return the found value or defaultValue
     */
    Boolean getFeatureValue(String featureKey, Boolean defaultValue);

    /**
     * Get the feature value as a string
     * @param featureKey name of the feature
     * @param defaultValue string value to return
     * @return the found value or defaultValue
     */
    String getFeatureValue(String featureKey, String defaultValue);

    /**
     * Get the feature value as a float
     * @param featureKey name of the feature
     * @param defaultValue float value to return
     * @return the found value or defaultValue
     */
    Float getFeatureValue(String featureKey, Float defaultValue);

    /**
     * Get the feature value as an integer
     * @param featureKey name of the feature
     * @param defaultValue integer value to return
     * @return the found value or defaultValue
     */
    Integer getFeatureValue(String featureKey, Integer defaultValue);

    /**
     * Get the feature value as a double
     * @param featureKey name of the feature
     * @param defaultValue integer value to return
     * @return the found value or defaultValue
     */
    Double getFeatureValue(String featureKey, Double defaultValue);

    /**
     * Get the feature value as an Object. This may be useful for implementations that do not use Gson.
     * @param featureKey feature identifier
     * @param defaultValue default object value
     * @return Object
     */
    Object getFeatureValue(String featureKey, Object defaultValue);

    /**
     * Get the feature value as a Gson-deserializable.
     * @param featureKey feature identifier
     * @param defaultValue default generic class
     * @param gsonDeserializableClass the class of the generic, e.g. MyFeature.class
     * @return ValueType instance
     * @param <ValueType> Gson deserializable type
     */
    <ValueType> ValueType getFeatureValue(String featureKey, ValueType defaultValue, Class<ValueType> gsonDeserializableClass);

    // endregion Features

    // TODO: getAllResults (not required)
}
