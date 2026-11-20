package growthbook.sdk.java.repository.internal;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.model.FeatureResponseKey;
import growthbook.sdk.java.util.DecryptionUtils;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

/**
 * Extracts the features and saved groups JSON from a features response, decrypting it when a
 * decryption key is set. Anything unusable — not a JSON object, no features, not encrypted as
 * configured — is a {@link FeatureFetchException}, so a bad body never replaces good features.
 */
@Slf4j
public final class PayloadParser {

    @Nullable
    private final String decryptionKey;

    public PayloadParser(@Nullable String decryptionKey) {
        this.decryptionKey = decryptionKey;
    }

    /**
     * @param responseJson the response body
     * @return the extracted payload
     * @throws FeatureFetchException {@code CONFIGURATION_ERROR} for an unusable body,
     *                               {@code UNKNOWN} when decryption fails
     */
    public Payload parse(String responseJson) throws FeatureFetchException {
        JsonObject response = parseObject(responseJson);
        try {
            return decryptionKey != null ? extractEncrypted(response, decryptionKey) : extractPlain(response);
        } catch (DecryptionUtils.DecryptionException e) {
            log.error("FeatureFetchException: UNKNOWN feature fetch error code {}", e.getMessage(), e);
            throw new FeatureFetchException(FeatureFetchException.FeatureFetchErrorCode.UNKNOWN, e.getMessage());
        }
    }

    private static JsonObject parseObject(String responseJson) throws FeatureFetchException {
        JsonObject response;
        try {
            response = GrowthBookJsonUtils.getInstance().gson.fromJson(responseJson, JsonObject.class);
        } catch (JsonSyntaxException e) {
            throw configurationError("features response is not a JSON object: " + e.getMessage());
        }
        if (response == null) {
            throw configurationError("empty or invalid features response");
        }
        return response;
    }

    private static Payload extractEncrypted(JsonObject response, String key)
            throws FeatureFetchException, DecryptionUtils.DecryptionException {
        JsonElement encryptedFeatures = response.get(FeatureResponseKey.ENCRYPTED_FEATURES_KEY.getKey());
        JsonElement encryptedSavedGroups = response.get(FeatureResponseKey.ENCRYPTED_SAVED_GROUPS_KEY.getKey());
        if (!isString(encryptedFeatures)) {
            throw configurationError("encryptionKey provided but endpoint not encrypted");
        }
        String savedGroups = isString(encryptedSavedGroups)
                ? DecryptionUtils.decrypt(encryptedSavedGroups.getAsString(), key).trim()
                : "";
        String features = DecryptionUtils.decrypt(encryptedFeatures.getAsString(), key).trim();
        if (!isObjectJson(features)) {
            throw configurationError("decrypted features payload is not a JSON object");
        }
        return new Payload(features, savedGroups);
    }

    private static Payload extractPlain(JsonObject response) throws FeatureFetchException {
        JsonElement features = response.get(FeatureResponseKey.FEATURE_KEY.getKey());
        JsonElement savedGroups = response.get(FeatureResponseKey.SAVED_GROUP_KEY.getKey());
        if (features == null || !features.isJsonObject()) {
            throw configurationError("No features found");
        }
        String savedGroupsJson = savedGroups != null && savedGroups.isJsonObject() ? savedGroups.toString().trim() : "";
        return new Payload(features.toString().trim(), savedGroupsJson);
    }

    private static boolean isString(@Nullable JsonElement element) {
        return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString();
    }

    private static boolean isObjectJson(String json) {
        try {
            JsonElement parsed = GrowthBookJsonUtils.getInstance().gson.fromJson(json, JsonElement.class);
            return parsed != null && parsed.isJsonObject();
        } catch (JsonSyntaxException e) {
            return false;
        }
    }

    private static FeatureFetchException configurationError(String message) {
        log.error("FeatureFetchException: CONFIGURATION_ERROR feature fetch error code: {}", message);
        return new FeatureFetchException(FeatureFetchException.FeatureFetchErrorCode.CONFIGURATION_ERROR, message);
    }

    /**
     * The features and saved groups JSON of one response; saved groups are {@code ""} when absent.
     */
    public static final class Payload {
        private final String features;
        private final String savedGroups;

        Payload(String features, String savedGroups) {
            this.features = features;
            this.savedGroups = savedGroups;
        }

        public String features() {
            return features;
        }

        public String savedGroups() {
            return savedGroups;
        }
    }
}
