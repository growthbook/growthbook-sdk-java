package growthbook.sdk.java.constants;

import lombok.experimental.UtilityClass;

/**
 * Shared SDK constants used across repository, refresh, and diagnostics code.
 */
@UtilityClass
public class SDKConstants {
    public static final int DEFAULT_SWR_TTL_SECONDS = 60;

    @UtilityClass
    public class Endpoints {
        public static final String STREAMING_ENDPOINT_PATH = "/sub/";
        public static final String FEATURES_ENDPOINT_PATH = "/api/features/";
        public static final String DEFAULT_API_HOST = "https://cdn.growthbook.io";
        public static final String FEATURES_ENDPOINT_PATTERN = ".*" + FEATURES_ENDPOINT_PATH + "[^/]+";
    }
}
