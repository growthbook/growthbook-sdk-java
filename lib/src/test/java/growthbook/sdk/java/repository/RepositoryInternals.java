package growthbook.sdk.java.repository;

import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.repository.internal.FeatureRefreshEngine;
import growthbook.sdk.java.repository.internal.OkHttpFeatureTransport;

/**
 * Test access to a {@link GBFeaturesRepository}'s refresh engine, without reflection.
 */
public final class RepositoryInternals {

    private RepositoryInternals() {
    }

    public static FeatureRefreshEngine engine(GBFeaturesRepository repository) {
        return repository.engine();
    }

    public static boolean applyResponse(GBFeaturesRepository repository, String responseJson, boolean fromCache)
            throws FeatureFetchException {
        return repository.engine().applyResponse(responseJson, fromCache);
    }

    public static OkHttpFeatureTransport transport(GBFeaturesRepository repository) {
        return (OkHttpFeatureTransport) repository.engine().transport();
    }
}
