package growthbook.sdk.java.remoteeval;

import growthbook.sdk.java.exception.InvalidOptionsException;
import growthbook.sdk.java.model.GBContext;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.repository.FeatureRefreshStrategy;
import growthbook.sdk.java.util.StringUtils;

import javax.annotation.Nullable;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Validates SDK options before enabling remote evaluation.
 */
public final class RemoteEvalOptionsValidator {

    private static final String SCHEME_SEPARATOR = "://";
    private static final String DEFAULT_SCHEME = "https://";
    private static final String GROWTHBOOK_CLOUD_DOMAIN = "growthbook.io";

    private RemoteEvalOptionsValidator() {
    }

    public static void validate(Options options) {
        if (options == null || !options.isRemoteEvalEnabled()) {
            return;
        }

        throwIfAny(collect(ValidationTarget.from(options), true));
    }

    public static void validate(GBContext context) {
        if (context == null || !context.isRemoteEvalEnabled()) {
            return;
        }

        throwIfAny(collect(ValidationTarget.from(context), true));
    }

    /**
     * Returns the remote-eval specific incompatibilities for the supplied options.
     *
     * <p>Intended for callers that already validate {@code apiHost}/{@code clientKey} presence
     * generally (e.g. {@code OptionsValidator}); this method therefore excludes those checks to
     * avoid duplicate messages and reports only constraints unique to remote evaluation.
     *
     * @param options client options to inspect; {@code null} or non remote-eval yields an empty list
     * @return an immutable list of human-readable problem descriptions, empty when valid
     */
    public static List<String> remoteEvalViolations(@Nullable Options options) {
        if (options == null || !options.isRemoteEvalEnabled()) {
            return Collections.emptyList();
        }

        return Collections.unmodifiableList(collect(ValidationTarget.from(options), false));
    }

    private static List<String> collect(ValidationTarget target, boolean includeApiConfiguration) {
        List<String> violations = new ArrayList<>();
        if (includeApiConfiguration) {
            collectApiConfiguration(target, violations);
        }
        collectUnsupportedOptions(target, violations);
        collectGrowthBookCloudHost(target.apiHost, violations);
        return violations;
    }

    private static void throwIfAny(List<String> violations) {
        if (!violations.isEmpty()) {
            throw new InvalidOptionsException(
                    "Invalid GrowthBook options: " + String.join("; ", violations), violations);
        }
    }

    private static void collectApiConfiguration(ValidationTarget target, List<String> violations) {
        if (StringUtils.isBlank(target.apiHost)) {
            violations.add("apiHost is required when remoteEval is enabled");
        }
        if (StringUtils.isBlank(target.clientKey)) {
            violations.add("clientKey is required when remoteEval is enabled");
        }
    }

    private static void collectUnsupportedOptions(ValidationTarget target, List<String> violations) {
        if (!StringUtils.isBlank(target.decryptionKey)) {
            violations.add("decryptionKey is not supported when remoteEval is enabled");
        }
        if (target.hasStickyBucketService) {
            violations.add("stickyBucketService is not supported when remoteEval is enabled");
        }
        if (target.refreshStrategy == FeatureRefreshStrategy.STALE_WHILE_REVALIDATE) {
            violations.add("stale-while-revalidate is not supported when remoteEval is enabled");
        }
    }

    private static void collectGrowthBookCloudHost(@Nullable String apiHost, List<String> violations) {
        if (isGrowthBookCloudHost(apiHost)) {
            violations.add("remoteEval requires a self-hosted GrowthBook proxy or edge API host");
        }
    }

    private static boolean isGrowthBookCloudHost(@Nullable String apiHost) {
        try {
            String raw = StringUtils.normalize(apiHost).trim();
            String normalized = raw.contains(SCHEME_SEPARATOR) ? raw : DEFAULT_SCHEME + raw;
            String host = URI.create(normalized).getHost();
            if (host == null) {
                return false;
            }
            String lowerHost = host.toLowerCase(Locale.ROOT);
            return lowerHost.equals(GROWTHBOOK_CLOUD_DOMAIN)
                    || lowerHost.endsWith("." + GROWTHBOOK_CLOUD_DOMAIN);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static final class ValidationTarget {

        @Nullable
        private final String apiHost;

        @Nullable
        private final String clientKey;

        @Nullable
        private final String decryptionKey;

        private final boolean hasStickyBucketService;

        @Nullable
        private final FeatureRefreshStrategy refreshStrategy;

        private ValidationTarget(
                @Nullable String apiHost,
                @Nullable String clientKey,
                @Nullable String decryptionKey,
                boolean hasStickyBucketService,
                @Nullable FeatureRefreshStrategy refreshStrategy
        ) {
            this.apiHost = apiHost;
            this.clientKey = clientKey;
            this.decryptionKey = decryptionKey;
            this.hasStickyBucketService = hasStickyBucketService;
            this.refreshStrategy = refreshStrategy;
        }

        private static ValidationTarget from(Options options) {
            return new ValidationTarget(
                    options.getApiHost(),
                    options.getClientKey(),
                    options.getDecryptionKey(),
                    options.getStickyBucketService() != null,
                    options.getRefreshStrategy()
            );
        }

        private static ValidationTarget from(GBContext context) {
            return new ValidationTarget(
                    context.getApiHost(),
                    context.getClientKey(),
                    context.getEncryptionKey(),
                    context.getStickyBucketService() != null,
                    null
            );
        }
    }
}
