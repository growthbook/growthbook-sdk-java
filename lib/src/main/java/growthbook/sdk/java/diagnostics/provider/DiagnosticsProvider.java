package growthbook.sdk.java.diagnostics.provider;

import growthbook.sdk.java.diagnostics.model.Diagnostics;

/**
 * Provides read-only diagnostic snapshots.
 */
public interface DiagnosticsProvider {
    Diagnostics getDiagnostics();
}
