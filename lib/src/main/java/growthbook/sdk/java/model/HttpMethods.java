package growthbook.sdk.java.model;

import lombok.Getter;

@Getter
public enum HttpMethods {
    GET("GET");
    private final String method;

    HttpMethods(String method) {
        this.method = method;
    }
}
