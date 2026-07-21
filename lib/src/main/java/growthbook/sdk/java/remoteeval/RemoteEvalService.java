package growthbook.sdk.java.remoteeval;

import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.model.RequestBodyForRemoteEval;
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

/**
 * HTTP client for the remote evaluation endpoint.
 */
public class RemoteEvalService {

    private static final MediaType JSON = MediaType.parse("application/json");

    private final String endpoint;
    private final OkHttpClient okHttpClient;
    private final boolean ownsHttpClient;
    private final RemoteEvalResponseParser responseParser;

    public RemoteEvalService(String apiHost, String clientKey) {
        this(apiHost, clientKey, null, new RemoteEvalResponseParser());
    }

    public RemoteEvalService(
            String apiHost,
            String clientKey,
            @Nullable OkHttpClient okHttpClient,
            RemoteEvalResponseParser responseParser
    ) {
        this.ownsHttpClient = okHttpClient == null;
        this.okHttpClient = okHttpClient == null ? new OkHttpClient() : okHttpClient;
        this.endpoint = RemoteEvalEndpoints.evalEndpoint(apiHost, clientKey);
        this.responseParser = responseParser == null ? new RemoteEvalResponseParser() : responseParser;
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
        Request request = new Request.Builder()
                .url(this.endpoint)
                .post(requestBody)
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
