package growthbook.sdk.java.diagnostics.model;

import growthbook.sdk.java.util.GrowthBookJsonUtils;

import lombok.Builder;
import lombok.Value;

/**
 * Read-only snapshot of the current SDK state.
 */
@Value
@Builder
public class Diagnostics {
    /**
     * Epoch-millis timestamp when this diagnostics snapshot was generated.
     */
    long generatedAtMillis;

    /**
     * SDK build metadata.
     */
    SdkDiagnostics sdk;

    /**
     * High-level health summary with actionable issues.
     */
    HealthDiagnostics health;

    /**
     * Sanitized configuration metadata.
     */
    ConfigDiagnostics config;

    /**
     * Client lifecycle metadata.
     */
    ClientDiagnostics client;

    /**
     * Current feature data metadata.
     */
    FeatureDiagnostics features;

    /**
     * Feature refresh metadata.
     */
    RefreshDiagnostics refresh;

    /**
     * Feature cache metadata.
     */
    CacheDiagnostics cache;

    /**
     * Streaming refresh metadata.
     */
    StreamingDiagnostics streaming;

    /**
     * Remote-eval metadata.
     */
    RemoteEvalDiagnostics remoteEval;

    /**
     * Serializes this SDK-produced diagnostics snapshot as JSON for support/debugging workflows.
     */
    public String toJson() {
        return GrowthBookJsonUtils.getInstance().gson.toJson(this);
    }
}
