package growthbook.sdk.java.model;

import lombok.Getter;

@Getter
public enum FeatureResponseKey {
    ENCRYPTED_FEATURES_KEY("encryptedFeatures"),
    ENCRYPTED_SAVED_GROUPS_KEY("encryptedSavedGroups"),
    FEATURE_KEY("features"),
    SAVED_GROUP_KEY("savedGroups");

    private final String key;

    FeatureResponseKey(String key) {
        this.key = key;
    }
}
