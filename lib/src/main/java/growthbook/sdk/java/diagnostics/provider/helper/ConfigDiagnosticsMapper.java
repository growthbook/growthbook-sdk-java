package growthbook.sdk.java.diagnostics.provider.helper;

import static growthbook.sdk.java.constants.SDKConstants.Endpoints.DEFAULT_API_HOST;
import static growthbook.sdk.java.constants.SDKConstants.Endpoints.FEATURES_ENDPOINT_PATH;
import static growthbook.sdk.java.constants.SDKConstants.Endpoints.STREAMING_ENDPOINT_PATH;

import growthbook.sdk.java.constants.SDKConstants;
import growthbook.sdk.java.diagnostics.model.ConfigDiagnostics;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.repository.GBFeaturesRepository;
import growthbook.sdk.java.retry.FeatureFetchRetryPolicy;

import javax.annotation.Nullable;
import java.time.Duration;

/**
 * Builds sanitized configuration diagnostics without exposing SDK secrets.
 */
public final class ConfigDiagnosticsMapper {

    public ConfigDiagnostics map(Options options, @Nullable GBFeaturesRepository repository) {
        DiagnosticsSecretMasker secretMasker = DiagnosticsSecretMasker.from(options, repository);
        String apiHost = apiHost(options, repository);
        String clientKey = clientKey(options, repository);

        return ConfigDiagnostics.builder()
                .apiHost(secretMasker.mask(apiHost))
                .featuresEndpoint(secretMasker.maskEndpoint(featuresEndpoint(apiHost, clientKey, repository)))
                .eventsEndpoint(secretMasker.maskEndpoint(eventsEndpoint(apiHost, clientKey, repository)))
                .clientKeyMasked(secretMasker.maskClientKey(clientKey))
                .encryptionConfigured(encryptionConfigured(options, repository))
                .swrTtlSeconds(swrTtlSeconds(options, repository))
                .backgroundFetchIntervalMillis(durationMillis(backgroundFetchInterval(options, repository)))
                .retryMaxAttempts(retryMaxAttempts(options, repository))
                .enabled(Boolean.TRUE.equals(options.getEnabled()))
                .qaMode(Boolean.TRUE.equals(options.getIsQaMode()))
                .stickyBucketingEnabled(options.getStickyBucketService() != null)
                .build();
    }

    private String apiHost(Options options, @Nullable GBFeaturesRepository repository) {
        String repositoryApiHost = repositoryApiHost(repository);
        if (repositoryApiHost != null) {
            return repositoryApiHost;
        }
        return options.getApiHost() == null ? DEFAULT_API_HOST : options.getApiHost();
    }

    @Nullable
    private String repositoryApiHost(@Nullable GBFeaturesRepository repository) {
        if (repository == null || repository.getFeaturesEndpoint() == null) {
            return null;
        }

        int markerIndex = repository.getFeaturesEndpoint().indexOf(FEATURES_ENDPOINT_PATH);
        if (markerIndex <= 0) {
            return null;
        }
        return repository.getFeaturesEndpoint().substring(0, markerIndex);
    }

    @Nullable
    private String clientKey(Options options, @Nullable GBFeaturesRepository repository) {
        if (options.getClientKey() != null) {
            return options.getClientKey();
        }
        return repositoryClientKey(repository);
    }

    @Nullable
    private String repositoryClientKey(@Nullable GBFeaturesRepository repository) {
        if (repository == null || repository.getFeaturesEndpoint() == null) {
            return null;
        }
        int markerIndex = repository.getFeaturesEndpoint().indexOf(FEATURES_ENDPOINT_PATH);
        if (markerIndex < 0) {
            return null;
        }
        return repository.getFeaturesEndpoint().substring(markerIndex + FEATURES_ENDPOINT_PATH.length());
    }

    @Nullable
    private String featuresEndpoint(
            String apiHost,
            @Nullable String clientKey,
            @Nullable GBFeaturesRepository repository
    ) {
        if (repository != null && repository.getFeaturesEndpoint() != null) {
            return repository.getFeaturesEndpoint();
        }
        return clientKey == null ? null : apiHost + FEATURES_ENDPOINT_PATH + clientKey;
    }

    @Nullable
    private String eventsEndpoint(
            String apiHost,
            @Nullable String clientKey,
            @Nullable GBFeaturesRepository repository
    ) {
        if (repository != null && repository.getEventsEndpoint() != null) {
            return repository.getEventsEndpoint();
        }
        return clientKey == null ? null : apiHost + STREAMING_ENDPOINT_PATH + clientKey;
    }

    private boolean encryptionConfigured(Options options, @Nullable GBFeaturesRepository repository) {
        if (repository != null) {
            return repository.getDecryptionKey() != null;
        }
        return options.getDecryptionKey() != null;
    }

    private int swrTtlSeconds(Options options, @Nullable GBFeaturesRepository repository) {
        if (repository != null && repository.getSwrTtlSeconds() != null) {
            return repository.getSwrTtlSeconds();
        }
        return options.getSwrTtlSeconds() == null ? SDKConstants.DEFAULT_SWR_TTL_SECONDS : options.getSwrTtlSeconds();
    }

    @Nullable
    private Duration backgroundFetchInterval(Options options, @Nullable GBFeaturesRepository repository) {
        if (repository != null && repository.getBackgroundFetchInterval() != null) {
            return repository.getBackgroundFetchInterval();
        }
        return options.getBackgroundFetchInterval();
    }

    private int retryMaxAttempts(Options options, @Nullable GBFeaturesRepository repository) {
        FeatureFetchRetryPolicy retryPolicy = repository != null && repository.getRetryPolicy() != null
                ? repository.getRetryPolicy()
                : options.getRetryPolicy();
        return retryPolicy == null ? FeatureFetchRetryPolicy.DEFAULT_MAX_ATTEMPTS : retryPolicy.getMaxAttempts();
    }

    @Nullable
    private Long durationMillis(@Nullable Duration duration) {
        return duration == null ? null : duration.toMillis();
    }

}
