package growthbook.sdk.java.TestHelpers;

import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import growthbook.sdk.java.services.GrowthBookJsonUtils;

import java.lang.reflect.Type;
import java.util.HashMap;

/**
 * A test Context that deserializes from the test case JSON data
 */
public class TestContext {
    JsonElement attributes;

    public HashMap<String, Object> getAttributes() {
        Type typeToken = new TypeToken<HashMap<String, Object>>() {}.getType();
        return GrowthBookJsonUtils.getInstance().gson.fromJson(attributes, typeToken);
    }
}
