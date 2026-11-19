package growthbook.sdk.java.multiusermode;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import growthbook.sdk.java.evaluators.ExperimentEvaluator;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.FeatureResultSource;
import growthbook.sdk.java.model.ParentCondition;
import growthbook.sdk.java.model.TrackData;
import growthbook.sdk.java.multiusermode.configurations.EvaluationContext;
import growthbook.sdk.java.multiusermode.configurations.GlobalContext;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.multiusermode.usage.FeatureUsageCallbackWithUser;
import growthbook.sdk.java.multiusermode.usage.TrackingCallbackWithUser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exposure-tracking hardening on the multi-user client: a throwing callback
 * never fails the assignment and its exposure is retried (the dedup key is
 * un-marked); dedup holds across many users (no 30-entry LRU thrash); the
 * single-user path is unchanged (the exception propagates, no retry).
 */
class GrowthBookClientTrackingTest {

    private static Experiment<String> experiment(String key) {
        return Experiment.<String>builder()
                .key(key)
                .variations(new ArrayList<>(Arrays.asList("control", "treatment")))
                .build();
    }

    private static UserContext user(String id) {
        return UserContext.builder().attributesJson("{\"id\":\"" + id + "\"}").build();
    }

    @Test
    @Timeout(30)
    void failedTrackingCallbackDoesNotFailEvaluationAndIsRetried() {
        AtomicInteger attempts = new AtomicInteger();
        TrackingCallbackWithUser failsOnce = new TrackingCallbackWithUser() {
            @Override
            public <ValueType> void onTrack(Experiment<ValueType> experiment,
                                            ExperimentResult<ValueType> result,
                                            UserContext userContext) {
                if (attempts.incrementAndGet() == 1) {
                    throw new IllegalStateException("analytics backend down");
                }
            }
        };
        GrowthBookClient client = new GrowthBookClient(Options.builder()
                .trackingCallBackWithUser(failsOnce)
                .build());
        try {
            ExperimentResult<String> first = client.run(experiment("exp-retry"), user("u1"));
            assertNotNull(first, "a throwing tracking callback must not fail the assignment");
            assertEquals(1, attempts.get());

            client.run(experiment("exp-retry"), user("u1"));
            assertEquals(2, attempts.get(), "failed exposure must be retried, not lost");

            client.run(experiment("exp-retry"), user("u1"));
            assertEquals(2, attempts.get(), "delivered exposure must not re-fire");
        } finally {
            client.shutdown();
        }
    }

    @Test
    @Timeout(30)
    void dedupHoldsAcrossManyUsers() {
        Map<String, AtomicInteger> tracksByUser = new ConcurrentHashMap<>();
        TrackingCallbackWithUser counting = new TrackingCallbackWithUser() {
            @Override
            public <ValueType> void onTrack(Experiment<ValueType> experiment,
                                            ExperimentResult<ValueType> result,
                                            UserContext userContext) {
                tracksByUser.computeIfAbsent(result.getHashValue(), k -> new AtomicInteger()).incrementAndGet();
            }
        };
        GrowthBookClient client = new GrowthBookClient(Options.builder()
                .trackingCallBackWithUser(counting)
                .build());
        try {
            // 100 distinct users — far past the old 30-entry evaluator-local LRU.
            for (int i = 0; i < 100; i++) {
                client.run(experiment("exp-dedup"), user("user-" + i));
            }
            assertEquals(100, tracksByUser.size());

            // Second pass: with the old thrashing LRU these would all re-fire.
            for (int i = 0; i < 100; i++) {
                client.run(experiment("exp-dedup"), user("user-" + i));
            }
            for (Map.Entry<String, AtomicInteger> entry : tracksByUser.entrySet()) {
                assertEquals(1, entry.getValue().get(),
                        "exposure re-fired for " + entry.getKey() + " — dedup tracker thrashed");
            }
        } finally {
            client.shutdown();
        }
    }

    @Test
    @Timeout(30)
    void failedFeatureUsageCallbackDoesNotFailEvaluation() {
        FeatureUsageCallbackWithUser throwing = new FeatureUsageCallbackWithUser() {
            @Override
            public <ValueType> void onFeatureUsage(String featureKey,
                                                   FeatureResult<ValueType> result,
                                                   UserContext userContext) {
                throw new IllegalStateException("analytics backend down");
            }
        };
        WireMockServer wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        GrowthBookClient client = null;
        try {
            wireMock.stubFor(get(urlPathMatching("/api/features/.*"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"features\":{\"plain\":{\"defaultValue\":42}}}")));

            client = new GrowthBookClient(Options.builder()
                    .apiHost("http://localhost:" + wireMock.port())
                    .clientKey("sdk-tracking-test")
                    .isCacheDisabled(true)
                    .featureUsageCallbackWithUser(throwing)
                    .build());
            assertTrue(client.initialize(), "client failed to initialize");

            FeatureResult<Object> result = client.evalFeature("plain", Object.class, user("u1"));
            assertNotNull(result.getValue(),
                    "a defined feature's value must survive a throwing usage callback, not collapse to the unknown-feature fallback");
            assertEquals(42, ((Number) result.getValue()).intValue(),
                    "a defined feature's value must survive a throwing usage callback");
            assertEquals(FeatureResultSource.DEFAULT_VALUE, result.getSource(),
                    "a defined feature's source must survive a throwing usage callback");
        } finally {
            if (client != null) {
                client.shutdown();
            }
            wireMock.stop();
        }
    }

    @Test
    @Timeout(30)
    void prerequisiteExposureIsNotRefiredOnLaterEvaluations() {
        Map<String, AtomicInteger> tracksByExperiment = new ConcurrentHashMap<>();
        TrackingCallbackWithUser counting = new TrackingCallbackWithUser() {
            @Override
            public <ValueType> void onTrack(Experiment<ValueType> experiment,
                                            ExperimentResult<ValueType> result,
                                            UserContext userContext) {
                tracksByExperiment.computeIfAbsent(experiment.getKey(), k -> new AtomicInteger()).incrementAndGet();
            }
        };
        WireMockServer wireMock = new WireMockServer(WireMockConfiguration.options().dynamicPort());
        wireMock.start();
        GrowthBookClient client = null;
        try {
            wireMock.stubFor(get(urlPathMatching("/api/features/.*"))
                    .willReturn(aResponse()
                            .withStatus(200)
                            .withHeader("Content-Type", "application/json")
                            .withBody("{\"features\":{\"parent\":{\"defaultValue\":\"off\",\"rules\":[{"
                                    + "\"key\":\"parent-exp\",\"variations\":[\"a\",\"b\"],\"hashAttribute\":\"id\"}]}}}")));

            client = new GrowthBookClient(Options.builder()
                    .apiHost("http://localhost:" + wireMock.port())
                    .clientKey("sdk-tracking-test")
                    .isCacheDisabled(true)
                    .trackingCallBackWithUser(counting)
                    .build());
            assertTrue(client.initialize(), "client failed to initialize");

            JsonObject parentIsDefined = JsonParser.parseString("{\"value\":{\"$exists\":true}}").getAsJsonObject();
            Experiment<String> child = Experiment.<String>builder()
                    .key("child-exp")
                    .variations(new ArrayList<>(Arrays.asList("control", "treatment")))
                    .parentConditions(new ArrayList<>(Collections.singletonList(
                            new ParentCondition("parent", parentIsDefined, null))))
                    .build();

            for (int i = 0; i < 3; i++) {
                client.run(child, user("u1"));
            }

            assertEquals(1, tracksByExperiment.get("parent-exp").get(),
                    "a prerequisite's exposure must be deduped across evaluations, not re-fired by each sub-evaluation");
            assertEquals(1, tracksByExperiment.get("child-exp").get());
        } finally {
            if (client != null) {
                client.shutdown();
            }
            wireMock.stop();
        }
    }

    @Test
    @Timeout(30)
    void failedRemoteEvalExposureIsRetriedWithoutFailingEvaluation() {
        AtomicInteger attempts = new AtomicInteger();
        TrackingCallbackWithUser failsOnce = new TrackingCallbackWithUser() {
            @Override
            public <ValueType> void onTrack(Experiment<ValueType> experiment,
                                            ExperimentResult<ValueType> result,
                                            UserContext userContext) {
                if (attempts.incrementAndGet() == 1) {
                    throw new IllegalStateException("analytics backend down");
                }
            }
        };
        EvaluationContext context = new EvaluationContext(
                GlobalContext.builder().enabled(true).build(),
                user("u1"),
                new EvaluationContext.StackContext(),
                Options.builder().trackingCallBackWithUser(failsOnce).build());
        context.setSuppressTrackingErrors(true); // as GrowthBookClient builds it
        ExperimentResult<String> result = ExperimentResult.<String>builder()
                .hashAttribute("id")
                .hashValue("u1")
                .variationId(1)
                .build();
        List<TrackData<String>> tracks = Collections.singletonList(
                new TrackData<>(experiment("exp-remote"), result));
        ExperimentEvaluator evaluator = new ExperimentEvaluator();

        evaluator.fireRemoteEvaluationTracks(tracks, context);
        assertEquals(1, attempts.get(), "a throwing callback must not propagate out of remote-eval tracking");

        evaluator.fireRemoteEvaluationTracks(tracks, context);
        assertEquals(2, attempts.get(), "failed remote-eval exposure must be retried, not lost");

        evaluator.fireRemoteEvaluationTracks(tracks, context);
        assertEquals(2, attempts.get(), "delivered remote-eval exposure must not re-fire");
    }

    @Test
    @Timeout(30)
    void aFailedDeliveryDoesNotUnmarkANewerDeliveryOfTheSameExposure() {
        ExperimentTracker tracker = new ExperimentTracker(10_000);
        ExperimentEvaluator evaluator = new ExperimentEvaluator();
        AtomicInteger attempts = new AtomicInteger();
        EvaluationContext[] context = new EvaluationContext[1];
        TrackingCallbackWithUser slowThenFails = new TrackingCallbackWithUser() {
            @Override
            public <ValueType> void onTrack(Experiment<ValueType> experiment,
                                            ExperimentResult<ValueType> result,
                                            UserContext userContext) {
                if (attempts.incrementAndGet() == 1) {
                    // While this delivery is still running, its key is evicted and another
                    // evaluation claims and delivers the same exposure. Only then does this one fail.
                    tracker.clearTrackedExperiments();
                    evaluator.evaluateExperiment(experiment("exp-stale"), context[0], null);
                    throw new IllegalStateException("analytics backend down");
                }
            }
        };
        context[0] = new EvaluationContext(
                GlobalContext.builder().enabled(true).build(),
                user("u1"),
                new EvaluationContext.StackContext(),
                Options.builder().trackingCallBackWithUser(slowThenFails).build());
        context[0].setSuppressTrackingErrors(true); // as GrowthBookClient builds it
        context[0].setSharedExperimentTracker(tracker);

        evaluator.evaluateExperiment(experiment("exp-stale"), context[0], null);
        assertEquals(2, attempts.get(), "the first delivery failed and the newer one succeeded");

        evaluator.evaluateExperiment(experiment("exp-stale"), context[0], null);
        assertEquals(2, attempts.get(), "the late failure must not un-mark the newer, delivered exposure");
    }

    @Test
    @Timeout(30)
    void singleUserContextsPropagateCallbackExceptionsAndDoNotRetry() {
        AtomicInteger attempts = new AtomicInteger();
        TrackingCallbackWithUser throwing = new TrackingCallbackWithUser() {
            @Override
            public <ValueType> void onTrack(Experiment<ValueType> experiment,
                                            ExperimentResult<ValueType> result,
                                            UserContext userContext) {
                attempts.incrementAndGet();
                throw new IllegalStateException("analytics backend down");
            }
        };
        Options options = Options.builder().trackingCallBackWithUser(throwing).build();
        // A context WITHOUT the multi-user client's flag, as the single-user
        // GrowthBook class builds it.
        EvaluationContext singleUserContext = new EvaluationContext(
                GlobalContext.builder().enabled(true).build(),
                user("u1"),
                new EvaluationContext.StackContext(),
                options);
        ExperimentEvaluator evaluator = new ExperimentEvaluator();

        assertThrows(IllegalStateException.class,
                () -> evaluator.evaluateExperiment(experiment("exp-legacy"), singleUserContext, null));
        assertNotNull(evaluator.evaluateExperiment(experiment("exp-legacy"), singleUserContext, null));
        assertEquals(1, attempts.get(), "the single-user path must not retry a failed exposure");
    }

    @Test
    @Timeout(30)
    void singleUserContextsDoNotRetryFailedRemoteEvalExposures() {
        AtomicInteger attempts = new AtomicInteger();
        TrackingCallbackWithUser throwing = new TrackingCallbackWithUser() {
            @Override
            public <ValueType> void onTrack(Experiment<ValueType> experiment,
                                            ExperimentResult<ValueType> result,
                                            UserContext userContext) {
                attempts.incrementAndGet();
                throw new IllegalStateException("analytics backend down");
            }
        };
        EvaluationContext singleUserContext = new EvaluationContext(
                GlobalContext.builder().enabled(true).build(),
                user("u1"),
                new EvaluationContext.StackContext(),
                Options.builder().trackingCallBackWithUser(throwing).build());
        ExperimentResult<String> result = ExperimentResult.<String>builder()
                .hashAttribute("id")
                .hashValue("u1")
                .variationId(1)
                .build();
        List<TrackData<String>> tracks = Collections.singletonList(
                new TrackData<>(experiment("exp-remote"), result));
        ExperimentEvaluator evaluator = new ExperimentEvaluator();

        evaluator.fireRemoteEvaluationTracks(tracks, singleUserContext);
        evaluator.fireRemoteEvaluationTracks(tracks, singleUserContext);

        assertEquals(1, attempts.get(), "the single-user path must not retry a failed exposure");
    }
}
