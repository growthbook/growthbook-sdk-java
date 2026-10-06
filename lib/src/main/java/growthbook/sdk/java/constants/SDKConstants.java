package growthbook.sdk.java.constants;

import lombok.experimental.UtilityClass;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Shared SDK constants used across repository, refresh, and diagnostics code.
 */
@UtilityClass
public class SDKConstants {
    public static final int DEFAULT_SWR_TTL_SECONDS = 60;

    /**
     * Header names (lowercase) managed by the SDK itself. User-supplied header maps
     * ({@code apiHostRequestHeaders}, {@code streamingHostRequestHeaders}) must not
     * override them: {@code User-Agent} identifies the SDK, {@code Accept} carries the
     * SSE content negotiation ({@code text/event-stream}) on the streaming request, and
     * {@code If-None-Match} and {@code Cache-Control} drive ETag/TTL-based cache revalidation.
     */
    public static final Set<String> RESERVED_REQUEST_HEADERS = Collections.unmodifiableSet(
            new HashSet<>(Arrays.asList("user-agent", "accept", "if-none-match", "cache-control")));

    @UtilityClass
    public class Endpoints {
        public static final String STREAMING_ENDPOINT_PATH = "/sub/";
        public static final String FEATURES_ENDPOINT_PATH = "/api/features/";
        public static final String DEFAULT_API_HOST = "https://cdn.growthbook.io";
        public static final String FEATURES_ENDPOINT_PATTERN = ".*" + FEATURES_ENDPOINT_PATH + "[^/]+";
    }
}
