package growthbook.sdk.java.multiusermode;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.retry.FeatureFetchRetryPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

/**
 * Client-facing behaviour of the inline bootstrap payload: an offline cold start that still evaluates
 * from the seeded snapshot, and a fail-fast on a malformed payload.
 */
class GrowthBookClientInitialPayloadTest {

    private static final String UNREACHABLE_API_HOST = "http://localhost:1";
    private static final FeatureFetchRetryPolicy NO_DELAY_RETRY_POLICY =
            new FeatureFetchRetryPolicy(1, Duration.ZERO, Duration.ZERO);

    @Test
    @DisplayName("Offline: initialize() returns true and isOn() evaluates from the seeded payload")
    void offlineClient_evaluatesFromInitialPayload() {
        Options options = Options.builder()
                .apiHost(UNREACHABLE_API_HOST)
                .clientKey("sdk-abc123")
                .isCacheDisabled(true)
                .retryPolicy(NO_DELAY_RETRY_POLICY)
                .initialPayload("{\"features\":{\"my-flag\":{\"defaultValue\":true}}}")
                .build();
        GrowthBookClient client = new GrowthBookClient(options);

        boolean initialized = client.initialize();

        assertTrue(initialized, "client should be ready from the seeded payload even while offline");
        assertTrue(client.isOn("my-flag", UserContext.builder().build()));

        client.shutdown();
    }

    @Test
    @DisplayName("Malformed payload: initialize() reports failure instead of silently starting empty")
    void malformedPayload_reportsStartupFailure() {
        Options options = Options.builder()
                .apiHost(UNREACHABLE_API_HOST)
                .clientKey("sdk-abc123")
                .isCacheDisabled(true)
                .retryPolicy(NO_DELAY_RETRY_POLICY)
                .initialPayload("{ not valid json")
                .build();
        GrowthBookClient client = new GrowthBookClient(options);

        assertFalse(client.initialize());
        client.shutdown();
    }
}
