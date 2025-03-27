package growthbook.sdk.java.multiusermode.util;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import growthbook.sdk.java.model.Feature;
import growthbook.sdk.java.util.DecryptionUtils;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class TransformationUtil {

    public static Map<String, Feature<?>> transformFeatures(String featuresJsonString) {
        try {
            Gson gson = GrowthBookJsonUtils.getInstance().gson;
            JsonObject jsonObject = gson.fromJson(featuresJsonString, JsonObject.class);

            Map<String, Feature<?>> featureMap = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : jsonObject.entrySet()) {
                featureMap.put(entry.getKey(), gson.fromJson(entry.getValue(), Feature.class));
            }

            return featureMap;
        } catch (Exception e) {
            log.error("Error parsing features JSON: {}", e.getMessage(), e);
            return Collections.emptyMap();
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

    public static Map<String, Feature<?>> transformEncryptedFeatures(
            @Nullable String featuresJson,
            @Nullable String encryptionKey
    ) {
        // Features start as empty JSON
        Map<String, Feature<?>> features = new HashMap<>();

        if (encryptionKey != null && featuresJson != null) {
            // Attempt to decrypt payload
            try {
                String decrypted = DecryptionUtils.decrypt(featuresJson, encryptionKey);
                String featuresJsonDecrypted = decrypted.trim();
                features = transformFeatures(featuresJsonDecrypted);

            } catch (DecryptionUtils.DecryptionException e) {
                log.error(e.getMessage(), e);

            }
        } else if (featuresJson != null) {
            features = transformFeatures(featuresJson);
        }

        return features;
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
