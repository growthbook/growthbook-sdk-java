package growthbook.sdk.java.remoteeval;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;
import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.model.Feature;
import growthbook.sdk.java.model.FeatureResponseKey;
import growthbook.sdk.java.multiusermode.util.TransformationUtil;
import growthbook.sdk.java.util.GrowthBookJsonUtils;

import java.util.Map;

/**
 * Parses the proxy response used by remote evaluation.
 */
public class RemoteEvalResponseParser {

    public RemoteEvalResponse parse(String responseJson) throws FeatureFetchException {
        if (responseJson == null || responseJson.trim().isEmpty()) {
            throw new FeatureFetchException(
                    FeatureFetchException.FeatureFetchErrorCode.NO_RESPONSE_ERROR,
                    "Remote evaluation response body is empty"
            );
        }

        JsonObject jsonObject = parseJsonObject(responseJson);
        JsonElement featuresElement = jsonObject.get(FeatureResponseKey.FEATURE_KEY.getKey());
        if (featuresElement == null || !featuresElement.isJsonObject()) {
            throw new FeatureFetchException(
                    FeatureFetchException.FeatureFetchErrorCode.CONFIGURATION_ERROR,
                    "Remote evaluation response does not include features"
            );
        }

        JsonElement savedGroupsElement = jsonObject.get(FeatureResponseKey.SAVED_GROUP_KEY.getKey());
        Map<String, Feature<?>> features = TransformationUtil.transformFeatures(featuresElement.toString());
        JsonObject savedGroups = savedGroupsElement == null
                ? new JsonObject()
                : TransformationUtil.transformSavedGroups(savedGroupsElement.toString());

        return new RemoteEvalResponse(features, savedGroups);
    }

    private JsonObject parseJsonObject(String responseJson) throws FeatureFetchException {
        try {
            JsonElement element = GrowthBookJsonUtils.getInstance().gson.fromJson(responseJson, JsonElement.class);
            if (element == null || !element.isJsonObject()) {
                throw new FeatureFetchException(
                        FeatureFetchException.FeatureFetchErrorCode.CONFIGURATION_ERROR,
                        "Remote evaluation response must be a JSON object"
                );
            }
            return element.getAsJsonObject();
        } catch (JsonSyntaxException e) {
            throw new FeatureFetchException(
                    FeatureFetchException.FeatureFetchErrorCode.CONFIGURATION_ERROR,
                    "Remote evaluation response is invalid JSON"
            );
        }
    }
}
