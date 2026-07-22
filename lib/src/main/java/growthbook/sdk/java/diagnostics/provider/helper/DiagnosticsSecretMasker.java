package growthbook.sdk.java.diagnostics.provider.helper;

import static growthbook.sdk.java.constants.SDKConstants.Endpoints.FEATURES_ENDPOINT_PATH;
import static growthbook.sdk.java.constants.SDKConstants.Endpoints.STREAMING_ENDPOINT_PATH;

import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.repository.GBFeaturesRepository;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Masks known diagnostics secrets before they can be exposed in support dumps.
 */
public final class DiagnosticsSecretMasker {

    private static final String REDACTED_SECRET = "[redacted]";
    private static final String REDACTED_QUERY = "?[redacted]";
    private static final Pattern FEATURES_ENDPOINT_KEY_PATTERN = endpointPathPattern(FEATURES_ENDPOINT_PATH);
    private static final Pattern STREAMING_ENDPOINT_KEY_PATTERN = endpointPathPattern(STREAMING_ENDPOINT_PATH);
    private static final Pattern URL_SENSITIVE_PARTS_PATTERN = Pattern.compile(
            "([A-Za-z][A-Za-z0-9+.-]*://)([^\\s/?#@]+@)?([^\\s?#]*)(\\?[^\\s#]*)?"
    );

    private final Map<String, String> replacements;

    private DiagnosticsSecretMasker(Map<String, String> replacements) {
        this.replacements = replacements;
    }

    public static DiagnosticsSecretMasker from(Options options, @Nullable GBFeaturesRepository repository) {
        Map<String, String> replacements = new HashMap<>();

        String optionsClientKey = options.getClientKey();
        addReplacement(replacements, optionsClientKey, maskClientKeyValue(optionsClientKey));

        String repositoryClientKey = repositoryClientKey(repository);
        addReplacement(replacements, repositoryClientKey, maskClientKeyValue(repositoryClientKey));

        addReplacement(replacements, options.getDecryptionKey(), REDACTED_SECRET);
        if (repository != null) {
            addReplacement(replacements, repository.getDecryptionKey(), REDACTED_SECRET);
            addRepositoryEncryptionKey(replacements, repository);
        }

        return new DiagnosticsSecretMasker(replacements);
    }

    public static DiagnosticsSecretMasker empty() {
        return new DiagnosticsSecretMasker(Collections.emptyMap());
    }

    @Nullable
    public String mask(@Nullable String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        String masked = value;
        for (Map.Entry<String, String> replacement : sortedReplacements()) {
            masked = masked.replace(replacement.getKey(), replacement.getValue());
        }
        return redactSensitiveUrlParts(maskEndpointPathSegments(masked));
    }

    @Nullable
    public String maskClientKey(@Nullable String clientKey) {
        return maskClientKeyValue(clientKey);
    }

    @Nullable
    public String maskEndpoint(@Nullable String endpoint) {
        if (endpoint == null || endpoint.isEmpty()) {
            return endpoint;
        }

        String masked = applyKnownReplacements(endpoint);
        masked = maskEndpointPathSegments(masked);
        masked = maskLastPathSegment(masked);
        return redactSensitiveUrlParts(masked);
    }

    private static Pattern endpointPathPattern(String endpointPath) {
        return Pattern.compile("(" + Pattern.quote(endpointPath) + ")([^/?#\\s]+)");
    }

    @Nullable
    private static String repositoryClientKey(@Nullable GBFeaturesRepository repository) {
        if (repository == null || repository.getFeaturesEndpoint() == null) {
            return null;
        }
        int markerIndex = repository.getFeaturesEndpoint().indexOf(FEATURES_ENDPOINT_PATH);
        if (markerIndex < 0) {
            return null;
        }
        return repository.getFeaturesEndpoint().substring(markerIndex + FEATURES_ENDPOINT_PATH.length());
    }

    @SuppressWarnings("deprecation")
    private static void addRepositoryEncryptionKey(
            Map<String, String> replacements,
            GBFeaturesRepository repository
    ) {
        addReplacement(replacements, repository.getEncryptionKey(), REDACTED_SECRET);
    }

    private static void addReplacement(
            Map<String, String> replacements,
            @Nullable String raw,
            @Nullable String masked
    ) {
        if (raw == null || raw.isEmpty() || masked == null) {
            return;
        }
        replacements.put(raw, masked);
    }

    @Nullable
    private static String maskClientKeyValue(@Nullable String value) {
        if (value == null || value.isEmpty()) {
            return null;
        }
        if (value.length() <= 5) {
            return "***";
        }

        int prefixLength = value.length() <= 8 ? 3 : 4;
        int suffixLength = value.length() <= 8 ? 2 : 4;
        return value.substring(0, prefixLength) + "..." + value.substring(value.length() - suffixLength);
    }

    private String applyKnownReplacements(String value) {
        String masked = value;
        for (Map.Entry<String, String> replacement : sortedReplacements()) {
            masked = masked.replace(replacement.getKey(), replacement.getValue());
        }
        return masked;
    }

    private static String maskEndpointPathSegments(String value) {
        return maskEndpointPathSegment(
                STREAMING_ENDPOINT_KEY_PATTERN,
                maskEndpointPathSegment(FEATURES_ENDPOINT_KEY_PATTERN, value)
        );
    }

    private static String maskEndpointPathSegment(Pattern pattern, String value) {
        Matcher matcher = pattern.matcher(value);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            String maskedSegment = maskClientKeyValue(matcher.group(2));
            String replacement = matcher.group(1) + (maskedSegment == null ? REDACTED_SECRET : maskedSegment);
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private static String redactSensitiveUrlParts(String value) {
        Matcher matcher = URL_SENSITIVE_PARTS_PATTERN.matcher(value);
        StringBuffer buffer = new StringBuffer();
        while (matcher.find()) {
            boolean hasUserInfo = matcher.group(2) != null;
            boolean hasQuery = matcher.group(4) != null;
            if (!hasUserInfo && !hasQuery) {
                continue;
            }

            String replacement = matcher.group(1)
                    + (hasUserInfo ? REDACTED_SECRET + "@" : "")
                    + matcher.group(3)
                    + (hasQuery ? REDACTED_QUERY : "");
            matcher.appendReplacement(buffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(buffer);
        return buffer.toString();
    }

    private String maskLastPathSegment(String endpoint) {
        int suffixIndex = firstSuffixIndex(endpoint);
        String path = suffixIndex < 0 ? endpoint : endpoint.substring(0, suffixIndex);
        String suffix = suffixIndex < 0 ? "" : endpoint.substring(suffixIndex);
        int lastSlashIndex = path.lastIndexOf('/');
        if (lastSlashIndex < 0 || lastSlashIndex == path.length() - 1) {
            return endpoint;
        }
        String lastSegment = path.substring(lastSlashIndex + 1);
        String maskedSegment = maskClientKeyValue(lastSegment);
        return maskedSegment == null ? endpoint : path.substring(0, lastSlashIndex + 1) + maskedSegment + suffix;
    }

    private int firstSuffixIndex(String endpoint) {
        int queryIndex = endpoint.indexOf('?');
        int fragmentIndex = endpoint.indexOf('#');
        if (queryIndex < 0) {
            return fragmentIndex;
        }
        if (fragmentIndex < 0) {
            return queryIndex;
        }
        return Math.min(queryIndex, fragmentIndex);
    }

    private List<Map.Entry<String, String>> sortedReplacements() {
        List<Map.Entry<String, String>> entries = new ArrayList<>(this.replacements.entrySet());
        entries.sort(Comparator
                .comparingInt((Map.Entry<String, String> entry) -> entry.getKey().length())
                .reversed()
                .thenComparing(Map.Entry::getKey));
        return entries;
    }
}
