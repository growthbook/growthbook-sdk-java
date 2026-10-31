package growthbook.sdk.java.repository;

import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.retry.FeatureFetchRetryPolicy;
import growthbook.sdk.java.sandbox.GbCacheManager;
import okhttp3.OkHttpClient;

import javax.annotation.Nullable;

class GBFeaturesRepositoryContractTest extends HttpFeaturesRepositoryContractTest<GBFeaturesRepository> {

    @Override
    GBFeaturesRepository createRepository(String apiHost, String clientKey, @Nullable String decryptionKey,
                                          @Nullable GbCacheManager cacheManager, FeatureFetchRetryPolicy retryPolicy) {
        return GBFeaturesRepository.builder()
                .apiHost(apiHost)
                .clientKey(clientKey)
                .decryptionKey(decryptionKey)
                .refreshStrategy(FeatureRefreshStrategy.STALE_WHILE_REVALIDATE)
                .swrTtlSeconds(SWR_TTL_SECONDS)
                .okHttpClient(new OkHttpClient.Builder().retryOnConnectionFailure(false).build())
                .isCacheDisabled(cacheManager == null)
                .cacheManager(cacheManager)
                .retryPolicy(retryPolicy)
                .build();
    }

    @Override
    void refresh(GBFeaturesRepository repository) throws FeatureFetchException {
        repository.refreshFeatures();
    }

    @Override
    boolean isInitialized(GBFeaturesRepository repository) {
        return repository.getInitialized();
    }

    @Override
    String savedGroupsJson(GBFeaturesRepository repository) {
        return repository.getSavedGroupsJson();
    }

    @Override
    void shutdown(GBFeaturesRepository repository) {
        repository.shutdown();
    }
}
