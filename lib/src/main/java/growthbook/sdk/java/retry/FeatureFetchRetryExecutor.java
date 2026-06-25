package growthbook.sdk.java.retry;

import java.util.Objects;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;

import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.exception.RetryableFeatureFetchException;

/**
 * Executes feature fetch attempts with a bounded retry policy.
 *
 * <p>The executor only owns retry timing and retryability decisions. It does
 * not decide how callers should recover after all attempts fail; repositories
 * keep that responsibility so they can return existing or cached feature data.
 */
@Slf4j
public final class FeatureFetchRetryExecutor {

    private final FeatureFetchRetryPolicy retryPolicy;

    public FeatureFetchRetryExecutor(FeatureFetchRetryPolicy retryPolicy) {
        this.retryPolicy = Objects.requireNonNull(retryPolicy, "retryPolicy");
    }

    /**
     * Runs the fetch attempt until it succeeds or retry attempts are exhausted.
     *
     * @return empty when the attempt succeeds; otherwise the last fetch failure
     */
    public Optional<FeatureFetchException> execute(FeatureFetchAttempt fetchAttempt) {
        FeatureFetchException lastFailure = null;
        int maxAttempts = this.retryPolicy.getMaxAttempts();
        boolean shouldRetry = true;

        for (int attempt = 1; shouldRetry && attempt <= maxAttempts; attempt++) {
            try {
                fetchAttempt.run();
                return Optional.empty();
            } catch (FeatureFetchException e) {
                lastFailure = e;
                shouldRetry = shouldRetry(e, attempt, maxAttempts);

                if (shouldRetry) {
                    int nextAttempt = attempt + 1;
                    if (!waitBeforeRetry(nextAttempt, maxAttempts)) {
                        shouldRetry = false;
                        lastFailure = interruptedFetchException();
                    }
                }
            }
        }

        return Optional.ofNullable(lastFailure);
    }

    private boolean shouldRetry(FeatureFetchException failure, int attempt, int maxAttempts) {
        return failure instanceof RetryableFeatureFetchException && attempt < maxAttempts;
    }

    private boolean waitBeforeRetry(int nextAttempt, int maxAttempts) {
        long delayMillis = this.retryPolicy.getDelayMillisBeforeAttempt(nextAttempt);
        log.warn(
                "Feature fetch failed. Retry attempt {}/{} in {}ms.",
                nextAttempt,
                maxAttempts,
                delayMillis
        );

        return sleepBeforeNextAttempt(delayMillis);
    }

    private boolean sleepBeforeNextAttempt(long delayMillis) {
        try {
            Thread.sleep(delayMillis);
            return true;
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private FeatureFetchException interruptedFetchException() {
        return new FeatureFetchException(
                FeatureFetchException.FeatureFetchErrorCode.UNKNOWN,
                "Feature fetch retry interrupted"
        );
    }

    /**
     * Unit of feature fetch work that can be retried by this executor.
     */
    @FunctionalInterface
    public interface FeatureFetchAttempt {
        void run() throws FeatureFetchException;
    }
}
