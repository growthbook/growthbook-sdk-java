package growthbook.sdk.java.models;

import com.google.gson.annotations.SerializedName;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import javax.annotation.Nullable;

@Data
@Builder
@AllArgsConstructor
public class FeatureResult<ValueType> {

    @Nullable
    @SerializedName("value")
    Object value;

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
        if (value == null) return false;

        if (value instanceof Boolean) {
            return (Boolean) value;
        }

        if (value instanceof String) {
            return !((String) value).isEmpty();
        }

        if (value instanceof Integer) {
            return (Integer) value != 0;
        }

        if (value instanceof Float) {
            return (Float) value != 0.0f;
        }

        return false;
    }

    public Boolean isOff() {
        return !isOn();
    }
}
