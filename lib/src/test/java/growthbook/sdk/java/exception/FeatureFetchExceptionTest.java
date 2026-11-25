package growthbook.sdk.java.exception;

import growthbook.sdk.java.exception.FeatureFetchException.FeatureFetchErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FeatureFetchExceptionTest {

    @ParameterizedTest(name = "{0}")
    @EnumSource(FeatureFetchErrorCode.class)
    @DisplayName("Uses the error code as the message when no message is given")
    void usesErrorCodeAsMessageWhenNoMessageGiven(FeatureFetchErrorCode errorCode) {
        // When
        FeatureFetchException exception = new FeatureFetchException(errorCode);

        // Then
        assertEquals(errorCode, exception.getErrorCode());
        assertEquals(errorCode.name(), exception.getMessage());
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(FeatureFetchErrorCode.class)
    @DisplayName("Prefixes the custom message with the error code")
    void prefixesCustomMessageWithErrorCode(FeatureFetchErrorCode errorCode) {
        // When
        FeatureFetchException exception = new FeatureFetchException(errorCode, "request failed");

        // Then
        assertEquals(errorCode, exception.getErrorCode());
        assertEquals(errorCode.name() + " : request failed", exception.getMessage());
    }
}
