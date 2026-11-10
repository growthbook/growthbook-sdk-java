package growthbook.sdk.java.multiusermode;

import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.FeatureKey;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * A per-request, single-user view over a shared {@link GrowthBookClient}, obtained from
 * {@link GrowthBookClient#forUser(UserContext)}.
 *
 * <p>It removes the need to repeat the {@link UserContext} on every call and, crucially, it
 * <b>defers experiment exposure tracking</b>: evaluations performed through this view accumulate
 * exposures in a buffer instead of firing the tracking callback / event logger immediately. Call
 * {@link #fireDeferredTrackingCalls()} once at the end of the request to flush them. This is the
 * standard SSR / serverless / batching pattern — evaluate now, send tracking later.
 *
 * <p>Feature-usage callbacks, the {@code "Feature Evaluated"} event, and explicit
 * {@link #logEvent(String, Map)} calls still fire immediately; only the exposure stream is deferred.
 *
 * <p><b>Not thread-safe.</b> Create one instance per request/thread; do not share it. The underlying
 * {@link GrowthBookClient} remains shared and thread-safe.
 */
public class UserScopedGrowthBook {

    private final GrowthBookClient client;
    @Getter
    private final UserContext userContext;
    private final DeferredTrackingBuffer deferredTracking = new DeferredTrackingBuffer();

    UserScopedGrowthBook(GrowthBookClient client, UserContext userContext) {
        this.client = client;
        this.userContext = userContext;
    }

    /**
     * Evaluates a feature for the bound user, buffering any experiment exposure.
     *
     * @param key            feature key
     * @param valueTypeClass expected value class
     * @param <T>            feature value type
     * @return feature evaluation result
     */
    public <T> FeatureResult<T> evalFeature(String key, Class<T> valueTypeClass) {
        return client.evalFeature(key, valueTypeClass, userContext, deferredTracking);
    }

    /**
     * Evaluates a feature for the bound user using a typed key, buffering any experiment exposure.
     *
     * @param featureKey typed feature key
     * @param <T>        feature value type carried by the key
     * @return feature evaluation result
     */
    public <T> FeatureResult<T> getFeature(FeatureKey<T> featureKey) {
        return client.evalFeature(featureKey.getKey(), featureKey.getValueType(), userContext, deferredTracking);
    }

    /**
     * @param featureKey feature key
     * @return true when the feature is on for the bound user
     */
    public Boolean isOn(String featureKey) {
        return client.isOn(featureKey, userContext, deferredTracking);
    }

    /**
     * @param featureKey typed feature key
     * @return true when the feature is on for the bound user
     */
    public Boolean isOn(FeatureKey<?> featureKey) {
        return client.isOn(featureKey.getKey(), userContext, deferredTracking);
    }

    /**
     * @param featureKey feature key
     * @return true when the feature is off for the bound user
     */
    public Boolean isOff(String featureKey) {
        return client.isOff(featureKey, userContext, deferredTracking);
    }

    /**
     * @param featureKey typed feature key
     * @return true when the feature is off for the bound user
     */
    public Boolean isOff(FeatureKey<?> featureKey) {
        return client.isOff(featureKey.getKey(), userContext, deferredTracking);
    }

    /**
     * Evaluates a feature value for the bound user, falling back to {@code defaultValue}.
     *
     * @param featureKey              feature key
     * @param defaultValue            fallback value
     * @param gsonDeserializableClass expected value class
     * @param <T>                     feature value type
     * @return evaluated feature value or default value
     */
    public <T> T getFeatureValue(String featureKey, T defaultValue, Class<T> gsonDeserializableClass) {
        return client.getFeatureValue(featureKey, defaultValue, gsonDeserializableClass, userContext, deferredTracking);
    }

    /**
     * Evaluates a feature value for the bound user using a typed key.
     *
     * @param featureKey   typed feature key
     * @param defaultValue fallback value
     * @param <T>          feature value type carried by the key
     * @return evaluated feature value or default value
     */
    public <T> T getFeatureValue(FeatureKey<T> featureKey, T defaultValue) {
        return client.getFeatureValue(
                featureKey.getKey(), defaultValue, featureKey.getValueType(), userContext, deferredTracking);
    }

    /**
     * Runs an experiment for the bound user, buffering any exposure.
     *
     * <p>Only exposure tracking is deferred; experiment-run subscriptions on the client
     * (registered via {@code GrowthBookClient.subscribe(...)}) still fire immediately.
     *
     * @param experiment experiment to evaluate
     * @param <T>        experiment value type
     * @return experiment evaluation result
     */
    public <T> ExperimentResult<T> run(Experiment<T> experiment) {
        return client.run(experiment, userContext, deferredTracking);
    }

    /**
     * Emits an application-defined event for the bound user. Fires immediately (never deferred).
     *
     * @param eventName  the event name
     * @param properties event properties; {@code null} is treated as empty
     */
    public void logEvent(String eventName, Map<String, Object> properties) {
        client.logEvent(eventName, properties, userContext);
    }

    /**
     * @return a snapshot of the exposures buffered so far, in evaluation order
     */
    public List<DeferredTrackingCall<?>> getDeferredTrackingCalls() {
        return deferredTracking.getCalls();
    }

    /**
     * Replaces the buffered exposures, e.g. exposures serialized from another process.
     *
     * @param calls the calls to buffer
     */
    public void setDeferredTrackingCalls(List<DeferredTrackingCall<?>> calls) {
        deferredTracking.setCalls(calls);
    }

    /**
     * Flushes all buffered exposures through the tracking callback and event logger, then clears the
     * buffer. Call once at the end of the request.
     */
    public void fireDeferredTrackingCalls() {
        client.flushDeferredTracking(deferredTracking);
    }
}
