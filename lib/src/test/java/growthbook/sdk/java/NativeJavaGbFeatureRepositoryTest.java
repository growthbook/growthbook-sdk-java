package growthbook.sdk.java;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import growthbook.sdk.java.callback.FeatureRefreshCallback;
import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.listener.FeatureRefreshListener;
import growthbook.sdk.java.model.FeatureRefreshEvent;
import growthbook.sdk.java.model.FeatureRefreshSource;
import growthbook.sdk.java.model.HttpHeaders;
import growthbook.sdk.java.repository.FeatureRefreshStrategy;
import growthbook.sdk.java.repository.NativeJavaGbFeatureRepository;
import growthbook.sdk.java.sandbox.GbCacheManager;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.HttpURLConnection;
import java.nio.charset.StandardCharsets;

class NativeJavaGbFeatureRepositoryTest {
    private static final String API_HOST = "https://cdn.growthbook.io";
    private static final String CLIENT_KEY = "java_NsrWldWd5bxQJZftGsWKl7R2yD2LtAK8C8EUYh9L8";
    private static final String TEST_CLIENT_KEY = "sdk-test";

    private NativeJavaGbFeatureRepository repository;


    @BeforeEach
    void setUp() {
        repository = NativeJavaGbFeatureRepository.builder()
                .apiHost(API_HOST)
                .clientKey(CLIENT_KEY)
                .encryptionKey(null)
                .refreshStrategy(FeatureRefreshStrategy.STALE_WHILE_REVALIDATE)
                .swrTtlSeconds(60)
                .build();
    }

    @Test
    void canBeConstructed_withNullEncryptionKey() {
        NativeJavaGbFeatureRepository subject = new NativeJavaGbFeatureRepository(
                "https://cdn.growthbook.io",
                "java_NsrWldWd5bxQJZftGsWKl7R2yD2LtAK8C8EUYh9L8",
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertNotNull(subject);
        assertEquals("https://cdn.growthbook.io/api/features/java_NsrWldWd5bxQJZftGsWKl7R2yD2LtAK8C8EUYh9L8", subject.getFeaturesEndpoint());
        assertEquals(FeatureRefreshStrategy.STALE_WHILE_REVALIDATE, subject.getRefreshStrategy());
    }

    @Test
    void canBeConstructed_withEncryptionKey() {
        NativeJavaGbFeatureRepository subject = new NativeJavaGbFeatureRepository(
                "https://cdn.growthbook.io",
                "sdk-862b5mHcP9XPugqD",
                "BhB1wORFmZLTDjbvstvS8w==",
                null,
                null,
                null,
                null,
                null
        );

        assertNotNull(subject);
        assertEquals("https://cdn.growthbook.io/api/features/sdk-862b5mHcP9XPugqD", subject.getFeaturesEndpoint());
        assertEquals("BhB1wORFmZLTDjbvstvS8w==", subject.getEncryptionKey());
    }

    @Test
    void canBeBuilt_withNullEncryptionKey() {
        NativeJavaGbFeatureRepository subject = NativeJavaGbFeatureRepository
                .builder()
                .apiHost("https://cdn.growthbook.io")
                .clientKey("java_NsrWldWd5bxQJZftGsWKl7R2yD2LtAK8C8EUYh9L8")
                .build();

        assertNotNull(subject);
        assertEquals("https://cdn.growthbook.io/api/features/java_NsrWldWd5bxQJZftGsWKl7R2yD2LtAK8C8EUYh9L8", subject.getFeaturesEndpoint());
    }

    @Test
    void canBeBuilt_withEncryptionKey() {
        NativeJavaGbFeatureRepository subject = NativeJavaGbFeatureRepository
                .builder()
                .apiHost("https://cdn.growthbook.io")
                .clientKey("sdk-862b5mHcP9XPugqD")
                .encryptionKey("BhB1wORFmZLTDjbvstvS8w==")
                .build();

        assertNotNull(subject);
        assertEquals("https://cdn.growthbook.io/api/features/sdk-862b5mHcP9XPugqD", subject.getFeaturesEndpoint());
        assertEquals("BhB1wORFmZLTDjbvstvS8w==", subject.getEncryptionKey());
    }

    @Test()
    void cannotBeBuild_withoutClientKey() {
        assertThrows(IllegalArgumentException.class, () -> new NativeJavaGbFeatureRepository(
                API_HOST,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ));
    }

    @Test
    void getEmptyFeatureJson_withouInvokeInitialize() {
        assertEquals("{}", repository.getFeaturesJson());
    }

    @Test
    public void testOnFeaturesRefresh_AddsListener() {
        FeatureRefreshCallback listener = mock(FeatureRefreshCallback.class);
        repository.onFeaturesRefresh(listener);

        repository.onRefreshSuccess("{}");

        verify(listener, times(1)).onRefresh("{}");
    }

    @Test
    public void testOnFeaturesRefresh_ContinuesAfterListenerFailure() {
        FeatureRefreshCallback failingListener = mock(FeatureRefreshCallback.class);
        FeatureRefreshCallback successfulListener = mock(FeatureRefreshCallback.class);
        doThrow(new RuntimeException("listener failed")).when(failingListener).onRefresh("{}");

        repository.onFeaturesRefresh(failingListener);
        repository.onFeaturesRefresh(successfulListener);
        repository.onRefreshSuccess("{}");

        verify(successfulListener).onRefresh("{}");
    }

    @Test
    public void testOnFeatureError_AddsListener() {
        FeatureRefreshCallback listener = mock(FeatureRefreshCallback.class);
        repository.onFeaturesRefresh(listener);

        repository.onRefreshFailed(new Exception("Error"));

        verify(listener, times(1)).onError(any(Throwable.class));
    }

    @Test
    public void testOnFeatureError_ContinuesAfterListenerFailure() {
        FeatureRefreshCallback failingListener = mock(FeatureRefreshCallback.class);
        FeatureRefreshCallback successfulListener = mock(FeatureRefreshCallback.class);
        RuntimeException refreshError = new RuntimeException("refresh failed");
        doThrow(new RuntimeException("listener failed")).when(failingListener).onError(refreshError);

        repository.onFeaturesRefresh(failingListener);
        repository.onFeaturesRefresh(successfulListener);
        repository.onRefreshFailed(refreshError);

        verify(successfulListener).onError(refreshError);
    }

    @Test
    public void testClearCallbacks_ClearsAllCallbacks() {
        FeatureRefreshCallback refreshListener = mock(FeatureRefreshCallback.class);
        repository.onFeaturesRefresh(refreshListener);

        repository.clearCallbacks();


        verify(refreshListener, never()).onRefresh(anyString());
    }

    @Test
    void testFeatureRefreshListener_receivesInitializationMetadata() throws Exception {
        HttpServer server = startFeatureServer(
                HttpURLConnection.HTTP_OK,
                "{\"features\":{\"test-feature\":{\"defaultValue\":true}}}"
        );
        try {
            NativeJavaGbFeatureRepository subject = NativeJavaGbFeatureRepository.builder()
                    .apiHost(apiHost(server))
                    .clientKey(TEST_CLIENT_KEY)
                    .isCacheDisabled(true)
                    .build();
            FeatureRefreshListener listener = mock(FeatureRefreshListener.class);
            subject.addFeatureRefreshListener(listener);

            subject.initialize();

            ArgumentCaptor<FeatureRefreshEvent> eventCaptor = ArgumentCaptor.forClass(FeatureRefreshEvent.class);
            verify(listener).onRefresh(eventCaptor.capture());
            FeatureRefreshEvent event = eventCaptor.getValue();
            assertTrue(event.isSuccessful());
            assertTrue(event.isFeaturesChanged());
            assertFalse(event.isLoadedFromCache());
            assertEquals(1, event.getActiveFeatureCount());
            assertEquals(FeatureRefreshSource.INITIALIZATION, event.getSource());
            assertTrue(event.getDurationMillis() >= 0);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void testFeatureRefreshListener_receivesManualNotModifiedMetadata() throws Exception {
        HttpServer server = startFeatureServer(HttpURLConnection.HTTP_NOT_MODIFIED, "");
        try {
            NativeJavaGbFeatureRepository subject = NativeJavaGbFeatureRepository.builder()
                    .apiHost(apiHost(server))
                    .clientKey(TEST_CLIENT_KEY)
                    .isCacheDisabled(true)
                    .build();
            FeatureRefreshListener listener = mock(FeatureRefreshListener.class);
            subject.addFeatureRefreshListener(listener);

            subject.fetchFeatures();

            ArgumentCaptor<FeatureRefreshEvent> eventCaptor = ArgumentCaptor.forClass(FeatureRefreshEvent.class);
            verify(listener).onRefresh(eventCaptor.capture());
            FeatureRefreshEvent event = eventCaptor.getValue();
            assertTrue(event.isSuccessful());
            assertFalse(event.isFeaturesChanged());
            assertFalse(event.isLoadedFromCache());
            assertEquals(0, event.getActiveFeatureCount());
            assertEquals(FeatureRefreshSource.MANUAL, event.getSource());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void testFeatureRefreshListener_continuesAfterListenerFailure() throws Exception {
        HttpServer server = startFeatureServer(HttpURLConnection.HTTP_OK, "{\"features\":{}}");
        try {
            NativeJavaGbFeatureRepository subject = NativeJavaGbFeatureRepository.builder()
                    .apiHost(apiHost(server))
                    .clientKey(TEST_CLIENT_KEY)
                    .isCacheDisabled(true)
                    .build();
            FeatureRefreshListener failingListener = mock(FeatureRefreshListener.class);
            FeatureRefreshListener successfulListener = mock(FeatureRefreshListener.class);
            doThrow(new RuntimeException("listener failed")).when(failingListener).onRefresh(any(FeatureRefreshEvent.class));

            subject.addFeatureRefreshListener(failingListener);
            subject.addFeatureRefreshListener(successfulListener);
            subject.fetchFeatures();

            verify(successfulListener).onRefresh(any(FeatureRefreshEvent.class));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void testRemoveFeatureRefreshListener_stopsNotifications() throws Exception {
        HttpServer server = startFeatureServer(HttpURLConnection.HTTP_OK, "{\"features\":{}}");
        try {
            NativeJavaGbFeatureRepository subject = NativeJavaGbFeatureRepository.builder()
                    .apiHost(apiHost(server))
                    .clientKey(TEST_CLIENT_KEY)
                    .isCacheDisabled(true)
                    .build();
            FeatureRefreshListener listener = mock(FeatureRefreshListener.class);

            subject.addFeatureRefreshListener(listener);
            subject.removeFeatureRefreshListener(listener);
            subject.fetchFeatures();

            verify(listener, never()).onRefresh(any(FeatureRefreshEvent.class));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void testFeatureRefreshListener_receivesCacheFallbackMetadata() throws Exception {
        HttpServer server = startFeatureServer(HttpURLConnection.HTTP_INTERNAL_ERROR, "{\"error\":\"temporary failure\"}");
        try {
            NativeJavaGbFeatureRepository subject = NativeJavaGbFeatureRepository.builder()
                    .apiHost(apiHost(server))
                    .clientKey(TEST_CLIENT_KEY)
                    .cacheManager(new StaticCacheManager("{\"features\":{\"cached-feature\":{\"defaultValue\":true}}}"))
                    .build();
            FeatureRefreshListener listener = mock(FeatureRefreshListener.class);
            subject.addFeatureRefreshListener(listener);

            subject.fetchFeatures();

            ArgumentCaptor<FeatureRefreshEvent> eventCaptor = ArgumentCaptor.forClass(FeatureRefreshEvent.class);
            verify(listener).onRefresh(eventCaptor.capture());
            FeatureRefreshEvent event = eventCaptor.getValue();
            assertFalse(event.isSuccessful());
            assertTrue(event.isFeaturesChanged());
            assertTrue(event.isLoadedFromCache());
            assertEquals(1, event.getActiveFeatureCount());
            assertEquals(FeatureRefreshSource.MANUAL, event.getSource());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void fetchFeaturesFails() throws Exception {
        HttpServer server = startFeatureServer(HttpURLConnection.HTTP_BAD_REQUEST, "{\"error\":\"bad request\"}");
        try {
            NativeJavaGbFeatureRepository subject = NativeJavaGbFeatureRepository.builder()
                    .apiHost(apiHost(server))
                    .clientKey(TEST_CLIENT_KEY)
                    .isCacheDisabled(true)
                    .build();

            assertThrows(FeatureFetchException.class, subject::fetchFeatures);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void initializeFails() throws Exception {
        HttpServer server = startFeatureServer(HttpURLConnection.HTTP_INTERNAL_ERROR, "{\"error\":\"temporary failure\"}");
        try {
            NativeJavaGbFeatureRepository subject = NativeJavaGbFeatureRepository.builder()
                    .apiHost(apiHost(server))
                    .clientKey(TEST_CLIENT_KEY)
                    .isCacheDisabled(true)
                    .build();

            assertThrows(FeatureFetchException.class, subject::initialize);
        } finally {
            server.stop(0);
        }
    }

    private static HttpServer startFeatureServer(int statusCode, String body) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/features/" + TEST_CLIENT_KEY, exchange -> {
            byte[] responseBytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add(HttpHeaders.X_SSE_SUPPORT.getHeader(), "enabled");
            if (statusCode == HttpURLConnection.HTTP_NOT_MODIFIED) {
                exchange.sendResponseHeaders(statusCode, -1);
            } else {
                exchange.sendResponseHeaders(statusCode, responseBytes.length);
                try (OutputStream responseBody = exchange.getResponseBody()) {
                    responseBody.write(responseBytes);
                }
            }
            exchange.close();
        });
        server.start();
        return server;
    }

    private static String apiHost(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static class StaticCacheManager implements GbCacheManager {
        private final String cachedData;

        private StaticCacheManager(String cachedData) {
            this.cachedData = cachedData;
        }

        @Override
        public void saveContent(String key, String data) {
        }

        @Override
        public String loadCache(String key) {
            return cachedData;
        }

        @Override
        public void clearCache() {
        }
    }
}
