package growthbook.sdk.java.diagnostics.provider.helper;

import growthbook.sdk.java.diagnostics.model.ErrorDiagnostics;
import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.exception.GrowthBookClientInitializationException;

/**
 * Maps internal exceptions into sanitized diagnostics output.
 */
public final class ErrorDiagnosticsMapper {

    public ErrorDiagnostics map(Throwable error, long timestampMillis) {
        return map(error, timestampMillis, DiagnosticsSecretMasker.empty());
    }

    public ErrorDiagnostics map(
            Throwable error,
            long timestampMillis,
            DiagnosticsSecretMasker secretMasker
    ) {
        Throwable normalizedError = normalize(error);
        return ErrorDiagnostics.builder()
                .type(normalizedError.getClass().getSimpleName())
                .code(errorCode(normalizedError))
                .message(secretMasker.mask(errorMessage(normalizedError)))
                .timestampMillis(timestampMillis)
                .build();
    }

    private Throwable normalize(Throwable error) {
        if (error == null) {
            return new FeatureFetchException(FeatureFetchException.FeatureFetchErrorCode.UNKNOWN);
        }
        if (error instanceof GrowthBookClientInitializationException && error.getCause() != null) {
            return error.getCause();
        }
        return error;
    }

    private String errorCode(Throwable error) {
        if (error instanceof FeatureFetchException) {
            return ((FeatureFetchException) error).getErrorCode().name();
        }
        return null;
    }

    private String errorMessage(Throwable error) {
        return error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage();
    }
}
