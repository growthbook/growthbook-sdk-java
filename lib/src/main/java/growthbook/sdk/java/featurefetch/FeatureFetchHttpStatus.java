package growthbook.sdk.java.featurefetch;

import java.net.HttpURLConnection;

/**
 * Classifies feature fetch HTTP responses for retry behavior.
 */
public final class FeatureFetchHttpStatus {
    private FeatureFetchHttpStatus() {
    }

    public static boolean isRetryable(int statusCode) {
        return statusCode == HttpURLConnection.HTTP_CLIENT_TIMEOUT
                || statusCode == 429
                || statusCode >= HttpURLConnection.HTTP_INTERNAL_ERROR;
    }
}
