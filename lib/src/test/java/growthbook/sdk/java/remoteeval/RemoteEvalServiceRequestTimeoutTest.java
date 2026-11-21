package growthbook.sdk.java.remoteeval;

import okhttp3.OkHttpClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Verifies that {@code requestTimeout} configures the client {@link RemoteEvalService} creates: the
 * whole-call timeout ({@code callTimeout}) and connect/read/write are all bounded, the default is
 * applied when unset, and a negative value is rejected.
 */
class RemoteEvalServiceRequestTimeoutTest {

    private static final String API_HOST = "https://cdn.growthbook.io";
    private static final String CLIENT_KEY = "sdk-remote-eval-timeout";

    @Test
    @DisplayName("Verify: an explicit requestTimeout bounds the whole call and connect/read/write")
    void explicitTimeoutBoundsCall() throws Exception {
        RemoteEvalService service = new RemoteEvalService(API_HOST, CLIENT_KEY, Duration.ofMillis(300));
        try {
            OkHttpClient client = client(service);
            assertEquals(300, client.callTimeoutMillis());
            assertEquals(300, client.connectTimeoutMillis());
            assertEquals(300, client.readTimeoutMillis());
            assertEquals(300, client.writeTimeoutMillis());
        } finally {
            service.close();
        }
    }

    @Test
    @DisplayName("Verify: an unset requestTimeout applies the 10s default to the remote-eval client")
    void defaultTimeoutAppliedWhenUnset() throws Exception {
        RemoteEvalService service = new RemoteEvalService(API_HOST, CLIENT_KEY);
        try {
            OkHttpClient client = client(service);
            assertEquals(10_000, client.callTimeoutMillis());
            assertEquals(10_000, client.connectTimeoutMillis());
            assertEquals(10_000, client.readTimeoutMillis());
            assertEquals(10_000, client.writeTimeoutMillis());
        } finally {
            service.close();
        }
    }

    @Test
    @DisplayName("Verify: a negative requestTimeout is rejected at construction")
    void rejectsNegativeRequestTimeout() {
        assertThrows(IllegalArgumentException.class, () ->
                new RemoteEvalService(API_HOST, CLIENT_KEY, Duration.ofMillis(-1)));
    }

    private static OkHttpClient client(RemoteEvalService service) throws Exception {
        Field field = RemoteEvalService.class.getDeclaredField("okHttpClient");
        field.setAccessible(true);
        return (OkHttpClient) field.get(service);
    }
}
