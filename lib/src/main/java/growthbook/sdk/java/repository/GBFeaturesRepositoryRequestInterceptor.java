package growthbook.sdk.java.repository;

import growthbook.sdk.java.Version;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Appends User-Agent info to the request headers.
 */
public class GBFeaturesRepositoryRequestInterceptor implements Interceptor {

    public static final String USER_AGENT_HEADER = "User-Agent";
    public static final String USER_AGENT_VALUE = "growthbook-sdk-java/" + Version.SDK_VERSION;

    @NotNull
    @Override
    public Response intercept(@NotNull Interceptor.Chain chain) throws IOException {
        Request modifiedRequest = chain.request()
            .newBuilder()
            .header(USER_AGENT_HEADER, USER_AGENT_VALUE)
            .build();

        return chain.proceed(modifiedRequest);
    }
}
