package growthbook.sdk.java.repository.internal;

import lombok.extern.slf4j.Slf4j;

import java.time.Clock;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Refreshes features in the background at a fixed delay, on a daemon thread so it never keeps
 * the JVM alive. Overlapping polls are skipped. Thread-safe.
 */
@Slf4j
public final class RefreshPoller {

    private static final ThreadFactory POLL_THREADS = runnable -> {
        Thread thread = new Thread(runnable, "growthbook-feature-poll");
        thread.setDaemon(true);
        return thread;
    };

    /**
     * One refresh; any exception it throws is logged and polling goes on.
     */
    @FunctionalInterface
    public interface Poll {
        void run() throws Exception;
    }

    private final Poll poll;
    private final Clock clock;
    private final AtomicBoolean polling = new AtomicBoolean();
    private final AtomicReference<ScheduledExecutorService> scheduler = new AtomicReference<>();

    public RefreshPoller(Poll poll, Clock clock) {
        this.poll = poll;
        this.clock = clock;
    }

    /**
     * Starts polling once; later calls are ignored.
     */
    public void start(long delaySeconds) {
        ScheduledExecutorService created = Executors.newSingleThreadScheduledExecutor(POLL_THREADS);
        if (!scheduler.compareAndSet(null, created)) {
            created.shutdown();
            return;
        }
        created.scheduleWithFixedDelay(this::pollOnce, delaySeconds, delaySeconds, TimeUnit.SECONDS);
    }

    public void stop() {
        ScheduledExecutorService current = scheduler.getAndSet(null);
        if (current != null) {
            current.shutdown();
            log.info("Polling scheduler shut down");
        }
    }

    void pollOnce() {
        if (!polling.compareAndSet(false, true)) {
            return;
        }
        long started = clock.millis();
        try {
            log.debug("GrowthBook Features Refresh polling starts");
            poll.run();
        } catch (Exception e) {
            log.error("Features Refresh polling failed.", e);
        } finally {
            log.debug("GrowthBook Features Refresh polling ends in ({} ms)", clock.millis() - started);
            polling.set(false);
        }
    }
}
