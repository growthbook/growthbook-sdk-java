package growthbook.sdk.java;

/*
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
*/
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

/**
 * Appends User-Agent info to the request headers.
 */
/*
public class GBFeaturesRepositoryRequestInterceptor implements Interceptor {
    @NotNull
    @Override
    public Response intercept(@NotNull Interceptor.Chain chain) throws IOException {
        Request modifiedRequest = chain.request()
            .newBuilder()
            .header("User-Agent", "growthbook-sdk-java/" + Version.SDK_VERSION)
            .build();

        return chain.proceed(modifiedRequest);
    }
}
*/
