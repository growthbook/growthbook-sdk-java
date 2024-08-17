package growthbook.sdk.java;

import static cloud.prefab.sse.SSEHandler.EVENT_STREAM_MEDIA_TYPE;

import cloud.prefab.sse.SSEHandler;
import cloud.prefab.sse.events.CommentEvent;
import cloud.prefab.sse.events.DataEvent;
import cloud.prefab.sse.events.Event;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import javax.annotation.Nullable;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Flow;

@Slf4j
public class GBFeaturesRepository implements IGBFeaturesRepository {
    
    @Getter
    private final String featuresEndpoint;
    
    @Getter
    private final String eventsEndpoint;
    
    @Getter
    private FeatureRefreshStrategy refreshStrategy;
    
    @Nullable
    @Getter
    private final String encryptionKey;
    Boolean sseAllowed;
    
    @Getter
    private final Integer swrTtlSeconds;
    
    @Getter
    private Long expiresAt;
    private final HttpClient httpClient = HttpClient.newHttpClient();
    private Boolean initialized = false;
    
    @Getter
    @Nullable
    private String savedGroupsJson = "{}";
    private String featuresJson = "{}";
    private final ArrayList<FeatureRefreshCallback> refreshCallbacks = new ArrayList<>();
    
    @Builder
    public GBFeaturesRepository(
    @Nullable String apiHost,
    String clientKey,
    @Nullable String encryptionKey,
    @Nullable FeatureRefreshStrategy refreshStrategy,
    @Nullable Integer swrTtlSeconds
    ) {
        if (clientKey == null) throw new IllegalArgumentException("clientKey cannot be null");
        if (apiHost == null) {
            apiHost = "https://cdn.growthbook.io";
        }
        this.refreshStrategy = refreshStrategy == null ? FeatureRefreshStrategy.STALE_WHILE_REVALIDATE : refreshStrategy;
        this.featuresEndpoint = apiHost + "/api/features/" + clientKey;
        this.eventsEndpoint = apiHost + "/sub/" + clientKey;
        this.encryptionKey = encryptionKey;
        this.swrTtlSeconds = swrTtlSeconds == null ? 60 : swrTtlSeconds;
        this.refreshExpiresAt();
    }
    
    private void refreshExpiresAt() {
        this.expiresAt = Instant.now().getEpochSecond() + this.swrTtlSeconds;
    }
    
    @Override
    public void initialize() throws FeatureFetchException {
        initialize(false);
    }
    
    @Override
    public void initialize(Boolean retryOnFailure) throws FeatureFetchException {
        if (this.initialized) return;
        
        switch (this.refreshStrategy) {
            case STALE_WHILE_REVALIDATE:
                fetchFeatures();
                break;
            
            case SERVER_SENT_EVENTS:
                fetchFeatures();
                initializeSSE(retryOnFailure);
                break;
        }
        
        this.initialized = true;
    }
    
    @Override
    public String getFeaturesJson() {
        switch (this.refreshStrategy) {
            case STALE_WHILE_REVALIDATE:
                if (isCacheExpired()) {
                    this.initializeSSE(true);
                    this.refreshExpiresAt();
                }
                return this.featuresJson;
            
            case SERVER_SENT_EVENTS:
                return this.featuresJson;
        }
        
        return this.featuresJson;
    }
    
    @Override
    public void onFeaturesRefresh(FeatureRefreshCallback callback) {
        this.refreshCallbacks.add(callback);
    }
    
    @Override
    public void clearCallbacks() {
        this.refreshCallbacks.clear();
    }
    
    
    private void fetchFeatures() throws FeatureFetchException {
        if (this.featuresEndpoint == null) {
            log.error("features endpoint cannot be null");
            throw new IllegalArgumentException("features endpoint cannot be null");
        }
        
        try {
            HttpRequest request = HttpRequest.newBuilder()
            .GET()
            .uri(new URI(this.featuresEndpoint))
            .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            this.sseAllowed = response.headers()
            .firstValue("x-sse-support")
            .orElseThrow()
            .equals("enabled");
            this.onSuccess(response);
        } catch (URISyntaxException | IOException | InterruptedException |
                 FeatureFetchException e) {
            throw new FeatureFetchException(
            FeatureFetchException.FeatureFetchErrorCode.UNKNOWN);
        }
    }
    
    private void onSuccess(HttpResponse<String> response) throws FeatureFetchException {
        String responseBody = response.body();
        if (responseBody == null) {
            log.error("FeatureFetchException: FeatureFetchErrorCode.NO_RESPONSE_ERROR");
            throw new FeatureFetchException(
            FeatureFetchException.FeatureFetchErrorCode.NO_RESPONSE_ERROR
            );
        }
        onResponseJson(responseBody);
    }
    
    private void onResponseJson(String responseJsonString) throws FeatureFetchException {
        if (responseJsonString == null || responseJsonString.trim().isEmpty()) {
            log.error("Received empty or null JSON response.");
            return;
        }
        
        try {
            JsonObject jsonObject = GrowthBookJsonUtils.getInstance()
            .gson.fromJson(responseJsonString, JsonObject.class);
            
            if (jsonObject == null) {
                
                throw new FeatureFetchException(
                FeatureFetchException.FeatureFetchErrorCode.UNKNOWN,
                "JSON response is null or invalid"
                );
            }
            
            String refreshedFeatures;
            String refreshedSavedGroups = "";
            
            if (this.encryptionKey != null) {
                JsonElement encryptedFeaturesJsonElement = jsonObject.get("encryptedFeatures");
                JsonElement encryptedSavedGroupsJsonElement = jsonObject.get("encryptedSavedGroups");
                if (encryptedFeaturesJsonElement == null) {
                    log.error(
                    "FeatureFetchException: CONFIGURATION_ERROR feature fetch error code: "
                    + "encryptionKey provided but endpoint not encrypted");
                    throw new FeatureFetchException(
                    FeatureFetchException.FeatureFetchErrorCode.CONFIGURATION_ERROR,
                    "encryptionKey provided but endpoint not encrypted"
                    );
                }
                if (encryptedSavedGroupsJsonElement != null) {
                    String encryptedSavedGroupsJson = encryptedSavedGroupsJsonElement.getAsString();
                    refreshedSavedGroups = DecryptionUtils.decrypt(encryptedSavedGroupsJson, this.encryptionKey).trim();
                }
                
                String encryptedFeaturesJson = encryptedFeaturesJsonElement.getAsString();
                refreshedFeatures = DecryptionUtils.decrypt(encryptedFeaturesJson, this.encryptionKey).trim();
            } else {
                JsonElement featuresJsonElement = jsonObject.get("features");
                
                if (featuresJsonElement == null) {
                    log.error(
                    "FeatureFetchException: CONFIGURATION_ERROR feature fetch error code: "
                    + "No features found");
                    
                    throw new FeatureFetchException(
                    FeatureFetchException.FeatureFetchErrorCode.CONFIGURATION_ERROR,
                    "No features found"
                    );
                }
                
                refreshedFeatures = featuresJsonElement.toString().trim();
            }
            
            this.featuresJson = refreshedFeatures;
            this.savedGroupsJson = refreshedSavedGroups;
            
            this.onRefreshSuccess(this.featuresJson);
        } catch (DecryptionUtils.DecryptionException e) {
            log.error("FeatureFetchException: UNKNOWN feature fetch error code {}",
            e.getMessage(), e);
            
            throw new FeatureFetchException(
            FeatureFetchException.FeatureFetchErrorCode.UNKNOWN,
            e.getMessage()
            );
        } catch (JsonSyntaxException e) {
            log.error("FeatureFetchException: JSON syntax error {}",
            e.getMessage(), e);
            
            throw new FeatureFetchException(
            FeatureFetchException.FeatureFetchErrorCode.UNKNOWN,
            "JSON syntax error: " + e.getMessage()
            );
        }
    }
    private void onRefreshSuccess(String featuresJson) {
        this.refreshCallbacks.forEach(featureRefreshCallback -> featureRefreshCallback.onRefresh(featuresJson));
    }
    
    private Boolean isCacheExpired() {
        long now = Instant.now().getEpochSecond();
        return now >= this.expiresAt;
    }
    
    
    private void initializeSSE(Boolean retryOnFailure) {
        if (!this.sseAllowed) {
            this.refreshStrategy = FeatureRefreshStrategy.STALE_WHILE_REVALIDATE;
        }
        
        HttpRequest request = HttpRequest
        .newBuilder()
        .header("Accept", "application/json; q=0.5")
        .header("Accept", EVENT_STREAM_MEDIA_TYPE)
        .timeout(Duration.ofSeconds(5))
        .uri(URI.create(this.eventsEndpoint))
        .build();
        
        SSEHandler sseHandler = new SSEHandler();
        
        CompletableFuture<HttpResponse<Void>> future = httpClient.sendAsync(
        request,
        HttpResponse.BodyHandlers.fromLineSubscriber(sseHandler)
        );
        
        future.thenAccept(response -> {
            log.info("SSE Connection Status: {}", response.statusCode());
            log.info("SSE body: {}", response.body().toString());
        }).exceptionally(e -> {
            log.error("Error in SSE connection: {}", e.getMessage());
            return null;
        });
        
        sseHandler.subscribe(new Flow.Subscriber<Event>() {
                                 @Override
                                 public void onSubscribe(Flow.Subscription subscription) {
                                     log.info("Subscribed to SSE stream");
                                     subscription.request(Long.MAX_VALUE);
                                 }
                                 
                                 @Override
                                 public void onNext(Event event) {
                                     if (event instanceof DataEvent) {
                                         DataEvent dataEvent = (DataEvent) event;
                                         log.info("Received data event: {}", dataEvent.getData());
                                         try {
                                             if (dataEvent.getData() != null) {
                                                 onResponseJson(dataEvent.getData());
                                             }
                                         } catch (FeatureFetchException e) {
                                             log.error("Exception occur onResponseJson method {}", e.getMessage(), e);
                                         }
                                     } else if (event instanceof CommentEvent) {
                                         CommentEvent commentEvent = (CommentEvent) event;
                                         log.info("Received comment: {}", commentEvent.getComment());
                                     }
                                 }
                                 
                                 @Override
                                 public void onError(Throwable throwable) {
                                     log.error("Error in SSE stream: {}", throwable.getMessage());
                                     if (retryOnFailure) {
                                         initializeSSE(true);
                                     }
                                     try {
                                         fetchFeatures();
                                     } catch (FeatureFetchException e) {
                                         log.error("SSE Connection error: {}", e.getMessage(), e);
                                     }
                                 }
                                 
                                 @Override
                                 public void onComplete() {
                                     log.info("SSE stream completed");
                                 }
                             }
        );
    }
}
