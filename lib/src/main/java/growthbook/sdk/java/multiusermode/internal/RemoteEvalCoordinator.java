package growthbook.sdk.java.multiusermode.internal;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.model.Feature;
import growthbook.sdk.java.model.FeatureRefreshEvent;
import growthbook.sdk.java.model.RequestBodyForRemoteEval;
import growthbook.sdk.java.multiusermode.configurations.EvaluationContext;
import growthbook.sdk.java.multiusermode.configurations.GlobalContext;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.remoteeval.RemoteEvalCache;
import growthbook.sdk.java.remoteeval.RemoteEvalCacheKey;
import growthbook.sdk.java.remoteeval.RemoteEvalOptionsValidator;
import growthbook.sdk.java.remoteeval.RemoteEvalRequestBuilder;
import growthbook.sdk.java.remoteeval.RemoteEvalResponse;
import growthbook.sdk.java.remoteeval.RemoteEvalService;
import growthbook.sdk.java.repository.FeatureRefreshStrategy;
import growthbook.sdk.java.repository.GBFeaturesRepository;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Keeps remote-evaluation fetching, caching, SSE-based cache invalidation, and per-user
 * evaluation context creation outside of the public facade.
 *
 * <p>This is the remote-eval counterpart to {@link GlobalContextManager}/
 * {@link FeatureRepositoryProvider}: the {@code GrowthBookClient} facade delegates to it instead
 * of carrying remote-eval-specific state and control flow itself.
 */
@Slf4j
public final class RemoteEvalCoordinator {
    private static final int DEFAULT_SWR_TTL_SECONDS = 60;

    private final Options options;
    private final FeatureRefreshListenerRegistry refreshListeners;
    private final AtomicBoolean shutdown = new AtomicBoolean(false);
    private final AtomicBoolean ready = new AtomicBoolean(false);

    @Nullable
    private RemoteEvalService remoteEvalService;

    @Nullable
    private RemoteEvalCache remoteEvalCache;

    @Nullable
    private GBFeaturesRepository invalidationRepository;

    @Nullable
    private GlobalContext fallbackContext;

    /**
     * Creates a coordinator bound to the client options instance.
     *
     * @param options          client options used to drive remote evaluation
     * @param refreshListeners registry used to publish SSE-driven refresh events to subscribers
     */
    public RemoteEvalCoordinator(Options options, FeatureRefreshListenerRegistry refreshListeners) {
        this.options = options;
        this.refreshListeners = refreshListeners;
    }

    /**
     * Prepares the remote-eval service, cache, and (when configured) the SSE invalidation channel.
     *
     * @return {@code true} once the coordinator is ready to evaluate
     */
    public boolean initialize() {
        this.shutdown.set(false);
        return ensureReady();
    }

    /**
     * Creates an evaluation context for a user by fetching (or reusing a cached) remote response.
     * Falls back to an empty local context when the remote request fails or after shutdown.
     *
     * @param userContext per-user evaluation context
     * @return evaluation context used by feature and experiment evaluators
     */
    public EvaluationContext createEvaluationContext(UserContext userContext) {
        UserContext mergedUserContext = withMergedAttributes(userContext);
        if (this.shutdown.get()) {
            return fallbackEvaluationContext(mergedUserContext);
        }
        try {
            RemoteEvalResponse response = fetchResponse(mergedUserContext);
            GlobalContext remoteGlobalContext = buildGlobalContext(response.getFeatures(), response.getSavedGroups());
            return new EvaluationContext(remoteGlobalContext, mergedUserContext, new EvaluationContext.StackContext(), this.options);
        } catch (FeatureFetchException e) {
            log.warn("Remote evaluation request failed. Falling back to local feature context.", e);
            return fallbackEvaluationContext(mergedUserContext);
        }
    }

    /**
     * Warms the cache for a user context without producing an evaluation context.
     *
     * @param userContext per-user evaluation context
     * @return {@code true} when the response was fetched (or already cached)
     */
    public boolean preload(UserContext userContext) {
        if (this.shutdown.get()) {
            return false;
        }
        try {
            fetchResponse(withMergedAttributes(userContext));
            return true;
        } catch (FeatureFetchException e) {
            log.warn("Unable to preload remote evaluation response", e);
            return false;
        }
    }

    /**
     * Fetches a fresh response for an explicit payload, refreshes the fallback context, and clears
     * the cache so the next evaluation uses the latest data.
     *
     * @param requestBodyForRemoteEval remote evaluation request payload
     * @throws FeatureFetchException when the remote request fails
     */
    public void refresh(RequestBodyForRemoteEval requestBodyForRemoteEval) throws FeatureFetchException {
        RemoteEvalResponse response = getService().fetch(requestBodyForRemoteEval);
        this.fallbackContext = buildGlobalContext(response.getFeatures(), response.getSavedGroups());
        invalidateCache();
    }

    /**
     * Drops all cached remote responses; the next evaluation refetches per user context.
     */
    public void invalidateCache() {
        if (this.remoteEvalCache != null) {
            this.remoteEvalCache.invalidateAll();
        }
    }

    /**
     * Releases the service, cache, and SSE invalidation resources owned by this coordinator.
     */
    public void shutdown() {
        this.shutdown.set(true);
        this.ready.set(false);
        shutdownInvalidationRepository();
        if (this.remoteEvalCache != null) {
            this.remoteEvalCache.shutdown();
            this.remoteEvalCache = null;
        }
        if (this.remoteEvalService != null) {
            this.remoteEvalService.close();
            this.remoteEvalService = null;
        }
    }

    private boolean ensureReady() {
        if (this.ready.get()) {
            return true;
        }
        synchronized (this) {
            if (this.ready.get()) {
                return true;
            }
            RemoteEvalOptionsValidator.validate(this.options);
            getService();
            getCache();
            initSseInvalidationIfNeeded();
            fallbackContext();
            this.ready.set(true);
            return true;
        }
    }

    private void initSseInvalidationIfNeeded() {
        if (this.options.getRefreshStrategy() != FeatureRefreshStrategy.SERVER_SENT_EVENTS
                || this.invalidationRepository != null) {
            return;
        }

        GBFeaturesRepository repository = GBFeaturesRepository.builder()
                .apiHost(this.options.getApiHost())
                .clientKey(this.options.getClientKey())
                .refreshStrategy(FeatureRefreshStrategy.SERVER_SENT_EVENTS)
                .isCacheDisabled(true)
                .retryPolicy(this.options.getRetryPolicy())
                .build();
        repository.addFeatureRefreshListener(this::handleInvalidationRefresh);
        this.invalidationRepository = repository;

        try {
            repository.initialize(true);
        } catch (FeatureFetchException e) {
            log.warn("Remote evaluation SSE invalidation could not be initialized", e);
        }
    }

    private void handleInvalidationRefresh(FeatureRefreshEvent event) {
        if (event.isSuccessful()) {
            invalidateCache();
        }
        this.refreshListeners.publish(event);
    }

    private void shutdownInvalidationRepository() {
        if (this.invalidationRepository == null) {
            return;
        }
        this.invalidationRepository.shutdown();
        this.invalidationRepository = null;
    }

    private EvaluationContext fallbackEvaluationContext(UserContext userContext) {
        return new EvaluationContext(fallbackContext(), userContext, new EvaluationContext.StackContext(), this.options);
    }

    private UserContext withMergedAttributes(UserContext userContext) {
        JsonObject merged = new JsonObject();
        if (this.options.getGlobalAttributes() != null) {
            merged = GrowthBookJsonUtils.getInstance().gson.fromJson(this.options.getGlobalAttributes(), JsonObject.class);
            if (merged == null) {
                merged = new JsonObject();
            }
        }
        JsonObject userAttributes = userContext.getAttributes();
        if (userAttributes != null) {
            for (Map.Entry<String, JsonElement> entry : userAttributes.entrySet()) {
                merged.add(entry.getKey(), entry.getValue());
            }
        }
        return userContext.withAttributes(merged);
    }

    private RemoteEvalResponse fetchResponse(UserContext userContext) throws FeatureFetchException {
        ensureReady();

        Map<String, Integer> forcedVariations = mergeForcedVariations(userContext);
        Map<String, Object> forcedFeatures = mergeForcedFeatures(userContext);
        String url = userContext.getUrl() == null ? this.options.getUrl() : userContext.getUrl();
        url = RemoteEvalRequestBuilder.normalizeUrl(url);
        String cacheKey = RemoteEvalCacheKey.fromContext(
                this.options.getApiHost(),
                this.options.getClientKey(),
                userContext.getAttributes(),
                forcedVariations,
                forcedFeatures,
                url,
                this.options.getCacheKeyAttributes()
        );

        RequestBodyForRemoteEval requestBody = RemoteEvalRequestBuilder.build(
                userContext.getAttributes(),
                forcedFeatures,
                forcedVariations,
                url
        );
        return getCache().get(cacheKey, requestBody);
    }

    private synchronized RemoteEvalService getService() {
        if (this.remoteEvalService == null) {
            this.remoteEvalService = new RemoteEvalService(this.options.getApiHost(), this.options.getClientKey());
        }
        return this.remoteEvalService;
    }

    private synchronized RemoteEvalCache getCache() {
        if (this.remoteEvalCache == null) {
            this.remoteEvalCache = new RemoteEvalCache(
                    getService(),
                    RemoteEvalRequestBuilder.normalizeCacheSize(this.options.getRemoteEvalCacheSize()),
                    secondsToDuration(this.options.getSwrTtlSeconds() == null ? DEFAULT_SWR_TTL_SECONDS : this.options.getSwrTtlSeconds()),
                    secondsToDuration(this.options.getRemoteEvalCacheTtlSeconds())
            );
        }
        return this.remoteEvalCache;
    }

    private static Duration secondsToDuration(@Nullable Integer seconds) {
        return seconds == null ? null : Duration.ofSeconds(seconds);
    }

    private GlobalContext fallbackContext() {
        if (this.fallbackContext == null) {
            this.fallbackContext = buildGlobalContext(Collections.emptyMap(), new JsonObject());
        }
        return this.fallbackContext;
    }

    private GlobalContext buildGlobalContext(Map<String, Feature<?>> features, JsonObject savedGroups) {
        return GlobalContext.builder()
                .features(features == null ? Collections.emptyMap() : features)
                .savedGroups(savedGroups == null ? new JsonObject() : savedGroups)
                .enabled(this.options.getEnabled())
                .qaMode(this.options.getIsQaMode())
                .forcedFeatureValues(this.options.getGlobalForcedFeatureValues())
                .forcedVariations(this.options.getGlobalForcedVariationsMap())
                .build();
    }

    private Map<String, Integer> mergeForcedVariations(UserContext userContext) {
        Map<String, Integer> forcedVariations = new HashMap<>();
        if (this.options.getGlobalForcedVariationsMap() != null) {
            forcedVariations.putAll(this.options.getGlobalForcedVariationsMap());
        }
        if (userContext.getForcedVariationsMap() != null) {
            forcedVariations.putAll(userContext.getForcedVariationsMap());
        }
        return forcedVariations;
    }

    private Map<String, Object> mergeForcedFeatures(UserContext userContext) {
        Map<String, Object> forcedFeatures = new HashMap<>();
        if (this.options.getGlobalForcedFeatureValues() != null) {
            forcedFeatures.putAll(this.options.getGlobalForcedFeatureValues());
        }
        if (userContext.getForcedFeatureValues() != null) {
            forcedFeatures.putAll(userContext.getForcedFeatureValues());
        }
        return forcedFeatures;
    }
}
