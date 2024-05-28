package growthbook.sdk.java;

import lombok.Getter;

/**
 * This error is thrown by {@link GBFeaturesRepository}
 * You can call getErrorCode() to get an enum of various error types you can handle.
 * <p>
 * CONFIGURATION_ERROR:
 * - an encryptionKey was provided but the endpoint does not support encryption so decryption fails
 * - no features were found for an unencrypted endpoint
 * NO_RESPONSE_ERROR:
 * - there was no response body
 * UNKNOWN:
 * - there was an unknown error that occurred when attempting to make the request.
 */
@Getter
public class FeatureFetchException extends Exception {

    /**
     * Allows you to identify an error by its unique error code.
     * Separate from the custom message.
     */
    private final FeatureFetchErrorCode errorCode;

    /**
     * Error codes available for a {@link FeatureFetchException}
     */
    public enum FeatureFetchErrorCode {
        /**
         * - an encryptionKey was provided but the endpoint does not support encryption so decryption fails
         * - no features were found for an unencrypted endpoint
         */
        CONFIGURATION_ERROR,

        /**
         * - there was no response body
         */
        NO_RESPONSE_ERROR,

        /**
         * - could not establish a connection to the events (server-sent events) for feature updates
         */
        SSE_CONNECTION_ERROR,

        /**
         * - there was an unknown error that occurred when attempting to make the request.
         */
        UNKNOWN,
    }


    /**
     * Create an exception with error code and custom message
     *
     * @param errorCode    {@link FeatureFetchErrorCode}
     * @param errorMessage Custom error message string
     */
    public FeatureFetchException(FeatureFetchErrorCode errorCode, String errorMessage) {
        super(errorCode.toString() + " : " + errorMessage);
        this.errorCode = errorCode;
    }

    /**
     * Create an exception with error code
     *
     * @param errorCode {@link FeatureFetchErrorCode}
     */
    public FeatureFetchException(FeatureFetchErrorCode errorCode) {
        super(errorCode.toString());
        this.errorCode = errorCode;
    }
}
