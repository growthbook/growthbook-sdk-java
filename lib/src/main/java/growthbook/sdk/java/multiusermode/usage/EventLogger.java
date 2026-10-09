package growthbook.sdk.java.multiusermode.usage;

import growthbook.sdk.java.multiusermode.configurations.UserContext;

import java.util.Map;

/**
 * Structured event sink invoked during evaluation and via explicit
 * {@code logEvent(...)} calls.
 *
 * <p>The SDK emits two built-in events with the names defined in {@link GrowthBookEvent}:
 * {@link GrowthBookEvent#EXPERIMENT_VIEWED} when a user is bucketed into an experiment, and
 * {@link GrowthBookEvent#FEATURE_EVALUATED} on every feature evaluation. Application code may emit
 * arbitrary custom events through the {@code logEvent(name, properties)} methods on
 * {@code GrowthBookClient} / {@code UserScopedGrowthBook} / {@code GrowthBook}.
 *
 * <p>This sink fires <em>in addition to</em>, not instead of, the transport-level
 * {@link TrackingCallbackWithUser} and {@link FeatureUsageCallbackWithUser} callbacks.
 *
 * <p>Threading &amp; contract: by default, the SDK invokes this on the calling (evaluation) thread.
 * When {@code eventLoggerExecutor} is configured, the SDK submits the call to that executor instead.
 * Implementations must support concurrent calls when used by a shared client or a concurrent
 * executor, and must not rely on request-local thread state when an executor is configured.
 * Runtime exceptions are logged and swallowed so they do not break evaluation.
 * Implementations should be fast and non-blocking. {@code properties} values are the raw evaluated
 * values (boxed primitives, {@code Map} or {@code List} for object/array features) and may be
 * {@code null}.
 */
@FunctionalInterface
public interface EventLogger {

    /**
     * Receives a structured event.
     *
     * @param eventName   the event name; one of the {@link GrowthBookEvent} constants for built-in
     *                    events, or an application-defined name for custom events
     * @param properties  event properties; never {@code null} (empty map when there are none)
     * @param userContext the user the event was produced for
     */
    void logEvent(String eventName, Map<String, Object> properties, UserContext userContext);
}
