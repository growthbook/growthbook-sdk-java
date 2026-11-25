package growthbook.sdk.java.repository;

import com.google.gson.JsonObject;
import growthbook.sdk.java.model.Feature;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.Map;

/**
 * Immutable view of one successfully processed features payload: the raw JSON
 * strings and their parsed forms, captured together.
 *
 * <p>The repository swaps a single {@code AtomicReference<FeatureSnapshot>} on
 * refresh, so a reader always observes features and saved groups from the SAME
 * payload. Reading the parts through separate getters across a concurrent
 * refresh could otherwise pair new features with old saved groups.
 */
public final class FeatureSnapshot {
    public static final FeatureSnapshot EMPTY = new FeatureSnapshot(
            GBFeaturesRepository.EMPTY_JSON_OBJECT_STRING,
            GBFeaturesRepository.EMPTY_JSON_OBJECT_STRING,
            "",
            Collections.emptyMap(),
            new JsonObject(),
            new JsonObject(),
            null
    );

    private final String featuresJson;
    private final String savedGroupsJson;
    private final String contextualBanditsJson;
    private final Map<String, Feature<?>> parsedFeatures;
    private final JsonObject parsedSavedGroups;
    private final JsonObject parsedContextualBandits;
    /**
     * The raw features-endpoint response this snapshot was built from, captured in the same immutable
     * swap as the parsed parts so the read-only payload accessors never report a response that
     * disagrees with the features currently driving evaluation. {@code null} for the empty snapshot
     * and for synthetic snapshots built through {@link #of}.
     */
    @Nullable
    private final String rawResponseJson;

    FeatureSnapshot(String featuresJson,
                    String savedGroupsJson,
                    String contextualBanditsJson,
                    Map<String, Feature<?>> parsedFeatures,
                    JsonObject parsedSavedGroups,
                    JsonObject parsedContextualBandits) {
        this(featuresJson, savedGroupsJson, contextualBanditsJson,
                parsedFeatures, parsedSavedGroups, parsedContextualBandits, null);
    }

    FeatureSnapshot(String featuresJson,
                    String savedGroupsJson,
                    String contextualBanditsJson,
                    Map<String, Feature<?>> parsedFeatures,
                    JsonObject parsedSavedGroups,
                    JsonObject parsedContextualBandits,
                    @Nullable String rawResponseJson) {
        this.featuresJson = featuresJson;
        this.savedGroupsJson = savedGroupsJson;
        this.contextualBanditsJson = contextualBanditsJson;
        this.parsedFeatures = parsedFeatures;
        this.parsedSavedGroups = parsedSavedGroups;
        this.parsedContextualBandits = parsedContextualBandits;
        this.rawResponseJson = rawResponseJson;
    }

    /**
     * Builds a snapshot from pre-parsed parts, with no contextual bandits. Intended for tests and
     * callers constructing synthetic payloads; the repository builds its own snapshots from fetched
     * responses.
     *
     * @param featuresJson      raw features JSON
     * @param savedGroupsJson   raw saved groups JSON
     * @param parsedFeatures    parsed feature definitions
     * @param parsedSavedGroups parsed saved groups
     * @return an immutable snapshot of the supplied parts
     */
    public static FeatureSnapshot of(String featuresJson,
                                     String savedGroupsJson,
                                     Map<String, Feature<?>> parsedFeatures,
                                     JsonObject parsedSavedGroups) {
        return new FeatureSnapshot(featuresJson, savedGroupsJson, "",
                parsedFeatures, parsedSavedGroups, new JsonObject());
    }

    /**
     * Builds a snapshot from pre-parsed parts, including contextual bandit definitions. Intended for
     * tests and callers constructing synthetic payloads; the repository builds its own snapshots from
     * fetched responses.
     *
     * @param featuresJson            raw features JSON
     * @param savedGroupsJson         raw saved groups JSON
     * @param parsedFeatures          parsed feature definitions
     * @param parsedSavedGroups       parsed saved groups
     * @param parsedContextualBandits parsed contextual bandit definitions
     * @return an immutable snapshot of the supplied parts
     */
    public static FeatureSnapshot of(String featuresJson,
                                     String savedGroupsJson,
                                     Map<String, Feature<?>> parsedFeatures,
                                     JsonObject parsedSavedGroups,
                                     JsonObject parsedContextualBandits) {
        return new FeatureSnapshot(featuresJson, savedGroupsJson,
                parsedContextualBandits == null ? "" : parsedContextualBandits.toString(),
                parsedFeatures, parsedSavedGroups,
                parsedContextualBandits == null ? new JsonObject() : parsedContextualBandits);
    }

    public String getFeaturesJson() {
        return featuresJson;
    }

    public String getSavedGroupsJson() {
        return savedGroupsJson;
    }

    public String getContextualBanditsJson() {
        return contextualBanditsJson;
    }

    public Map<String, Feature<?>> getParsedFeatures() {
        return parsedFeatures;
    }

    public JsonObject getParsedSavedGroups() {
        return parsedSavedGroups;
    }

    public JsonObject getParsedContextualBandits() {
        return parsedContextualBandits;
    }

    /**
     * @return the raw features-endpoint response this snapshot was built from, or {@code null} when
     * unknown (the empty snapshot, or a synthetic snapshot built through {@link #of})
     */
    @Nullable
    public String getRawResponseJson() {
        return rawResponseJson;
    }
}
