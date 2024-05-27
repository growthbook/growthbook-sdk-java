package growthbook.sdk.java;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.reflect.TypeToken;
import javax.annotation.Nullable;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <b>INTERNAL</b>: Implementation of condition evaluation
 */
class ConditionEvaluator implements IConditionEvaluator {

    private final GrowthBookJsonUtils jsonUtils = GrowthBookJsonUtils.getInstance();

    /**
     * Evaluate a condition for a set of user attributes based on the provided condition.
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

        if (entries.isEmpty()) {
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
     *
     * @param attributes User attributes
     * @param path       String path, e.g. path.to.something
     * @return the value at that path (or null if the path doesn't exist)
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
     * <p>
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
     * <p>
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
     * @param actual         Nullable JSON element
     * @param operatorString String value of the operator
     * @param expected       The conditions to use to verify that the attributes match, based on the operator
     * @return if it's a match
     */
    Boolean evalOperatorCondition(String operatorString, @Nullable JsonElement actual, JsonElement expected) {
        Operator operator = Operator.fromString(operatorString);
        if (operator == null) return false;

        DataType attributeDataType = GrowthBookJsonUtils.getElementType(actual);

        switch (operator) {
            case IN:
                if (actual == null) return false;

                if (DataType.ARRAY == attributeDataType) {
                    if (!expected.isJsonArray()) return false;

                    JsonArray value = actual.getAsJsonArray();
                    JsonArray expectedArr = expected.getAsJsonArray();

                    return isIn(value, expectedArr);
                }

                if (DataType.STRING == attributeDataType) {
                    String value = actual.getAsString();
                    Type listType = new TypeToken<ArrayList<String>>() {
                    }.getType();
                    ArrayList<String> conditionsList = jsonUtils.gson.fromJson(expected, listType);
                    return conditionsList.contains(value);
                }

                if (DataType.NUMBER == attributeDataType) {
                    Float value = actual.getAsFloat();
                    Type listType = new TypeToken<ArrayList<Float>>() {
                    }.getType();
                    ArrayList<Float> conditionsList = jsonUtils.gson.fromJson(expected, listType);
                    return conditionsList.contains(value);
                }

                if (DataType.BOOLEAN == attributeDataType) {
                    Boolean value = actual.getAsBoolean();
                    Type listType = new TypeToken<ArrayList<Boolean>>() {
                    }.getType();
                    ArrayList<Boolean> conditionsList = jsonUtils.gson.fromJson(expected, listType);
                    return conditionsList.contains(value);
                }
                break;


            case NIN:
                if (actual == null) return false;

                if (DataType.ARRAY == attributeDataType) {
                    if (!expected.isJsonArray()) return false;

                    JsonArray value = actual.getAsJsonArray();
                    JsonArray expectedArr = expected.getAsJsonArray();

                    return !isIn(value, expectedArr);
                }

                if (DataType.STRING == attributeDataType) {
                    String value = actual.getAsString();
                    Type listType = new TypeToken<ArrayList<String>>() {
                    }.getType();
                    ArrayList<String> conditionsList = jsonUtils.gson.fromJson(expected, listType);
                    return !conditionsList.contains(value);
                }

                if (DataType.NUMBER == attributeDataType) {
                    Float value = actual.getAsFloat();
                    Type listType = new TypeToken<ArrayList<Float>>() {
                    }.getType();
                    ArrayList<Float> conditionsList = jsonUtils.gson.fromJson(expected, listType);
                    return !conditionsList.contains(value);
                }

                if (DataType.BOOLEAN == attributeDataType) {
                    Boolean value = actual.getAsBoolean();
                    Type listType = new TypeToken<ArrayList<Boolean>>() {
                    }.getType();
                    ArrayList<Boolean> conditionsList = jsonUtils.gson.fromJson(expected, listType);
                    return !conditionsList.contains(value);
                }
                break;


            case GT:
                if (actual == null || DataType.NULL.equals(attributeDataType)) {
                    return (!expected.isJsonPrimitive() || expected.getAsJsonPrimitive().isNumber())
                            && 0.0 > expected.getAsDouble();
                }
                if (actual.getAsJsonPrimitive().isNumber()) {
                    return actual.getAsNumber().floatValue() > expected.getAsNumber().floatValue();
                }
                if (actual.getAsJsonPrimitive().isString()) {
                    return actual.getAsString().compareTo(expected.getAsString()) > 0;
                }
                break;

            case GTE:
                if (actual == null || DataType.NULL.equals(attributeDataType)) {
                    return (!expected.isJsonPrimitive() || expected.getAsJsonPrimitive().isNumber())
                            && 0.0 >= expected.getAsDouble();
                }
                if (actual.getAsJsonPrimitive().isNumber()) {
                    return actual.getAsNumber().floatValue() >= expected.getAsNumber().floatValue();
                }
                if (actual.getAsJsonPrimitive().isString()) {
                    return actual.getAsString().compareTo(expected.getAsString()) >= 0;
                }
                break;

            case LT:
                if (actual == null || DataType.NULL.equals(attributeDataType)) {
                    return (!expected.isJsonPrimitive() || expected.getAsJsonPrimitive().isNumber())
                            && 0.0 < expected.getAsDouble();
                }
                if (actual.getAsString().toLowerCase(Locale.ROOT).matches("\\d+")) {
                    return Double.parseDouble(actual.getAsString()) < expected.getAsDouble();
                }
                if (actual.getAsJsonPrimitive().isNumber()) {
                    return actual.getAsNumber().floatValue() < expected.getAsNumber().floatValue();
                }
                if (actual.getAsJsonPrimitive().isString()) {
                    return actual.getAsString().compareTo(expected.getAsString()) < 0;
                }
                break;

            case LTE:
                if (actual == null || DataType.NULL.equals(attributeDataType)) {
                    return (!expected.isJsonPrimitive() || expected.getAsJsonPrimitive().isNumber())
                            && 0.0 <= expected.getAsDouble();
                }
                if (actual.getAsJsonPrimitive().isNumber()) {
                    return actual.getAsNumber().floatValue() <= expected.getAsNumber().floatValue();
                }
                if (actual.getAsJsonPrimitive().isString()) {
                    return actual.getAsString().compareTo(expected.getAsString()) <= 0;
                }
                break;

            case REGEX:
                if (actual == null || DataType.NULL.equals(attributeDataType)) return false;
                Pattern pattern = Pattern.compile(expected.getAsString());
                Matcher matcher = pattern.matcher(actual.getAsString());

                boolean matches = false;

                while (matcher.find()) {
                    matches = true;
                }

                return matches;

            case NE:
                if (DataType.NULL.equals(attributeDataType)) return false;
                return !Objects.equals(actual, expected);

            case EQ:
                if (actual == null || DataType.NULL.equals(attributeDataType)) return false;
                return arePrimitivesEqual(actual.getAsJsonPrimitive(), expected.getAsJsonPrimitive(), attributeDataType);

            case SIZE:
                if (actual == null || !actual.isJsonArray()) return false;
                JsonArray attributeValueArrayForSize = (JsonArray) actual;
                JsonElement size = new JsonPrimitive(attributeValueArrayForSize.size());
                return evalConditionValue(expected, size);

            case ELEMENT_MATCH:
                if (actual == null) return false;
                return elemMatch(actual, expected);

            case ALL:
                if (actual == null || !actual.isJsonArray()) return false;
                JsonArray actualArrayForAll = (JsonArray) actual;
                JsonArray expectedArrayForAll = (JsonArray) expected;

                for (int i = 0; i < expectedArrayForAll.size(); i++) {
                    boolean passed = false;
                    for (int j = 0; j < actualArrayForAll.size(); j++) {
                        if (evalConditionValue(expectedArrayForAll.get(i), actualArrayForAll.get(j))) {
                            passed = true;
                            break;
                        }
                    }
                    if (!passed) return false;
                }
                return true;

            case NOT:
                return !evalConditionValue(expected, actual);

            case TYPE:
                return GrowthBookJsonUtils.getElementType(actual).toString().equals(expected.getAsString());

            case EXISTS:
                boolean exists = expected.getAsBoolean();

                if (exists) {
                    // Ensure it's present
                    return actual != null;
                } else {
                    // Ensure it's not present
                    return actual == null || actual.isJsonNull();
                }

            case VERSION_GT:
                if (actual == null || expected == null || DataType.NULL.equals(attributeDataType))
                    return false;

                return StringUtils.paddedVersionString(actual.getAsString())
                        .compareTo(StringUtils.paddedVersionString(expected.getAsString())) > 0;

            case VERSION_GTE:
                if (actual == null || expected == null || DataType.NULL.equals(attributeDataType))
                    return false;

                return StringUtils.paddedVersionString(actual.getAsString())
                        .compareTo(StringUtils.paddedVersionString(expected.getAsString())) >= 0;

            case VERSION_LT:
                if (actual == null || expected == null || DataType.NULL.equals(attributeDataType))
                    return false;

                return StringUtils.paddedVersionString(actual.getAsString())
                        .compareTo(StringUtils.paddedVersionString(expected.getAsString())) < 0;

            case VERSION_LTE:
                if (actual == null || expected == null || DataType.NULL.equals(attributeDataType))
                    return false;

                return StringUtils.paddedVersionString(actual.getAsString())
                        .compareTo(StringUtils.paddedVersionString(expected.getAsString())) <= 0;

            case VERSION_NE:
                if (actual == null || expected == null || DataType.NULL.equals(attributeDataType))
                    return false;

                return StringUtils.paddedVersionString(actual.getAsString())
                        .compareTo(StringUtils.paddedVersionString(expected.getAsString())) != 0;

            case VERSION_EQ:
                if (actual == null || expected == null || DataType.NULL.equals(attributeDataType))
                    return false;

                return StringUtils.paddedVersionString(actual.getAsString())
                        .compareTo(StringUtils.paddedVersionString(expected.getAsString())) == 0;

            default:
                return false;
        }
        return false;
    }

    /**
     * Compares two primitives for equality.
     *
     * @param a left side primitive
     * @param b right side primitive
     * @param dataType The data type of the primitives
     * @return if they are equal
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
     * Else, do a deep comparison between attributeValue and conditionValue.
     *
     * @param conditionValue Object or primitive
     * @param attributeValue Object or primitive
     * @return true if equal
     */
    Boolean evalConditionValue(JsonElement conditionValue, @Nullable JsonElement attributeValue) {
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

        if (
                conditionValue.isJsonNull() &&
                        (attributeValue == null || attributeValue.isJsonNull())
        ) {
            return true;
        }

        if (attributeValue == null) {
            return false;
        }

        return conditionValue.toString().equals(attributeValue.toString());
    }

    Boolean elemMatch(JsonElement actual, JsonElement expected) {
        if (!actual.isJsonArray()) {
            return false;
        }

        JsonArray actualArray = actual.getAsJsonArray();

        boolean isOperator = isOperatorObject(expected);

        for (JsonElement actualElement : actualArray) {
            if (isOperator) {
                if (evalConditionValue(expected, actualElement)) {
                    return true;
                }
            } else if (evaluateCondition(actualElement.toString(), expected.toString())) {
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
        if (conditions.isEmpty()) {
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

    private Boolean isIn(JsonElement actual, JsonArray expected) {
        Type listType = new TypeToken<ArrayList<Object>>() {}.getType();
        ArrayList<JsonElement> expectedAsList = jsonUtils.gson.fromJson(expected, listType);

        if (!actual.isJsonArray()) return expectedAsList.contains(actual);

        JsonArray actualArr = actual.getAsJsonArray();

        if (actualArr.isEmpty()) return false;

        DataType attributeDataType = GrowthBookJsonUtils.getElementType(actualArr.get(0));
        ArrayList<Object> actualAsList = jsonUtils.gson.fromJson(actualArr, listType);

        return actualAsList.stream()
                .anyMatch(o -> {
                    if (
                            attributeDataType == DataType.STRING ||
                                    attributeDataType == DataType.NUMBER ||
                                    attributeDataType == DataType.BOOLEAN
                    ) {
                        return expectedAsList.contains(o);
                    }

                    return false;
                });
    }
}
