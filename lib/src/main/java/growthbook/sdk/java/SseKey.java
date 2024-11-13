package growthbook.sdk.java;

import lombok.Getter;

@Getter
enum SseKey {
    DATA("data:");
    private final String key;

    SseKey(String key) {
        this.key = key;
    }
}
