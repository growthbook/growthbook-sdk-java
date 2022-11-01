package growthbook.sdk.java.testhelpers;

import javax.annotation.Nullable;
import java.util.HashMap;

/**
 * A test Context that deserializes from the test case JSON data
 */
public class TestContext {
    public String attributes;

    @Nullable
    public String features;

    @Nullable
    public Boolean enabled;

    @Nullable
    public Boolean qaMode;

    @Nullable
    public String url;

    @Nullable
    public HashMap<String, Integer> forcedVariations;
}
