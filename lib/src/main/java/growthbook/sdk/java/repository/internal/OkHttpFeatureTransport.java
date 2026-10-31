package growthbook.sdk.java.repository.internal;

import growthbook.sdk.java.model.HttpHeaders;
import growthbook.sdk.java.repository.GBFeaturesRepositoryRequestInterceptor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import okhttp3.sse.EventSources;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * {@link FeatureTransport} over OkHttp. Feature fetches use the given client; events use a
 * second client without timeouts and without a cache, created on first use and released by
 * {@link #closeEvents()}.
 */
@Slf4j
public final class OkHttpFeatureTransport implements FeatureTransport {

    private static final MediaType JSON = MediaType.parse("application/json");

    private final OkHttpClient httpClient;
    private final AtomicReference<OkHttpClient> eventsClient = new AtomicReference<>();

    /**
     * @param httpClient the client for feature fetches, or {@code null} for a default one that
     *                   retries on connection failure and sends the SDK's User-Agent
     */
    public OkHttpFeatureTransport(@Nullable OkHttpClient httpClient) {
        this.httpClient = httpClient != null ? httpClient : new OkHttpClient.Builder()
                .addInterceptor(new GBFeaturesRepositoryRequestInterceptor())
                .retryOnConnectionFailure(true)
                .build();
    }

    @Override
    public TransportResponse get(String url, Map<String, String> headers) throws IOException {
        Request.Builder request = new Request.Builder().url(url);
        headers.forEach(request::header);
        return execute(request.build());
    }

    @Override
    public TransportResponse postJson(String url, String json) throws IOException {
        return execute(new Request.Builder().url(url).post(RequestBody.create(json, JSON)).build());
    }

    @Override
    public SseConnection openEvents(String url, SseListener listener) {
        Request request = new Request.Builder()
                .url(url)
                .header(HttpHeaders.ACCEPT.getHeader(), HttpHeaders.APPLICATION_JSON.getHeader())
                .addHeader(HttpHeaders.ACCEPT.getHeader(), HttpHeaders.SSE_HEADER.getHeader())
                .build();
        EventSource eventSource = EventSources.createFactory(eventsClient())
                .newEventSource(request, new ListenerAdapter(listener));
        return eventSource::cancel;
    }

    @Override
    public void closeEvents() {
        OkHttpClient client = eventsClient.getAndSet(null);
        if (client == null) {
            return;
        }
        client.dispatcher().cancelAll();
        client.connectionPool().evictAll();
        client.dispatcher().executorService().shutdown();
        log.info("SseHttpClient shutdown");
    }

    /**
     * @return the client for feature fetches
     */
    public OkHttpClient httpClient() {
        return httpClient;
    }

    /**
     * @return the client for events, or {@code null} before the first connection or after
     * {@link #closeEvents()}
     */
    @Nullable
    public OkHttpClient eventsClientIfCreated() {
        return eventsClient.get();
    }

    private OkHttpClient eventsClient() {
        OkHttpClient existing = eventsClient.get();
        if (existing != null) {
            return existing;
        }
        OkHttpClient created = new OkHttpClient.Builder()
                .addInterceptor(new GBFeaturesRepositoryRequestInterceptor())
                .retryOnConnectionFailure(false)
                .connectTimeout(0, TimeUnit.SECONDS)
                .readTimeout(0, TimeUnit.SECONDS)
                .writeTimeout(0, TimeUnit.SECONDS)
                .build();
        if (eventsClient.compareAndSet(null, created)) {
            return created;
        }
        created.dispatcher().executorService().shutdown();
        return eventsClient.get();
    }

    private TransportResponse execute(Request request) throws IOException {
        try (Response response = httpClient.newCall(request).execute()) {
            ResponseBody body = response.body();
            return new TransportResponse(response.code(), response.message(), body != null ? body.string() : null, response::header);
        }
    }

    private static final class ListenerAdapter extends EventSourceListener {
        private final SseListener listener;

        ListenerAdapter(SseListener listener) {
            this.listener = listener;
        }

        @Override
        public void onOpen(@NotNull EventSource eventSource, @NotNull Response response) {
            listener.onOpen();
        }

        @Override
        public void onEvent(@NotNull EventSource eventSource, @Nullable String id, @Nullable String type, @NotNull String data) {
            listener.onEvent(type, data);
        }

        @Override
        public void onClosed(@NotNull EventSource eventSource) {
            listener.onClosed();
        }

        @Override
        public void onFailure(@NotNull EventSource eventSource, @Nullable Throwable t, @Nullable Response response) {
            listener.onFailure(t);
        }
    }
}
