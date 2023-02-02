package growthbook.sdk.java;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import lombok.Builder;
import lombok.Getter;
import okhttp3.*;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.io.IOException;

/**
 * This class can be created with its `builder()` or constructor.
 * It will fetch the features from the endpoint provided.
 * Initialize with {@link GBFeaturesRepository#initialize()}
 * Get the features JSON with {@link GBFeaturesRepository#getFeaturesJson()}.
 * You would provide the features JSON when creating the {@link GBContext}
 */
public class GBFeaturesRepository implements IGBFeaturesRepository {

    @Nullable @Getter
    private final String endpoint;

    @Nullable @Getter
    private final String encryptionKey;

    private final OkHttpClient okHttpClient;

    /**
     * Allows you to get the features JSON from the provided {@link GBFeaturesRepository#getEndpoint()}.
     * You must call {@link GBFeaturesRepository#initialize()} before calling this method
     * or your features would not have loaded.
     */
    @Getter
    private String featuresJson = "{}";

    /**
     * Create a new GBFeaturesRepository
     * @param endpoint SDK Endpoint URL
     * @param encryptionKey optional key for decrypting encrypted payload
     */
    @Builder
    public GBFeaturesRepository(
        @Nullable String endpoint,
        @Nullable String encryptionKey
    ) {
        this.endpoint = endpoint;
        this.encryptionKey = encryptionKey;
        this.okHttpClient = this.initializeHttpClient();
    }

    /**
     * INTERNAL: This constructor is for using for unit tests
     * @param okHttpClient mock HTTP client
     * @param endpoint SDK Endpoint URL
     * @param encryptionKey optional key for decrypting encrypted payload
     */
    GBFeaturesRepository(
        OkHttpClient okHttpClient,
        @Nullable String endpoint,
        @Nullable String encryptionKey
    ) {
        this.encryptionKey = encryptionKey;
        this.endpoint = endpoint;
        this.okHttpClient = okHttpClient;
    }

    @Override
    public void initialize() throws FeatureFetchException {
        fetchFeatures();
    }

    private OkHttpClient initializeHttpClient() {
        OkHttpClient client = new OkHttpClient.Builder()
            .addInterceptor(new GBFeaturesRepositoryRequestInterceptor())
            .build();

        return client;
    }

    /**
     * Performs a network request to fetch the features from the GrowthBook API
     * with the provided endpoint.
     * If an encryptionKey is provided, it is assumed the features endpoint is using encrypted features.
     * This method will attempt to decrypt the encrypted features with the provided encryptionKey.
     */
    private void fetchFeatures() throws FeatureFetchException {
        if (this.endpoint == null) {
            throw new IllegalArgumentException("endpoint cannot be null");
        }

        Request request = new Request.Builder()
            .url(this.endpoint)
            .build();

        try (Response response = this.okHttpClient.newCall(request).execute()) {
            ResponseBody responseBody = response.body();
            if (responseBody == null) {
                throw new FeatureFetchException(
                    FeatureFetchException.FeatureFetchErrorCode.NO_RESPONSE_ERROR
                );
            }

            JsonObject jsonObject = GrowthBookJsonUtils.getInstance()
                .gson.fromJson(responseBody.string(), JsonObject.class);

            if (this.encryptionKey != null) {
                // Decrypt features
                JsonElement encryptedFeaturesJsonElement = jsonObject.get("encryptedFeatures");

                if (encryptedFeaturesJsonElement == null) {
                    throw new FeatureFetchException(
                        FeatureFetchException.FeatureFetchErrorCode.CONFIGURATION_ERROR,
                        "encryptionKey provided but endpoint not encrypted"
                    );
                }

                String encryptedFeaturesJson = encryptedFeaturesJsonElement.getAsString();
                this.featuresJson = DecryptionUtils.decrypt(encryptedFeaturesJson, this.encryptionKey);
            } else {
                JsonElement featuresJsonElement = jsonObject.get("features");
                if (featuresJsonElement == null) {
                    throw new FeatureFetchException(
                        FeatureFetchException.FeatureFetchErrorCode.CONFIGURATION_ERROR,
                        "No features found"
                    );
                }

                this.featuresJson = featuresJsonElement.toString();
            }
        } catch (IOException e) {
            e.printStackTrace();
            throw new FeatureFetchException(
                FeatureFetchException.FeatureFetchErrorCode.UNKNOWN,
                e.getMessage()
            );
        }
    }



    /**
     * Appends User-Agent info to the request headers.
     */
    private static class GBFeaturesRepositoryRequestInterceptor implements Interceptor {

        @NotNull
        @Override
        public Response intercept(@NotNull Chain chain) throws IOException {
            Request modifiedRequest = chain.request()
                .newBuilder()
                .header("User-Agent", "growthbook-sdk-java/" + Version.SDK_VERSION)
                .build();

            return chain.proceed(modifiedRequest);
        }
    }
}
