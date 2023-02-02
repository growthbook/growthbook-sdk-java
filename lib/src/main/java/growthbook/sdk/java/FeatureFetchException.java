package growthbook.sdk.java;

import lombok.Getter;

/**
 * This error is thrown by {@link GBFeaturesRepository}
 * You can call {@link #getErrorCode()} to get an enum of various error types you can handle.
 *
 * CONFIGURATION_ERROR:
 *   - an encryptionKey was provided but the endpoint does not support encryption so decryption fails
 *   - no features were found for an unencrypted endpoint
 * NO_RESPONSE_ERROR:
 *   - there was no response body
 * UNKNOWN:
 *   - there was an unknown error that occurred when attempting to make the request.
 */
public class FeatureFetchException extends Exception {
    @Getter
    private final FeatureFetchErrorCode errorCode;

    enum FeatureFetchErrorCode {
        CONFIGURATION_ERROR,
        NO_RESPONSE_ERROR,
        UNKNOWN,
    }

    public FeatureFetchException(FeatureFetchErrorCode errorCode, String errorMessage) {
        super(errorCode.toString() + " : " + errorMessage);
        this.errorCode = errorCode;
    }

    public FeatureFetchException(FeatureFetchErrorCode errorCode) {
        super(errorCode.toString());
        this.errorCode = errorCode;
    }
}
