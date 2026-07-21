package growthbook.sdk.java.model;

import lombok.Getter;

@Getter
public enum SseKey {
    DATA("data:"),
    EVENT("event:");
    private final String key;

    SseKey(String key) {
        this.key = key;
    }
}
