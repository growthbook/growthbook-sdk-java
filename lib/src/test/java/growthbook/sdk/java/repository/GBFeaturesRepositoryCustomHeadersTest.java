package growthbook.sdk.java.repository;

import growthbook.sdk.java.exception.FeatureFetchException;
import okhttp3.Call;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

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
    @DisplayName("Verify: the SSE endpoint falls back to apiHost when streamingHost is unset")
    void eventsEndpointFallsBackToApiHost() {
        GBFeaturesRepository subject = GBFeaturesRepository.builder()
                .apiHost("https://api.example.com")
                .clientKey("sdk-abc123")
                .build();

        assertEquals("https://api.example.com/sub/sdk-abc123", subject.getEventsEndpoint());
    }

    private static void stubFeaturesResponse(OkHttpClient mockHttpClient) throws IOException {
        Call mockCall = mock(Call.class);
        Response mockResponse = mock(Response.class);
        ResponseBody mockResponseBody = mock(ResponseBody.class);

        when(mockHttpClient.newCall(any(Request.class))).thenReturn(mockCall);
        when(mockCall.execute()).thenReturn(mockResponse);
        when(mockResponse.code()).thenReturn(200);
        when(mockResponse.header(anyString())).thenReturn(null);
        when(mockResponse.body()).thenReturn(mockResponseBody);
        when(mockResponseBody.string()).thenReturn(FEATURES_BODY);
    }

    private static Request captureRequest(OkHttpClient mockHttpClient) throws FeatureFetchException {
        ArgumentCaptor<Request> requestCaptor = ArgumentCaptor.forClass(Request.class);
        verify(mockHttpClient).newCall(requestCaptor.capture());
        return requestCaptor.getValue();
    }
}
