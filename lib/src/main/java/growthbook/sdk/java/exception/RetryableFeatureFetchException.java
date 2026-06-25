package growthbook.sdk.java.exception;

public final class RetryableFeatureFetchException extends FeatureFetchException {
    public RetryableFeatureFetchException(FeatureFetchErrorCode errorCode, String errorMessage) {
        super(errorCode, errorMessage);
    }

    public RetryableFeatureFetchException(FeatureFetchErrorCode errorCode, String errorMessage, Throwable cause) {
        super(errorCode, errorMessage);
        initCause(cause);
    }
}
