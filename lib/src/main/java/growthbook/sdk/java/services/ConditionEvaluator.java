package growthbook.sdk.java.services;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.reflect.TypeToken;
import com.sun.org.apache.xpath.internal.operations.Bool;
import growthbook.sdk.java.models.BucketRange;
import growthbook.sdk.java.models.Operator;
import growthbook.sdk.java.models.UserAttributes;

import javax.annotation.Nullable;
import java.lang.reflect.Type;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
            System.out.println("\n\n---------------------------------------------------------");
            System.out.printf("\nEvaluating JSON %s", attributesJsonString);
            System.out.printf("\n\nEvaluating condition %s", conditionJsonString);
            System.out.println("\n\n---------------------------------------------------------");

            JsonElement attributesJson = jsonUtils.gson.fromJson(attributesJsonString, JsonElement.class);
            JsonObject conditionJson = jsonUtils.gson.fromJson(conditionJsonString, JsonObject.class);

            if (conditionJson.has("$or")) {
                JsonArray targetItems = conditionJson.get("$or").getAsJsonArray();
                return evalOr(attributesJson, targetItems);
            }

            if (conditionJson.has("$nor")) {
                JsonArray targetItems = conditionJson.get("$nor").getAsJsonArray();
                return !evalOr(attributesJson, targetItems);
            }

            if (conditionJson.has("$and")) {
                JsonArray targetItems = conditionJson.get("$and").getAsJsonArray();
                return evalAnd(attributesJson, targetItems);
            }

            if (conditionJson.has("$not")) {
                JsonElement targetItem = conditionJson.get("$not");
                return !evaluateCondition(attributesJsonString, targetItem.toString());
            }

            Set<Map.Entry<String, JsonElement>> conditionEntries = conditionJson.entrySet();
            for (Map.Entry<String, JsonElement> entry : conditionEntries) {
                JsonElement element = (JsonElement) getPath(attributesJson, entry.getKey());
                if (entry.getValue() != null) {
                    if (!evalConditionValue(entry.getValue(), element)) {
                        return false;
                    }
                }
            }

            return true;
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

        DataType attributeDataType = getType(attributeValue);

        System.out.printf("Operator: %s - Attr type: %s - Attr value = %s", operator, attributeDataType, attributeValue);

        // When conditionValue is an array
        if (attributeValue != null && conditionValue.isJsonArray()) {
            if (Operator.IN == operator) {
                if (DataType.STRING == attributeDataType) {
                    String value = attributeValue.getAsString();
                    Type listType = new TypeToken<ArrayList<String>>() {}.getType();
                    ArrayList<String> conditionsList = jsonUtils.gson.fromJson(conditionValue, listType);
                    return conditionsList.contains(value);
                }

                if (DataType.NUMBER == attributeDataType) {
                    Float value = attributeValue.getAsFloat();
                    Type listType = new TypeToken<ArrayList<Float>>() {}.getType();
                    ArrayList<Float> conditionsList = jsonUtils.gson.fromJson(conditionValue, listType);
                    return conditionsList.contains(value);
                }

                if (DataType.BOOLEAN == attributeDataType) {
                    Boolean value = attributeValue.getAsBoolean();
                    Type listType = new TypeToken<ArrayList<Boolean>>() {}.getType();
                    ArrayList<Boolean> conditionsList = jsonUtils.gson.fromJson(conditionValue, listType);
                    return conditionsList.contains(value);
                }
            }

            if (Operator.NIN == operator) {
                if (DataType.STRING == attributeDataType) {
                    String value = attributeValue.getAsString();
                    Type listType = new TypeToken<ArrayList<String>>() {}.getType();
                    ArrayList<String> conditionsList = jsonUtils.gson.fromJson(conditionValue, listType);
                    return !conditionsList.contains(value);
                }

                if (DataType.NUMBER == attributeDataType) {
                    Float value = attributeValue.getAsFloat();
                    Type listType = new TypeToken<ArrayList<Float>>() {}.getType();
                    ArrayList<Float> conditionsList = jsonUtils.gson.fromJson(conditionValue, listType);
                    return !conditionsList.contains(value);
                }

                if (DataType.BOOLEAN == attributeDataType) {
                    Boolean value = attributeValue.getAsBoolean();
                    Type listType = new TypeToken<ArrayList<Boolean>>() {}.getType();
                    ArrayList<Boolean> conditionsList = jsonUtils.gson.fromJson(conditionValue, listType);
                    return !conditionsList.contains(value);
                }
            }

            if (Operator.ALL == operator) {
                // TODO: ALL
            }
        }

        // When attributeValue is an array
        if (attributeValue != null && attributeValue.isJsonArray()) {
            JsonArray attributeValueArray = (JsonArray) attributeValue;

            if (operator == Operator.ELEMENT_MATCH) {
                return elemMatch(attributeValue, conditionValue);
            }

            if (operator == Operator.SIZE) {
                JsonElement size = new JsonPrimitive(attributeValueArray.size());
                return evalConditionValue(conditionValue, size);
            }
        }

        // TODO: Verify if this is a good spot for this.
        if (attributeValue == null) {
            return attributeDataType == DataType.UNDEFINED;
        }

        if (attributeValue.isJsonNull() && attributeDataType == DataType.NULL) {
            return true;
        }

        // TODO: private evalOperatorCondition(operator, attributeValue, conditionValue)
        if (attributeValue.isJsonPrimitive()) {
            if (Operator.EQ == operator) {
                return arePrimitivesEqual(attributeValue.getAsJsonPrimitive(), conditionValue.getAsJsonPrimitive(), attributeDataType);
            }

            if (Operator.NE == operator) {
                return !arePrimitivesEqual(attributeValue.getAsJsonPrimitive(), conditionValue.getAsJsonPrimitive(), attributeDataType);
            }

            if (Operator.LT == operator) {
                if (attributeValue.getAsJsonPrimitive().isNumber()) {
                    return attributeValue.getAsNumber().floatValue() < conditionValue.getAsNumber().floatValue();
                }
                if (attributeValue.getAsJsonPrimitive().isString()) {
                    return attributeValue.getAsString().compareTo(conditionValue.getAsString()) < 0;
                }
            }

            if (Operator.LTE == operator) {
                if (attributeValue.getAsJsonPrimitive().isNumber()) {
                    return attributeValue.getAsNumber().floatValue() <= conditionValue.getAsNumber().floatValue();
                }
                if (attributeValue.getAsJsonPrimitive().isString()) {
                    return attributeValue.getAsString().compareTo(conditionValue.getAsString()) <= 0;
                }
            }

            if (Operator.GT == operator) {
                if (attributeValue.getAsJsonPrimitive().isNumber()) {
                    return attributeValue.getAsNumber().floatValue() > conditionValue.getAsNumber().floatValue();
                }
                if (attributeValue.getAsJsonPrimitive().isString()) {
                    return attributeValue.getAsString().compareTo(conditionValue.getAsString()) > 0;
                }
            }

            if (Operator.GTE == operator) {
                if (attributeValue.getAsJsonPrimitive().isNumber()) {
                    return attributeValue.getAsNumber().floatValue() >= conditionValue.getAsNumber().floatValue();
                }
                if (attributeValue.getAsJsonPrimitive().isString()) {
                    return attributeValue.getAsString().compareTo(conditionValue.getAsString()) >= 0;
                }
            }

            if (Operator.REGEX == operator) {
                Pattern pattern = Pattern.compile(conditionValue.getAsString());
                Matcher matcher = pattern.matcher(attributeValue.getAsString());

                boolean matches = false;

                while (matcher.find()) {
                    matches = true;
                    System.out.print("Start index: " + matcher.start());
                    System.out.print(" End index: " + matcher.end() + " ");
                    System.out.println(matcher.group());
                }

                return matches;
            }
        }

        return false;
    }

    /**
     * Compares two primitives for equality.
     * @param a
     * @param b
     * @param dataType The data type of the primitives
     * @return
     */
    Boolean arePrimitivesEqual(JsonPrimitive a, JsonPrimitive b, DataType dataType) {
        switch (dataType) {
            case STRING:
                return a.getAsString().equals(b.getAsString());

            case NUMBER:
                return Objects.equals(a.getAsNumber(), b.getAsNumber());

            case BOOLEAN:
                return a.getAsBoolean() == b.getAsBoolean();

            case ARRAY:
            case OBJECT:
            case NULL:
            case UNDEFINED:
            case UNKNOWN:
                //
        }

        System.out.printf("\nUnsupported data type %s", dataType);

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

        if (attributeValue == null) {
            return false;
        }

        // TODO: conditionValue is a string, number or boolean
        // TODO: conditionValue is an array -> do a deep equal check

        return conditionValue.toString().equals(attributeValue.toString());
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
