package growthbook.sdk.java.multiusermode;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import growthbook.sdk.java.evaluators.ExperimentEvaluator;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.FeatureResult;
import growthbook.sdk.java.model.FeatureResultSource;
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
 * legacy single-user propagation contract is unchanged.
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
    void legacyContextsKeepPropagatingCallbackExceptions() {
        TrackingCallbackWithUser throwing = new TrackingCallbackWithUser() {
            @Override
            public <ValueType> void onTrack(Experiment<ValueType> experiment,
                                            ExperimentResult<ValueType> result,
                                            UserContext userContext) {
                throw new IllegalStateException("analytics backend down");
            }
        };
        Options options = Options.builder().trackingCallBackWithUser(throwing).build();
        // A context WITHOUT the multi-user client's suppress flag — the legacy
        // documented contract: the exception propagates out of evaluation.
        EvaluationContext legacyContext = new EvaluationContext(
                GlobalContext.builder().enabled(true).build(),
                user("u1"),
                new EvaluationContext.StackContext(),
                options);

        assertThrows(IllegalStateException.class,
                () -> new ExperimentEvaluator().evaluateExperiment(experiment("exp-legacy"), legacyContext, null));
    }
}
