package growthbook.sdk.java.diagnostics.provider.model;

import lombok.Builder;
import lombok.Value;

/**
 * Raw streaming signals gathered from the active repository or remote-eval coordinator.
 */
@Value
@Builder
public class StreamingStatusSnapshot {
    boolean allowed;
    boolean shutdown;
    boolean connected;
    boolean configured;
    boolean initialized;
    int reconnectAttempts;
}
