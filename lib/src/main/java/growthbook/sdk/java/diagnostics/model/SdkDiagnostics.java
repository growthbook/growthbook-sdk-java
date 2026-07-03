package growthbook.sdk.java.diagnostics.model;

import lombok.Builder;
import lombok.Value;

/**
 * SDK build metadata.
 */
@Value
@Builder
public class SdkDiagnostics {
    /**
     * SDK version string.
     */
    String version;
}
