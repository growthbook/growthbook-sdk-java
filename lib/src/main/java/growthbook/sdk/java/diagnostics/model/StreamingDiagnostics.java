package growthbook.sdk.java.diagnostics.model;

import growthbook.sdk.java.diagnostics.model.enums.StreamingState;
import lombok.Builder;
import lombok.Value;

/**
 * Streaming refresh connection metadata.
 */
@Value
@Builder
public class StreamingDiagnostics {
    /**
     * Derived streaming connection state.
     */
    StreamingState state;

    /**
     * Current number of scheduled/attempted streaming reconnects.
     */
    int reconnectAttempts;
}
