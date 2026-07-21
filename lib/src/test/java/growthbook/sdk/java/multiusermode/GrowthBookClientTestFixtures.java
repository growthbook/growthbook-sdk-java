package growthbook.sdk.java.multiusermode;

import com.google.gson.JsonObject;
import growthbook.sdk.java.callback.FeatureRefreshCallback;
import growthbook.sdk.java.model.Feature;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.repository.FeatureRefreshStrategy;
import growthbook.sdk.java.repository.GBFeaturesRepository;

import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

final class GrowthBookClientTestFixtures {

    private GrowthBookClientTestFixtures() {
    }

    static GBFeaturesRepository createMockRepository() {
        GBFeaturesRepository repository = mock(GBFeaturesRepository.class);
        HashMap<String, Feature<?>> features = new HashMap<>();
        long nowMillis = System.currentTimeMillis();
        features.put("test-feature", new Feature<>());
        when(repository.getInitialized()).thenReturn(true);
        when(repository.getFeaturesEndpoint()).thenReturn("https://custom.growthbook.io/api/features/custom_key");
        when(repository.getEventsEndpoint()).thenReturn("https://custom.growthbook.io/sub/custom_key");
        when(repository.getDecryptionKey()).thenReturn("test_key");
        when(repository.getSwrTtlSeconds()).thenReturn(60);
        when(repository.getFeaturesJson()).thenReturn("{}");
        when(repository.getSavedGroupsJson()).thenReturn("{}");
        when(repository.getParsedFeatures()).thenReturn(features);
        when(repository.getParsedSavedGroups()).thenReturn(new JsonObject());
        when(repository.getRefreshStrategy()).thenReturn(FeatureRefreshStrategy.STALE_WHILE_REVALIDATE);
        when(repository.hasFeatureData()).thenReturn(true);
        when(repository.getActiveFeatureCount()).thenReturn(features.size());
        when(repository.getLastSuccessfulFetchAtMillis()).thenReturn(nowMillis - TimeUnit.SECONDS.toMillis(1));
        when(repository.getExpiresAt()).thenReturn(TimeUnit.MILLISECONDS.toSeconds(nowMillis + TimeUnit.SECONDS.toMillis(60)));
        when(repository.isCacheDisabled()).thenReturn(false);
        when(repository.getCacheLastUpdatedMillis()).thenReturn(nowMillis - 500L);
        when(repository.getRefreshSuccessCount()).thenReturn(1L);
        when(repository.getRefreshFailureCount()).thenReturn(0L);
        when(repository.getRefreshConsecutiveFailureCount()).thenReturn(0L);
        when(repository.getLastRefreshFailureAtMillis()).thenReturn(0L);
        when(repository.getLastRefreshLoadedFromCache()).thenReturn(false);
        when(repository.isSseAllowed()).thenReturn(true);
        when(repository.isSseConnected()).thenReturn(false);
        when(repository.getSseRetryAttempts()).thenReturn(0);
        return repository;
    }

    static GBFeaturesRepository.GBFeaturesRepositoryBuilder createMockBuilder(GBFeaturesRepository repository) {
        GBFeaturesRepository.GBFeaturesRepositoryBuilder builder =
                mock(GBFeaturesRepository.GBFeaturesRepositoryBuilder.class);

        when(builder.apiHost(anyString())).thenReturn(builder);
        when(builder.clientKey(anyString())).thenReturn(builder);
        when(builder.decryptionKey(anyString())).thenReturn(builder);
        when(builder.refreshStrategy(any())).thenReturn(builder);
        when(builder.swrTtlSeconds(any())).thenReturn(builder);
        when(builder.isCacheDisabled(anyBoolean())).thenReturn(builder);
        when(builder.requestBodyForRemoteEval(any())).thenReturn(builder);
        when(builder.cacheManager(any())).thenReturn(builder);
        when(builder.backgroundFetchInterval(any())).thenReturn(builder);
        when(builder.retryPolicy(any())).thenReturn(builder);
        when(builder.build()).thenReturn(repository);

        return builder;
    }

    static Options createDefaultOptions(FeatureRefreshCallback callback) {
        return Options.builder()
                .apiHost("https://custom.growthbook.io")
                .clientKey("custom_key")
                .decryptionKey("test_key")
                .refreshStrategy(FeatureRefreshStrategy.STALE_WHILE_REVALIDATE)
                .featureRefreshCallback(callback)
                .build();
    }
}
