package growthbook.sdk.java.repository;

import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.model.RequestBodyForRemoteEval;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Verifies the custom request header and dedicated streaming host behavior of
 * {@link GBFeaturesRepository}: custom {@code apiHostRequestHeaders} and the SDK
 * User-Agent are sent on the features GET, reserved SDK-managed headers are dropped,
 * and the SSE endpoint is built from {@code streamingHost} when supplied.
 */
class GBFeaturesRepositoryCustomHeadersTest {

    private static final String FEATURES_BODY = "{\"status\":200,\"features\":{\"flag\":{\"defaultValue\":true}}}";

    @Test
    @DisplayName("Verify: custom apiHostRequestHeaders and the SDK User-Agent are sent on the features GET")
    void featuresGetCarriesCustomHeaders() throws Exception {
        OkHttpClient mockHttpClient = mock(OkHttpClient.class);
        stubFeaturesResponse(mockHttpClient);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer proxy-token");
        headers.put("X-Gateway-Key", "gateway-value");

        GBFeaturesRepository subject = GBFeaturesRepository.builder()
                .apiHost("http://localhost")
                .clientKey("sdk-abc123")
                .refreshStrategy(FeatureRefreshStrategy.STALE_WHILE_REVALIDATE)
                .isCacheDisabled(true)
                .okHttpClient(mockHttpClient)
                .apiHostRequestHeaders(headers)
                .build();

        subject.fetchFeatures();

        Request request = captureRequest(mockHttpClient);
        assertEquals("Bearer proxy-token", request.header("Authorization"));
        assertEquals("gateway-value", request.header("X-Gateway-Key"));
        assertNotNull(request.header("User-Agent"));
        assertTrue(request.header("User-Agent").startsWith("growthbook-sdk-java/"), request.header("User-Agent"));
    }

    @Test
    @DisplayName("Verify: a reserved header in apiHostRequestHeaders is dropped and the SDK value wins")
    void reservedHeaderIsDropped() throws Exception {
        OkHttpClient mockHttpClient = mock(OkHttpClient.class);
        stubFeaturesResponse(mockHttpClient);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("User-Agent", "custom-agent/1.0");
        headers.put("Authorization", "Bearer proxy-token");

        GBFeaturesRepository subject = GBFeaturesRepository.builder()
                .apiHost("http://localhost")
                .clientKey("sdk-abc123")
                .refreshStrategy(FeatureRefreshStrategy.STALE_WHILE_REVALIDATE)
                .isCacheDisabled(true)
                .okHttpClient(mockHttpClient)
                .apiHostRequestHeaders(headers)
                .build();

        subject.fetchFeatures();

        Request request = captureRequest(mockHttpClient);
        assertTrue(request.header("User-Agent").startsWith("growthbook-sdk-java/"), request.header("User-Agent"));
        assertEquals("Bearer proxy-token", request.header("Authorization"));
    }

    @Test
    @DisplayName("Verify: a custom Accept header is allowed on apiHostRequestHeaders and sent on the features GET")
    void customAcceptHeaderIsSentOnFeaturesGet() throws Exception {
        OkHttpClient mockHttpClient = mock(OkHttpClient.class);
        stubFeaturesResponse(mockHttpClient);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/vnd.gateway+json");
        headers.put("Authorization", "Bearer proxy-token");

        GBFeaturesRepository subject = GBFeaturesRepository.builder()
                .apiHost("http://localhost")
                .clientKey("sdk-abc123")
                .refreshStrategy(FeatureRefreshStrategy.STALE_WHILE_REVALIDATE)
                .isCacheDisabled(true)
                .okHttpClient(mockHttpClient)
                .apiHostRequestHeaders(headers)
                .build();

        subject.fetchFeatures();

        Request request = captureRequest(mockHttpClient);
        assertEquals("application/vnd.gateway+json", request.header("Accept"));
        assertEquals("Bearer proxy-token", request.header("Authorization"));
    }

    @Test
    @DisplayName("Verify: the SSE endpoint is built from streamingHost when supplied")
    void streamingHostDrivesEventsEndpoint() {
        GBFeaturesRepository subject = GBFeaturesRepository.builder()
                .apiHost("https://api.example.com")
                .clientKey("sdk-abc123")
                .streamingHost("https://beacon.growthbook.io")
                .build();

        assertEquals("https://beacon.growthbook.io/sub/sdk-abc123", subject.getEventsEndpoint());
    }

    @Test
    @DisplayName("Verify: a scheme-less streamingHost is normalized with https:// for the SSE endpoint")
    void schemelessStreamingHostIsNormalized() {
        GBFeaturesRepository subject = GBFeaturesRepository.builder()
                .apiHost("https://api.example.com")
                .clientKey("sdk-abc123")
                .streamingHost("beacon.growthbook.io")
                .build();

        assertEquals("https://beacon.growthbook.io/sub/sdk-abc123", subject.getEventsEndpoint());
    }

    @Test
    @DisplayName("Verify: a trailing slash on streamingHost does not double the streaming path")
    void trailingSlashStreamingHostIsNormalized() {
        GBFeaturesRepository subject = GBFeaturesRepository.builder()
                .apiHost("https://api.example.com")
                .clientKey("sdk-abc123")
                .streamingHost("https://beacon.growthbook.io/")
                .build();

        assertEquals("https://beacon.growthbook.io/sub/sdk-abc123", subject.getEventsEndpoint());
    }

    @Test
    @DisplayName("Verify: the SSE endpoint falls back to apiHost when streamingHost is unset")
    void eventsEndpointFallsBackToApiHost() {
        GBFeaturesRepository subject = GBFeaturesRepository.builder()
                .apiHost("https://api.example.com")
                .clientKey("sdk-abc123")
                .build();

        assertEquals("https://api.example.com/sub/sdk-abc123", subject.getEventsEndpoint());
    }

    @Test
    @DisplayName("Verify: streamingHostRequestHeaders and the SDK User-Agent are sent on the SSE request")
    void sseRequestCarriesStreamingHeaders() throws Exception {
        OkHttpClient mockSseHttpClient = mock(OkHttpClient.class);
        when(mockSseHttpClient.newCall(any(Request.class))).thenReturn(mock(Call.class));

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer stream-token");
        headers.put("X-Gateway-Key", "gateway-value");

        GBFeaturesRepository subject = GBFeaturesRepository.builder()
                .apiHost("http://localhost")
                .clientKey("sdk-abc123")
                .streamingHost("http://localhost")
                .streamingHostRequestHeaders(headers)
                .refreshStrategy(FeatureRefreshStrategy.SERVER_SENT_EVENTS)
                .build();

        setInternalField(subject, "sseHttpClient", mockSseHttpClient);

        Method connect = GBFeaturesRepository.class
                .getDeclaredMethod("createEventSourceListenerAndStartListening", Boolean.class);
        connect.setAccessible(true);
        connect.invoke(subject, false);

        Request sseRequest = (Request) getInternalField(subject, "sseRequest");

        assertNotNull(sseRequest);
        assertEquals("Bearer stream-token", sseRequest.header("Authorization"));
        assertEquals("gateway-value", sseRequest.header("X-Gateway-Key"));
        assertNotNull(sseRequest.header("User-Agent"));
        assertTrue(sseRequest.header("User-Agent").startsWith("growthbook-sdk-java/"), sseRequest.header("User-Agent"));
    }

    @Test
    @DisplayName("Verify: custom apiHostRequestHeaders and the SDK User-Agent are sent on the remote-evaluation POST")
    void remoteEvalPostCarriesCustomHeaders() throws Exception {
        OkHttpClient mockHttpClient = mock(OkHttpClient.class);
        stubFeaturesResponse(mockHttpClient);

        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Authorization", "Bearer proxy-token");
        headers.put("X-Gateway-Key", "gateway-value");

        GBFeaturesRepository subject = GBFeaturesRepository.builder()
                .apiHost("http://localhost")
                .clientKey("sdk-abc123")
                .isCacheDisabled(true)
                .okHttpClient(mockHttpClient)
                .apiHostRequestHeaders(headers)
                .build();

        subject.fetchForRemoteEval(new RequestBodyForRemoteEval());

        Request request = captureRequest(mockHttpClient);
        assertEquals("POST", request.method());
        assertEquals("Bearer proxy-token", request.header("Authorization"));
        assertEquals("gateway-value", request.header("X-Gateway-Key"));
        assertNotNull(request.header("User-Agent"));
        assertTrue(request.header("User-Agent").startsWith("growthbook-sdk-java/"), request.header("User-Agent"));
    }

    private static void stubFeaturesResponse(OkHttpClient mockHttpClient) throws IOException {
        Call mockCall = mock(Call.class);
        Response mockResponse = mock(Response.class);
        ResponseBody mockResponseBody = mock(ResponseBody.class);

        when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);
        when(mockCall.execute()).thenReturn(mockResponse);
        when(mockResponse.isSuccessful()).thenReturn(true);
        when(mockResponse.code()).thenReturn(200);
        when(mockResponse.header(anyString())).thenReturn(null);
        when(mockResponse.body()).thenReturn(mockResponseBody);
        when(mockResponseBody.string()).thenReturn(FEATURES_BODY);
    }

    // The SSE client/request fields are a plain reference on this branch but an AtomicReference once
    // main's thread-safe lifecycle changes are merged; handle both so the test survives the merge.
    @SuppressWarnings("unchecked")
    private static void setInternalField(Object target, String name, Object value) throws Exception {
        Field field = GBFeaturesRepository.class.getDeclaredField(name);
        field.setAccessible(true);
        Object current = field.get(target);
        if (current instanceof AtomicReference) {
            ((AtomicReference<Object>) current).set(value);
        } else {
            field.set(target, value);
        }
    }

    private static Object getInternalField(Object target, String name) throws Exception {
        Field field = GBFeaturesRepository.class.getDeclaredField(name);
        field.setAccessible(true);
        Object current = field.get(target);
        return current instanceof AtomicReference ? ((AtomicReference<?>) current).get() : current;
    }

    private static Request captureRequest(OkHttpClient mockHttpClient) throws FeatureFetchException {
        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(mockHttpClient).newCall(requestCaptor.capture());
        return requestCaptor.getValue();
    }
}
