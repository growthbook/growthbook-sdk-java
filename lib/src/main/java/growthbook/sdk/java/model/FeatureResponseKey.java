package growthbook.sdk.java.model;

import lombok.Getter;

@Getter
public enum FeatureResponseKey {
    ENCRYPTED_FEATURES_KEY("encryptedFeatures"),
    ENCRYPTED_SAVED_GROUPS_KEY("encryptedSavedGroups"),
    ENCRYPTED_CONTEXTUAL_BANDITS_KEY("encryptedContextualBandits"),
    FEATURE_KEY("features"),
    SAVED_GROUP_KEY("savedGroups"),
    CONTEXTUAL_BANDITS_KEY("contextualBandits");

    private final String key;

    FeatureResponseKey(String key) {
        this.key = key;
    }
}
