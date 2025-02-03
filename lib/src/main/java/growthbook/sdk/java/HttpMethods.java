package growthbook.sdk.java;

import lombok.Getter;

@Getter
enum HttpMethods {
    GET("GET");
    private final String method;

    HttpMethods(String method) {
        this.method = method;
    }
}
