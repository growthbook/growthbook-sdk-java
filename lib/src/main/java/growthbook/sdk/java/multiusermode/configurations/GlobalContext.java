package growthbook.sdk.java.multiusermode.configurations;

import com.google.gson.JsonObject;
import growthbook.sdk.java.model.Experiment;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

@Data
@Builder
@Slf4j
public class GlobalContext {


    /**
     * Keys are unique identifiers for the features and the values are Feature objects.
     * Feature definitions - To be pulled from API / Cache
     */
    @Getter
    @Nullable
    private JsonObject features;

    @Nullable
    private JsonObject savedGroups;

    @Getter
    @Nullable
    private List<Experiment> experiments;

    @Getter
    @Nullable
    private Boolean enabled;

    @Getter
    @Nullable
    private Boolean qaMode;

    @Getter
    @Nullable
    private Map<String, Integer> forcedVariations;

    @Getter
    @Nullable
    private Map<String, Object> forcedFeatureValues;
}
