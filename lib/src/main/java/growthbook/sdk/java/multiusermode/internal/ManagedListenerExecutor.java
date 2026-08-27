package growthbook.sdk.java.multiusermode.internal;

import javax.annotation.Nullable;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Resolves the executor that dispatches client-level feature refresh listener callbacks.
 *
 * <p>A caller-supplied executor is used as-is and its lifecycle is left to the caller. When none is
 * supplied, a dedicated daemon executor is created and owned here, so {@link #shutdown()} only stops
 * what this instance created.
 */
public final class ManagedListenerExecutor {

    private final Executor executor;

    @Nullable
    private final ExecutorService owned;

    private ManagedListenerExecutor(Executor executor, @Nullable ExecutorService owned) {
        this.executor = executor;
        this.owned = owned;
    }

    /**
     * @param configured caller-supplied executor, or null to own a default
     * @return a managed executor wrapping the configured executor, or a newly owned daemon executor
     */
    public static ManagedListenerExecutor resolve(@Nullable Executor configured) {
        if (configured != null) {
            return new ManagedListenerExecutor(configured, null);
        }
        ExecutorService owned = Executors.newSingleThreadExecutor(daemonThreadFactory());
        return new ManagedListenerExecutor(owned, owned);
    }

    public Executor executor() {
        return executor;
    }

    /**
     * Stops the executor only when it is owned by this instance.
     */
    public void shutdown() {
        if (owned != null) {
            owned.shutdown();
        }
    }

    private static ThreadFactory daemonThreadFactory() {
        AtomicInteger threadNumber = new AtomicInteger();
        return runnable -> {
            Thread thread = new Thread(runnable, "growthbook-feature-refresh-listener-" + threadNumber.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
