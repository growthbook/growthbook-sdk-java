package growthbook.sdk.java.util;

import okhttp3.OkHttpClient;

import javax.annotation.Nullable;
import java.time.Duration;

/**
 * Validation and application of the optional {@code requestTimeout} shared by the features fetch and
 * remote-evaluation HTTP clients. Internal helper; not part of the supported API.
 */
public final class RequestTimeouts {

    /** OkHttp stores timeouts as an {@code int} number of milliseconds. */
    private static final Duration MAX = Duration.ofMillis(Integer.MAX_VALUE);

    private static final Duration MIN = Duration.ofMillis(1);

    private RequestTimeouts() {
    }

    /**
     * Returns why {@code requestTimeout} is invalid, or {@code null} when it is valid. {@code null} (not
     * configured) and {@link Duration#ZERO} (no timeout) are valid; otherwise the value must be between
     * 1 ms and {@link Integer#MAX_VALUE} ms, because OkHttp rounds a smaller value down to 0 (no timeout)
     * and cannot represent a larger one.
     *
     * @param requestTimeout the configured timeout, may be {@code null}
     * @return a human-readable violation, or {@code null} when valid
     */
    @Nullable
    public static String violation(@Nullable Duration requestTimeout) {
        if (requestTimeout == null || requestTimeout.isZero()) {
            return null;
        }
        if (requestTimeout.isNegative()) {
            return "requestTimeout must not be negative, but was " + requestTimeout
                    + " (use Duration.ZERO to disable the timeout)";
        }
        if (requestTimeout.compareTo(MIN) < 0) {
            return "requestTimeout must be at least 1 ms, but was " + requestTimeout
                    + " (use Duration.ZERO to disable the timeout)";
        }
        if (requestTimeout.compareTo(MAX) > 0) {
            return "requestTimeout must not exceed " + Integer.MAX_VALUE + " ms, but was " + requestTimeout;
        }
        return null;
    }

    /**
     * @param requestTimeout the configured timeout, may be {@code null}
     * @throws IllegalArgumentException when {@link #violation} reports a violation
     */
    public static void requireValid(@Nullable Duration requestTimeout) {
        String violation = violation(requestTimeout);
        if (violation != null) {
            throw new IllegalArgumentException(violation);
        }
    }

    /**
     * Applies {@code requestTimeout} to the whole call ({@code callTimeout}) and to connect, read and
     * write. The call timeout bounds total elapsed time, so a trickle of response chunks cannot keep a
     * read alive past the budget. {@link Duration#ZERO} means no timeout.
     *
     * @param builder        the client builder to configure
     * @param requestTimeout a timeout accepted by {@link #violation}, not {@code null}
     */
    public static void apply(OkHttpClient.Builder builder, Duration requestTimeout) {
        builder.callTimeout(requestTimeout)
                .connectTimeout(requestTimeout)
                .readTimeout(requestTimeout)
                .writeTimeout(requestTimeout);
    }
}
