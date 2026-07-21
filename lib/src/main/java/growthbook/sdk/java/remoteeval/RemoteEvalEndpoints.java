package growthbook.sdk.java.remoteeval;

import javax.annotation.Nullable;

/**
 * Endpoint helpers for the GrowthBook remote-eval API.
 */
public final class RemoteEvalEndpoints {
    public static final String EVAL_PATH_PREFIX = "/api/eval/";

    private RemoteEvalEndpoints() {
    }

    public static String evalEndpoint(@Nullable String apiHost, @Nullable String clientKey) {
        return normalize(apiHost) + EVAL_PATH_PREFIX + normalize(clientKey);
    }

    private static String normalize(@Nullable String value) {
        return value == null ? "" : value;
    }
}
