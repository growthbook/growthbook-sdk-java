package growthbook.sdk.java.constants;

import lombok.experimental.UtilityClass;

import java.time.Duration;

/**
 * Shared SDK constants used across repository, refresh, and diagnostics code.
 */
@UtilityClass
public class SDKConstants {
    public static final int DEFAULT_SWR_TTL_SECONDS = 60;

    /**
     * Default connect/read/write timeout applied to the features fetch and remote-eval HTTP calls
     * when no {@code requestTimeout} is configured. Bounds SDK start-up so a slow or unreachable
     * endpoint cannot block initialization indefinitely. Configure {@link Duration#ZERO} to restore
     * OkHttp's default of an infinite timeout.
     */
    public static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(10);

    @UtilityClass
    public class Endpoints {
        public static final String STREAMING_ENDPOINT_PATH = "/sub/";
        public static final String FEATURES_ENDPOINT_PATH = "/api/features/";
        public static final String DEFAULT_API_HOST = "https://cdn.growthbook.io";
        public static final String FEATURES_ENDPOINT_PATTERN = ".*" + FEATURES_ENDPOINT_PATH + "[^/]+";
    }
}
