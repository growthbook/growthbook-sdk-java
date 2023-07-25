package growthbook.sdk.java;

import lombok.Builder;
import lombok.Getter;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.sse.EventSource;
import okhttp3.sse.EventSourceListener;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.ArrayList;

/**
 * This class can be created with its `builder()` or constructor.
 * It will fetch the features from the endpoint provided using server-sent events.
 * It provides real-time feature updates by subscribing to GrowthBook's server-sent events (SSE)
 * Initialize with {@link GBFeaturesRepository#initialize()}
 * Get the features JSON with {@link GBRealTimeFeaturesRepository#getFeaturesJson()}.
 * You would provide the features JSON when creating the {@link GBContext}
 */
public class GBRealTimeFeaturesRepository implements IGBFeaturesRepository {
    @Getter
    private final String endpoint;

    @Nullable
    @Getter
    private final String encryptionKey;

    private final OkHttpClient okHttpClient;

    private final ArrayList<FeatureRefreshCallback> refreshCallbacks = new ArrayList<>();

    /**
     * Allows you to get the features JSON from the provided {@link GBRealTimeFeaturesRepository#getEndpoint()}.
     * You must call {@link GBRealTimeFeaturesRepository#initialize()} before calling this method
     * or your features would not have loaded.
     */
    private String featuresJson = "{}";

    private Boolean initialized = false;

    final private GBEventSourceListener eventSourceListener;

    @Builder
    public GBRealTimeFeaturesRepository(
        String apiHost,
        String clientKey,
        @Nullable String encryptionKey
    ) {
        if (apiHost == null || clientKey == null) {
            throw new IllegalArgumentException("endpoint cannot be null");
        }

        this.endpoint = apiHost + "/sub/" + clientKey;
        this.encryptionKey = encryptionKey;
        this.okHttpClient = this.initializeHttpClient();
        this.eventSourceListener = new GBEventSourceListener();
    }

    private OkHttpClient initializeHttpClient() {
        OkHttpClient client = new OkHttpClient.Builder()
            .addInterceptor(new GBFeaturesRepositoryRequestInterceptor())
//            .retryOnConnectionFailure(true)
            .build();

        return client;
    }

    @Override
    public void initialize() throws FeatureFetchException {
        if (this.initialized) return;

        // todo:

        Request request = new Request.Builder()
            .url(this.endpoint)
            .addHeader("Accept", "text/event-stream")
            .build();

//        eventSourceListener.onEvent();

        this.initialized = true;
    }

    @Override
    public String getFeaturesJson() {
        // todo:
        return null;
    }

    @Override
    public void onFeaturesRefresh(FeatureRefreshCallback callback) {
        this.refreshCallbacks.add(callback);
    }

    @Override
    public void clearCallbacks() {
        this.refreshCallbacks.clear();
    }

    private static class GBEventSourceListener extends EventSourceListener {
//        final private FeatureRefreshCallback callback;
//
//        public GBEventSourceListener(FeatureRefreshCallback callback) {
//            super();
//            this.callback = callback;
//        }

        public GBEventSourceListener() {
        }

        @Override
        public void onClosed(@NotNull EventSource eventSource) {
            System.out.printf("\n\nonClosed %s \n\n", eventSource);
            super.onClosed(eventSource);
        }

        @Override
        public void onEvent(@NotNull EventSource eventSource, @Nullable String id, @Nullable String type, @NotNull String data) {
            System.out.printf("\n\n eventsource = %s - id = %s - type = %s - data = %s \n\n", eventSource, id, type, data);
            super.onEvent(eventSource, id, type, data);
        }

        @Override
        public void onFailure(@NotNull EventSource eventSource, @Nullable Throwable t, @Nullable Response response) {
            System.out.printf("\n\n eventsource = %s , error = %s , response = %s\n\n", eventSource, t, response);
            super.onFailure(eventSource, t, response);
        }

        @Override
        public void onOpen(@NotNull EventSource eventSource, @NotNull Response response) {
            System.out.printf("\n\n eventsource = %s , response = %s\n\n", eventSource, response);
            super.onOpen(eventSource, response);
        }
    }
}
