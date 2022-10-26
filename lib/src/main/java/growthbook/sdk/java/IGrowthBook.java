package growthbook.sdk.java;

import growthbook.sdk.java.models.Experiment;
import growthbook.sdk.java.models.ExperimentResult;
import growthbook.sdk.java.models.ExperimentRunCallback;
import growthbook.sdk.java.models.FeatureResult;

interface IGrowthBook {

    public <ValueType>ExperimentResult<ValueType> run(Experiment<ValueType> experiment);

    public void subscribe(ExperimentRunCallback callback);

    public void destroy();


    // region Features

    public <ValueType> FeatureResult<ValueType> evalFeature(String key);

    /**
     * Call this with the JSON string returned from API.
     * @param featuresJsonString features JSON from the GrowthBook API
     */
    public void setFeatures(String featuresJsonString);

    public Boolean isOn(String featureKey);
    public Boolean isOff(String featureKey);

    /**
     * Get the feature value as a boolean
     * @param featureKey name of the feature
     * @param defaultValue boolean value to return
     * @return the found value or defaultValue
     */
    public Boolean getFeatureValue(String featureKey, Boolean defaultValue);

    /**
     * Get the feature value as a string
     * @param featureKey name of the feature
     * @param defaultValue string value to return
     * @return the found value or defaultValue
     */
    public String getFeatureValue(String featureKey, String defaultValue);

    /**
     * Get the feature value as a float
     * @param featureKey name of the feature
     * @param defaultValue float value to return
     * @return the found value or defaultValue
     */
    public Float getFeatureValue(String featureKey, Float defaultValue);

    /**
     * Get the feature value as an integer
     * @param featureKey name of the feature
     * @param defaultValue integer value to return
     * @return the found value or defaultValue
     */
    public Integer getFeatureValue(String featureKey, Integer defaultValue);

    /**
     * Skip over the JSON parsing.
     * You may want to do this if you'd like to skip Gson parsing and parse it yourself.
     * @param featureKey name of the feature
     * @return string JSON of the value
     */
    public String getRawFeatureValue(String featureKey);

    // endregion Features

    // TODO: getAllResults (not required)
}
