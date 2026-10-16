package growthbook.sdk.java.featurefetch;

import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.repository.RefreshMode;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Executes feature-refresh operations, either synchronously on the caller's thread
 * ({@link #runNow}) or on a shared background daemon executor ({@link #runInBackground}).
 *
 * <p>Execution mode (now vs background) and cache semantics ({@link RefreshMode}) are independent:
 * the caller chooses the method for the former and passes the latter through to its action. The
 * scheduler never derives one from the other — deciding, for example, that a forced refresh should
 * run in the background is the caller's policy, not this class's.
 */
@Slf4j
public final class FeatureRefreshScheduler {
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private ExecutorService featureRefreshExecutor;

    /**
     * @deprecated Execution mode and {@link RefreshMode} are separate concerns. Choose explicitly with
     * {@link #runNow(RefreshMode, FeatureRefreshAction)} or
     * {@link #runInBackground(RefreshMode, FeatureRefreshAction)} instead.
     */
    @Deprecated
    public void requestRefresh(RefreshMode refreshMode, FeatureRefreshAction refreshAction) {
        RefreshMode resolvedRefreshMode = refreshMode == null ? RefreshMode.DEFAULT : refreshMode;
        if (resolvedRefreshMode == RefreshMode.FORCE) {
            runInBackground(resolvedRefreshMode, refreshAction);
            return;
        }

        runNow(resolvedRefreshMode, refreshAction);
    }

    /**
     * Runs {@code action} synchronously on the calling thread with the given cache semantics. A
     * {@link FeatureFetchException} is logged and swallowed so a refresh failure cannot propagate out
     * of a fire-and-forget request.
     */
    public void runNow(RefreshMode refreshMode, FeatureRefreshAction action) {
        RefreshMode mode = refreshMode == null ? RefreshMode.DEFAULT : refreshMode;
        FeatureRefreshAction resolvedAction = Objects.requireNonNull(action, "action");
        try {
            resolvedAction.refresh(mode);
        } catch (FeatureFetchException e) {
            log.error("Refreshing wasn't successful. Message is: {}", e.getMessage(), e);
        }
    }

    /**
     * Runs {@code action} on the background refresh executor with the given cache semantics. Skips
     * quietly while shutting down; failures are logged, never propagated.
     */
    public void runInBackground(RefreshMode refreshMode, FeatureRefreshAction action) {
        RefreshMode mode = refreshMode == null ? RefreshMode.DEFAULT : refreshMode;
        FeatureRefreshAction resolvedAction = Objects.requireNonNull(action, "action");
        if (this.shuttingDown.get()) {
            log.warn("Skipping background feature refresh because the repository is shutting down.");
            return;
        }

        try {
            getFeatureRefreshExecutor().execute(() -> {
                if (this.shuttingDown.get()) {
                    return;
                }

                try {
                    resolvedAction.refresh(mode);
                } catch (FeatureFetchException e) {
                    log.error("Background feature refresh failed. Message is: {}", e.getMessage(), e);
                } catch (RuntimeException e) {
                    log.error("Background feature refresh failed.", e);
                }
            });
        } catch (RejectedExecutionException e) {
            log.warn("Skipping background feature refresh because the refresh executor is shutting down.", e);
        }
    }

    /**
     * Convenience for background execution with {@link RefreshMode#DEFAULT} (ETag-aware) semantics.
     */
    public void runInBackground(FeatureRefreshAction action) {
        runInBackground(RefreshMode.DEFAULT, action);
    }

    public synchronized void shutdown() {
        this.shuttingDown.set(true);
        if (this.featureRefreshExecutor != null) {
            this.featureRefreshExecutor.shutdownNow();
            this.featureRefreshExecutor = null;
            log.info("Feature refresh executor shut down");
        }
    }

    private synchronized ExecutorService getFeatureRefreshExecutor() {
        if (this.shuttingDown.get()) {
            throw new RejectedExecutionException("Feature refresh scheduler is shutting down.");
        }
        if (this.featureRefreshExecutor == null || this.featureRefreshExecutor.isShutdown()) {
            this.featureRefreshExecutor = Executors.newSingleThreadExecutor(new FeatureRefreshThreadFactory());
        }
        return this.featureRefreshExecutor;
    }

    @FunctionalInterface
    public interface FeatureRefreshAction {
        void refresh(RefreshMode refreshMode) throws FeatureFetchException;
    }

    private static final class FeatureRefreshThreadFactory implements ThreadFactory {
        @Override
        public Thread newThread(@NotNull Runnable runnable) {
            Thread thread = new Thread(runnable, "growthbook-feature-refresh");
            thread.setDaemon(true);
            return thread;
        }
    }
}
