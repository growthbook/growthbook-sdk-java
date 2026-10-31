package growthbook.sdk.java.repository;

import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.retry.FeatureFetchRetryPolicy;
import growthbook.sdk.java.sandbox.GbCacheManager;

import javax.annotation.Nullable;

class NativeJavaGbFeatureRepositoryContractTest extends HttpFeaturesRepositoryContractTest<NativeJavaGbFeatureRepository> {

    @Override
    NativeJavaGbFeatureRepository createRepository(String apiHost, String clientKey, @Nullable String decryptionKey,
                                                   @Nullable GbCacheManager cacheManager, FeatureFetchRetryPolicy retryPolicy) {
        return NativeJavaGbFeatureRepository.builder()
                .apiHost(apiHost)
                .clientKey(clientKey)
                .encryptionKey(decryptionKey)
                .refreshStrategy(FeatureRefreshStrategy.STALE_WHILE_REVALIDATE)
                .swrTtlSeconds(SWR_TTL_SECONDS)
                .isCacheDisabled(cacheManager == null)
                .cacheManager(cacheManager)
                .retryPolicy(retryPolicy)
                .build();
    }

    @Override
    void refresh(NativeJavaGbFeatureRepository repository) throws FeatureFetchException {
        repository.refreshFeatures();
    }

    @Override
    boolean isInitialized(NativeJavaGbFeatureRepository repository) {
        return repository.getInitialized().get();
    }

    @Override
    String savedGroupsJson(NativeJavaGbFeatureRepository repository) {
        return repository.getSavedGroupsJson();
    }

    @Override
    void shutdown(NativeJavaGbFeatureRepository repository) {
        repository.shutdown();
    }
}
