package growthbook.sdk.java.multiusermode;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Delayed;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Sticky bucket timeout timer that a test fires by hand: no store call times out until
 * {@link #elapseNext()}. Shared by the manager and client tests.
 *
 * <p>Only the one-shot {@code schedule} that {@link StickyBucketManager} uses is replaced. Nothing is
 * ever handed to the pool this extends, so it starts no thread.
 */
final class ManualTimeoutScheduler extends ScheduledThreadPoolExecutor {

    private final BlockingQueue<ArmedTimeout> armed = new LinkedBlockingQueue<>();

    ManualTimeoutScheduler() {
        super(1);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        if (isShutdown()) {
            throw new RejectedExecutionException("timeout scheduler is shut down");
        }
        ArmedTimeout timeout = new ArmedTimeout(command, unit.toMillis(delay));
        armed.add(timeout);
        return timeout;
    }

    /**
     * Lets the timeout elapse for the oldest store call that is still being timed. A call started on
     * another thread is waited for, so the timeout never fires before the call is in flight.
     *
     * @return the delay that timeout was armed with, in milliseconds
     */
    long elapseNext() throws InterruptedException {
        while (true) {
            ArmedTimeout timeout = armed.poll(10, TimeUnit.SECONDS);
            if (timeout == null) {
                throw new AssertionError("no sticky bucket store call is being timed");
            }
            if (!timeout.isDone()) { // a call that already answered has cancelled its timeout
                timeout.command.run();
                timeout.complete(null);
                return timeout.delayMillis;
            }
        }
    }

    /** How many store calls are being timed right now. */
    int armedCount() {
        int count = 0;
        for (ArmedTimeout timeout : armed) {
            if (!timeout.isDone()) {
                count++;
            }
        }
        return count;
    }

    private static final class ArmedTimeout extends CompletableFuture<Object> implements ScheduledFuture<Object> {
        private final Runnable command;
        private final long delayMillis;

        ArmedTimeout(Runnable command, long delayMillis) {
            this.command = command;
            this.delayMillis = delayMillis;
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return unit.convert(delayMillis, TimeUnit.MILLISECONDS);
        }

        @Override
        public int compareTo(Delayed other) {
            return Long.compare(delayMillis, other.getDelay(TimeUnit.MILLISECONDS));
        }
    }
}
