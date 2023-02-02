package growthbook.sdk.java;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FeatureFetchExceptionTest {

    @Test
    void exceptionsHaveEnumErrorCodesAndMessages() {
        // CONFIGURATION_ERROR
        FeatureFetchException configExc = new FeatureFetchException(
            FeatureFetchException.FeatureFetchErrorCode.CONFIGURATION_ERROR
        );
        assertEquals(
            FeatureFetchException.FeatureFetchErrorCode.CONFIGURATION_ERROR,
            configExc.getErrorCode()
        );
        FeatureFetchException configExcWithError = new FeatureFetchException(
            FeatureFetchException.FeatureFetchErrorCode.CONFIGURATION_ERROR,
            "config with message"
        );
        assertEquals(
            FeatureFetchException.FeatureFetchErrorCode.CONFIGURATION_ERROR,
            configExcWithError.getErrorCode()
        );
        assertEquals(
            "CONFIGURATION_ERROR : config with message",
            configExcWithError.getMessage()
        );

        // NO_RESPONSE_ERROR
        FeatureFetchException noResponseExc = new FeatureFetchException(
            FeatureFetchException.FeatureFetchErrorCode.NO_RESPONSE_ERROR
        );
        assertEquals(
            FeatureFetchException.FeatureFetchErrorCode.NO_RESPONSE_ERROR,
            noResponseExc.getErrorCode()
        );
        FeatureFetchException noResponseExcWithMessage = new FeatureFetchException(
            FeatureFetchException.FeatureFetchErrorCode.NO_RESPONSE_ERROR,
            "no response with message"
        );
        assertEquals(
            FeatureFetchException.FeatureFetchErrorCode.NO_RESPONSE_ERROR,
            noResponseExcWithMessage.getErrorCode()
        );
        assertEquals(
            "NO_RESPONSE_ERROR : no response with message",
            noResponseExcWithMessage.getMessage()
        );

        // UNKNOWN
        FeatureFetchException unknownExc = new FeatureFetchException(
            FeatureFetchException.FeatureFetchErrorCode.UNKNOWN
        );
        assertEquals(
            FeatureFetchException.FeatureFetchErrorCode.UNKNOWN,
            unknownExc.getErrorCode()
        );
        FeatureFetchException unknownExcWithMessage = new FeatureFetchException(
            FeatureFetchException.FeatureFetchErrorCode.UNKNOWN,
            "unknown with message"
        );
        assertEquals(
            FeatureFetchException.FeatureFetchErrorCode.UNKNOWN,
            unknownExcWithMessage.getErrorCode()
        );
        assertEquals(
            "UNKNOWN : unknown with message",
            unknownExcWithMessage.getMessage()
        );
    }
}
