package growthbook.sdk.java;

import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.exception.GrowthBookInitializationException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class GrowthBookInitializationExceptionTest {

    @Test
    void preservesFeatureFetchExceptionDetails() {
        FeatureFetchException cause = new FeatureFetchException(
                FeatureFetchException.FeatureFetchErrorCode.NO_RESPONSE_ERROR,
                "Request failed"
        );

        GrowthBookInitializationException exception = new GrowthBookInitializationException(cause);

        assertEquals("Failed to initialize features repository", exception.getMessage());
        assertEquals(FeatureFetchException.FeatureFetchErrorCode.NO_RESPONSE_ERROR, exception.getErrorCode());
        assertSame(cause, exception.getCause());
    }
}
