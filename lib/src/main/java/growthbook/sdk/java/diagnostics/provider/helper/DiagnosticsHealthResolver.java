package growthbook.sdk.java.diagnostics.provider.helper;

import growthbook.sdk.java.diagnostics.model.CacheDiagnostics;
import growthbook.sdk.java.diagnostics.model.enums.CacheState;
import growthbook.sdk.java.diagnostics.model.ClientDiagnostics;
import growthbook.sdk.java.diagnostics.model.ConfigDiagnostics;
import growthbook.sdk.java.diagnostics.model.enums.DiagnosticsHealthState;
import growthbook.sdk.java.diagnostics.model.DiagnosticsIssue;
import growthbook.sdk.java.diagnostics.model.enums.DiagnosticsIssueCode;
import growthbook.sdk.java.diagnostics.model.enums.DiagnosticsIssueSeverity;
import growthbook.sdk.java.diagnostics.model.FeatureDiagnostics;
import growthbook.sdk.java.diagnostics.model.enums.FeatureState;
import growthbook.sdk.java.diagnostics.model.HealthDiagnostics;
import growthbook.sdk.java.diagnostics.model.RefreshDiagnostics;
import growthbook.sdk.java.diagnostics.model.RemoteEvalDiagnostics;
import growthbook.sdk.java.diagnostics.model.StreamingDiagnostics;
import growthbook.sdk.java.diagnostics.model.enums.StreamingState;
import growthbook.sdk.java.diagnostics.provider.model.DiagnosticsSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Derives a compact health summary from diagnostics sections.
 */
public final class DiagnosticsHealthResolver {

    public HealthDiagnostics resolve(DiagnosticsSnapshot snapshot) {
        long generatedAtMillis = snapshot.getGeneratedAtMillis();
        ClientDiagnostics client = snapshot.getClient();
        FeatureDiagnostics features = snapshot.getFeatures();
        RefreshDiagnostics refresh = snapshot.getRefresh();

        List<DiagnosticsIssue> issues = new ArrayList<>();
        addClientIssues(issues, generatedAtMillis, client);
        addConfigIssues(issues, generatedAtMillis, snapshot.getConfig());
        addFeatureIssues(issues, generatedAtMillis, client, features);
        addRefreshIssues(issues, generatedAtMillis, client, features, refresh);
        addCacheIssues(issues, generatedAtMillis, client, snapshot.getCache());
        addStreamingIssues(issues, generatedAtMillis, client, snapshot.getStreaming());
        addRemoteEvalIssues(issues, generatedAtMillis, client, snapshot.getRemoteEval());

        return HealthDiagnostics.builder()
                .state(resolveState(client, issues))
                .issues(Collections.unmodifiableList(issues))
                .build();
    }

    private void addClientIssues(
            List<DiagnosticsIssue> issues,
            long generatedAtMillis,
            ClientDiagnostics client
    ) {
        if (client.isShutdown()) {
            issues.add(issue(
                    DiagnosticsIssueCode.CLIENT_SHUTDOWN,
                    DiagnosticsIssueSeverity.INFO,
                    "Client has been shut down.",
                    generatedAtMillis
            ));
            return;
        }
        if (!client.isInitialized()) {
            issues.add(issue(
                    DiagnosticsIssueCode.CLIENT_NOT_INITIALIZED,
                    DiagnosticsIssueSeverity.WARNING,
                    "Client has not completed initialization.",
                    generatedAtMillis
            ));
        }
    }

    private void addConfigIssues(
            List<DiagnosticsIssue> issues,
            long generatedAtMillis,
            ConfigDiagnostics config
    ) {
        if (!config.isEnabled()) {
            issues.add(issue(
                    DiagnosticsIssueCode.SDK_DISABLED,
                    DiagnosticsIssueSeverity.WARNING,
                    "SDK evaluations are globally disabled by configuration.",
                    generatedAtMillis
            ));
        }
        if (config.isQaMode()) {
            issues.add(issue(
                    DiagnosticsIssueCode.QA_MODE_ENABLED,
                    DiagnosticsIssueSeverity.INFO,
                    "QA mode is enabled.",
                    generatedAtMillis
            ));
        }
    }

    private void addFeatureIssues(
            List<DiagnosticsIssue> issues,
            long generatedAtMillis,
            ClientDiagnostics client,
            FeatureDiagnostics features
    ) {
        if (client.isShutdown() || client.isRemoteEvalEnabled() || !client.isInitialized()) {
            return;
        }
        if (!features.isAvailable()) {
            issues.add(issue(
                    DiagnosticsIssueCode.FEATURES_NOT_LOADED,
                    DiagnosticsIssueSeverity.ERROR,
                    "Local feature data is not loaded.",
                    generatedAtMillis
            ));
            return;
        }
        if (features.getState() == FeatureState.LOADED_EMPTY) {
            issues.add(issue(
                    DiagnosticsIssueCode.FEATURES_EMPTY,
                    DiagnosticsIssueSeverity.INFO,
                    "Feature data is loaded but contains no feature definitions.",
                    generatedAtMillis
            ));
        }
    }

    private void addRefreshIssues(
            List<DiagnosticsIssue> issues,
            long generatedAtMillis,
            ClientDiagnostics client,
            FeatureDiagnostics features,
            RefreshDiagnostics refresh
    ) {
        if (!client.isShutdown() && client.isInitialized() && features.isAvailable() && refresh.isStale()) {
            issues.add(issue(
                    DiagnosticsIssueCode.FEATURES_STALE,
                    DiagnosticsIssueSeverity.WARNING,
                    "Feature data is past its cache expiry.",
                    generatedAtMillis
            ));
        }
        if (!hasUnresolvedRefreshError(refresh)) {
            return;
        }

        DiagnosticsIssueSeverity severity = features.isAvailable()
                ? DiagnosticsIssueSeverity.WARNING
                : DiagnosticsIssueSeverity.ERROR;
        String errorCode = refresh.getLastError().getCode() == null
                ? refresh.getLastError().getType()
                : refresh.getLastError().getCode();
        issues.add(issue(
                DiagnosticsIssueCode.REFRESH_FAILED,
                severity,
                "Last feature refresh failed: " + errorCode + " - " + refresh.getLastError().getMessage(),
                generatedAtMillis
        ));
    }

    private boolean hasUnresolvedRefreshError(RefreshDiagnostics refresh) {
        if (refresh.getLastError() == null) {
            return false;
        }
        if (refresh.getConsecutiveFailures() > 0) {
            return true;
        }
        Long lastSuccessfulRefreshAtMillis = refresh.getLastSuccessfulRefreshAtMillis();
        Long lastFailureAtMillis = refresh.getLastFailureAtMillis();
        if (lastSuccessfulRefreshAtMillis == null) {
            return true;
        }
        return lastFailureAtMillis != null && lastFailureAtMillis > lastSuccessfulRefreshAtMillis;
    }

    private void addCacheIssues(
            List<DiagnosticsIssue> issues,
            long generatedAtMillis,
            ClientDiagnostics client,
            CacheDiagnostics cache
    ) {
        if (client.isShutdown() || !client.isInitialized()) {
            return;
        }
        if (cache.isEnabled() && cache.getState() == CacheState.UNKNOWN) {
            issues.add(issue(
                    DiagnosticsIssueCode.CACHE_STATE_UNKNOWN,
                    DiagnosticsIssueSeverity.WARNING,
                    "Cache persistence is enabled but cache state is unavailable.",
                    generatedAtMillis
            ));
        }
    }

    private void addStreamingIssues(
            List<DiagnosticsIssue> issues,
            long generatedAtMillis,
            ClientDiagnostics client,
            StreamingDiagnostics streaming
    ) {
        if (client.isShutdown() || !client.isInitialized()) {
            return;
        }
        if (streaming.getState() == StreamingState.UNSUPPORTED) {
            issues.add(issue(
                    DiagnosticsIssueCode.STREAMING_UNSUPPORTED,
                    DiagnosticsIssueSeverity.WARNING,
                    "Streaming refresh is configured but not supported by the server.",
                    generatedAtMillis
            ));
        }
        if (streaming.getState() == StreamingState.INTERRUPTED) {
            issues.add(issue(
                    DiagnosticsIssueCode.STREAMING_INTERRUPTED,
                    DiagnosticsIssueSeverity.WARNING,
                    "Streaming refresh is currently reconnecting.",
                    generatedAtMillis
            ));
        }
    }

    private void addRemoteEvalIssues(
            List<DiagnosticsIssue> issues,
            long generatedAtMillis,
            ClientDiagnostics client,
            RemoteEvalDiagnostics remoteEval
    ) {
        if (client.isShutdown() || !client.isInitialized() || !remoteEval.isEnabled()) {
            return;
        }
        if (!remoteEval.isReady()) {
            issues.add(issue(
                    DiagnosticsIssueCode.REMOTE_EVAL_NOT_READY,
                    DiagnosticsIssueSeverity.ERROR,
                    "Remote evaluation is enabled but not ready.",
                    generatedAtMillis
            ));
        }
    }

    private DiagnosticsHealthState resolveState(
            ClientDiagnostics client,
            List<DiagnosticsIssue> issues
    ) {
        if (client.isShutdown()) {
            return DiagnosticsHealthState.SHUTDOWN;
        }
        if (hasSeverity(issues, DiagnosticsIssueSeverity.ERROR)) {
            return DiagnosticsHealthState.ERROR;
        }
        if (!client.isInitialized()) {
            return DiagnosticsHealthState.NOT_READY;
        }
        if (hasSeverity(issues, DiagnosticsIssueSeverity.WARNING)) {
            return DiagnosticsHealthState.DEGRADED;
        }
        return DiagnosticsHealthState.READY;
    }

    private boolean hasSeverity(List<DiagnosticsIssue> issues, DiagnosticsIssueSeverity severity) {
        for (DiagnosticsIssue issue : issues) {
            if (issue.getSeverity() == severity) {
                return true;
            }
        }
        return false;
    }

    private DiagnosticsIssue issue(
            DiagnosticsIssueCode code,
            DiagnosticsIssueSeverity severity,
            String message,
            long generatedAtMillis
    ) {
        return DiagnosticsIssue.builder()
                .code(code)
                .severity(severity)
                .message(message)
                .timestampMillis(generatedAtMillis)
                .build();
    }
}
