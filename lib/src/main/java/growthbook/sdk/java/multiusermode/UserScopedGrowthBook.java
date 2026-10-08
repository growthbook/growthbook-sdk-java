package growthbook.sdk.java.multiusermode;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.FeatureKey;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.multiusermode.usage.TrackingCallbackWithUser;

import java.util.Map;

/**
 * A lightweight, per-user (typically per-request) handle over a shared {@link GrowthBookClient}.
 *
 * <p>Created through {@link GrowthBookClient#createScopedInstance(UserContext)}, it binds a single
 * {@link UserContext} so evaluation methods ({@link #isOn(String)}, {@link #getFeatureValue},
 * {@link #run(Experiment)}, and their typed {@link FeatureKey} variants) take no context argument.
 * Every evaluation delegates to the owning client, so feature refresh, experiment subscriptions,
 * and remote-eval behavior are shared, not duplicated.
 *
 * <p>This handle is also the home for per-user state: forced feature values and variations,
 * attributes, URL, and a per-user {@link TrackingCallbackWithUser} that overrides the client-level
 * callback for experiments evaluated through this instance. Mutators rebuild the bound context and
 * return {@code this} for chaining.
 *
 * <p><strong>Thread-safety:</strong> unlike {@link GrowthBookClient}, a scoped instance is intended
 * for use within a single request/thread. The bound context is published through a {@code volatile}
 * reference, but interleaving mutations across threads is not supported.
 */
public class UserScopedGrowthBook {

    private final GrowthBookClient client;
    private volatile UserContext userContext;

    UserScopedGrowthBook(GrowthBookClient client, UserContext userContext) {
        this.client = client;
        this.userContext = userContext == null ? UserContext.builder().build() : userContext;
    }

    /**
     * Evaluates a feature for the bound user.
     *
     * @param key            feature key
     * @param valueTypeClass expected value class
     * @param <T>            feature value type
     * @return feature evaluation result
     */
    public <T> FeatureResult<T> evalFeature(String key, Class<T> valueTypeClass) {
        return client.evalFeature(key, valueTypeClass, userContext);
    }

    /**
     * Checks whether a feature evaluates to on for the bound user.
     *
     * @param featureKey feature key
     * @return true when the feature is on
     */
    public Boolean isOn(String featureKey) {
        return client.isOn(featureKey, userContext);
    }

    /**
     * Checks whether a feature evaluates to off for the bound user.
     *
     * @param featureKey feature key
     * @return true when the feature is off
     */
    public Boolean isOff(String featureKey) {
        return client.isOff(featureKey, userContext);
    }

    /**
     * Evaluates a feature and returns its value, falling back to the supplied default on missing or invalid values.
     *
     * @param featureKey              feature key
     * @param defaultValue            fallback value
     * @param gsonDeserializableClass expected value class
     * @param <T>                     feature value type
     * @return evaluated feature value or default value
     */
    public <T> T getFeatureValue(String featureKey, T defaultValue, Class<T> gsonDeserializableClass) {
        return client.getFeatureValue(featureKey, defaultValue, gsonDeserializableClass, userContext);
    }

    /**
     * Evaluates a feature for the bound user using a type-safe {@link FeatureKey}.
     *
     * @param featureKey typed feature key
     * @param <T>        feature value type carried by the key
     * @return the feature result
     */
    public <T> FeatureResult<T> getFeature(FeatureKey<T> featureKey) {
        return client.getFeature(featureKey, userContext);
    }

    /**
     * Checks whether the feature identified by the typed key evaluates to on for the bound user.
     *
     * @param featureKey typed feature key
     * @return true when the feature is on
     */
    public Boolean isOn(FeatureKey<?> featureKey) {
        return client.isOn(featureKey, userContext);
    }

    /**
     * Checks whether the feature identified by the typed key evaluates to off for the bound user.
     *
     * @param featureKey typed feature key
     * @return true when the feature is off
     */
    public Boolean isOff(FeatureKey<?> featureKey) {
        return client.isOff(featureKey, userContext);
    }

    /**
     * Gets a feature value using a typed key, inferring the deserialization class from the key.
     *
     * @param featureKey   typed feature key
     * @param defaultValue value to return when the feature is missing or invalid
     * @param <T>          feature value type carried by the key
     * @return the found value or defaultValue
     */
    public <T> T getFeatureValue(FeatureKey<T> featureKey, T defaultValue) {
        return client.getFeatureValue(featureKey, defaultValue, userContext);
    }

    /**
     * Gets a boolean feature value, defaulting to {@code false} when missing or falsy.
     *
     * @param featureKey typed boolean feature key
     * @return the found value or {@code false}
     */
    public Boolean getBooleanFeature(FeatureKey<Boolean> featureKey) {
        return client.getBooleanFeature(featureKey, userContext);
    }

    /**
     * Gets a boolean feature value.
     *
     * @param featureKey   typed boolean feature key
     * @param defaultValue value to return when the feature is missing or invalid
     * @return the found value or defaultValue
     */
    public Boolean getBooleanFeature(FeatureKey<Boolean> featureKey, Boolean defaultValue) {
        return client.getBooleanFeature(featureKey, defaultValue, userContext);
    }

    /**
     * Gets a string feature value.
     *
     * @param featureKey   typed string feature key
     * @param defaultValue value to return when the feature is missing or invalid
     * @return the found value or defaultValue
     */
    public String getStringFeature(FeatureKey<String> featureKey, String defaultValue) {
        return client.getStringFeature(featureKey, defaultValue, userContext);
    }

    /**
     * Gets an integer feature value.
     *
     * @param featureKey   typed integer feature key
     * @param defaultValue value to return when the feature is missing or invalid
     * @return the found value or defaultValue
     */
    public Integer getIntegerFeature(FeatureKey<Integer> featureKey, Integer defaultValue) {
        return client.getIntegerFeature(featureKey, defaultValue, userContext);
    }

    /**
     * Gets a double feature value.
     *
     * @param featureKey   typed double feature key
     * @param defaultValue value to return when the feature is missing or invalid
     * @return the found value or defaultValue
     */
    public Double getDoubleFeature(FeatureKey<Double> featureKey, Double defaultValue) {
        return client.getDoubleFeature(featureKey, defaultValue, userContext);
    }

    /**
     * Gets a float feature value.
     *
     * @param featureKey   typed float feature key
     * @param defaultValue value to return when the feature is missing or invalid
     * @return the found value or defaultValue
     */
    public Float getFloatFeature(FeatureKey<Float> featureKey, Float defaultValue) {
        return client.getFloatFeature(featureKey, defaultValue, userContext);
    }

    /**
     * Runs an inline experiment for the bound user, notifying the client's experiment subscribers
     * when assignment changes.
     *
     * @param experiment experiment to evaluate
     * @param <T>        experiment value type
     * @return experiment evaluation result
     */
    public <T> ExperimentResult<T> run(Experiment<T> experiment) {
        return client.run(experiment, userContext);
    }

    /**
     * Replaces the forced feature values applied to this user's evaluations.
     *
     * @param forcedFeatureValues feature key to forced value map
     * @return this instance
     */
    public UserScopedGrowthBook setForcedFeatures(Map<String, Object> forcedFeatureValues) {
        this.userContext = userContext.toBuilder().forcedFeatureValues(forcedFeatureValues).build();
        return this;
    }

    /**
     * Replaces the forced variations applied to this user's experiment evaluations.
     *
     * @param forcedVariations experiment key to forced variation index map
     * @return this instance
     */
    public UserScopedGrowthBook setForcedVariations(Map<String, Integer> forcedVariations) {
        this.userContext = userContext.toBuilder().forcedVariationsMap(forcedVariations).build();
        return this;
    }

    /**
     * Merges the supplied attributes into this user's attributes, replacing existing keys.
     *
     * @param attributes attributes to merge in; null is a no-op
     * @return this instance
     */
    public UserScopedGrowthBook updateAttributes(JsonObject attributes) {
        if (attributes == null) {
            return this;
        }
        JsonObject merged = deepCopy(userContext.getAttributes());
        for (Map.Entry<String, JsonElement> entry : attributes.entrySet()) {
            merged.add(entry.getKey(), entry.getValue());
        }
        this.userContext = userContext.toBuilder().attributes(merged).build();
        return this;
    }

    /**
     * Replaces this user's attributes.
     *
     * @param attributes new attributes; null clears them
     * @return this instance
     */
    public UserScopedGrowthBook setAttributes(JsonObject attributes) {
        this.userContext = userContext.toBuilder()
                .attributes(attributes == null ? new JsonObject() : attributes)
                .build();
        return this;
    }

    /**
     * Sets the URL used for URL-based targeting for this user.
     *
     * @param url request URL
     * @return this instance
     */
    public UserScopedGrowthBook setURL(String url) {
        this.userContext = userContext.toBuilder().url(url).build();
        return this;
    }

    /**
     * Sets a per-user tracking callback that takes precedence over the client-level callback for
     * experiments evaluated through this instance.
     *
     * @param callback tracking callback; null falls back to the client-level callback
     * @return this instance
     */
    public UserScopedGrowthBook setTrackingCallback(TrackingCallbackWithUser callback) {
        this.userContext = userContext.toBuilder().trackingCallback(callback).build();
        return this;
    }

    /**
     * Returns the current bound user context.
     *
     * @return the user context backing this instance
     */
    public UserContext getUserContext() {
        return userContext;
    }

    private static JsonObject deepCopy(JsonObject source) {
        JsonObject copy = new JsonObject();
        if (source != null) {
            for (Map.Entry<String, JsonElement> entry : source.entrySet()) {
                copy.add(entry.getKey(), entry.getValue());
            }
        }
        return copy;
    }
}
