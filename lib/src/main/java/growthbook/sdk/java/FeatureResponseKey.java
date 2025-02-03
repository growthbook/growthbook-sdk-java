package growthbook.sdk.java;

import lombok.Getter;

@Getter
enum FeatureResponseKey {
    ENCRYPTED_FEATURES_KEY("encryptedFeatures"),
    ENCRYPTED_SAVED_GROUPS_KEY("encryptedSavedGroups"),
    FEATURE_KEY("features"),
    SAVED_GROUP_KEY("savedGroups");

    private final String key;

    FeatureResponseKey(String key) {
        this.key = key;
    }
}
