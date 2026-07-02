package growthbook.sdk.java;

import growthbook.sdk.java.repository.FeatureRefreshStrategy;
import growthbook.sdk.java.repository.GBFeaturesRepository;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class GBFeaturesRepositoryEtagTest {
    private final String FAKE_ETAG = "v1-123456";
    private final String FAKE_JSON = "{\"features\":{\"test\":{\"defaultValue\":true}}}";
    private final String FAKE_URL = "http://localhost/api/features/sdk-abc123";

    @Test
    void usesEtagCache_WhenFeaturesNotExpired() throws Exception {
        OkHttpClient mockEtagClient = mockEtagClient(FAKE_JSON, FAKE_ETAG);

        GBFeaturesRepository subject = new GBFeaturesRepository(
                "http://localhost:80",
                "sdk-abc123",
                null,
                FeatureRefreshStrategy.STALE_WHILE_REVALIDATE,
                60,
                mockEtagClient,
                null, null, null, null
        );

        subject.initialize();

        verify(mockEtagClient, times(1)).newCall(any());
    }

    @Test
    void usesEtagCache_WhenCacheExpired_ButFeaturesNotModified() throws Exception {
        Integer ttlSeconds = 3;
        OkHttpClient mockEtagClient = mockEtagClient(FAKE_JSON, FAKE_ETAG);

        GBFeaturesRepository subject = new GBFeaturesRepository(
                "http://localhost:80",
                "sdk-abc123",
                null,
                FeatureRefreshStrategy.STALE_WHILE_REVALIDATE,
                ttlSeconds,
                mockEtagClient,
                null, null, null, null
        );

        subject.initialize();

        TimeUnit.SECONDS.sleep(ttlSeconds + 1);

        verify(mockEtagClient, times(2)).newCall(any());

        verify(mockEtagClient, times(1)).newCall(argThat(request -> request.url().toString().equals(FAKE_URL) &&
                request.header("If-None-Match") != null &&
                request.header("If-None-Match").equals(FAKE_ETAG)));

        assertTrue(subject.getFeaturesJson().contains("test"));
    }

    private static OkHttpClient mockEtagClient(final String jsonBody, final String expectedEtag) throws IOException {
        OkHttpClient okHttpClient = mock(OkHttpClient.class);

        when(okHttpClient.newCall(any(Request.class))).thenAnswer(invocation -> {
            Request request = invocation.getArgument(0);

            String ifNoneMatchHeader = request.header("If-None-Match");

            if (ifNoneMatchHeader != null && ifNoneMatchHeader.equals(expectedEtag)) {
                return mockCall(304, "Not Modified", null, null);
            } else {
                return mockCall(200, "OK", jsonBody, expectedEtag);
            }
        });

        return okHttpClient;
    }

    private static Call mockCall(int code, String message, String body, String etagHeader) throws IOException, IOException {
        Call call = mock(Call.class);
        Response.Builder responseBuilder = new Response.Builder()
                .request(new Request.Builder().url("http://url.com").build())
                .protocol(Protocol.HTTP_1_1)
                .code(code)
                .message(message);

        if (body != null) {
            responseBuilder.body(
                    ResponseBody.create(body, MediaType.parse("application/json"))
            );
        }

        if (etagHeader != null) {
            responseBuilder.header("ETag", etagHeader);
        }

        when(call.execute()).thenReturn(responseBuilder.build());
        return call;
    }
}
