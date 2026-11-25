package growthbook.sdk.java.plugin.tracking;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrackingPluginConfigTest {

    @ParameterizedTest(name = "ingestorHost = \"{0}\" resolves to {1}")
    @CsvSource(value = {
            "https://ingest.example.test/, https://ingest.example.test",
            "https://ingest.example.test, https://ingest.example.test",
            "'', https://us1.gb-ingest.com",
            "'   ', https://us1.gb-ingest.com",
            "NULL, https://us1.gb-ingest.com"
    }, nullValues = "NULL")
    @DisplayName("Resolves the ingestor host without a trailing slash, falling back to the default")
    void resolvesIngestorHost(String ingestorHost, String expected) {
        // Given
        TrackingPluginConfig config = TrackingPluginConfig.builder().clientKey("sdk-test").ingestorHost(ingestorHost).build();

        // When
        String resolved = config.resolvedIngestorHost();

        // Then
        assertEquals(expected, resolved);
    }

    @ParameterizedTest(name = "batchSize = {0} resolves to {1}")
    @CsvSource(value = {
            "1, 1",
            "250, 250",
            "10000, 10000",
            "10001, 10000",
            "2147483647, 10000",
            "0, 100",
            "-5, 100",
            "NULL, 100"
    }, nullValues = "NULL")
    @DisplayName("Resolves the batch size, clamping to the maximum and defaulting non-positive values")
    void resolvesBatchSize(Integer batchSize, int expected) {
        // Given
        TrackingPluginConfig config = TrackingPluginConfig.builder().clientKey("sdk-test").batchSize(batchSize).build();

        // When
        int resolved = config.resolvedBatchSize();

        // Then
        assertEquals(expected, resolved);
    }

    @ParameterizedTest(name = "batchTimeout = {0} ms")
    @NullSource
    @ValueSource(longs = {0, -1})
    @DisplayName("Falls back to the default batch timeout for a missing, zero or negative value")
    void defaultsNonPositiveBatchTimeout(Long millis) {
        // Given
        TrackingPluginConfig config = TrackingPluginConfig.builder()
                .clientKey("sdk-test")
                .batchTimeout(millis == null ? null : Duration.ofMillis(millis))
                .build();

        // When
        Duration resolved = config.resolvedBatchTimeout();

        // Then
        assertEquals(TrackingPluginConfig.DEFAULT_BATCH_TIMEOUT, resolved);
    }

    @ParameterizedTest(name = "closeTimeout = {0} ms")
    @NullSource
    @ValueSource(longs = {0, -1})
    @DisplayName("Falls back to the default close timeout for a missing, zero or negative value")
    void defaultsNonPositiveCloseTimeout(Long millis) {
        // Given
        TrackingPluginConfig config = TrackingPluginConfig.builder()
                .clientKey("sdk-test")
                .closeTimeout(millis == null ? null : Duration.ofMillis(millis))
                .build();

        // When
        Duration resolved = config.resolvedCloseTimeout();

        // Then
        assertEquals(TrackingPluginConfig.DEFAULT_CLOSE_TIMEOUT, resolved);
    }

    @ParameterizedTest(name = "{0} ms")
    @ValueSource(longs = {1, 250})
    @DisplayName("Keeps positive batch and close timeouts as configured")
    void keepsPositiveTimeouts(long millis) {
        // Given
        TrackingPluginConfig config = TrackingPluginConfig.builder()
                .clientKey("sdk-test")
                .batchTimeout(Duration.ofMillis(millis))
                .closeTimeout(Duration.ofMillis(millis))
                .build();

        // When / Then
        assertEquals(Duration.ofMillis(millis), config.resolvedBatchTimeout());
        assertEquals(Duration.ofMillis(millis), config.resolvedCloseTimeout());
    }
}
