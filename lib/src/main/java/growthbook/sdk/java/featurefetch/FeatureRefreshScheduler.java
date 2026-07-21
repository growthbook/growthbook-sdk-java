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
 * Owns refresh execution mode selection and background refresh scheduling.
 *
 * <p>Repositories provide the actual refresh operation. This class decides
 * whether the operation should run synchronously or in the background refresh
 * executor based on {@link RefreshMode}.
 */
@Slf4j
public final class FeatureRefreshScheduler {
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);
    private ExecutorService featureRefreshExecutor;

    public void requestRefresh(RefreshMode refreshMode, FeatureRefreshAction refreshAction) {
        FeatureRefreshAction resolvedRefreshAction = Objects.requireNonNull(refreshAction, "refreshAction");
        RefreshMode resolvedRefreshMode = refreshMode == null ? RefreshMode.DEFAULT : refreshMode;
        if (resolvedRefreshMode == RefreshMode.FORCE) {
            refreshInBackground(resolvedRefreshMode, resolvedRefreshAction);
            return;
        }

        refreshNow(resolvedRefreshMode, resolvedRefreshAction);
    }

    public synchronized void shutdown() {
        this.shuttingDown.set(true);
        if (this.featureRefreshExecutor != null) {
            this.featureRefreshExecutor.shutdownNow();
            this.featureRefreshExecutor = null;
            log.info("Feature refresh executor shut down");
        }
    }

    private void refreshNow(RefreshMode refreshMode, FeatureRefreshAction refreshAction) {
        try {
            refreshAction.refresh(refreshMode);
        } catch (FeatureFetchException e) {
            log.error("Refreshing wasn't successful. Message is: {}", e.getMessage(), e);
        }
    }

    private void refreshInBackground(RefreshMode refreshMode, FeatureRefreshAction refreshAction) {
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
                    refreshAction.refresh(refreshMode);
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
