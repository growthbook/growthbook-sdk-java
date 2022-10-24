package growthbook.sdk.java.models;

import com.google.gson.JsonElement;
import growthbook.sdk.java.services.GrowthBookJsonUtils;

import javax.annotation.Nullable;

public class Feature {

    // TODO: Rules?

    private String defaultValueJsonString;

    public Feature(String defaultValueJsonString) {
        this.defaultValueJsonString = defaultValueJsonString;
    }

    /**
     * Internally the SDK uses Gson. The defaultValue should be a JSON stringified representation.
     * This utility can help get the inferred type.
     * @return the {@link DataType} or null if it cannot be parsed as JSON
     */
    @Nullable
    public DataType getValueDataType() {
        try {
            JsonElement jsonElement = GrowthBookJsonUtils.getInstance()
                    .gson.fromJson(this.defaultValueJsonString, JsonElement.class);
            return GrowthBookJsonUtils.getElementType(jsonElement);
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}
