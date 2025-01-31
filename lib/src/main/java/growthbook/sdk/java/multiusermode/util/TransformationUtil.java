package growthbook.sdk.java.multiusermode.util;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import growthbook.sdk.java.DecryptionUtils;
import growthbook.sdk.java.GrowthBookJsonUtils;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

@Slf4j
public class TransformationUtil {

    public static JsonObject transformFeatures(String featuresJsonString) {
        try {
            return GrowthBookJsonUtils.getInstance().gson.fromJson(featuresJsonString, JsonObject.class);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return null;
        }
    }

    public static JsonObject transformSavedGroups(String savedGroupsJsonString) {
        try {
            return GrowthBookJsonUtils.getInstance().gson.fromJson(savedGroupsJsonString, JsonObject.class);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return null;
        }
    }

    public static JsonObject transformEncryptedFeatures(
            @Nullable String featuresJson,
            @Nullable String encryptionKey
    ) {
        // Features start as empty JSON
        JsonObject jsonObject = new JsonObject();

        if (encryptionKey != null && featuresJson != null) {
            // Attempt to decrypt payload
            try {
                String decrypted = DecryptionUtils.decrypt(featuresJson, encryptionKey);
                String featuresJsonDecrypted = decrypted.trim();
                jsonObject = transformFeatures(featuresJsonDecrypted);

            } catch (DecryptionUtils.DecryptionException e) {
                log.error(e.getMessage(), e);

            }
        } else if (featuresJson != null) {
            jsonObject = transformFeatures(featuresJson);
        }

        return jsonObject;
    }

    public static JsonObject transformAttributes(@Nullable String attributesJsonString) {
        try {
            if (attributesJsonString == null) {
                return new JsonObject();
            }

            JsonElement element = GrowthBookJsonUtils.getInstance().gson.fromJson(attributesJsonString, JsonElement.class);
            if (element == null || element.isJsonNull()) {
                return new JsonObject();
            }

            return GrowthBookJsonUtils.getInstance().gson.fromJson(attributesJsonString, JsonObject.class);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return new JsonObject();
        }
    }
}
