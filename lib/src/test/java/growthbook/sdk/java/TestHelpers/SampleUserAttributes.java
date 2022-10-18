package growthbook.sdk.java.TestHelpers;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.annotations.SerializedName;
import growthbook.sdk.java.models.UserAttributes;

/**
 * This is an example implementation of sample user attributes.
 */
public class SampleUserAttributes implements UserAttributes {
    @SerializedName("device") String device;

    @SerializedName("country") String country;

    public SampleUserAttributes(String device, String country) {
        this.device = device;
        this.country = country;
    }

    @Override
    public String toJson() {
        Gson gson = new GsonBuilder().create();
        return gson.toJson(this);
    }
}
