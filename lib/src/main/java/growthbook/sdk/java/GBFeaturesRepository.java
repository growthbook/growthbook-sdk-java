package growthbook.sdk.java;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Builder;
import lombok.Getter;
import okhttp3.*;
import okhttp3.sse.EventSources;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * This class can be created with its `builder()` or constructor.
 * It will fetch the features from the endpoint provided.
 * Initialize with {@link GBFeaturesRepository#initialize()}
 * Get the features JSON with {@link GBFeaturesRepository#getFeaturesJson()}.
 * You would provide the features JSON when creating the {@link GBContext}
 */
public class GBFeaturesRepository implements IGBFeaturesRepository {

    @Getter
    private final String featuresEndpoint;

    @Getter
    private final String eventsEndpoint;

    @Getter
    private final FeatureRefreshStrategy refreshStrategy;

    @Nullable @Getter
    private final String encryptionKey;

    @Getter
    private final Integer swrTtlSeconds;

    @Getter
    private Long expiresAt;

    private final OkHttpClient okHttpClient;

    @Nullable
    private OkHttpClient sseHttpClient;

    private final ArrayList<FeatureRefreshCallback> refreshCallbacks = new ArrayList<>();

    private Boolean initialized = false;

    private Boolean sseAllowed = false;

    /**
     * Allows you to get the features JSON from the provided {@link GBFeaturesRepository#getFeaturesEndpoint()}.
     * You must call {@link GBFeaturesRepository#initialize()} before calling this method
     * or your features would not have loaded.
     */
    private String featuresJson = "{}";

    /**
     * Create a new GBFeaturesRepository
     * @param apiHost The GrowthBook API host (default: https://cdn.growthbook.io)
     * @param clientKey Your client ID, e.g. sdk-abc123
     * @param encryptionKey optional key for decrypting encrypted payload
     * @param swrTtlSeconds How often the cache should be invalidated when using {@link FeatureRefreshStrategy#STALE_WHILE_REVALIDATE} (default: 60)
     */
    public GBFeaturesRepository(
        @Nullable String apiHost,
        String clientKey,
        @Nullable String encryptionKey,
        @Nullable FeatureRefreshStrategy refreshStrategy,
        @Nullable Integer swrTtlSeconds
    ) {
        this(apiHost, clientKey, encryptionKey, refreshStrategy, swrTtlSeconds, null);
    }

    /**
     * Create a new GBFeaturesRepository
     * @param apiHost The GrowthBook API host (default: https://cdn.growthbook.io)
     * @param clientKey Your client ID, e.g. sdk-abc123
     * @param encryptionKey optional key for decrypting encrypted payload
     * @param swrTtlSeconds How often the cache should be invalidated when using {@link FeatureRefreshStrategy#STALE_WHILE_REVALIDATE} (default: 60)
     * @param okHttpClient HTTP client (optional)
     */
    @Builder
    public GBFeaturesRepository(
        @Nullable String apiHost,
        String clientKey,
        @Nullable String encryptionKey,
        @Nullable FeatureRefreshStrategy refreshStrategy,
        @Nullable Integer swrTtlSeconds,
        @Nullable OkHttpClient okHttpClient
    ) {
        if (clientKey == null) throw new IllegalArgumentException("clientKey cannot be null");

        // Set the defaults when the user does not provide them
        if (apiHost == null) {
            apiHost = "https://cdn.growthbook.io";
        }
        this.refreshStrategy = refreshStrategy == null ? FeatureRefreshStrategy.STALE_WHILE_REVALIDATE : refreshStrategy;

        // Build the endpoints from the apiHost and clientKey
        this.featuresEndpoint = apiHost + "/api/features/" + clientKey;
        this.eventsEndpoint = apiHost + "/sub/" + clientKey;

        this.encryptionKey = encryptionKey;
        this.swrTtlSeconds = swrTtlSeconds == null ? 60 : swrTtlSeconds;
        this.refreshExpiresAt();

        // Use provided OkHttpClient or create a new one
        if (okHttpClient == null) {
            this.okHttpClient = this.initializeHttpClient();
        } else {
            // TODO: Check for valid interceptor
            this.okHttpClient = okHttpClient;
        }
    }

    public String getFeaturesJson() {
        switch (this.refreshStrategy) {
            case STALE_WHILE_REVALIDATE:
                if (isCacheExpired()) {
                    this.enqueueFeatureRefreshRequest();
                    this.refreshExpiresAt();
                }
                return this.featuresJson;

            case SERVER_SENT_EVENTS:
                return this.featuresJson;
        }

        return this.featuresJson;
    }

    /**
     * Subscribe to feature refresh events
     * This callback is called when the features are successfully refreshed.
     * This is called even if the features have not changed.
     * This will not be called if fetching the features results in a failure.
     * @param callback  This callback will be called when features are refreshed
     */
    @Override
    public void onFeaturesRefresh(FeatureRefreshCallback callback) {
        this.refreshCallbacks.add(callback);
    }

    @Override
    public void clearCallbacks() {
        this.refreshCallbacks.clear();
    }

    private void enqueueFeatureRefreshRequest() {
        GBFeaturesRepository self = this;

        Request request = new Request.Builder()
            .url(this.featuresEndpoint)
            .build();

        this.okHttpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                // OkHttp will auto-retry on failure
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                try {
                    self.onSuccess(response);
                } catch (FeatureFetchException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    @Override
    public void initialize() throws FeatureFetchException {
        if (this.initialized) return;

        switch (this.refreshStrategy) {
            case STALE_WHILE_REVALIDATE:
                fetchFeatures();
                break;

            case SERVER_SENT_EVENTS:
                fetchFeatures();
                initializeSSE();
                break;
        }

        this.initialized = true;
    }

    private void initializeSSE() throws FeatureFetchException {
        if (!this.sseAllowed) {
            System.out.printf("\nNot initializing SSE because header 'X-Sse-Support: enabled' not present on resource returned at %s", this.featuresEndpoint);
            throw new FeatureFetchException(FeatureFetchException.FeatureFetchErrorCode.SSE_CONNECTION_ERROR);
//            return;
        }

        this.sseHttpClient = new OkHttpClient.Builder()
            .addInterceptor(new GBFeaturesRepositoryRequestInterceptor())
            .connectTimeout(0, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MINUTES)
            .writeTimeout(0, TimeUnit.MINUTES)
            .build();

        Request sseRequest = new Request.Builder()
            .url(this.eventsEndpoint)
            .header("Accept", "application/json; q=0.5")
            .addHeader("Accept", "text/event-stream")
            .build();

        EventSources
            .createFactory(this.sseHttpClient)
            .newEventSource(sseRequest, new GBEventSourceListener());

        this.sseHttpClient.newCall(sseRequest).enqueue(new Callback() {
            @Override
            public void onFailure(@NotNull Call call, @NotNull IOException e) {
                System.out.println("SSE connection failed");
                e.printStackTrace();
            }

            @Override
            public void onResponse(@NotNull Call call, @NotNull Response response) throws IOException {
                System.out.printf("\n\n SSE onResponse: %s \n\n", response);
            }
        });
    }

    /**
     * @return A new {@link OkHttpClient} with an interceptor {@link GBFeaturesRepositoryRequestInterceptor}
     */
    private OkHttpClient initializeHttpClient() {
        OkHttpClient client = new OkHttpClient.Builder()
            .addInterceptor(new GBFeaturesRepositoryRequestInterceptor())
            .retryOnConnectionFailure(true)
            .build();

        return client;
    }

    private void refreshExpiresAt() {
        this.expiresAt = Instant.now().getEpochSecond() + this.swrTtlSeconds;
    }

    private Boolean isCacheExpired() {
        long now = Instant.now().getEpochSecond();
        return now >= this.expiresAt;
    }

    /**
     * Performs a network request to fetch the features from the GrowthBook API
     * with the provided endpoint.
     * If an encryptionKey is provided, it is assumed the features endpoint is using encrypted features.
     * This method will attempt to decrypt the encrypted features with the provided encryptionKey.
     */
    private void fetchFeatures() throws FeatureFetchException {
        if (this.featuresEndpoint == null) {
            throw new IllegalArgumentException("features endpoint cannot be null");
        }

        Request request = new Request.Builder()
            .url(this.featuresEndpoint)
            .build();

        try (Response response = this.okHttpClient.newCall(request).execute()) {
            String sseSupportHeader = response.header("x-sse-support");
            this.sseAllowed = Objects.equals(sseSupportHeader, "enabled");

            this.onSuccess(response);
        } catch (IOException e) {
            e.printStackTrace();

            throw new FeatureFetchException(
                FeatureFetchException.FeatureFetchErrorCode.UNKNOWN,
                e.getMessage()
            );
        }
    }

    /**
     * Handles the successful features fetching response
     * @param response Successful response
     */
    private void onSuccess(Response response) throws FeatureFetchException {
        try {
            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                throw new FeatureFetchException(
                    FeatureFetchException.FeatureFetchErrorCode.NO_RESPONSE_ERROR
                );
            }

            JsonObject jsonObject = GrowthBookJsonUtils.getInstance()
                .gson.fromJson(responseBody.string(), JsonObject.class);

            // Features will be refreshed as either an encrypted or un-encrypted JSON string
            String refreshedFeatures;

            if (this.encryptionKey != null) {
                // Use encrypted features at responseBody.encryptedFeatures
                JsonElement encryptedFeaturesJsonElement = jsonObject.get("encryptedFeatures");
                if (encryptedFeaturesJsonElement == null) {
                    throw new FeatureFetchException(
                        FeatureFetchException.FeatureFetchErrorCode.CONFIGURATION_ERROR,
                        "encryptionKey provided but endpoint not encrypted"
                    );
                }

                String encryptedFeaturesJson = encryptedFeaturesJsonElement.getAsString();
                refreshedFeatures = DecryptionUtils.decrypt(encryptedFeaturesJson, this.encryptionKey).trim();
            } else {
                // Use unencrypted features at responseBody.features
                JsonElement featuresJsonElement = jsonObject.get("features");
                if (featuresJsonElement == null) {
                    throw new FeatureFetchException(
                        FeatureFetchException.FeatureFetchErrorCode.CONFIGURATION_ERROR,
                        "No features found"
                    );
                }

                refreshedFeatures = featuresJsonElement.toString().trim();
            }

            this.featuresJson = refreshedFeatures;

            this.refreshCallbacks.forEach(featureRefreshCallback -> {
                featureRefreshCallback.onRefresh(this.featuresJson);
            });
        } catch (IOException | DecryptionUtils.DecryptionException e) {
            e.printStackTrace();

            throw new FeatureFetchException(
                FeatureFetchException.FeatureFetchErrorCode.UNKNOWN,
                e.getMessage()
            );
        }
    }
}
