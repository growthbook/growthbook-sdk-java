package growthbook.sdk.java.diagnostics.provider;

import growthbook.sdk.java.constants.SDKConstants;
import growthbook.sdk.java.Version;
import growthbook.sdk.java.diagnostics.model.CacheDiagnostics;
import growthbook.sdk.java.diagnostics.model.enums.CacheState;
import growthbook.sdk.java.diagnostics.model.ClientDiagnostics;
import growthbook.sdk.java.diagnostics.model.ConfigDiagnostics;
import growthbook.sdk.java.diagnostics.model.Diagnostics;
import growthbook.sdk.java.diagnostics.model.ErrorDiagnostics;
import growthbook.sdk.java.diagnostics.model.enums.FeatureDataSource;
import growthbook.sdk.java.diagnostics.model.FeatureDiagnostics;
import growthbook.sdk.java.diagnostics.model.HealthDiagnostics;
import growthbook.sdk.java.diagnostics.model.RefreshDiagnostics;
import growthbook.sdk.java.diagnostics.model.RemoteEvalDiagnostics;
import growthbook.sdk.java.diagnostics.model.SdkDiagnostics;
import growthbook.sdk.java.diagnostics.model.StreamingDiagnostics;
import growthbook.sdk.java.diagnostics.provider.helper.ConfigDiagnosticsMapper;
import growthbook.sdk.java.diagnostics.provider.helper.DiagnosticsHealthResolver;
import growthbook.sdk.java.diagnostics.provider.helper.DiagnosticsSecretMasker;
import growthbook.sdk.java.diagnostics.provider.helper.ErrorDiagnosticsMapper;
import growthbook.sdk.java.diagnostics.provider.helper.FeatureDiagnosticsMapper;
import growthbook.sdk.java.diagnostics.provider.helper.StreamingStateResolveHelper;
import growthbook.sdk.java.diagnostics.provider.model.DiagnosticsCollectionContext;
import growthbook.sdk.java.diagnostics.provider.model.DiagnosticsSnapshot;
import growthbook.sdk.java.diagnostics.provider.model.StreamingStatusSnapshot;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.repository.FeatureRefreshStrategy;
import growthbook.sdk.java.repository.GBFeaturesRepository;
import growthbook.sdk.java.sandbox.CacheMode;

import javax.annotation.Nullable;

/**
 * Provides read-only diagnostic snapshots from a multi-user {@code GrowthBookClient}.
 */
public final class GrowthBookClientDiagnosticsProvider implements DiagnosticsProvider {

    /**
     * Read-only view over the client state required to build diagnostics snapshots.
     */
    public interface ClientState {

        @Nullable
        GBFeaturesRepository currentRepository();

        @Nullable
        Throwable lastInitializationError();

        long lastInitializationErrorAtMillis();

        boolean isRemoteEvalReady();

        boolean isShutdown();

        boolean isRemoteEvalCacheConfigured();

        int fallbackFeatureCount();
    }

    private final Options options;
    private final ClientState clientState;
    private final ErrorDiagnosticsMapper errorDiagnosticsMapper = new ErrorDiagnosticsMapper();
    private final ConfigDiagnosticsMapper configDiagnosticsMapper = new ConfigDiagnosticsMapper();
    private final FeatureDiagnosticsMapper featureDiagnosticsMapper = new FeatureDiagnosticsMapper();
    private final DiagnosticsHealthResolver diagnosticsHealthResolver = new DiagnosticsHealthResolver();
    private final StreamingStateResolveHelper streamingStateResolver = new StreamingStateResolveHelper();

    public GrowthBookClientDiagnosticsProvider(Options options, ClientState clientState) {
        this.options = options;
        this.clientState = clientState;
    }

    @Override
    public Diagnostics getDiagnostics() {
        DiagnosticsSnapshot snapshot = collectSnapshot(collectionContext());
        return diagnostics(snapshot);
    }

    private DiagnosticsCollectionContext collectionContext() {
        GBFeaturesRepository repository = this.clientState.currentRepository();
        return DiagnosticsCollectionContext.builder()
                .generatedAtMillis(System.currentTimeMillis())
                .repository(repository)
                .secretMasker(DiagnosticsSecretMasker.from(this.options, repository))
                .build();
    }

    private DiagnosticsSnapshot collectSnapshot(DiagnosticsCollectionContext context) {
        GBFeaturesRepository repository = context.getRepository();

        return DiagnosticsSnapshot.builder()
                .generatedAtMillis(context.getGeneratedAtMillis())
                .config(configDiagnostics(repository))
                .client(clientDiagnostics(repository))
                .features(featureDiagnostics(repository))
                .refresh(refreshDiagnostics(context))
                .cache(cacheDiagnostics(context))
                .streaming(streamingDiagnostics(repository))
                .remoteEval(remoteEvalDiagnostics())
                .build();
    }

    private Diagnostics diagnostics(DiagnosticsSnapshot snapshot) {
        return Diagnostics.builder()
                .generatedAtMillis(snapshot.getGeneratedAtMillis())
                .sdk(sdkDiagnostics())
                .health(healthDiagnostics(snapshot))
                .config(snapshot.getConfig())
                .client(snapshot.getClient())
                .features(snapshot.getFeatures())
                .refresh(snapshot.getRefresh())
                .cache(snapshot.getCache())
                .streaming(snapshot.getStreaming())
                .remoteEval(snapshot.getRemoteEval())
                .build();
    }

    private HealthDiagnostics healthDiagnostics(DiagnosticsSnapshot snapshot) {
        return this.diagnosticsHealthResolver.resolve(snapshot);
    }

    private ConfigDiagnostics configDiagnostics(GBFeaturesRepository repository) {
        return this.configDiagnosticsMapper.map(this.options, repository);
    }

    private SdkDiagnostics sdkDiagnostics() {
        return SdkDiagnostics.builder()
                .version(Version.SDK_VERSION)
                .build();
    }

    private ClientDiagnostics clientDiagnostics(GBFeaturesRepository repository) {
        boolean remoteEvalEnabled = this.options.isRemoteEvalEnabled();
        boolean initialized = remoteEvalEnabled
                ? this.clientState.isRemoteEvalReady()
                : repository != null && Boolean.TRUE.equals(repository.getInitialized());

        return ClientDiagnostics.builder()
                .initialized(initialized)
                .shutdown(this.clientState.isShutdown())
                .remoteEvalEnabled(remoteEvalEnabled)
                .build();
    }

    private FeatureDiagnostics featureDiagnostics(GBFeaturesRepository repository) {
        if (this.options.isRemoteEvalEnabled()) {
            int fallbackFeatureCount = this.clientState.fallbackFeatureCount();
            return this.featureDiagnosticsMapper.fromCount(
                    FeatureDataSource.REMOTE_EVAL_FALLBACK,
                    fallbackFeatureCount > 0,
                    fallbackFeatureCount
            );
        }

        if (repository != null) {
            return this.featureDiagnosticsMapper.fromFeatureMap(
                    FeatureDataSource.LOCAL_REPOSITORY,
                    repository.hasFeatureData(),
                    repository.getActiveFeatureCount(),
                    repository.getParsedFeatures()
            );
        }

        return this.featureDiagnosticsMapper.unavailable();
    }

    private RefreshDiagnostics refreshDiagnostics(DiagnosticsCollectionContext context) {
        GBFeaturesRepository repository = context.getRepository();
        long generatedAtMillis = context.getGeneratedAtMillis();
        Long lastSuccessfulRefreshAtMillis = lastSuccessfulRefreshAtMillis(repository);
        Boolean lastLoadedFromCache = lastRefreshLoadedFromCache(repository);
        Long nextCacheExpiresAtMillis = nextCacheExpiresAtMillis(repository, lastLoadedFromCache);
        Long millisUntilCacheExpiry = millisUntil(nextCacheExpiresAtMillis, generatedAtMillis);

        return RefreshDiagnostics.builder()
                .strategy(refreshStrategy(repository))
                .lastSuccessfulRefreshAtMillis(lastSuccessfulRefreshAtMillis)
                .lastSuccessfulRefreshAgeMillis(ageMillis(lastSuccessfulRefreshAtMillis, generatedAtMillis))
                .nextCacheExpiresAtMillis(nextCacheExpiresAtMillis)
                .millisUntilCacheExpiry(millisUntilCacheExpiry)
                .stale(millisUntilCacheExpiry != null && millisUntilCacheExpiry < 0)
                .successCount(refreshSuccessCount(repository))
                .failureCount(refreshFailureCount(repository))
                .consecutiveFailures(refreshConsecutiveFailures(repository))
                .lastFailureAtMillis(lastRefreshFailureAtMillis(repository))
                .lastLoadedFromCache(lastLoadedFromCache)
                .lastError(lastRefreshError(repository, context.getSecretMasker()))
                .build();
    }

    private CacheDiagnostics cacheDiagnostics(DiagnosticsCollectionContext context) {
        GBFeaturesRepository repository = context.getRepository();
        boolean cacheEnabled = cacheEnabled(repository);
        Long lastUpdatedMillis = cacheLastUpdatedMillis(repository);

        return CacheDiagnostics.builder()
                .state(cacheState(repository, cacheEnabled, lastUpdatedMillis))
                .enabled(cacheEnabled)
                .mode(this.options.getCacheMode())
                .lastUpdatedMillis(timestampOrUnknown(lastUpdatedMillis))
                .ageMillis(ageMillis(lastUpdatedMillis, context.getGeneratedAtMillis()))
                .build();
    }

    private Long lastSuccessfulRefreshAtMillis(GBFeaturesRepository repository) {
        if (repository == null) {
            return null;
        }
        return zeroAsNull(repository.getLastSuccessfulFetchAtMillis());
    }

    private Long nextCacheExpiresAtMillis(GBFeaturesRepository repository, Boolean lastLoadedFromCache) {
        if (Boolean.TRUE.equals(lastLoadedFromCache)) {
            Long cacheLastUpdatedMillis = cacheLastUpdatedMillis(repository);
            if (cacheLastUpdatedMillis != null && cacheLastUpdatedMillis > 0) {
                return cacheLastUpdatedMillis + swrTtlMillis(repository);
            }
        }
        if (repository == null || repository.getExpiresAt() == null) {
            return null;
        }
        return repository.getExpiresAt() * 1000L;
    }

    private long swrTtlMillis(GBFeaturesRepository repository) {
        Integer ttlSeconds = repository == null ? this.options.getSwrTtlSeconds() : repository.getSwrTtlSeconds();
        int resolvedTtlSeconds = ttlSeconds == null ? SDKConstants.DEFAULT_SWR_TTL_SECONDS : ttlSeconds;
        return resolvedTtlSeconds * 1000L;
    }

    private long refreshSuccessCount(GBFeaturesRepository repository) {
        return repository == null ? 0L : repository.getRefreshSuccessCount();
    }

    private long refreshFailureCount(GBFeaturesRepository repository) {
        return repository == null ? 0L : repository.getRefreshFailureCount();
    }

    private long refreshConsecutiveFailures(GBFeaturesRepository repository) {
        return repository == null ? 0L : repository.getRefreshConsecutiveFailureCount();
    }

    private Long lastRefreshFailureAtMillis(GBFeaturesRepository repository) {
        if (repository == null) {
            return null;
        }
        return zeroAsNull(repository.getLastRefreshFailureAtMillis());
    }

    private Boolean lastRefreshLoadedFromCache(GBFeaturesRepository repository) {
        return repository == null ? null : repository.getLastRefreshLoadedFromCache();
    }

    private boolean cacheEnabled(GBFeaturesRepository repository) {
        if (repository == null) {
            return !Boolean.TRUE.equals(this.options.getIsCacheDisabled())
                    && this.options.getCacheMode() != CacheMode.NONE;
        }
        return !repository.isCacheDisabled();
    }

    private Long cacheLastUpdatedMillis(GBFeaturesRepository repository) {
        if (repository == null) {
            return null;
        }
        return repository.getCacheLastUpdatedMillis();
    }

    private CacheState cacheState(GBFeaturesRepository repository, boolean cacheEnabled, Long lastUpdatedMillis) {
        if (!cacheEnabled) {
            return CacheState.DISABLED;
        }
        if (repository == null) {
            return CacheState.NOT_INITIALIZED;
        }
        if (lastUpdatedMillis != null && lastUpdatedMillis > 0) {
            return CacheState.AVAILABLE;
        }
        return CacheState.UNKNOWN;
    }

    private StreamingDiagnostics streamingDiagnostics(GBFeaturesRepository repository) {
        StreamingStatusSnapshot streamingStatus = streamingStatus(repository);
        return StreamingDiagnostics.builder()
                .state(this.streamingStateResolver.resolve(streamingStatus))
                .reconnectAttempts(streamingStatus.getReconnectAttempts())
                .build();
    }

    private StreamingStatusSnapshot streamingStatus(GBFeaturesRepository repository) {
        boolean initialized = this.options.isRemoteEvalEnabled()
                ? this.clientState.isRemoteEvalReady()
                : repository != null && Boolean.TRUE.equals(repository.getInitialized());

        return StreamingStatusSnapshot.builder()
                .configured(isStreamingConfigured())
                .allowed(repository != null && repository.isSseAllowed())
                .connected(repository != null && repository.isSseConnected())
                .reconnectAttempts(repository == null ? 0 : repository.getSseRetryAttempts())
                .initialized(initialized)
                .shutdown(this.clientState.isShutdown())
                .build();
    }

    private boolean isStreamingConfigured() {
        return this.options.getRefreshingStrategy() == FeatureRefreshStrategy.SERVER_SENT_EVENTS;
    }

    private RemoteEvalDiagnostics remoteEvalDiagnostics() {
        return RemoteEvalDiagnostics.builder()
                .enabled(this.options.isRemoteEvalEnabled())
                .ready(this.clientState.isRemoteEvalReady())
                .cacheConfigured(this.clientState.isRemoteEvalCacheConfigured())
                .cacheMaxSize(this.options.getRemoteEvalCacheSize())
                .cacheTtlSeconds(this.options.getRemoteEvalCacheTtlSeconds())
                .build();
    }

    private FeatureRefreshStrategy refreshStrategy(GBFeaturesRepository repository) {
        if (repository != null && repository.getRefreshStrategy() != null) {
            return repository.getRefreshStrategy();
        }
        return this.options.getRefreshingStrategy();
    }

    private ErrorDiagnostics lastRefreshError(
            GBFeaturesRepository repository,
            DiagnosticsSecretMasker secretMasker
    ) {
        Long lastRefreshErrorAtMillis = repository == null ? null : repository.getLastRefreshErrorAtMillis();
        if (lastRefreshErrorAtMillis != null && lastRefreshErrorAtMillis > 0) {
            return this.errorDiagnosticsMapper.map(repository.getLastRefreshError(), lastRefreshErrorAtMillis, secretMasker);
        }

        Throwable initializationError = this.clientState.lastInitializationError();
        long initializationErrorAtMillis = this.clientState.lastInitializationErrorAtMillis();
        if (initializationError == null || initializationErrorAtMillis <= 0) {
            return null;
        }

        return this.errorDiagnosticsMapper.map(initializationError, initializationErrorAtMillis, secretMasker);
    }

    private Long zeroAsNull(long timestampMillis) {
        return timestampMillis <= 0 ? null : timestampMillis;
    }

    private long timestampOrUnknown(Long timestampMillis) {
        return timestampMillis == null ? 0L : Math.max(0L, timestampMillis);
    }

    private Long ageMillis(Long timestampMillis, long generatedAtMillis) {
        if (timestampMillis == null || timestampMillis <= 0) {
            return null;
        }
        return Math.max(0L, generatedAtMillis - timestampMillis);
    }

    private Long millisUntil(Long timestampMillis, long generatedAtMillis) {
        return timestampMillis == null ? null : timestampMillis - generatedAtMillis;
    }
}
