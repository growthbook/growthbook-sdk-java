package growthbook.sdk.java;

import com.google.gson.JsonObject;

interface IConditionEvaluator {
    Boolean evaluateCondition(JsonObject attributesJson, JsonObject conditionJson);
}
