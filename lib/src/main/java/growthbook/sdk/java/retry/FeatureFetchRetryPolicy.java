package growthbook.sdk.java.retry;

import java.time.Duration;

import lombok.Getter;

/**
 * Configures bounded exponential backoff for feature fetch requests.
 *
 * <p>The default policy allows five total attempts. Delays are calculated before
 * each retry, so the default five attempts produce retry waits of
 * 1s, 2s, 4s, and 8s. The 16s default maximum delay caps custom policies with
 * more attempts.
 */
public class FeatureFetchRetryPolicy {

    public static final int DEFAULT_MAX_ATTEMPTS = 5;
    public static final Duration DEFAULT_MAX_DELAY = Duration.ofSeconds(16);
    public static final Duration DEFAULT_INITIAL_DELAY = Duration.ofSeconds(1);

    @Getter
    private final int maxAttempts;

    @Getter
    private final Duration maxDelay;

    @Getter
    private final Duration initialDelay;

    public FeatureFetchRetryPolicy() {
        this(DEFAULT_MAX_ATTEMPTS, DEFAULT_INITIAL_DELAY, DEFAULT_MAX_DELAY);
    }

    /**
     * Creates an exponential backoff policy.
     *
     * @param maxAttempts total attempts, including the first attempt
     * @param initialDelay delay before the first retry
     * @param maxDelay upper bound for any retry delay
     */
    public FeatureFetchRetryPolicy(int maxAttempts, Duration initialDelay, Duration maxDelay) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be at least 1");
        }
        if (initialDelay == null || initialDelay.isNegative()) {
            throw new IllegalArgumentException("initialDelay must not be null or negative");
        }
        if (maxDelay == null || maxDelay.isNegative()) {
            throw new IllegalArgumentException("maxDelay must not be null or negative");
        }

        this.maxAttempts = maxAttempts;
        this.initialDelay = initialDelay;
        this.maxDelay = maxDelay;
    }

    /**
     * Returns the delay before the requested attempt number.
     *
     * @param attemptNumber one-based attempt number
     * @return zero for the first attempt, otherwise the capped exponential delay
     */
    public long getDelayMillisBeforeAttempt(int attemptNumber) {
        if (attemptNumber <= 1) {
            return 0;
        }

        long initialDelayMillis = initialDelay.toMillis();
        long maxDelayMillis = maxDelay.toMillis();
        int exponent = attemptNumber - 2;
        long delayMillis;

        if (exponent >= Long.SIZE - 1 || initialDelayMillis > (Long.MAX_VALUE >> exponent)) {
            delayMillis = Long.MAX_VALUE;
        } else {
            delayMillis = initialDelayMillis << exponent;
        }

        return Math.min(delayMillis, maxDelayMillis);
    }
}
