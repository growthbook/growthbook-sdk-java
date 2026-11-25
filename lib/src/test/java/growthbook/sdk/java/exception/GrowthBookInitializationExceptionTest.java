package growthbook.sdk.java.exception;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class GrowthBookInitializationExceptionTest {

    @Test
    @DisplayName("Preserves the cause and error code of the feature fetch failure")
    void preservesCauseAndErrorCodeOfFeatureFetchFailure() {
        // Given
        FeatureFetchException cause = new FeatureFetchException(
                FeatureFetchException.FeatureFetchErrorCode.NO_RESPONSE_ERROR,
                "Request failed"
        );

        // When
        GrowthBookInitializationException exception = new GrowthBookInitializationException(cause);

        // Then
        assertEquals("Failed to initialize features repository", exception.getMessage());
        assertEquals(FeatureFetchException.FeatureFetchErrorCode.NO_RESPONSE_ERROR, exception.getErrorCode());
        assertSame(cause, exception.getCause());
    }
}
