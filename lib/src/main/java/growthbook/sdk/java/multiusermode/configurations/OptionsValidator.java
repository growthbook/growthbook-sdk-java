package growthbook.sdk.java.multiusermode.configurations;

import growthbook.sdk.java.constants.SDKConstants;
import growthbook.sdk.java.exception.InvalidOptionsException;
import growthbook.sdk.java.remoteeval.RemoteEvalOptionsValidator;
import growthbook.sdk.java.sandbox.CacheMode;
import growthbook.sdk.java.util.StringUtils;

import javax.annotation.Nullable;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Validates {@link Options} once, at client start-up, so misconfigurations are reported up front with
 * a clear message instead of surfacing later as an opaque fetch failure.
 *
 * <p>All independent problems are collected and reported together, so a caller fixing several
 * misconfigured options sees every issue at once rather than one per restart.
 *
 * <p>Checks performed:
 * <ul>
 *     <li>{@code apiHost} is present and a syntactically valid {@code http(s)} URL.</li>
 *     <li>{@code streamingHost}, when set, is a syntactically valid {@code http(s)} URL.</li>
 *     <li>{@code apiHostRequestHeaders} and {@code streamingHostRequestHeaders} contain only
 *     syntactically valid HTTP header names and values, no blank names, no null values, and none
 *     of the SDK-managed (reserved) headers {@code User-Agent}, {@code Accept},
 *     {@code If-None-Match}, {@code Cache-Control}.</li>
 *     <li>{@code clientKey} is present.</li>
 *     <li>{@code swrTtlSeconds} (refresh interval) is positive when set.</li>
 *     <li>{@code backgroundFetchInterval} is non-negative when set.</li>
 *     <li>{@code remoteEvalCacheTtlSeconds} is positive when set.</li>
 *     <li>No contradictory cache configuration (e.g. a {@code cacheManager} supplied while caching
 *     is disabled, or {@link CacheMode#CUSTOM} without a {@code cacheManager}).</li>
 *     <li>Remote-eval incompatibilities, delegated to
 *     {@code RemoteEvalOptionsValidator#remoteEvalViolations} so all problems surface together.</li>
 * </ul>
 */
public final class OptionsValidator {

    private static final String HTTP = "http";
    private static final String HTTPS = "https";
    private static final String SCHEME_SEPARATOR = "://";

    private OptionsValidator() {
    }

    /**
     * Validates the supplied options, throwing when any option is invalid or contradictory.
     *
     * @param options client options to validate; {@code null} is treated as nothing to validate
     * @throws InvalidOptionsException listing every problem found (a subtype of
     *         {@link IllegalArgumentException})
     */
    public static void validate(@Nullable Options options) {
        List<String> violations = findViolations(options);
        if (!violations.isEmpty()) {
            throw new InvalidOptionsException(
                    "Invalid GrowthBook options: " + String.join("; ", violations), violations);
        }
    }

    /**
     * Collects every validation problem without throwing.
     *
     * @param options client options to inspect; {@code null} yields an empty list
     * @return an immutable list of human-readable problem descriptions, empty when valid
     */
    public static List<String> findViolations(@Nullable Options options) {
        if (options == null) {
            return Collections.emptyList();
        }

        List<String> violations = new ArrayList<>();
        checkApiHost(options.getApiHost(), violations);
        checkStreamingHost(options.getStreamingHost(), violations);
        checkRequestHeaders("apiHostRequestHeaders", options.getApiHostRequestHeaders(), violations);
        checkRequestHeaders("streamingHostRequestHeaders", options.getStreamingHostRequestHeaders(), violations);
        checkClientKey(options.getClientKey(), violations);
        checkRefreshInterval(options.getSwrTtlSeconds(), violations);
        checkBackgroundFetchInterval(options.getBackgroundFetchInterval(), violations);
        checkRemoteEvalCacheTtl(options.getRemoteEvalCacheTtlSeconds(), violations);
        checkCacheConfiguration(options, violations);
        violations.addAll(RemoteEvalOptionsValidator.remoteEvalViolations(options));
        return Collections.unmodifiableList(violations);
    }

    private static void checkApiHost(@Nullable String apiHost, List<String> violations) {
        if (StringUtils.isBlank(apiHost)) {
            violations.add("apiHost is required");
            return;
        }
        checkHostUrl("apiHost", apiHost, violations);
    }

    private static void checkStreamingHost(@Nullable String streamingHost, List<String> violations) {
        if (streamingHost == null) {
            return;
        }
        if (StringUtils.isBlank(streamingHost)) {
            violations.add("streamingHost must not be blank when set");
            return;
        }
        checkHostUrl("streamingHost", streamingHost, violations);
    }

    private static void checkHostUrl(String optionName, String host, List<String> violations) {
        String raw = host.trim();
        boolean hasScheme = raw.contains(SCHEME_SEPARATOR);

        URI uri;
        try {
            uri = URI.create(hasScheme ? raw : HTTPS + SCHEME_SEPARATOR + raw);
        } catch (IllegalArgumentException e) {
            violations.add(optionName + " is not a valid URL: " + host);
            return;
        }

        if (hasScheme) {
            String scheme = uri.getScheme();
            if (scheme == null || (!scheme.equalsIgnoreCase(HTTP) && !scheme.equalsIgnoreCase(HTTPS))) {
                violations.add(optionName + " must use http or https scheme: " + host);
                return;
            }
        }

        if (StringUtils.isBlank(uri.getHost())) {
            violations.add(optionName + " is not a valid URL: " + host);
        }
    }

    private static void checkRequestHeaders(
            String optionName,
            @Nullable Map<String, String> headers,
            List<String> violations
    ) {
        if (headers == null || headers.isEmpty()) {
            return;
        }

        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String name = entry.getKey();
            String value = entry.getValue();
            if (StringUtils.isBlank(name)) {
                violations.add(optionName + " must not contain a null or blank header name");
            } else if (SDKConstants.RESERVED_REQUEST_HEADERS.contains(name.toLowerCase(Locale.ROOT))) {
                violations.add(optionName + " must not contain the reserved header '" + name
                        + "'; User-Agent, Accept, If-None-Match and Cache-Control are managed by the SDK");
            } else if (!isValidHeaderName(name)) {
                violations.add(optionName + " contains an invalid HTTP header name '" + name + "'");
            } else if (value == null) {
                violations.add(optionName + " must not contain a null value for header '" + name + "'");
            } else if (!isValidHeaderValue(value)) {
                // The value may hold a secret, so it is never echoed in the message.
                violations.add(optionName + " contains an invalid value for header '" + name + "'");
            }
        }
    }

    /**
     * @return {@code true} if every character is allowed in an HTTP header name (printable ASCII,
     *         excluding spaces and control characters), matching what OkHttp accepts when the
     *         request is built. Rejecting here reports the problem at start-up instead of as an
     *         unchecked exception on the first request.
     */
    private static boolean isValidHeaderName(String name) {
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (c <= '\u0020' || c >= '\u007f') {
                return false;
            }
        }
        return true;
    }

    /**
     * @return {@code true} if every character is allowed in an HTTP header value (a horizontal tab
     *         or printable ASCII), matching what OkHttp accepts when the request is built.
     */
    private static boolean isValidHeaderValue(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (c != '\t' && (c <= '\u001f' || c >= '\u007f')) {
                return false;
            }
        }
        return true;
    }

    private static void checkClientKey(@Nullable String clientKey, List<String> violations) {
        if (StringUtils.isBlank(clientKey)) {
            violations.add("clientKey is required");
        }
    }

    private static void checkRefreshInterval(@Nullable Integer swrTtlSeconds, List<String> violations) {
        if (swrTtlSeconds != null && swrTtlSeconds <= 0) {
            violations.add("refresh interval (swrTtlSeconds) must be greater than 0, but was " + swrTtlSeconds);
        }
    }

    private static void checkBackgroundFetchInterval(@Nullable Duration backgroundFetchInterval, List<String> violations) {
        if (backgroundFetchInterval != null && backgroundFetchInterval.isNegative()) {
            violations.add("backgroundFetchInterval must not be negative, but was " + backgroundFetchInterval);
        }
    }

    private static void checkRemoteEvalCacheTtl(@Nullable Integer remoteEvalCacheTtlSeconds, List<String> violations) {
        if (remoteEvalCacheTtlSeconds != null && remoteEvalCacheTtlSeconds <= 0) {
            violations.add("remoteEvalCacheTtlSeconds must be greater than 0 when set, but was "
                    + remoteEvalCacheTtlSeconds);
        }
    }

    private static void checkCacheConfiguration(Options options, List<String> violations) {
        CacheMode cacheMode = options.getCacheMode();
        boolean cacheManagerSupplied = options.getCacheManager() != null;
        boolean cacheDisabled = Boolean.TRUE.equals(options.getIsCacheDisabled())
                || cacheMode == CacheMode.NONE;

        if (cacheManagerSupplied && cacheDisabled) {
            violations.add("a cacheManager was supplied but caching is disabled "
                    + "(isCacheDisabled=true or CacheMode.NONE); remove one of them");
        }

        if (cacheMode == CacheMode.CUSTOM && !cacheManagerSupplied) {
            violations.add("CacheMode.CUSTOM requires a cacheManager to be supplied");
        }
    }
}
