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

            // TODO: evaluateCondition

            System.out.printf("JSON attr %s ... JSON condition %s", attributesJson, conditionJson);

            return false;
        } catch (RuntimeException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * This accepts a parsed JSON object as input and returns true if every key in the object starts with $.
     *
     * @param object The object to evaluate
     * @return if all keys start with $
     */
    Boolean isOperatorObject(JsonElement object) {
        if (!object.isJsonObject()) {
            return false;
        }

        Set<Map.Entry<String, JsonElement>> entries = ((JsonObject) object).entrySet();

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

    /**
     * Evaluates the condition using the operator. For example, if you provide the following condition:
     *
     * <pre>
     * {
     *   "$not": {
     *     "country": "usa"
     *   }
     * }
     * </pre>
     *
     * And the following attributes:
     *
     * <pre>
     * {
     *   "country": "canada"
     * }
     * </pre>
     * <p>
     * It will evaluate to true because the <code>country</code> is not <code>usa</code>.
     * </p>
     *
     * <p>
     * There are basic comparison operators in the form attributeValue {op} conditionValue,
     * i.e. <code>$eq, $ne, $lt, $lte, $gt, $gte, $regex</code>
     * </p>
     *
     *<p>
     * There are 2 operators where conditionValue is an array,
     * i.e. <code>$in, $nin</code>
     * </p>
     *
     * <p>
     * There are 2 operators where attributeValue is an array,
     * i.e. <code>$elemMatch, $size</code>
     * </p>
     *
     * <p>
     * There is 1 operator where both attributeValue and conditionValue are arrays,
     * i.e. <code>$all</code>
     * </p>
     *
     * <p>
     * There are 3 other operators,
     * i.e. <code>$exists, $type, $not</code>
     * </p>
     *
     * @param attributeValue Nullable JSON element
     * @param operatorString String value of the operator
     * @param conditionValue The conditions to use to verify that the attributes match, based on the operator
     * @return if it's a match
     */
    Boolean evalOperatorCondition(String operatorString, @Nullable JsonElement attributeValue, JsonElement conditionValue) {
        Operator operator = Operator.fromString(operatorString);
        if (operator == null) return false;

        if (Operator.TYPE == operator) {
            return getType(attributeValue).toString().equals(conditionValue.toString());
        }

        if (Operator.NOT == operator) {
            return !evalConditionValue(conditionValue, attributeValue);
        }

        if (Operator.EXISTS == operator) {
            boolean exists = conditionValue.getAsBoolean();

            if (exists) {
                // Ensure it's present
                return attributeValue != null;
            } else {
                // Ensure it's not present
                return attributeValue == null || attributeValue.isJsonNull();
            }
        }

        DataType attributeType = getType(attributeValue);

        System.out.printf("Operator: %s - Attr type: %s - Attr value = %s", operator, attributeType, attributeValue);

        // TODO: private evalOperatorCondition(operator, attributeValue, conditionValue)
        // When conditionValue is an array
        if (conditionValue.isJsonArray()) {
            switch (operator) {
                case IN:
                    break;

                case NIN:
                    break;
                case ALL:
                    break;
                case GT:
                case GTE:
                case LT:
                case LTE:
                case REGEX:
                case NE:
                case EQ:
                case SIZE:
                case ELEMENT_MATCH:
                    // Do nothing
            }
        }

        // When attributeValue is an array
        if (attributeValue != null && attributeValue.isJsonArray()) {
            //
        }

        return false;
    }

    /**
     * If conditionValue is an object and isOperatorObject(conditionValue) is true
     * Loop over each key/value pair
     * If evalOperatorCondition(key, attributeValue, value) is false, return false
     * Return true
     * Else, do a deep comparison between attributeValue and conditionValue. Return true if equal, false if not.
     *
     * @param conditionValue
     * @param attributeValue
     * @return
     */
    Boolean evalConditionValue(JsonElement conditionValue, @Nullable JsonElement attributeValue) {
        // conditionValue is an object
        if (conditionValue.isJsonObject()) {
            JsonObject conditionValueObject = (JsonObject) conditionValue;

            if (isOperatorObject(conditionValueObject)) {
                Set<Map.Entry<String, JsonElement>> entries = conditionValueObject.entrySet();

                for (Map.Entry<String, JsonElement> entry : entries) {
                    if (!evalOperatorCondition(entry.getKey(), attributeValue, entry.getValue())) {
                        return false;
                    }
                }

                return true;
            }
        }

        return conditionValue.toString().equals(conditionValue.toString());
    }

    Boolean elemMatch(JsonElement attributeValue, JsonElement condition) {
        if (!attributeValue.isJsonArray()) {
            return false;
        }

        JsonArray attributeValueArr = attributeValue.getAsJsonArray();

        for (JsonElement element : attributeValueArr) {
            if (isOperatorObject(element)) {
                if (evalConditionValue(condition, element)) {
                    return true;
                }
            }
            else if (evaluateCondition(element.toString(), condition.toString())) {
                return true;
            }
        }

        return false;
    }


    /**
     * @param attributes User attributes
     * @param conditions an array of condition objects
     * @return if matches
     */
    Boolean evalOr(JsonElement attributes, JsonArray conditions) {
        if (conditions.size() == 0) {
            return true;
        }

        for (JsonElement condition : conditions) {
            String attributesString = attributes == null ? "{}" : attributes.toString();
            Boolean matches = evaluateCondition(attributesString, condition.toString());

            if (matches) {
                return true;
            }
        }

        return false;
    }

    /**
     * @param attributes User attributes
     * @param conditions an array of condition objects
     * @return if matches
     */
    Boolean evalAnd(JsonElement attributes, JsonArray conditions) {
        for (JsonElement condition : conditions) {
            String attributesString = attributes == null ? "{}" : attributes.toString();
            Boolean matches = evaluateCondition(attributesString, condition.toString());

            if (!matches) {
                return false;
            }
        }

        return true;
    }
}
