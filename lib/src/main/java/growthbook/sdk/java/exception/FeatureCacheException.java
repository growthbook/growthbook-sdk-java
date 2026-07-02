package growthbook.sdk.java.exception;

/**
 * Thrown when a configured feature cache cannot be accessed.
 */
public class FeatureCacheException extends RuntimeException {
    public FeatureCacheException(String message) {
        super(message);
    }

    public FeatureCacheException(String message, Throwable cause) {
        super(message, cause);
    }
}
