package growthbook.sdk.java.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.listener.FeatureRefreshListener;
import growthbook.sdk.java.model.FeatureRefreshEvent;
import growthbook.sdk.java.model.FeatureRefreshSource;
import growthbook.sdk.java.retry.FeatureFetchRetryPolicy;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Bootstrapping the repository from an inline payload (offline cold start), mirroring the TS
 * {@code init({payload})} / {@code initSync()} behaviour.
 */
class GBFeaturesRepositoryInitialPayloadTest {

    private static final FeatureFetchRetryPolicy NO_DELAY_RETRY_POLICY =
            new FeatureFetchRetryPolicy(1, Duration.ZERO, Duration.ZERO);

    private static final String PAYLOAD_FLAG_ON =
            "{\"features\":{\"my-flag\":{\"defaultValue\":true}},\"savedGroups\":{}}";

    @Test
    @DisplayName("Offline start: seeds features from the inline payload without a network call")
    void offlineStart_seedsFeaturesFromInitialPayload() throws FeatureFetchException {
        GBFeaturesRepository subject = GBFeaturesRepository.builder()
                .apiHost("http://localhost:80")
                .clientKey("sdk-abc123")
                .okHttpClient(failingHttpClient())
                .retryPolicy(NO_DELAY_RETRY_POLICY)
                .isCacheDisabled(true)
                .initialPayload(PAYLOAD_FLAG_ON)
                .build();

        subject.initialize();

        assertTrue(subject.getInitialized());
        assertTrue(subject.hasFeatureData());
        assertEquals("{\"my-flag\":{\"defaultValue\":true}}", subject.getFeaturesJson());
        assertEquals(0L, subject.getLastSuccessfulFetchAtMillis());

        subject.shutdown();
    }

    @Test
    @DisplayName("Live server wins: the first (background) refresh replaces the seeded payload")
    void liveServer_overridesInitialPayload() throws Exception {
        String serverResponse = "{\"status\":200,\"features\":{\"my-flag\":{\"defaultValue\":false}}}";

        GBFeaturesRepository subject = GBFeaturesRepository.builder()
                .apiHost("http://localhost:80")
                .clientKey("sdk-abc123")
                .okHttpClient(mockHttpClient(serverResponse))
                .isCacheDisabled(true)
                .initialPayload(PAYLOAD_FLAG_ON)
                .build();

        CountDownLatch refreshed = new CountDownLatch(1);
        subject.addFeatureRefreshListener(event -> {
            if (event.getSource() == FeatureRefreshSource.INITIALIZATION && event.isSuccessful()) {
                refreshed.countDown();
            }
        });

        subject.initialize();

        assertTrue(refreshed.await(5, TimeUnit.SECONDS), "background refresh did not complete");
        assertEquals("{\"my-flag\":{\"defaultValue\":false}}", subject.getFeaturesJson());

        subject.shutdown();
    }

    @Test
    @DisplayName("Malformed payload fails fast at construction (no silent fallback)")
    void malformedInitialPayload_failsFastAtConstruction() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                GBFeaturesRepository.builder()
                        .apiHost("http://localhost:80")
                        .clientKey("sdk-abc123")
                        .isCacheDisabled(true)
                        .initialPayload("{ this is not json")
                        .build());

        assertTrue(ex.getMessage().contains("initialPayload"));
    }

    @Test
    @DisplayName("Encrypted payload is decrypted when a decryptionKey is set")
    void encryptedInitialPayload_isDecrypted() throws FeatureFetchException {
        String encryptedPayload = "{\n" +
                "  \"features\": {},\n" +
                "  \"encryptedFeatures\": \"jfLnSxjChWcbyHaIF30RNw==.iz8DywkSk4+WhNqnIwvr/PdvAwaRNjN3RE30JeOezGAQ/zZ2yoVyVo4w0nLHYqOje5MbhmL0ssvlH0ojk/BxqdSzXD4Wzo3DXfKV81Nzi1aSdiCMnVAIYEzjPl1IKZC3fl88YDBNV3F6YnR9Lemy9yzT03cvMZ0NZ9t5LZO2xS2MhpPYNcAfAlfxXhBGXj6UFDoNKGAtGKdc/zmJsUVQGLtHmqLspVynnJlPPo9nXG+87bt6SjSfQfySUgHm28hb4VmDhVmCx0N37buolVr3pzjZ1QK+tyMKIV7x4/Gu06k8sm0eU4HjG5DFsPgTR7qDu/N5Nk5UTRpG7aSXTUErxhHSJ7MQaxH/Dp/71zVEicaJ0qZE3oPRnU187QVBfdVLLRbqq2QU7Yu0GyJ1jjuf6TA+759OgifHdm17SX43L94Qe62CMU7JQyAqt7h7XmTTQBG664HYwgHJ0ju/9jySC4KUlRxNsixH1tJfznnEXqxgSozn4J61UprTqcmlxLZ1hZPCcRew3mm9DMAG9+YEiL8MhaIwsw8oVq9GirN1S8G3m/6UxQHxZVraPvMRXpGt5VpzEDJ0Po+phrIAhPuIbNpgb08b6Ej4Xh9XXeOLtIcpuj+gNpc4pR4tqF2IOwET\"\n" +
                "}";
        String decryptionKey = "o0maZL/O7AphxcbRvaJIzw==";

        GBFeaturesRepository subject = GBFeaturesRepository.builder()
                .apiHost("http://localhost:80")
                .clientKey("sdk-abc123")
                .decryptionKey(decryptionKey)
                .okHttpClient(failingHttpClient())
                .retryPolicy(NO_DELAY_RETRY_POLICY)
                .isCacheDisabled(true)
                .initialPayload(encryptedPayload)
                .build();

        subject.initialize();

        assertTrue(subject.hasFeatureData());
        assertTrue(subject.getFeaturesJson().contains("string_feature"));

        subject.shutdown();
    }

    @Test
    @DisplayName("Encrypted payload with the wrong key produces a clear startup error")
    void encryptedInitialPayload_decryptFailure_failsAtStartup() {
        String encryptedPayload = "{\"encryptedFeatures\":\"jfLnSxjChWcbyHaIF30RNw==.iz8DywkSk4+WhNqnIwvr/PdvAwaRNjN3RE30JeOezGAQ\"}";

        GBFeaturesRepository subject = GBFeaturesRepository.builder()
                .apiHost("http://localhost:80")
                .clientKey("sdk-abc123")
                .decryptionKey("aW52YWxpZC1rZXktMTIzNDU2Nzg5MA==")
                .okHttpClient(failingHttpClient())
                .retryPolicy(NO_DELAY_RETRY_POLICY)
                .isCacheDisabled(true)
                .initialPayload(encryptedPayload)
                .build();

        assertThrows(FeatureFetchException.class, subject::initialize);

        subject.shutdown();
    }

    @Test
    @DisplayName("Seed is reported with INITIAL_PAYLOAD source and loadedFromCache=true, and the "
            + "offline fetch failure is still visible in metrics while evaluation keeps working")
    void seedAndOfflineFailure_areReportedToListeners() throws Exception {
        FeatureRefreshListener listener = mock(FeatureRefreshListener.class);
        GBFeaturesRepository subject = GBFeaturesRepository.builder()
                .apiHost("http://localhost:80")
                .clientKey("sdk-abc123")
                .okHttpClient(failingHttpClient())
                .retryPolicy(NO_DELAY_RETRY_POLICY)
                .isCacheDisabled(true)
                .initialPayload(PAYLOAD_FLAG_ON)
                .build();
        subject.addFeatureRefreshListener(listener);
        // The post-seed refresh now runs in the background; await the reported failure deterministically.
        CountDownLatch failureLatch = new CountDownLatch(1);
        subject.addFeatureRefreshListener(event -> {
            if (!event.isSuccessful()) {
                failureLatch.countDown();
            }
        });

        subject.initialize();
        assertTrue(failureLatch.await(5, TimeUnit.SECONDS), "offline fetch failure was not reported");

        ArgumentCaptor<FeatureRefreshEvent> captor = ArgumentCaptor.forClass(FeatureRefreshEvent.class);
        verify(listener, org.mockito.Mockito.atLeast(2)).onRefresh(captor.capture());
        List<FeatureRefreshEvent> events = captor.getAllValues();

        FeatureRefreshEvent seedEvent = events.stream()
                .filter(e -> e.getSource() == FeatureRefreshSource.INITIAL_PAYLOAD)
                .findFirst()
                .orElseThrow(() -> new AssertionError("no INITIAL_PAYLOAD event emitted"));
        assertTrue(seedEvent.isSuccessful());
        assertTrue(seedEvent.isLoadedFromCache());
        assertEquals(1, seedEvent.getActiveFeatureCount());

        boolean failureReported = events.stream().anyMatch(e -> !e.isSuccessful());
        assertTrue(failureReported, "offline fetch failure must be reported to listeners");

        assertTrue(subject.hasFeatureData());
        assertEquals("{\"my-flag\":{\"defaultValue\":true}}", subject.getFeaturesJson());

        subject.shutdown();
    }

    /**
     * Mock client that always fails the network call, simulating an unreachable API.
     */
    private static OkHttpClient failingHttpClient() {
        try {
            OkHttpClient okHttpClient = mock(OkHttpClient.class);
            Call call = mock(Call.class);
            when(call.execute()).thenThrow(new IOException("simulated offline"));
            when(okHttpClient.newCall(any())).thenReturn(call);
            return okHttpClient;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private static OkHttpClient mockHttpClient(final String serializedBody) throws IOException {
        OkHttpClient okHttpClient = mock(OkHttpClient.class);
        Call remoteCall = mock(Call.class);
        Response response = new Response.Builder()
                .request(new Request.Builder().url("http://url.com").build())
                .protocol(Protocol.HTTP_1_1)
                .code(200).message("").body(
                        ResponseBody.create(serializedBody, MediaType.parse("application/json")))
                .build();
        when(remoteCall.execute()).thenReturn(response);
        when(okHttpClient.newCall(any())).thenReturn(remoteCall);
        return okHttpClient;
    }
}
