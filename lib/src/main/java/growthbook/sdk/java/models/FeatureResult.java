package growthbook.sdk.java.models;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import javax.annotation.Nullable;

@Data
@Builder
@AllArgsConstructor
//public class FeatureResult {
public class FeatureResult<ValueType> {

    @Builder.Default
    Boolean on = false;

    @Nullable
    @SerializedName("value")
    String rawJsonValue;

    /**
     * One of "unknownFeature", "defaultValue", "force", or "experiment"
     */
    @Nullable
    FeatureResultSource source;

    /**
     * When source is "experiment", this will be an Experiment object
     */
    @Nullable
    Experiment<ValueType> experiment;

    // TODO: ExperimentResult experimentResult
    // When source is "experiment", this will be an ExperimentResult object
    @Nullable
    ExperimentResult<ValueType> experimentResult;

    @Nullable
    String ruleId;

    public Boolean isOn() {
        return on;
    }

    public Boolean isOff() {
        return !on;
    }
}
