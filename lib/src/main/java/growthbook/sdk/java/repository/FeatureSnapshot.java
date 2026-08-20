package growthbook.sdk.java.repository;

import com.google.gson.JsonObject;
import growthbook.sdk.java.model.Feature;

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
            Collections.emptyMap(),
            new JsonObject()
    );

    private final String featuresJson;
    private final String savedGroupsJson;
    private final Map<String, Feature<?>> parsedFeatures;
    private final JsonObject parsedSavedGroups;

    FeatureSnapshot(String featuresJson,
                    String savedGroupsJson,
                    Map<String, Feature<?>> parsedFeatures,
                    JsonObject parsedSavedGroups) {
        this.featuresJson = featuresJson;
        this.savedGroupsJson = savedGroupsJson;
        this.parsedFeatures = parsedFeatures;
        this.parsedSavedGroups = parsedSavedGroups;
    }

    /**
     * Builds a snapshot from pre-parsed parts. Intended for tests and callers
     * constructing synthetic payloads; the repository builds its own snapshots
     * from fetched responses.
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
        return new FeatureSnapshot(featuresJson, savedGroupsJson, parsedFeatures, parsedSavedGroups);
    }

    public String getFeaturesJson() {
        return featuresJson;
    }

    public String getSavedGroupsJson() {
        return savedGroupsJson;
    }

    public Map<String, Feature<?>> getParsedFeatures() {
        return parsedFeatures;
    }

    public JsonObject getParsedSavedGroups() {
        return parsedSavedGroups;
    }
}
