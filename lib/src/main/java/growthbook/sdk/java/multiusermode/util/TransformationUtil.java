package growthbook.sdk.java.multiusermode.util;

import com.google.common.reflect.TypeToken;
import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import growthbook.sdk.java.model.Feature;
import growthbook.sdk.java.util.DecryptionUtils;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class TransformationUtil {
    private static final Gson GSON = GrowthBookJsonUtils.getInstance().gson;


    public static Map<String, Feature<?>> transformFeatures(String featuresJsonString) {
        try {
            Type featureMapType = new TypeToken<Map<String, Feature<?>>>() {
            }.getType();
            return GSON.fromJson(featuresJsonString, featureMapType);
        } catch (JsonSyntaxException e) {
            log.error("Invalid JSON format: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("Error parsing features JSON: {}", e.getMessage(), e);
        }
        return Collections.emptyMap();
    }

    public static JsonObject transformSavedGroups(String savedGroupsJsonString) {
        try {
            return GSON.fromJson(savedGroupsJsonString, JsonObject.class);
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

            JsonElement element = GSON.fromJson(attributesJsonString, JsonElement.class);
            return (element != null && element.isJsonObject()) ? element.getAsJsonObject() : new JsonObject();
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            return new JsonObject();
        }
    }
}
