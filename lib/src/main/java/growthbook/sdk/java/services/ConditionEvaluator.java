package growthbook.sdk.java.services;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import growthbook.sdk.java.models.Operator;
import growthbook.sdk.java.models.UserAttributes;

import javax.annotation.Nullable;
import java.util.*;

public class ConditionEvaluator implements IConditionEvaluator {

    // TODO: ConditionType
    private enum ConditionType {
        OR_CONDITION,
        NOR_CONDITION,
        AND_CONDITION,
        NOT_CONDITION,
        OPERATION_CONDITION
    }

    enum DataType {
        STRING("string"),
        NUMBER("number"),
        BOOLEAN("boolean"),
        ARRAY("array"),
        OBJECT("object"),
        NULL("null"),
        UNDEFINED("undefined"),
        UNKNOWN("unknown"),
        ;
        private final String rawValue;

        DataType(String rawValue) {
            this.rawValue = rawValue;
        }

        @Override
        public String toString() {
            return this.rawValue;
        }
    }

    private final GrowthBookJsonUtils jsonUtils = GrowthBookJsonUtils.getInstance();

    // TODO: evaluateCondition
    /**
     * Evaluate a condition for a set of user attributes based on the provided condition.
     * <p>
     * If you are using the {@link UserAttributes} interface, you can call <code>userAttributes.toJson()</code>
     * before passing it to this method.
     * <p>
     * The condition syntax closely resembles MongoDB's syntax.
     * This is defined in the Feature's targeting conditions' Advanced settings.
     *
     * @param attributesJsonString A JSON string of the user attributes to evaluate
     * @param conditionJsonString  A JSON string of the condition
     * @return Whether the condition should be true for the user
     */
    @Override
    public Boolean evaluateCondition(String attributesJsonString, String conditionJsonString) {
        try {
            JsonObject attributesJson = jsonUtils.gson.fromJson(attributesJsonString, JsonObject.class);
            JsonObject conditionJson = jsonUtils.gson.fromJson(conditionJsonString, JsonObject.class);

            Set<Map.Entry<String, JsonElement>> conditionEntries = conditionJson.entrySet();
            for (Map.Entry<String, JsonElement> entry : conditionEntries) {
                System.out.println(entry.getKey());
            }

            Set<Map.Entry<String, JsonElement>> attributesEntries = attributesJson.entrySet();
            for (Map.Entry<String, JsonElement> entry : attributesEntries) {
                System.out.println(entry.getKey());
            }

            System.out.printf("JSON attr %s ... JSON condition %s", attributesJson, conditionJson);

            return false;
        } catch (RuntimeException e) {
            e.printStackTrace();
            return false;
        }
    }

    private Boolean evalOperatorCondition(String operator, JsonElement attributeValue, JsonElement conditionValue) {
        // TODO: https://github.com/growthbook/growthbook-kotlin/blob/721952391eb4fe62df8f486d63edcd7c2d2b046e/GrowthBook/src/commonMain/kotlin/com/sdk/growthbook/evaluators/GBConditionEvaluator.kt#L334
        return false;
    }

    /**
     * This accepts a parsed JSON object as input and returns true if every key in the object starts with $.
     *
     * @param object The object to evaluate
     * @return if all keys start with $
     */
    Boolean isOperator(JsonObject object) {
        Set<Map.Entry<String, JsonElement>> entries = object.entrySet();

        if (entries.size() == 0) {
            return true;
        }

        long without$Prefix = entries
                .stream()
                .filter(o -> !o.getKey().startsWith("$"))
                .count();

        return without$Prefix == 0;
    }

    /**
     * Given attributes and a dot-separated path string,
     * @return the value at that path (or null if the path doesn't exist)
     *
     * @param attributes User attributes
     * @param path       String path, e.g. path.to.something
     */
    @Nullable
    Object getPath(JsonElement attributes, String path) {
        if (Objects.equals(path, "")) return null;

        ArrayList<String> paths = new ArrayList<>();

        if (path.contains(".")) {
            String[] pathSegments = path.split("\\.");

            Collections.addAll(paths, pathSegments);
        } else {
            paths.add(path);
        }

        JsonElement element = attributes;

        for (String segment : paths) {
            if (element == null || element instanceof JsonArray) {
                return null;
            }
            if (element instanceof JsonObject) {
                element = ((JsonObject) element).get(segment);
            } else {
                return null;
            }
        }

        return element;
    }

    DataType getType(@Nullable JsonElement element) {
        try {
            if (element == null) return DataType.UNDEFINED;
            if (element.isJsonNull()) return DataType.NULL;
            if (element.isJsonArray()) return DataType.ARRAY;
            if (element.isJsonObject()) return DataType.OBJECT;
            if (element.isJsonPrimitive()) {
                JsonPrimitive primitive = element.getAsJsonPrimitive();
                if (primitive.isBoolean()) return DataType.BOOLEAN;
                if (primitive.isNumber()) return DataType.NUMBER;
                if (primitive.isString()) return DataType.STRING;
            }

            return DataType.UNKNOWN;
        } catch (RuntimeException e) {
            e.printStackTrace();
            return DataType.UNKNOWN;
        }
    }

    // TODO: private evalConditionValue(conditionValue, attributeValue): boolean

    // TODO: private elemMatch(condition, attributeValue): boolean

    // TODO: private evalOperatorCondition(operator, attributeValue, conditionValue)

    // TODO: private evalAnd(attributes: Attributes, conditions: Condition[]): boolean

    // TODO: private evalOr(attributes: Attributes, conditions: Condition[]): boolean
}
