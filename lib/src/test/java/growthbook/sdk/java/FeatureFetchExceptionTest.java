package growthbook.sdk.java;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

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

        // HTTP_RESPONSE_ERROR
        FeatureFetchException httpResponseExc = new FeatureFetchException(
                FeatureFetchException.FeatureFetchErrorCode.HTTP_RESPONSE_ERROR
        );
        assertEquals(
                FeatureFetchException.FeatureFetchErrorCode.HTTP_RESPONSE_ERROR,
                httpResponseExc.getErrorCode()
        );
        FeatureFetchException httpResponseExcWithMessage = new FeatureFetchException(
                FeatureFetchException.FeatureFetchErrorCode.HTTP_RESPONSE_ERROR,
                "responded with status 400"
        );
        assertEquals(
                FeatureFetchException.FeatureFetchErrorCode.HTTP_RESPONSE_ERROR,
                httpResponseExcWithMessage.getErrorCode()
        );
        assertEquals(
                "HTTP_RESPONSE_ERROR : responded with status 400",
                httpResponseExcWithMessage.getMessage()
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
