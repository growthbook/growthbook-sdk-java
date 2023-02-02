package growthbook.sdk.java;

class FeatureFetchException extends Exception {
    enum FeatureFetchErrorCode {
        CONFIGURATION_ERROR,
        NO_RESPONSE_ERROR,
        UNKNOWN,
    }

    public FeatureFetchException(FeatureFetchErrorCode errorCode, String errorMessage) {
        super(errorCode.toString() + " : " + errorMessage);
    }

    public FeatureFetchException(FeatureFetchErrorCode errorCode) {
        super(errorCode.toString());
    }
}
