package growthbook.sdk.java.retry;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeatureFetchRetryPolicyTest {
    @Test
    void usesExponentialBackoffAndCapsDelay() {
        FeatureFetchRetryPolicy policy = new FeatureFetchRetryPolicy(
                10,
                Duration.ofSeconds(1),
                Duration.ofSeconds(16)
        );

        assertEquals(1_000, policy.getDelayMillisBeforeAttempt(2));
        assertEquals(2_000, policy.getDelayMillisBeforeAttempt(3));
        assertEquals(4_000, policy.getDelayMillisBeforeAttempt(4));
        assertEquals(8_000, policy.getDelayMillisBeforeAttempt(5));
        assertEquals(16_000, policy.getDelayMillisBeforeAttempt(6));
        assertEquals(16_000, policy.getDelayMillisBeforeAttempt(7));
    }
}
