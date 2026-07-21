package growthbook.sdk.java.diagnostics.provider.model;

import growthbook.sdk.java.diagnostics.model.CacheDiagnostics;
import growthbook.sdk.java.diagnostics.model.ClientDiagnostics;
import growthbook.sdk.java.diagnostics.model.ConfigDiagnostics;
import growthbook.sdk.java.diagnostics.model.FeatureDiagnostics;
import growthbook.sdk.java.diagnostics.model.RefreshDiagnostics;
import growthbook.sdk.java.diagnostics.model.RemoteEvalDiagnostics;
import growthbook.sdk.java.diagnostics.model.StreamingDiagnostics;

import lombok.Builder;
import lombok.Value;

/**
 * Collected diagnostics sections before the final public diagnostics object is assembled.
 */
@Value
@Builder
public class DiagnosticsSnapshot {
    long generatedAtMillis;
    ConfigDiagnostics config;
    ClientDiagnostics client;
    FeatureDiagnostics features;
    RefreshDiagnostics refresh;
    CacheDiagnostics cache;
    StreamingDiagnostics streaming;
    RemoteEvalDiagnostics remoteEval;
}
