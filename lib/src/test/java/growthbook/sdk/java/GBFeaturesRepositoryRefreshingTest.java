package growthbook.sdk.java;

import okhttp3.*;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;


/**
 * // TODO: Fix concurrency issue with these tests and re-enable
 * These tests use Thread.sleep() to pass time to test the callback logic.
 * When the whole test suite is run, one of them always fails.
 * When that failing test is run on its own, it passes.
 * To run these tests individually, comment out the @Disabled annotation and run each individually
 */
@Disabled("There is a concurrency issue with these tests due to Thread.sleep() calls when run within a test suite. Run individually manually.")
public class GBFeaturesRepositoryRefreshingTest {

    @Test
    void refreshesFeaturesWhenGetFeaturesCalledAfterCacheExpired() throws IOException, FeatureFetchException, InterruptedException {
        Integer ttlSeconds = 5; // cache invalidates every 5 seconds
        String fakeResponseJson = "{\n" +
            "  \"status\": 200,\n" +
            "  \"features\": {},\n" +
            "  \"dateUpdated\": \"2023-01-25T00:51:26.772Z\",\n" +
            "  \"encryptedFeatures\": \"jfLnSxjChWcbyHaIF30RNw==.iz8DywkSk4+WhNqnIwvr/PdvAwaRNjN3RE30JeOezGAQ/zZ2yoVyVo4w0nLHYqOje5MbhmL0ssvlH0ojk/BxqdSzXD4Wzo3DXfKV81Nzi1aSdiCMnVAIYEzjPl1IKZC3fl88YDBNV3F6YnR9Lemy9yzT03cvMZ0NZ9t5LZO2xS2MhpPYNcAfAlfxXhBGXj6UFDoNKGAtGKdc/zmJsUVQGLtHmqLspVynnJlPPo9nXG+87bt6SjSfQfySUgHm28hb4VmDhVmCx0N37buolVr3pzjZ1QK+tyMKIV7x4/Gu06k8sm0eU4HjG5DFsPgTR7qDu/N5Nk5UTRpG7aSXTUErxhHSJ7MQaxH/Dp/71zVEicaJ0qZE3oPRnU187QVBfdVLLRbqq2QU7Yu0GyJ1jjuf6TA+759OgifHdm17SX43L94Qe62CMU7JQyAqt7h7XmTTQBG664HYwgHJ0ju/9jySC4KUlRxNsixH1tJfznnEXqxgSozn4J61UprTqcmlxLZ1hZPCcRew3mm9DMAG9+YEiL8MhaIwsw8oVq9GirN1S8G3m/6UxQHxZVraPvMRXpGt5VpzEDJ0Po+phrIAhPuIbNpgb08b6Ej4Xh9XXeOLtIcpuj+gNpc4pR4tqF2IOwET\"\n" +
            "}";
        String encryptionKey = "o0maZL/O7AphxcbRvaJIzw==";
        OkHttpClient mockOkHttpClient = mockHttpClient(fakeResponseJson);
        GBFeaturesRepository subject = new GBFeaturesRepository(
            mockOkHttpClient,
            "http://localhost:80",
            encryptionKey,
            ttlSeconds
        );
        subject.initialize();

        // Advance time 3 seconds. We are still within the cache TTL so it should not trigger a refresh.
        sleepSeconds(3);
        subject.getFeaturesJson();

        // Advance time 3 seconds. We are now at 6 seconds which is 1 second past, so it should trigger a refresh
        sleepSeconds(3);
        subject.getFeaturesJson();

        // Calls:
        //  1 = initial .execute()
        //  2 = refresh .enqueue()
        verify(mockOkHttpClient.newCall(any()), times(2));
    }

    @Test()
    void doesNotRefreshFeaturesWhenGetFeaturesCalledWithinCacheTime() throws IOException, FeatureFetchException, InterruptedException {
        Integer ttlSeconds = 5; // cache invalidates every 5 seconds
        String fakeResponseJson = "{\n" +
            "  \"status\": 200,\n" +
            "  \"features\": {},\n" +
            "  \"dateUpdated\": \"2024-01-25T00:51:26.772Z\",\n" +
            "  \"encryptedFeatures\": \"jfLnSxjChWcbyHaIF30RNw==.iz8DywkSk4+WhNqnIwvr/PdvAwaRNjN3RE30JeOezGAQ/zZ2yoVyVo4w0nLHYqOje5MbhmL0ssvlH0ojk/BxqdSzXD4Wzo3DXfKV81Nzi1aSdiCMnVAIYEzjPl1IKZC3fl88YDBNV3F6YnR9Lemy9yzT03cvMZ0NZ9t5LZO2xS2MhpPYNcAfAlfxXhBGXj6UFDoNKGAtGKdc/zmJsUVQGLtHmqLspVynnJlPPo9nXG+87bt6SjSfQfySUgHm28hb4VmDhVmCx0N37buolVr3pzjZ1QK+tyMKIV7x4/Gu06k8sm0eU4HjG5DFsPgTR7qDu/N5Nk5UTRpG7aSXTUErxhHSJ7MQaxH/Dp/71zVEicaJ0qZE3oPRnU187QVBfdVLLRbqq2QU7Yu0GyJ1jjuf6TA+759OgifHdm17SX43L94Qe62CMU7JQyAqt7h7XmTTQBG664HYwgHJ0ju/9jySC4KUlRxNsixH1tJfznnEXqxgSozn4J61UprTqcmlxLZ1hZPCcRew3mm9DMAG9+YEiL8MhaIwsw8oVq9GirN1S8G3m/6UxQHxZVraPvMRXpGt5VpzEDJ0Po+phrIAhPuIbNpgb08b6Ej4Xh9XXeOLtIcpuj+gNpc4pR4tqF2IOwET\"\n" +
            "}";
        String encryptionKey = "o0maZL/O7AphxcbRvaJIzw==";
        OkHttpClient mockOkHttpClient = mockHttpClient(fakeResponseJson);
        GBFeaturesRepository subject = new GBFeaturesRepository(
            mockOkHttpClient,
            "http://localhost:80",
            encryptionKey,
            ttlSeconds
        );
        subject.initialize();

        // Advance time 2 seconds. We are still within the cache TTL so it should not trigger a refresh.
        sleepSeconds(2);
        subject.getFeaturesJson();

        // Advance time 2 seconds. We are now at 4 seconds which is still under the TTL of 5 seconds.
        sleepSeconds(2);
        subject.getFeaturesJson();

        // Calls:
        //  1 = initial .execute()
        verify(mockOkHttpClient, times(1)).newCall(any());
    }

    @Test
    void refreshesFeaturesWhenGetFeaturesCalledAfterCacheExpired_multipleTimes() throws IOException, FeatureFetchException, InterruptedException {
        Integer ttlSeconds = 5; // cache invalidates every 5 seconds
        String fakeResponseJson = "{\n" +
            "  \"status\": 200,\n" +
            "  \"features\": {},\n" +
            "  \"dateUpdated\": \"2023-01-25T00:51:26.772Z\",\n" +
            "  \"encryptedFeatures\": \"jfLnSxjChWcbyHaIF30RNw==.iz8DywkSk4+WhNqnIwvr/PdvAwaRNjN3RE30JeOezGAQ/zZ2yoVyVo4w0nLHYqOje5MbhmL0ssvlH0ojk/BxqdSzXD4Wzo3DXfKV81Nzi1aSdiCMnVAIYEzjPl1IKZC3fl88YDBNV3F6YnR9Lemy9yzT03cvMZ0NZ9t5LZO2xS2MhpPYNcAfAlfxXhBGXj6UFDoNKGAtGKdc/zmJsUVQGLtHmqLspVynnJlPPo9nXG+87bt6SjSfQfySUgHm28hb4VmDhVmCx0N37buolVr3pzjZ1QK+tyMKIV7x4/Gu06k8sm0eU4HjG5DFsPgTR7qDu/N5Nk5UTRpG7aSXTUErxhHSJ7MQaxH/Dp/71zVEicaJ0qZE3oPRnU187QVBfdVLLRbqq2QU7Yu0GyJ1jjuf6TA+759OgifHdm17SX43L94Qe62CMU7JQyAqt7h7XmTTQBG664HYwgHJ0ju/9jySC4KUlRxNsixH1tJfznnEXqxgSozn4J61UprTqcmlxLZ1hZPCcRew3mm9DMAG9+YEiL8MhaIwsw8oVq9GirN1S8G3m/6UxQHxZVraPvMRXpGt5VpzEDJ0Po+phrIAhPuIbNpgb08b6Ej4Xh9XXeOLtIcpuj+gNpc4pR4tqF2IOwET\"\n" +
            "}";
        String encryptionKey = "o0maZL/O7AphxcbRvaJIzw==";
        OkHttpClient mockOkHttpClient = mockHttpClient(fakeResponseJson);
        GBFeaturesRepository subject = new GBFeaturesRepository(
            mockOkHttpClient,
            "http://localhost:80",
            encryptionKey,
            ttlSeconds
        );
        subject.initialize();

        // Advance time 3 seconds. We are still within the cache TTL so it should not trigger a refresh.
        sleepSeconds(3);
        subject.getFeaturesJson();

        // Advance time 3 seconds. We are now at 6 seconds which is 1 second past, so it should trigger a refresh
        sleepSeconds(3);
        subject.getFeaturesJson();

        // Advance time another 5 seconds, which should trigger another refresh
        sleepSeconds(5);
        subject.getFeaturesJson();

        // Advance time another 5 seconds, which should trigger another refresh
        sleepSeconds(5);
        subject.getFeaturesJson();

        // Calls:
        //  1 = initial .execute()
        //  2 = refresh .enqueue()
        //  3 = refresh .enqueue()
        //  4 = refresh .enqueue()
        verify(mockOkHttpClient, times(4)).newCall(any());
    }

    /**
     * Create a mock instance of {@link OkHttpClient}
     * @param serializedBody JSON string response
     * @return mock {@link OkHttpClient}
     */
    private static OkHttpClient mockHttpClient(final String serializedBody) throws IOException {
        OkHttpClient okHttpClient = mock(OkHttpClient.class);

        Call remoteCall = mock(Call.class);

        Response response = new Response.Builder()
            .request(new Request.Builder().url("http://url.com").build())
            .protocol(Protocol.HTTP_1_1)
            .code(200).message("").body(
                ResponseBody.create(
                    serializedBody,
                    MediaType.parse("application/json")
                ))
            .build();

        when(remoteCall.execute()).thenReturn(response);
        when(okHttpClient.newCall(any())).thenReturn(remoteCall);

        return okHttpClient;
    }

    private void sleepSeconds(Integer seconds) throws InterruptedException {
        TimeUnit.SECONDS.sleep(seconds);
    }
}
