package growthbook.sdk.java.testhelpers;

import com.google.gson.JsonObject;
import javax.annotation.Nullable;
import java.util.HashMap;

/**
 * A test Context that deserializes from the test case JSON data
 */
public class TestContext {
    public JsonObject attributes;

    @Nullable
    public JsonObject features;

    @Nullable
    public Boolean enabled;

    @Nullable
    public Boolean qaMode;

    @Nullable
    public String url;

    @Nullable
    public HashMap<String, Integer> forcedVariations;
}
