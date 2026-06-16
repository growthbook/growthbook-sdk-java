package growthbook.sdk.java.multiusermode.internal;

import growthbook.sdk.java.model.RequestBodyForRemoteEval;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.repository.GBFeaturesRepository;
import growthbook.sdk.java.sandbox.CacheManagerFactory;
import growthbook.sdk.java.sandbox.CacheMode;
import growthbook.sdk.java.sandbox.GbCacheManager;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Internal factory for repositories used by {@code GrowthBookClient}.
 * Keeps repository construction and option mapping out of the public client facade.
 */
public final class GrowthBookClientRepositoryFactory {

    /**
     * Creates a repository from client options.
     *
     * @param options client options
     * @return configured feature repository
     */
    public GBFeaturesRepository create(Options options) {
        GbCacheManager cacheManager = options.getCacheManager() != null
                ? options.getCacheManager()
                : CacheManagerFactory.create(options.getCacheMode(), options.getCacheDirectory());

        return GBFeaturesRepository.builder()
                .apiHost(options.getApiHost())
                .clientKey(options.getClientKey())
                .decryptionKey(options.getDecryptionKey())
                .refreshStrategy(options.getRefreshStrategy())
                .swrTtlSeconds(options.getSwrTtlSeconds())
                .isCacheDisabled(options.getIsCacheDisabled() || options.getCacheMode() == CacheMode.NONE)
                .cacheManager(cacheManager)
                .requestBodyForRemoteEval(configurePayloadForRemoteEval(options))
                .build();
    }

    private RequestBodyForRemoteEval configurePayloadForRemoteEval(Options options) {
        List<List<Object>> forceFeaturesForPayload = new ArrayList<>();
        if (options.getGlobalForcedFeatureValues() != null) {
            forceFeaturesForPayload = options.getGlobalForcedFeatureValues().entrySet().stream()
                    .map(entry -> Arrays.asList(entry.getKey(), entry.getValue()))
                    .collect(Collectors.toList());
        }
        return new RequestBodyForRemoteEval(
                options.getGlobalAttributes(),
                forceFeaturesForPayload,
                options.getGlobalForcedVariationsMap(),
                options.getUrl()
        );
    }
}
