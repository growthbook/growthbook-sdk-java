package growthbook.sdk.java.diagnostics.model;

import lombok.Builder;
import lombok.Value;

/**
 * Client lifecycle metadata.
 */
@Value
@Builder
public class ClientDiagnostics {
    /**
     * Whether the client is ready for evaluations.
     */
    boolean initialized;

    /**
     * Whether client-owned background resources have been shut down.
     */
    boolean shutdown;

    /**
     * Whether the client is configured to use remote evaluation.
     */
    boolean remoteEvalEnabled;
}
