package growthbook.sdk.java.multiusermode.configurations;

import com.google.gson.JsonObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;

/**
 * Options you use to configure your growthbook instance
 */
@Data
@Slf4j
public class OptionsDup {

    /**
     * Whether globally all experiments are enabled (default: true)
     * Switch to globally disable all experiments.
     */
    @Nullable
    private Boolean enabled;

    /**
     * The URL of the current page - A URL string that is used for experiment evaluation, as well as forcing feature values.
     */
    @Nullable
    private String url;


    @Nullable
    private JsonObject savedGroups;

    // Why do you need attributes here?
    //attributes?: Attributes;

    // debug?: boolean; - No implementation at all? What happens when debug is on? Why Java SDK doesn't implement it?
}
