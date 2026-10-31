package growthbook.sdk.java.retry;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FeatureFetchRetryPolicyTest {

    @ParameterizedTest(name = "attempt {0} waits {1} ms")
    @CsvSource({
            "0, 0",
            "1, 0",
            "2, 1000",
            "3, 2000",
            "4, 4000",
            "5, 8000",
            "6, 16000",
            "7, 16000",
            "100, 16000"
    })
    @DisplayName("Doubles the delay before each retry and caps it at the maximum delay")
    void doublesDelayAndCapsAtMaximum(int attemptNumber, long expectedDelayMillis) {
        // Given
        FeatureFetchRetryPolicy policy = new FeatureFetchRetryPolicy(10, Duration.ofSeconds(1), Duration.ofSeconds(16));

        // When
        long delay = policy.getDelayMillisBeforeAttempt(attemptNumber);

        // Then
        assertEquals(expectedDelayMillis, delay);
    }

    @Test
    @DisplayName("The default policy allows five attempts with a one second initial and sixteen second maximum delay")
    void defaultPolicyUsesDocumentedDefaults() {
        // When
        FeatureFetchRetryPolicy policy = new FeatureFetchRetryPolicy();

        // Then
        assertEquals(5, policy.getMaxAttempts());
        assertEquals(Duration.ofSeconds(1), policy.getInitialDelay());
        assertEquals(Duration.ofSeconds(16), policy.getMaxDelay());
    }

    @ParameterizedTest(name = "maxAttempts = {0}")
    @CsvSource({"0", "-1"})
    @DisplayName("Rejects fewer than one attempt")
    void rejectsFewerThanOneAttempt(int maxAttempts) {
        // When / Then
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new FeatureFetchRetryPolicy(maxAttempts, Duration.ofSeconds(1), Duration.ofSeconds(16)));
        assertEquals("maxAttempts must be at least 1", exception.getMessage());
    }

    @Test
    @DisplayName("Rejects a null or negative initial delay")
    void rejectsNullOrNegativeInitialDelay() {
        // When / Then
        IllegalArgumentException nullDelay = assertThrows(IllegalArgumentException.class,
                () -> new FeatureFetchRetryPolicy(1, null, Duration.ofSeconds(16)));
        IllegalArgumentException negativeDelay = assertThrows(IllegalArgumentException.class,
                () -> new FeatureFetchRetryPolicy(1, Duration.ofMillis(-1), Duration.ofSeconds(16)));
        assertEquals("initialDelay must not be null or negative", nullDelay.getMessage());
        assertEquals("initialDelay must not be null or negative", negativeDelay.getMessage());
    }

    @Test
    @DisplayName("Rejects a null or negative maximum delay")
    void rejectsNullOrNegativeMaxDelay() {
        // When / Then
        IllegalArgumentException nullDelay = assertThrows(IllegalArgumentException.class,
                () -> new FeatureFetchRetryPolicy(1, Duration.ofSeconds(1), null));
        IllegalArgumentException negativeDelay = assertThrows(IllegalArgumentException.class,
                () -> new FeatureFetchRetryPolicy(1, Duration.ofSeconds(1), Duration.ofMillis(-1)));
        assertEquals("maxDelay must not be null or negative", nullDelay.getMessage());
        assertEquals("maxDelay must not be null or negative", negativeDelay.getMessage());
    }
}
