package growthbook.sdk.java.remoteeval;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import growthbook.sdk.java.GrowthBook;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.GBContext;
import growthbook.sdk.java.model.RequestBodyForRemoteEval;
import growthbook.sdk.java.multiusermode.GrowthBookClient;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.multiusermode.usage.TrackingCallbackWithUser;
import growthbook.sdk.java.repository.FeatureRefreshStrategy;
import growthbook.sdk.java.stickyBucketing.InMemoryStickyBucketServiceImpl;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteEvalSupportTest {
    private final GrowthBookJsonUtils jsonUtils = GrowthBookJsonUtils.getInstance();

    @Test
    @DisplayName("Posts the user context to the remote eval endpoint and evaluates the response")
    void growthBookClient_remoteEvalPostsUserContextAndEvaluatesResponse() throws Exception {
        try (RemoteEvalTestServer server = new RemoteEvalTestServer(featureResponse(true))) {
            GrowthBookClient client = new GrowthBookClient(Options.builder()
                    .apiHost(server.apiHost())
                    .clientKey("sdk-test")
                    .remoteEval(true)
                    .globalForcedVariationsMap(Collections.singletonMap("global-exp", 1))
                    .build());

            assertTrue(client.initialize());

            UserContext userContext = UserContext.builder()
                    .attributes(jsonObject("{\"id\":\"user-1\"}"))
                    .forcedVariationsMap(Collections.singletonMap("user-exp", 2))
                    .build();

            assertTrue(client.isOn("remote-feature", userContext));
            assertEquals(1, server.callCount());

            JsonObject requestBody = jsonObject(server.lastBody());
            assertEquals("user-1", requestBody.getAsJsonObject("attributes").get("id").getAsString());
            assertEquals(1, requestBody.getAsJsonObject("forcedVariations").get("global-exp").getAsInt());
            assertEquals(2, requestBody.getAsJsonObject("forcedVariations").get("user-exp").getAsInt());
            assertEquals(0, requestBody.getAsJsonArray("forcedFeatures").size());
            assertEquals("", requestBody.get("url").getAsString());
        }
    }

    @Test
    @DisplayName("Reuses the cached response for the same cache key")
    void growthBookClient_remoteEvalReusesCachedResponseForSameCacheKey() throws Exception {
        try (RemoteEvalTestServer server = new RemoteEvalTestServer(featureResponse(true))) {
            GrowthBookClient client = new GrowthBookClient(remoteEvalOptions(server));
            assertTrue(client.initialize());

            UserContext userContext = UserContext.builder()
                    .attributes(jsonObject("{\"id\":\"user-1\"}"))
                    .build();

            assertTrue(client.isOn("remote-feature", userContext));
            assertTrue(client.isOn("remote-feature", userContext));

            assertEquals(1, server.callCount());
        }
    }

    @Test
    @DisplayName("cacheKeyAttributes narrows the cache key to the listed attributes")
    void growthBookClient_cacheKeyAttributesCanNarrowRemoteEvalCacheKey() throws Exception {
        try (RemoteEvalTestServer server = new RemoteEvalTestServer(featureResponse(true))) {
            GrowthBookClient client = new GrowthBookClient(Options.builder()
                    .apiHost(server.apiHost())
                    .clientKey("sdk-test")
                    .remoteEval(true)
                    .cacheKeyAttributes(Collections.singletonList("id"))
                    .build());
            assertTrue(client.initialize());

            client.isOn("remote-feature", UserContext.builder()
                    .attributes(jsonObject("{\"id\":\"user-1\",\"plan\":\"free\"}"))
                    .build());
            client.isOn("remote-feature", UserContext.builder()
                    .attributes(jsonObject("{\"id\":\"user-1\",\"plan\":\"enterprise\"}"))
                    .build());

            assertEquals(1, server.callCount());
        }
    }

    @Test
    @DisplayName("An empty cacheKeyAttributes list excludes all attributes from the cache key")
    void growthBookClient_emptyCacheKeyAttributesExcludeAllAttributesFromCacheKey() throws Exception {
        try (RemoteEvalTestServer server = new RemoteEvalTestServer(featureResponse(true))) {
            GrowthBookClient client = new GrowthBookClient(Options.builder()
                    .apiHost(server.apiHost())
                    .clientKey("sdk-test")
                    .remoteEval(true)
                    .cacheKeyAttributes(Collections.emptyList())
                    .build());
            assertTrue(client.initialize());

            client.isOn("remote-feature", UserContext.builder()
                    .attributes(jsonObject("{\"id\":\"user-1\"}"))
                    .build());
            client.isOn("remote-feature", UserContext.builder()
                    .attributes(jsonObject("{\"id\":\"user-2\"}"))
                    .build());

            assertEquals(1, server.callCount());
        }
    }

    @Test
    @DisplayName("Fetches again when the default cache key changes")
    void growthBookClient_remoteEvalFetchesAgainWhenDefaultCacheKeyChanges() throws Exception {
        try (RemoteEvalTestServer server = new RemoteEvalTestServer(featureResponse(true))) {
            GrowthBookClient client = new GrowthBookClient(remoteEvalOptions(server));
            assertTrue(client.initialize());

            client.isOn("remote-feature", UserContext.builder()
                    .attributes(jsonObject("{\"id\":\"user-1\"}"))
                    .build());
            client.isOn("remote-feature", UserContext.builder()
                    .attributes(jsonObject("{\"id\":\"user-2\"}"))
                    .build());

            assertEquals(2, server.callCount());
        }
    }

    @Test
    @DisplayName("Coalesces concurrent identical requests into a single network call")
    void growthBookClient_remoteEvalCoalescesConcurrentIdenticalRequests() throws Exception {
        try (RemoteEvalTestServer server = new RemoteEvalTestServer(featureResponse(true), 200)) {
            GrowthBookClient client = new GrowthBookClient(remoteEvalOptions(server));
            assertTrue(client.initialize());

            UserContext userContext = UserContext.builder()
                    .attributes(jsonObject("{\"id\":\"user-1\"}"))
                    .build();

            ExecutorService executor = Executors.newFixedThreadPool(4);
            try {
                Future<Boolean> first = executor.submit(() -> client.preloadRemoteEval(userContext));
                Future<Boolean> second = executor.submit(() -> client.preloadRemoteEval(userContext));
                Future<Boolean> third = executor.submit(() -> client.preloadRemoteEval(userContext));

                assertTrue(first.get());
                assertTrue(second.get());
                assertTrue(third.get());
            } finally {
                executor.shutdownNow();
            }

            assertEquals(1, server.callCount());
        }
    }

    @Test
    @DisplayName("refreshFeature clears the remote eval cache")
    void growthBookClient_refreshFeatureClearsRemoteEvalCache() throws Exception {
        try (RemoteEvalTestServer server = new RemoteEvalTestServer(featureResponse(true))) {
            GrowthBookClient client = new GrowthBookClient(remoteEvalOptions(server));
            assertTrue(client.initialize());

            UserContext userContext = UserContext.builder()
                    .attributes(jsonObject("{\"id\":\"user-1\"}"))
                    .build();

            client.isOn("remote-feature", userContext);
            client.refreshFeature();
            client.isOn("remote-feature", userContext);

            assertEquals(2, server.callCount());
        }
    }

    @Test
    @DisplayName("Forced features are sent in the payload and contribute to the cache key")
    void growthBookClient_forcedFeaturesAreSentAndContributeToCacheKey() throws Exception {
        try (RemoteEvalTestServer server = new RemoteEvalTestServer(featureResponse(true))) {
            GrowthBookClient client = new GrowthBookClient(remoteEvalOptions(server));
            assertTrue(client.initialize());

            Map<String, Object> firstForcedFeatures = new HashMap<>();
            firstForcedFeatures.put("overridden-feature", true);
            Map<String, Object> secondForcedFeatures = new HashMap<>();
            secondForcedFeatures.put("overridden-feature", false);

            JsonObject attributes = jsonObject("{\"id\":\"user-1\"}");
            client.isOn("remote-feature", UserContext.builder()
                    .attributes(attributes)
                    .forcedFeatureValues(firstForcedFeatures)
                    .build());
            // Same user/url/forced variations but different forced feature values must not reuse the cached response.
            client.isOn("remote-feature", UserContext.builder()
                    .attributes(attributes)
                    .forcedFeatureValues(secondForcedFeatures)
                    .build());

            assertEquals(2, server.callCount());

            JsonArray forcedFeatures = jsonObject(server.lastBody()).getAsJsonArray("forcedFeatures");
            assertEquals(1, forcedFeatures.size());
            assertEquals("overridden-feature", forcedFeatures.get(0).getAsJsonArray().get(0).getAsString());
            assertFalse(forcedFeatures.get(0).getAsJsonArray().get(1).getAsBoolean());
        }
    }

    @Test
    @DisplayName("Reuses the cached response when forced feature values are identical")
    void growthBookClient_forcedFeaturesReuseCacheWhenIdentical() throws Exception {
        try (RemoteEvalTestServer server = new RemoteEvalTestServer(featureResponse(true))) {
            GrowthBookClient client = new GrowthBookClient(remoteEvalOptions(server));
            assertTrue(client.initialize());

            JsonObject attributes = jsonObject("{\"id\":\"user-1\"}");
            Map<String, Object> firstForcedFeatures = new HashMap<>();
            firstForcedFeatures.put("overridden-feature", true);
            Map<String, Object> sameForcedFeatures = new HashMap<>();
            sameForcedFeatures.put("overridden-feature", true);

            client.isOn("remote-feature", UserContext.builder()
                    .attributes(attributes)
                    .forcedFeatureValues(firstForcedFeatures)
                    .build());
            client.isOn("remote-feature", UserContext.builder()
                    .attributes(attributes)
                    .forcedFeatureValues(sameForcedFeatures)
                    .build());

            assertEquals(1, server.callCount());
        }
    }

    @Test
    @DisplayName("Rejects GrowthBook Cloud hosts for remote eval")
    void growthBookClient_remoteEvalRejectsGrowthBookCloudHost() {
        GrowthBookClient client = new GrowthBookClient(Options.builder()
                .apiHost("https://cdn.growthbook.io")
                .clientKey("sdk-test")
                .remoteEval(true)
                .build());

        assertFalse(client.initialize());

        assertFalse(new GrowthBookClient(Options.builder()
                .apiHost("cdn.growthbook.io")
                .clientKey("sdk-test")
                .remoteEval(true)
                .build()).initialize());

        assertFalse(new GrowthBookClient(Options.builder()
                .apiHost("GROWTHBOOK.IO")
                .clientKey("sdk-test")
                .remoteEval(true)
                .build()).initialize());
    }

    @Test
    @DisplayName("Rejects options incompatible with remote eval (missing clientKey, decryptionKey, sticky bucketing, SWR)")
    void growthBookClient_remoteEvalRejectsUnsupportedOptions() {
        assertFalse(new GrowthBookClient(Options.builder()
                .apiHost("http://127.0.0.1:1234")
                .remoteEval(true)
                .build()).initialize());

        assertFalse(new GrowthBookClient(Options.builder()
                .apiHost("http://127.0.0.1:1234")
                .clientKey("sdk-test")
                .decryptionKey("secret")
                .remoteEval(true)
                .build()).initialize());

        assertFalse(new GrowthBookClient(Options.builder()
                .apiHost("http://127.0.0.1:1234")
                .clientKey("sdk-test")
                .stickyBucketService(new InMemoryStickyBucketServiceImpl(new HashMap<>()))
                .remoteEval(true)
                .build()).initialize());

        assertFalse(new GrowthBookClient(Options.builder()
                .apiHost("http://127.0.0.1:1234")
                .clientKey("sdk-test")
                .refreshStrategy(FeatureRefreshStrategy.STALE_WHILE_REVALIDATE)
                .remoteEval(true)
                .build()).initialize());
    }

    @Test
    @DisplayName("remoteEvalCacheSize bounds the number of cached responses")
    void growthBookClient_remoteEvalCacheSizeBoundsCachedResponses() throws Exception {
        try (RemoteEvalTestServer server = new RemoteEvalTestServer(featureResponse(true))) {
            GrowthBookClient client = new GrowthBookClient(Options.builder()
                    .apiHost(server.apiHost())
                    .clientKey("sdk-test")
                    .remoteEval(true)
                    .remoteEvalCacheSize(1)
                    .build());
            assertTrue(client.initialize());

            UserContext firstUser = UserContext.builder()
                    .attributes(jsonObject("{\"id\":\"user-1\"}"))
                    .build();
            UserContext secondUser = UserContext.builder()
                    .attributes(jsonObject("{\"id\":\"user-2\"}"))
                    .build();

            client.isOn("remote-feature", firstUser);
            client.isOn("remote-feature", secondUser);
            client.isOn("remote-feature", firstUser);

            assertEquals(3, server.callCount());
        }
    }

    @Test
    @DisplayName("A negative remoteEvalCacheSize disables caching")
    void growthBookClient_negativeRemoteEvalCacheSizeDisablesCaching() throws Exception {
        try (RemoteEvalTestServer server = new RemoteEvalTestServer(featureResponse(true))) {
            GrowthBookClient client = new GrowthBookClient(Options.builder()
                    .apiHost(server.apiHost())
                    .clientKey("sdk-test")
                    .remoteEval(true)
                    .remoteEvalCacheSize(-1)
                    .build());
            assertTrue(client.initialize());

            UserContext userContext = UserContext.builder()
                    .attributes(jsonObject("{\"id\":\"user-1\"}"))
                    .build();

            client.isOn("remote-feature", userContext);
            client.isOn("remote-feature", userContext);

            assertEquals(2, server.callCount());
        }
    }

    @Test
    @DisplayName("Fires tracking callbacks from the response rule.tracks")
    void growthBookClient_remoteEvalFiresRuleTracks() throws Exception {
        try (RemoteEvalTestServer server = new RemoteEvalTestServer(trackResponse())) {
            List<String> tracked = new CopyOnWriteArrayList<>();
            GrowthBookClient client = new GrowthBookClient(Options.builder()
                    .apiHost(server.apiHost())
                    .clientKey("sdk-test")
                    .remoteEval(true)
                    .trackingCallBackWithUser(new TrackingCallbackWithUser() {
                        @Override
                        public <ValueType> void onTrack(
                                Experiment<ValueType> experiment,
                                ExperimentResult<ValueType> experimentResult,
                                UserContext userContext
                        ) {
                            tracked.add(experiment.getKey() + ":" + experimentResult.getVariationId()
                                    + ":" + userContext.getAttributes().get("id").getAsString());
                        }
                    })
                    .build());
            assertTrue(client.initialize());

            assertTrue(client.isOn("remote-feature", UserContext.builder()
                    .attributes(jsonObject("{\"id\":\"user-1\"}"))
                    .build()));

            assertEquals(Collections.singletonList("track-exp:1:user-1"), tracked);
        }
    }

    @Test
    @DisplayName("Ignores malformed rule.tracks entries")
    void growthBookClient_remoteEvalIgnoresMalformedTrackEntries() throws Exception {
        try (RemoteEvalTestServer server = new RemoteEvalTestServer(malformedTrackResponse())) {
            AtomicInteger trackCount = new AtomicInteger();
            GrowthBookClient client = new GrowthBookClient(Options.builder()
                    .apiHost(server.apiHost())
                    .clientKey("sdk-test")
                    .remoteEval(true)
                    .trackingCallBackWithUser(new TrackingCallbackWithUser() {
                        @Override
                        public <ValueType> void onTrack(
                                Experiment<ValueType> experiment,
                                ExperimentResult<ValueType> experimentResult,
                                UserContext userContext
                        ) {
                            trackCount.incrementAndGet();
                        }
                    })
                    .build());
            assertTrue(client.initialize());

            assertTrue(client.isOn("remote-feature", UserContext.builder()
                    .attributes(jsonObject("{\"id\":\"user-1\"}"))
                    .build()));
            assertEquals(0, trackCount.get());
        }
    }

    @Test
    @DisplayName("Single-context client fetches on initialization and on attribute updates")
    void growthBook_remoteEvalFetchesOnInitializationAndAttributeUpdate() throws Exception {
        try (RemoteEvalTestServer server = new RemoteEvalTestServer(featureResponse(true))) {
            GBContext context = GBContext.builder()
                    .apiHost(server.apiHost())
                    .clientKey("sdk-test")
                    .remoteEval(true)
                    .attributes(jsonObject("{\"id\":\"user-1\"}"))
                    .build();

            GrowthBook growthBook = new GrowthBook(context);

            assertTrue(growthBook.getFeatureValue("remote-feature", false));
            assertEquals(1, server.callCount());

            growthBook.setAttributes("{\"id\":\"user-2\"}");

            assertTrue(growthBook.getFeatureValue("remote-feature", false));
            assertEquals(2, server.callCount());
        }
    }

    @Test
    @DisplayName("Remote eval fires tracking once per assignment even when the result is served from cache")
    void growthBookClient_remoteEvalTrackFiresOncePerCachedAssignment() throws Exception {
        try (RemoteEvalTestServer server = new RemoteEvalTestServer(trackResponse())) {
            List<String> tracked = new CopyOnWriteArrayList<>();
            GrowthBookClient client = trackingClient(server, tracked);
            assertTrue(client.initialize());

            UserContext user = UserContext.builder().attributes(jsonObject("{\"id\":\"user-1\"}")).build();
            assertTrue(client.isOn("remote-feature", user));
            assertTrue(client.isOn("remote-feature", user));

            assertEquals(1, server.callCount());
            assertEquals(Collections.singletonList("track-exp:1:user-1"), tracked);
        }
    }

    @Test
    @DisplayName("Remote eval tracking fires independently for distinct users")
    void growthBookClient_remoteEvalTrackFiresPerDistinctUser() throws Exception {
        try (RemoteEvalTestServer server = new RemoteEvalTestServer(trackResponse())) {
            server.respondWith(200, trackResponseFor("user-1"));
            server.respondWith(200, trackResponseFor("user-2"));

            List<String> tracked = new CopyOnWriteArrayList<>();
            GrowthBookClient client = trackingClient(server, tracked);
            assertTrue(client.initialize());

            client.isOn("remote-feature", UserContext.builder().attributes(jsonObject("{\"id\":\"user-1\"}")).build());
            client.isOn("remote-feature", UserContext.builder().attributes(jsonObject("{\"id\":\"user-2\"}")).build());

            assertEquals(2, tracked.size());
            assertTrue(tracked.contains("track-exp:1:user-1"));
            assertTrue(tracked.contains("track-exp:1:user-2"));
        }
    }

    @Test
    @DisplayName("Remote eval tracking is isolated per client instance")
    void growthBookClient_remoteEvalTrackingIsIsolatedPerClient() throws Exception {
        try (RemoteEvalTestServer server = new RemoteEvalTestServer(trackResponse())) {
            List<String> trackedA = new CopyOnWriteArrayList<>();
            List<String> trackedB = new CopyOnWriteArrayList<>();
            GrowthBookClient clientA = trackingClient(server, trackedA);
            GrowthBookClient clientB = trackingClient(server, trackedB);
            assertTrue(clientA.initialize());
            assertTrue(clientB.initialize());

            UserContext user = UserContext.builder().attributes(jsonObject("{\"id\":\"user-1\"}")).build();
            clientA.isOn("remote-feature", user);
            clientB.isOn("remote-feature", user);

            assertEquals(Collections.singletonList("track-exp:1:user-1"), trackedA);
            assertEquals(Collections.singletonList("track-exp:1:user-1"), trackedB);
        }
    }

    @Test
    @DisplayName("Remote eval cache hits within the TTL and refetches once it expires")
    void growthBookClient_remoteEvalCacheTtlExpiresViaPublicApi() throws Exception {
        try (RemoteEvalTestServer server = new RemoteEvalTestServer(featureResponse(true))) {
            GrowthBookClient client = new GrowthBookClient(Options.builder()
                    .apiHost(server.apiHost())
                    .clientKey("sdk-test")
                    .remoteEval(true)
                    .remoteEvalCacheTtlSeconds(1)
                    .build());
            assertTrue(client.initialize());

            UserContext user = UserContext.builder().attributes(jsonObject("{\"id\":\"user-1\"}")).build();
            client.isOn("remote-feature", user);
            client.isOn("remote-feature", user);
            assertEquals(1, server.callCount());

            Thread.sleep(1200);
            client.isOn("remote-feature", user);
            assertEquals(2, server.callCount());
        }
    }

    @Test
    @DisplayName("Remote eval caches do not leak responses between client instances")
    void growthBookClient_remoteEvalCacheDoesNotLeakBetweenClients() throws Exception {
        try (RemoteEvalTestServer server = new RemoteEvalTestServer(featureResponse(true))) {
            GrowthBookClient clientA = new GrowthBookClient(remoteEvalOptions(server));
            GrowthBookClient clientB = new GrowthBookClient(remoteEvalOptions(server));
            assertTrue(clientA.initialize());
            assertTrue(clientB.initialize());

            UserContext user = UserContext.builder().attributes(jsonObject("{\"id\":\"user-1\"}")).build();
            clientA.isOn("remote-feature", user);
            clientB.isOn("remote-feature", user);

            assertEquals(2, server.callCount());
        }
    }

    @Test
    @DisplayName("An HTTP error falls back and does not poison the remote eval cache")
    void growthBookClient_remoteEvalHttpErrorDoesNotPoisonCache() throws Exception {
        try (RemoteEvalTestServer server = new RemoteEvalTestServer(featureResponse(true))) {
            server.respondWith(500, "{}");
            server.respondWith(200, featureResponse(true));

            GrowthBookClient client = new GrowthBookClient(remoteEvalOptions(server));
            assertTrue(client.initialize());

            UserContext user = UserContext.builder().attributes(jsonObject("{\"id\":\"user-1\"}")).build();
            assertFalse(client.isOn("remote-feature", user));
            assertTrue(client.isOn("remote-feature", user));
            assertEquals(2, server.callCount());
        }
    }

    @Test
    @DisplayName("A malformed JSON response falls back and does not poison the remote eval cache")
    void growthBookClient_remoteEvalMalformedJsonDoesNotPoisonCache() throws Exception {
        try (RemoteEvalTestServer server = new RemoteEvalTestServer(featureResponse(true))) {
            server.respondWith(200, "not-json");
            server.respondWith(200, featureResponse(true));

            GrowthBookClient client = new GrowthBookClient(remoteEvalOptions(server));
            assertTrue(client.initialize());

            UserContext user = UserContext.builder().attributes(jsonObject("{\"id\":\"user-1\"}")).build();
            assertFalse(client.isOn("remote-feature", user));
            assertTrue(client.isOn("remote-feature", user));
            assertEquals(2, server.callCount());
        }
    }

    @Test
    @DisplayName("Unknown fields in the remote eval response are tolerated")
    void growthBookClient_remoteEvalToleratesUnknownResponseFields() throws Exception {
        String body = "{\"features\":{\"remote-feature\":{\"defaultValue\":true,\"unknownField\":42}},"
                + "\"savedGroups\":{},\"meta\":{\"proxy\":\"v1\"}}";
        try (RemoteEvalTestServer server = new RemoteEvalTestServer(body)) {
            GrowthBookClient client = new GrowthBookClient(remoteEvalOptions(server));
            assertTrue(client.initialize());

            assertTrue(client.isOn("remote-feature",
                    UserContext.builder().attributes(jsonObject("{\"id\":\"user-1\"}")).build()));
        }
    }

    @Test
    @DisplayName("A response missing the features field falls back safely")
    void growthBookClient_remoteEvalMissingFeaturesFieldFallsBack() throws Exception {
        try (RemoteEvalTestServer server = new RemoteEvalTestServer("{\"savedGroups\":{}}")) {
            GrowthBookClient client = new GrowthBookClient(remoteEvalOptions(server));
            assertTrue(client.initialize());

            assertFalse(client.isOn("remote-feature",
                    UserContext.builder().attributes(jsonObject("{\"id\":\"user-1\"}")).build()));
        }
    }

    @Test
    @DisplayName("Shutting down during an in-flight request does not hang and stays safe afterwards")
    void growthBookClient_shutdownDuringInflightRequestIsSafe() throws Exception {
        try (RemoteEvalTestServer server = new RemoteEvalTestServer(featureResponse(true), 300)) {
            GrowthBookClient client = new GrowthBookClient(remoteEvalOptions(server));
            assertTrue(client.initialize());

            UserContext user = UserContext.builder().attributes(jsonObject("{\"id\":\"user-1\"}")).build();
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                Future<Boolean> inFlight = executor.submit(() -> client.preloadRemoteEval(user));
                Thread.sleep(50);
                client.shutdown();

                inFlight.get(2, TimeUnit.SECONDS);

                assertFalse(client.isOn("remote-feature", user));
            } finally {
                executor.shutdownNow();
            }
        }
    }

    @Test
    @DisplayName("Legacy constructors keep remote eval disabled by default")
    void oldConstructorsKeepRemoteEvalDisabledByDefault() {
        Options options = new Options(
                null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null
        );
        GBContext context = new GBContext(
                null, null, null, null, null, null, null, null, null, null, null,
                null, null, null, null, null
        );

        assertFalse(options.isRemoteEvalEnabled());
        assertEquals(RemoteEvalRequestBuilder.DEFAULT_CACHE_SIZE, options.getRemoteEvalCacheSize());
        assertFalse(context.isRemoteEvalEnabled());
        assertEquals(RemoteEvalRequestBuilder.DEFAULT_CACHE_SIZE, context.getRemoteEvalCacheSize());
    }

    @Test
    @DisplayName("Request body normalizes null fields to empty values")
    void requestBodyNormalizesNullFields() {
        RequestBodyForRemoteEval requestBody = new RequestBodyForRemoteEval();

        requestBody.setAttributes(null);
        requestBody.setForcedFeatures(null);
        requestBody.setForcedVariations(null);
        requestBody.setUrl(null);

        assertEquals(new JsonObject(), requestBody.getAttributes());
        assertTrue(requestBody.getForcedFeatures().isEmpty());
        assertTrue(requestBody.getForcedVariations().isEmpty());
        assertEquals("", requestBody.getUrl());
    }

    private Options remoteEvalOptions(RemoteEvalTestServer server) {
        return Options.builder()
                .apiHost(server.apiHost())
                .clientKey("sdk-test")
                .remoteEval(true)
                .build();
    }

    private JsonObject jsonObject(String json) {
        return jsonUtils.gson.fromJson(json, JsonObject.class);
    }

    private static String featureResponse(boolean enabled) {
        return "{\"features\":{\"remote-feature\":{\"defaultValue\":" + enabled + "}},\"savedGroups\":{}}";
    }

    private static String malformedTrackResponse() {
        return "{\"features\":{\"remote-feature\":{\"rules\":[{\"force\":true,\"tracks\":[{}]}]}},\"savedGroups\":{}}";
    }

    private static String trackResponse() {
        return trackResponseFor("user-1");
    }

    private static String trackResponseFor(String hashValue) {
        return "{\"features\":{\"remote-feature\":{\"defaultValue\":false,\"rules\":[{\"force\":true,\"tracks\":[{"
                + "\"experiment\":{\"key\":\"track-exp\",\"variations\":[false,true]},"
                + "\"result\":{\"variationId\":1,\"inExperiment\":true,\"value\":true,\"hashUsed\":true,"
                + "\"hashAttribute\":\"id\",\"hashValue\":\"" + hashValue + "\",\"featureId\":\"remote-feature\",\"key\":\"1\"}"
                + "}]}]}},\"savedGroups\":{}}";
    }

    private GrowthBookClient trackingClient(RemoteEvalTestServer server, List<String> tracked) {
        return new GrowthBookClient(Options.builder()
                .apiHost(server.apiHost())
                .clientKey("sdk-test")
                .remoteEval(true)
                .trackingCallBackWithUser(new TrackingCallbackWithUser() {
                    @Override
                    public <ValueType> void onTrack(
                            Experiment<ValueType> experiment,
                            ExperimentResult<ValueType> experimentResult,
                            UserContext userContext
                    ) {
                        tracked.add(experiment.getKey() + ":" + experimentResult.getVariationId()
                                + ":" + userContext.getAttributes().get("id").getAsString());
                    }
                })
                .build());
    }

    private static class RemoteEvalTestServer implements AutoCloseable {
        private final HttpServer server;
        private final ExecutorService executor;
        private final AtomicInteger callCount = new AtomicInteger();
        private final List<String> bodies = new CopyOnWriteArrayList<>();
        private final String responseBody;
        private final int delayMillis;

        RemoteEvalTestServer(String responseBody) throws IOException {
            this(responseBody, 0);
        }

        RemoteEvalTestServer(String responseBody, int delayMillis) throws IOException {
            this.responseBody = responseBody;
            this.delayMillis = delayMillis;
            this.server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            this.executor = Executors.newCachedThreadPool();
            this.server.createContext("/api/eval/sdk-test", this::handle);
            this.server.setExecutor(this.executor);
            this.server.start();
        }

        private final List<int[]> statuses = new CopyOnWriteArrayList<>();
        private final List<String> scriptedBodies = new CopyOnWriteArrayList<>();

        /** Append a scripted (status, body) response; used in call order, last entry repeats. */
        void respondWith(int status, String body) {
            statuses.add(new int[]{status});
            scriptedBodies.add(body);
        }

        String apiHost() {
            return "http://127.0.0.1:" + this.server.getAddress().getPort();
        }

        int callCount() {
            return this.callCount.get();
        }

        String lastBody() {
            return this.bodies.get(this.bodies.size() - 1);
        }

        private void handle(HttpExchange exchange) throws IOException {
            int n = callCount.incrementAndGet();
            bodies.add(readBody(exchange));
            if (delayMillis > 0) {
                sleep(delayMillis);
            }

            int status = 200;
            String body = responseBody;
            if (!scriptedBodies.isEmpty()) {
                int index = Math.min(n - 1, scriptedBodies.size() - 1);
                status = statuses.get(index)[0];
                body = scriptedBodies.get(index);
            }

            byte[] response = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, response.length == 0 ? -1 : response.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(response);
            }
        }

        private String readBody(HttpExchange exchange) throws IOException {
            try (InputStream inputStream = exchange.getRequestBody();
                 ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[1024];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                }
                return new String(outputStream.toByteArray(), StandardCharsets.UTF_8);
            }
        }

        private void sleep(int millis) {
            try {
                Thread.sleep(millis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        @Override
        public void close() {
            this.server.stop(0);
            this.executor.shutdownNow();
        }
    }
}
