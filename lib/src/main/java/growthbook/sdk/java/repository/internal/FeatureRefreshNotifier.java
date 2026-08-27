package growthbook.sdk.java.repository.internal;

import growthbook.sdk.java.listener.FeatureRefreshListener;
import growthbook.sdk.java.listener.internal.FeatureRefreshListenerDispatcher;
import growthbook.sdk.java.model.FeatureRefreshEvent;
import growthbook.sdk.java.model.FeatureRefreshSource;
import growthbook.sdk.java.repository.FeatureRefreshStrategy;

import javax.annotation.Nullable;
import java.util.concurrent.TimeUnit;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

/**
 * Internal dispatcher for repository-level feature refresh events.
 * Builds metadata-only refresh events and isolates listener failures from repository refresh flow.
 */
public final class FeatureRefreshNotifier {

    private final IntSupplier activeFeatureCountSupplier;
    private final Supplier<FeatureRefreshStrategy> refreshStrategySupplier;
    private final FeatureRefreshListenerDispatcher dispatcher = new FeatureRefreshListenerDispatcher();

    /**
     * Creates a notifier backed by repository-specific metadata suppliers.
     *
     * @param activeFeatureCountSupplier supplies the current active feature count
     * @param refreshStrategySupplier supplies the repository refresh strategy
     */
    public FeatureRefreshNotifier(
            IntSupplier activeFeatureCountSupplier,
            Supplier<FeatureRefreshStrategy> refreshStrategySupplier
    ) {
        this.activeFeatureCountSupplier = activeFeatureCountSupplier;
        this.refreshStrategySupplier = refreshStrategySupplier;
    }

    /**
     * Registers a listener if it is non-null and not already registered.
     *
     * @param listener listener to register
     */
    public void add(FeatureRefreshListener listener) {
        dispatcher.add(listener);
    }

    /**
     * Removes a registered listener.
     *
     * @param listener listener to remove
     */
    public void remove(FeatureRefreshListener listener) {
        dispatcher.remove(listener);
    }

    /**
     * Removes all registered listeners. Intended for repository shutdown.
     */
    public void clear() {
        dispatcher.clear();
    }

    /**
     * Publishes a successful refresh event.
     *
     * @param source refresh source
     * @param featuresChanged whether feature data changed
     * @param loadedFromCache whether the successful data came from cache
     * @param durationMillis refresh duration in milliseconds
     */
    public void notifySuccess(
            FeatureRefreshSource source,
            boolean featuresChanged,
            boolean loadedFromCache,
            long durationMillis
    ) {
        if (dispatcher.hasNoListeners()) {
            return;
        }
        notifyListeners(FeatureRefreshEvent.success(
                featuresChanged,
                loadedFromCache,
                activeFeatureCountSupplier.getAsInt(),
                source,
                getRefreshStrategy(),
                durationMillis
        ));
    }

    /**
     * Publishes a failed refresh event.
     *
     * @param error refresh failure
     * @param source refresh source
     * @param featuresChanged whether cache fallback changed feature data
     * @param loadedFromCache whether cache fallback data was loaded
     * @param durationMillis refresh duration in milliseconds
     */
    public void notifyFailure(
            Throwable error,
            FeatureRefreshSource source,
            boolean featuresChanged,
            boolean loadedFromCache,
            long durationMillis
    ) {
        if (dispatcher.hasNoListeners()) {
            return;
        }
        notifyListeners(FeatureRefreshEvent.failure(
                error,
                featuresChanged,
                loadedFromCache,
                activeFeatureCountSupplier.getAsInt(),
                source,
                getRefreshStrategy(),
                durationMillis
        ));
    }

    /**
     * Calculates elapsed milliseconds from a {@link System#nanoTime()} start value.
     *
     * @param startedAtNanos start time from {@link System#nanoTime()}
     * @return elapsed milliseconds
     */
    public static long elapsedMillis(long startedAtNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos);
    }

    @Nullable
    private FeatureRefreshStrategy getRefreshStrategy() {
        return refreshStrategySupplier == null ? null : refreshStrategySupplier.get();
    }

    private void notifyListeners(FeatureRefreshEvent event) {
        dispatcher.publish(event);
    }
}
