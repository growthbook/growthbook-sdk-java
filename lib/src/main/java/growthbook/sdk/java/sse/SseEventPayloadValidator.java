package growthbook.sdk.java.sse;

import growthbook.sdk.java.util.StringUtils;
import lombok.experimental.UtilityClass;

import javax.annotation.Nullable;
import java.util.Locale;

@UtilityClass
public class SseEventPayloadValidator {
    public boolean isValidFeaturePayload(@Nullable String eventType, @Nullable String data) {
        return !StringUtils.isBlank(data) && !isHeartbeatEvent(eventType);
    }

    private boolean isHeartbeatEvent(@Nullable String eventType) {
        if (StringUtils.isBlank(eventType)) {
            return false;
        }

        String normalizedType = eventType.trim().toLowerCase(Locale.ROOT);
        return "heartbeat".equals(normalizedType)
                || "keepalive".equals(normalizedType)
                || "ping".equals(normalizedType);
    }
}
