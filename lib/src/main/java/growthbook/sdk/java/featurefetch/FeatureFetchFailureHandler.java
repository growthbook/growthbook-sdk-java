package growthbook.sdk.java.featurefetch;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import growthbook.sdk.java.exception.FeatureFetchException;

/**
 * Applies the shared failure behavior after feature fetch retries are exhausted.
 *
 * <p>Repositories provide the concrete cache loading and callback hooks. This
 * helper only coordinates the common sequence: notify failure, try cache if no
 * feature data is available, and throw only when no usable data remains.
 */
public final class FeatureFetchFailureHandler {
    private FeatureFetchFailureHandler() {
    }

    public static void handle(
            FeatureFetchException failure,
            Consumer<Throwable> refreshFailureCallback,
            BooleanSupplier hasFeatureData,
            BooleanSupplier loadCachedFeatures
    ) throws FeatureFetchException {
        FeatureFetchException resolvedFailure = failure == null
                ? new FeatureFetchException(FeatureFetchException.FeatureFetchErrorCode.UNKNOWN)
                : failure;

        refreshFailureCallback.accept(
                resolvedFailure.getCause() == null ? resolvedFailure : resolvedFailure.getCause()
        );

        if (!hasFeatureData.getAsBoolean()) {
            loadCachedFeatures.getAsBoolean();
        }

        if (!hasFeatureData.getAsBoolean()) {
            throw resolvedFailure;
        }
    }
}
