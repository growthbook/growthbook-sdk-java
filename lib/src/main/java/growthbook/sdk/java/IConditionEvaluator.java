package growthbook.sdk.java;

import com.google.gson.JsonObject;

import javax.annotation.Nullable;

interface IConditionEvaluator {
    Boolean evaluateCondition(JsonObject attributesJson, JsonObject conditionJson, @Nullable JsonObject savedGroups);
}
