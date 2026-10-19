package growthbook.sdk.java.util;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.Feature;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * <b>INTERNAL:</b> shapes the feature-endpoint payload for the read-only accessors on the SDK
 * facades ({@code getPayload}, {@code getDecryptedPayload}, {@code getExperiments}).
 *
 * <p>The "payload" is a JSON object in the same shape as the features endpoint response
 * ({@code {"features": {...}, "savedGroups": {...}, "contextualBandits": {...}, "experiments": [...]}},
 * or the {@code encrypted*} variants when the endpoint is encrypted).
 *
 * <p>All methods are stateless and return freshly allocated objects, so callers may mutate the
 * result without affecting SDK state.
 */
@Slf4j
public final class PayloadUtils {

    /**
     * Encrypted payload keys paired with the plaintext key each decrypts into. Mirrors the fields
     * the features endpoint may deliver encrypted.
     */
    private static final String[][] ENCRYPTED_TO_PLAIN = {
            {"encryptedFeatures", "features"},
            {"encryptedSavedGroups", "savedGroups"},
            {"encryptedContextualBandits", "contextualBandits"},
            {"encryptedExperiments", "experiments"},
    };

    private PayloadUtils() {
    }

    /**
     * Parses a raw features-endpoint response string into a payload object.
     *
     * @param rawResponseJson the raw response, exactly as received; may be {@code null} or blank
     * @return the parsed payload, or an empty object when the input is {@code null}, blank, or not a
     * JSON object
     */
    public static JsonObject parsePayload(@Nullable String rawResponseJson) {
        if (rawResponseJson == null || rawResponseJson.trim().isEmpty()) {
            return new JsonObject();
        }
        try {
            JsonElement parsed = GrowthBookJsonUtils.getInstance().gson.fromJson(rawResponseJson, JsonElement.class);
            if (parsed != null && parsed.isJsonObject()) {
                return parsed.getAsJsonObject();
            }
        } catch (JsonSyntaxException e) {
            log.warn("Unable to parse the current feature payload for a read-only accessor.", e);
        }
        return new JsonObject();
    }

    /**
     * Rebuilds a payload object from already-decrypted, in-memory state. Used on paths that do not
     * retain the raw response (remote evaluation, and single-context evaluation without a repository),
     * where the loaded state is always plaintext.
     *
     * @param features         the loaded feature definitions; {@code null} yields an empty features object
     * @param savedGroups      the loaded saved groups; omitted when {@code null} or empty
     * @param contextualBandits the loaded contextual bandit definitions; omitted when {@code null} or empty
     * @return a payload object holding the supplied state
     */
    public static JsonObject reconstructPayload(
            @Nullable Map<String, Feature<?>> features,
            @Nullable JsonObject savedGroups,
            @Nullable JsonObject contextualBandits
    ) {
        Gson gson = GrowthBookJsonUtils.getInstance().gson;
        JsonObject payload = new JsonObject();
        payload.add("features", features == null ? new JsonObject() : gson.toJsonTree(features));
        if (savedGroups != null && !savedGroups.isEmpty()) {
            payload.add("savedGroups", savedGroups.deepCopy());
        }
        if (contextualBandits != null && !contextualBandits.isEmpty()) {
            payload.add("contextualBandits", contextualBandits.deepCopy());
        }
        return payload;
    }

    /**
     * Returns a copy of {@code rawPayload} with any encrypted fields decrypted into their plaintext
     * counterparts and the encrypted fields removed. When there is nothing to decrypt (no encrypted
     * fields, or no key) the returned copy is structurally equal to {@code rawPayload}.
     *
     * <p>Decryption delegates to {@link DecryptionUtils}. A field that fails to decrypt is left as-is
     * and a warning is logged; the decryption key is never logged.
     *
     * @param rawPayload    the raw payload, as received
     * @param decryptionKey the decryption key, or {@code null}/blank when the payload is not encrypted
     * @return a new payload object with encrypted fields resolved to plaintext
     */
    public static JsonObject decryptPayload(JsonObject rawPayload, @Nullable String decryptionKey) {
        JsonObject decrypted = rawPayload == null ? new JsonObject() : rawPayload.deepCopy();
        if (decryptionKey == null || decryptionKey.trim().isEmpty()) {
            return decrypted;
        }

        Gson gson = GrowthBookJsonUtils.getInstance().gson;
        for (String[] pair : ENCRYPTED_TO_PLAIN) {
            String encryptedKey = pair[0];
            String plainKey = pair[1];
            JsonElement encryptedElement = decrypted.get(encryptedKey);
            if (encryptedElement == null || !encryptedElement.isJsonPrimitive()) {
                continue;
            }
            try {
                String plaintext = DecryptionUtils.decrypt(encryptedElement.getAsString(), decryptionKey);
                decrypted.add(plainKey, gson.fromJson(plaintext, JsonElement.class));
                decrypted.remove(encryptedKey);
            } catch (DecryptionUtils.DecryptionException | JsonSyntaxException e) {
                log.warn("Unable to decrypt payload field '{}' for a read-only accessor.", encryptedKey, e);
            }
        }
        return decrypted;
    }

    /**
     * Extracts the auto-experiment definitions from a (decrypted) payload.
     *
     * @param payload a decrypted payload; may be {@code null}
     * @return an unmodifiable list of parsed experiments, empty when the payload carries none
     */
    public static List<Experiment<?>> extractExperiments(@Nullable JsonObject payload) {
        if (payload == null) {
            return Collections.emptyList();
        }
        JsonElement experimentsElement = payload.get("experiments");
        if (experimentsElement == null || !experimentsElement.isJsonArray()) {
            return Collections.emptyList();
        }

        Gson gson = GrowthBookJsonUtils.getInstance().gson;
        List<Experiment<?>> experiments = new ArrayList<>();
        for (JsonElement element : experimentsElement.getAsJsonArray()) {
            try {
                Experiment<?> experiment = gson.fromJson(element, Experiment.class);
                if (experiment != null) {
                    experiments.add(experiment);
                }
            } catch (JsonSyntaxException e) {
                log.warn("Skipping an experiment that could not be parsed for a read-only accessor.", e);
            }
        }
        return Collections.unmodifiableList(experiments);
    }
}
