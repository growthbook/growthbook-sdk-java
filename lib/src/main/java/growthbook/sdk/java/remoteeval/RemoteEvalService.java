package growthbook.sdk.java.remoteeval;

import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.model.RequestBodyForRemoteEval;
import growthbook.sdk.java.repository.GBFeaturesRepositoryRequestInterceptor;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

import javax.annotation.Nullable;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * HTTP client for the remote evaluation endpoint.
 */
public class RemoteEvalService {

    private static final MediaType JSON = MediaType.parse("application/json");

    private final String endpoint;
    private final OkHttpClient okHttpClient;
    private final boolean ownsHttpClient;
    private final RemoteEvalResponseParser responseParser;

    /**
     * Custom headers added to every remote evaluation request. Values may contain
     * secrets and must never be logged.
     */
    private final Map<String, String> customHeaders;

    public RemoteEvalService(String apiHost, String clientKey) {
        this(apiHost, clientKey, null, new RemoteEvalResponseParser(), null);
    }

    /**
     * @param customHeaders custom headers added to every remote evaluation request,
     *                      e.g. {@code apiHostRequestHeaders}; may be null
     */
    public RemoteEvalService(String apiHost, String clientKey, @Nullable Map<String, String> customHeaders) {
        this(apiHost, clientKey, null, new RemoteEvalResponseParser(), customHeaders);
    }

    public RemoteEvalService(
            String apiHost,
            String clientKey,
            @Nullable OkHttpClient okHttpClient,
            RemoteEvalResponseParser responseParser
    ) {
        this(apiHost, clientKey, okHttpClient, responseParser, null);
    }

    public RemoteEvalService(
            String apiHost,
            String clientKey,
            @Nullable OkHttpClient okHttpClient,
            RemoteEvalResponseParser responseParser,
            @Nullable Map<String, String> customHeaders
    ) {
        this.ownsHttpClient = okHttpClient == null;
        this.okHttpClient = okHttpClient == null ? new OkHttpClient() : okHttpClient;
        this.endpoint = RemoteEvalEndpoints.evalEndpoint(apiHost, clientKey);
        this.responseParser = responseParser == null ? new RemoteEvalResponseParser() : responseParser;
        this.customHeaders = customHeaders == null || customHeaders.isEmpty()
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(customHeaders));
    }

    /**
     * Releases the HTTP resources owned by this service. A client passed in by the caller is left
     * untouched; only an internally created {@link OkHttpClient} is shut down.
     */
    public void close() {
        if (ownsHttpClient) {
            okHttpClient.dispatcher().executorService().shutdown();
            okHttpClient.connectionPool().evictAll();
        }
    }

    public RemoteEvalResponse fetch(RequestBodyForRemoteEval requestBodyForRemoteEval) throws FeatureFetchException {
        RequestBodyForRemoteEval payload = requestBodyForRemoteEval == null
                ? new RequestBodyForRemoteEval()
                : requestBodyForRemoteEval;
        String jsonBody = GrowthBookJsonUtils.getInstance().gson.toJson(payload);
        RequestBody requestBody = RequestBody.create(jsonBody, JSON);
        Request.Builder requestBuilder = new Request.Builder()
                .url(this.endpoint)
                .post(requestBody);
        for (Map.Entry<String, String> entry : this.customHeaders.entrySet()) {
            requestBuilder.header(entry.getKey(), entry.getValue());
        }
        Request request = requestBuilder
                .header(
                        GBFeaturesRepositoryRequestInterceptor.USER_AGENT_HEADER,
                        GBFeaturesRepositoryRequestInterceptor.USER_AGENT_VALUE
                )
                .build();

        try (Response response = this.okHttpClient.newCall(request).execute()) {
            ResponseBody responseBody = getSuccessfulResponseBody(response);
            return responseParser.parse(responseBody.string());
        } catch (IOException e) {
            throw new FeatureFetchException(
                    FeatureFetchException.FeatureFetchErrorCode.NO_RESPONSE_ERROR,
                    e.getMessage()
            );
        }
    }

    private ResponseBody getSuccessfulResponseBody(Response response) throws FeatureFetchException {
        ResponseBody responseBody = response.body();
        if (response.code() == HttpURLConnection.HTTP_OK && responseBody != null) {
            return responseBody;
        }

        throw new FeatureFetchException(
                responseBody == null
                        ? FeatureFetchException.FeatureFetchErrorCode.NO_RESPONSE_ERROR
                        : FeatureFetchException.FeatureFetchErrorCode.HTTP_RESPONSE_ERROR,
                "Remote evaluation request failed with status " + response.code()
        );
    }
}
