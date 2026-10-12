package growthbook.sdk.java.remoteeval;

import growthbook.sdk.java.constants.SDKConstants;
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
import java.time.Duration;

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
        this(apiHost, clientKey, null, new RemoteEvalResponseParser(), null);
    }

    /**
     * @param requestTimeout connect/read/write timeout for the internally created client;
     *                       {@code null} applies {@link SDKConstants#DEFAULT_REQUEST_TIMEOUT},
     *                       {@link Duration#ZERO} disables the timeout
     */
    public RemoteEvalService(String apiHost, String clientKey, @Nullable Duration requestTimeout) {
        this(apiHost, clientKey, null, new RemoteEvalResponseParser(), requestTimeout);
    }

    public RemoteEvalService(
            String apiHost,
            String clientKey,
            @Nullable OkHttpClient okHttpClient,
            RemoteEvalResponseParser responseParser
    ) {
        this(apiHost, clientKey, okHttpClient, responseParser, null);
    }

    /**
     * @param requestTimeout connect/read/write timeout for an internally created client; ignored when
     *                       {@code okHttpClient} is supplied. {@code null} applies
     *                       {@link SDKConstants#DEFAULT_REQUEST_TIMEOUT}, {@link Duration#ZERO} disables it.
     */
    public RemoteEvalService(
            String apiHost,
            String clientKey,
            @Nullable OkHttpClient okHttpClient,
            RemoteEvalResponseParser responseParser,
            @Nullable Duration requestTimeout
    ) {
        if (requestTimeout != null && requestTimeout.isNegative()) {
            throw new IllegalArgumentException("requestTimeout must not be negative");
        }
        this.ownsHttpClient = okHttpClient == null;
        this.okHttpClient = okHttpClient == null ? buildTimedClient(requestTimeout) : okHttpClient;
        this.endpoint = RemoteEvalEndpoints.evalEndpoint(apiHost, clientKey);
        this.responseParser = responseParser == null ? new RemoteEvalResponseParser() : responseParser;
    }

    private static OkHttpClient buildTimedClient(@Nullable Duration requestTimeout) {
        Duration timeout = requestTimeout == null ? SDKConstants.DEFAULT_REQUEST_TIMEOUT : requestTimeout;
        return new OkHttpClient.Builder()
                .connectTimeout(timeout)
                .readTimeout(timeout)
                .writeTimeout(timeout)
                .build();
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
