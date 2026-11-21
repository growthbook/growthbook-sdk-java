package growthbook.sdk.java.repository.internal;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.UnaryOperator;

/**
 * An HTTP response read in full: status, body and headers. Header names are case-insensitive.
 * Immutable.
 */
public final class TransportResponse {

    private final int code;
    private final String message;

    @Nullable
    private final String body;
    private final UnaryOperator<String> headers;

    /**
     * @param code    the status code
     * @param message the status reason phrase, e.g. {@code "Not Found"}; empty when there is none
     * @param body    the body, or {@code null} when there is none
     * @param headers looks up a header value by case-insensitive name; {@code null} when absent
     */
    public TransportResponse(int code, @Nullable String message, @Nullable String body, UnaryOperator<String> headers) {
        this.code = code;
        this.message = message != null ? message : "";
        this.body = body;
        this.headers = headers;
    }

    /**
     * @param headers header values by name; looked up case-insensitively
     */
    public static TransportResponse of(int code, @Nullable String message, @Nullable String body, Map<String, String> headers) {
        Map<String, String> caseInsensitive = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        caseInsensitive.putAll(headers);
        return new TransportResponse(code, message, body, Collections.unmodifiableMap(caseInsensitive)::get);
    }

    public int code() {
        return code;
    }

    public String message() {
        return message;
    }

    @Nullable
    public String body() {
        return body;
    }

    @Nullable
    public String header(String name) {
        return headers.apply(name);
    }
}
