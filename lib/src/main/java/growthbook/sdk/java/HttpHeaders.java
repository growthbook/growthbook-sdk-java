package growthbook.sdk.java;

import lombok.Getter;

@Getter
enum HttpHeaders {
    X_SSE_SUPPORT("x-sse-support"),
    ACCEPT("Accept"),
    SSE_HEADER("text/event-stream"),
    APPLICATION_JSON("application/json; q=0.5");

    private final String header;

    HttpHeaders(String header) {
        this.header = header;
    }
}
