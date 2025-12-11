package growthbook.sdk.java.model;

import lombok.Getter;

@Getter
public enum HttpHeaders {
    X_SSE_SUPPORT("x-sse-support"),
    ACCEPT("Accept"),
    SSE_HEADER("text/event-stream"),
    APPLICATION_JSON("application/json; q=0.5"),
    IF_NONE_MATCH("If-None-Match"),
    CACHE_CONTROL("Cache-Control");

    private final String header;

    HttpHeaders(String header) {
        this.header = header;
    }
}
