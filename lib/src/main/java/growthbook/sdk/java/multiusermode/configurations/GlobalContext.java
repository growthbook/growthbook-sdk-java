package growthbook.sdk.java.multiusermode.configurations;

import com.google.gson.JsonObject;
import growthbook.sdk.java.Experiment;
import growthbook.sdk.java.multiusermode.util.TransformationUtil;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.List;

@Data
@Builder
@Slf4j
public class GlobalContext {

    /**
     * Keys are unique identifiers for the features and the values are Feature objects.
     * Feature definitions - To be pulled from API / Cache
     */
    @Nullable
    private JsonObject features; // do you need features Json? -- How is it handled in JS SDK?

    @Nullable
    private String featuresJson;

    @Nullable
    private JsonObject savedGroups;

    private List<Experiment> experiments;

    public JsonObject getFeatures() {
        if (this.features == null) {
            this.features = TransformationUtil.transformFeatures(this.featuresJson);
            // Both are constructed then n there at the time of processing. Restructure them?
            // TBD:M read saved Groups
            // TBD:M experiments
        }
        return this.features;
    }

    public void loadEncryptedFeatures(String decryptionKey) {
        if (this.features == null) {
            this.features = TransformationUtil.transformEncryptedFeatures(this.featuresJson, decryptionKey);
        }
    }
}
