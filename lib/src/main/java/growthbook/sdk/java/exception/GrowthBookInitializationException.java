package growthbook.sdk.java.exception;

import lombok.Getter;

/**
 * Indicates that a GrowthBook client could not initialize its features repository.
 */
@Getter
public class GrowthBookInitializationException extends RuntimeException {
    private final FeatureFetchException.FeatureFetchErrorCode errorCode;

    public GrowthBookInitializationException(FeatureFetchException cause) {
        super("Failed to initialize features repository", cause);
        this.errorCode = cause.getErrorCode();
    }
}
