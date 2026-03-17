package growthbook.sdk.java.evaluators;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSyntaxException;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import growthbook.sdk.java.model.Operator;
import growthbook.sdk.java.util.StringUtils;
import growthbook.sdk.java.model.DataType;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * <b>INTERNAL</b>: Implementation of condition evaluation
 */
@Slf4j
public class ConditionEvaluator implements IConditionEvaluator {

    private final GrowthBookJsonUtils jsonUtils = GrowthBookJsonUtils.getInstance();

    /**
     * Evaluate a condition for a set of user attributes based on the provided condition.
     * The condition syntax closely resembles MongoDB's syntax.
     * This is defined in the Feature's targeting conditions' Advanced settings.
     *
     * @param attributes    A JsonObject of the user attributes to evaluate
     * @param conditionJson A JsonObject of the condition
     * @return Whether the condition should be true for the user
     */
    @Override
    public Boolean evaluateCondition(JsonObject attributes, JsonObject conditionJson, @Nullable JsonObject savedGroups) {
        try {
            // Loop through the conditionObj key/value pairs
            for (Map.Entry<String, JsonElement> entry : conditionJson.entrySet()) {
                String key = entry.getKey();
                JsonElement value = entry.getValue();

                switch (key) {
                    case "$or":
                        // If conditionObj has a key $or, return evalOr(attributes, condition["$or"])
                        JsonArray orTargetItems = value.getAsJsonArray();
                        if (orTargetItems != null) {
                            if (!evalOr(attributes, orTargetItems, savedGroups)) {
                                return false;
                            }
                        }
                        break;
                    case "$nor":
                        // If conditionObj has a key $nor, return !evalOr(attributes, condition["$nor"])
                        JsonArray norTargetItems = value.getAsJsonArray();
                        if (norTargetItems != null) {
                            if (evalOr(attributes, norTargetItems, savedGroups)) {
                                return false;
                            }
                        }
                        break;
                    case "$and":
                        // If conditionObj has a key $and, return !evalAnd(attributes, condition["$and"])
                        JsonArray andTargetItems = value.getAsJsonArray();
                        if (andTargetItems != null) {
                            if (!evalAnd(attributes, andTargetItems, savedGroups)) {
                                return false;
                            }
                        }
                        break;
                    case "$not":
                        // If conditionObj has a key $not, return !evalCondition(attributes, condition["$not"])
                        if (value != null) {
                            if (evaluateCondition(attributes, value.getAsJsonObject(), savedGroups)) {
                                return false;
                            }
                        }
                        break;
                    default:
                        JsonElement element = (JsonElement) getPath(attributes, key);
                        // If evalConditionValue(value, getPath(attributes, key)) is false,
                        // break out of loop and return false
                        if (!evalConditionValue(value, element, savedGroups)) {
                            return false;
                        }
                        break;
                }
            }
            // If none of the entries failed their checks, `evalCondition` returns true
            return true;
        } catch (JsonSyntaxException jsonSyntaxException) {
            log.error(jsonSyntaxException.getMessage(), jsonSyntaxException);
            return false;
        } catch (PatternSyntaxException patternSyntaxException) {
            log.error(patternSyntaxException.getMessage(), patternSyntaxException);
            return false;
        } catch (Exception exception) { // for the case if something was missed
            log.error(exception.getMessage(), exception);
            return false;
        }
    }

    /**
     * This accepts a parsed JSON object as input and returns true if every key in the object starts with $.
     *
     * @param object The object to evaluate
     * @return if all keys start with $
     */
    public Boolean isOperatorObject(JsonElement object) {
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
    public Object getPath(JsonElement attributes, String path) {
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
    Boolean evalOperatorCondition(String operatorString, @Nullable JsonElement actual, JsonElement expected, @Nullable JsonObject savedGroups) {
        Operator operator = Operator.fromString(operatorString);
        if (operator == null) return false;

        DataType attributeDataType = GrowthBookJsonUtils.getElementType(actual);

        switch (operator) {
            case IN:
                if (actual == null) return false;
                if (!expected.isJsonArray()) return false;
                return isIn(actual, expected.getAsJsonArray(), false);

            case INI:
                if (actual == null) return false;
                if (!expected.isJsonArray()) return false;
                return isIn(actual, expected.getAsJsonArray(), true);

            case NIN:
                if (actual == null) return false;
                if (!expected.isJsonArray()) return false;
                return !isIn(actual, expected.getAsJsonArray(), false);

            case NINI:
                if (actual == null) return false;
                if (!expected.isJsonArray()) return false;
                return !isIn(actual, expected.getAsJsonArray(), true);

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
                if (actual.getAsString().toLowerCase().matches("\\d+")) {
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
                return evalRegex(actual, expected, attributeDataType, false, false);

            case REGEX_I:
                return evalRegex(actual, expected, attributeDataType, true, false);

            case NOT_REGEX:
                return evalRegex(actual, expected, attributeDataType, false, true);

            case NOT_REGEX_I:
                return evalRegex(actual, expected, attributeDataType, true, true);

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
                return evalConditionValue(expected, size, savedGroups);

            case ELEMENT_MATCH:
                if (actual == null) return false;
                return elemMatch(actual, expected, savedGroups);

            case ALL:
                if (actual == null || !actual.isJsonArray()) return false;
                if (!expected.isJsonArray()) return false;
                return isInAll(actual.getAsJsonArray(), expected.getAsJsonArray(), savedGroups, false);

            case ALLI:
                if (actual == null || !actual.isJsonArray()) return false;
                if (!expected.isJsonArray()) return false;
                return isInAll(actual.getAsJsonArray(), expected.getAsJsonArray(), savedGroups, true);

            case NOT:
                return !evalConditionValue(expected, actual, savedGroups);

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

            case IN_GROUP:
                if (actual != null && expected != null) {
                    JsonElement jsonElement = savedGroups != null ? savedGroups.get(expected.getAsString()) : null;
                    if (jsonElement != null) {
                        return isIn(actual, jsonElement.getAsJsonArray(), false);
                    }
                    return isIn(actual, new JsonArray(), false);
                }
            case NOT_IN_GROUP:
                if (actual != null && expected != null) {
                    JsonElement jsonElement = savedGroups != null ? savedGroups.get(expected.getAsString()) : null;
                    if (jsonElement != null) {
                        return !isIn(actual, jsonElement.getAsJsonArray(), false);
                    }
                    return !isIn(actual, new JsonArray(), false);
                }
            default:
                return false;
        }
        return false;
    }

    /**
     * Compares two primitives for equality.
     *
     * @param a        left side primitive
     * @param b        right side primitive
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

        log.info("\nUnsupported data type {}", dataType);

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
    Boolean evalConditionValue(JsonElement conditionValue, @Nullable JsonElement attributeValue, @Nullable JsonObject savedGroups, boolean inSensitive) {

        if (conditionValue == null) {
            return attributeValue == null;
        }
        DataType conditionValueElementType = GrowthBookJsonUtils.getElementType(conditionValue);
        DataType attributeValueElementType = GrowthBookJsonUtils.getElementType(attributeValue);

        if (inSensitive && attributeValue != null
                && attributeValueElementType == DataType.STRING
                && conditionValueElementType == DataType.STRING) {
            return conditionValue.getAsString().equalsIgnoreCase(attributeValue.getAsString());
        }

        switch (conditionValueElementType) {
            case STRING:
                return isMatchingPrimitive(conditionValue, attributeValue, JsonPrimitive::getAsJsonPrimitive);

            case NUMBER:
                return isMatchingPrimitive(conditionValue, attributeValue, JsonPrimitive::getAsDouble);

            case BOOLEAN:
                return isMatchingPrimitive(conditionValue, attributeValue, JsonPrimitive::getAsBoolean);

            case ARRAY:
                return attributeValue != null && attributeValue.isJsonArray()
                        && jsonUtils.gson.toJson(conditionValue).equals(jsonUtils.gson.toJson(attributeValue));

            case OBJECT:
                JsonObject conditionValueObject = conditionValue.getAsJsonObject();
                if (isOperatorObject(conditionValueObject)) {
                    return conditionValueObject.entrySet().stream()
                            .allMatch(entry -> evalOperatorCondition(
                                            entry.getKey(),
                                            attributeValue,
                                            entry.getValue(),
                                            savedGroups
                                    )
                            );
                }
                return attributeValue != null && attributeValue.isJsonObject() &&
                        jsonUtils.gson.toJson(conditionValue).equals(jsonUtils.gson.toJson(attributeValue));

            case NULL:
                return attributeValue == null || attributeValue.isJsonNull();
            case UNDEFINED:
            case UNKNOWN:
            default:
                return conditionValue.toString().equals(attributeValue != null ? attributeValue.toString() : null);
        }
    }

    Boolean evalConditionValue(JsonElement conditionValue, @Nullable JsonElement attributeValue, @Nullable JsonObject savedGroups) {
        return evalConditionValue(conditionValue, attributeValue, savedGroups, false);
    }

    Boolean elemMatch(JsonElement actual, JsonElement expected, @Nullable JsonObject savedGroups) {
        if (!actual.isJsonArray()) {
            return false;
        }

        JsonArray actualArray = actual.getAsJsonArray();

        boolean isOperator = isOperatorObject(expected);

        for (JsonElement actualElement : actualArray) {
            if (isOperator) {
                if (evalConditionValue(expected, actualElement, savedGroups)) {
                    return true;
                }
            } else if (evaluateCondition(actualElement.getAsJsonObject(), expected.getAsJsonObject(), savedGroups)) {
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
    Boolean evalOr(JsonElement attributes, JsonArray conditions, @Nullable JsonObject savedGroups) {
        if (conditions.isEmpty()) {
            return true;
        }

        for (JsonElement condition : conditions) {
            JsonObject attributesObj = (null == attributes) ? new JsonObject() : attributes.getAsJsonObject();
            Boolean matches = evaluateCondition(attributesObj, condition.getAsJsonObject(), savedGroups);

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
    Boolean evalAnd(JsonElement attributes, JsonArray conditions, @Nullable JsonObject savedGroups) {
        for (JsonElement condition : conditions) {
            JsonObject attributesObj = (null == attributes) ? new JsonObject() : attributes.getAsJsonObject();
            Boolean matches = evaluateCondition(attributesObj, condition.getAsJsonObject(), savedGroups);

            if (!matches) {
                return false;
            }
        }

        return true;
    }

    private Boolean isIn(JsonElement actual, JsonArray expected, boolean inSensitive) {
        if (actual == null) return false;

        if (!actual.isJsonArray()) {
            // actual is a primitive — check if expected contains it
            if (inSensitive) {
                for (JsonElement exp : expected) {
                    if (caseFold(actual).equals(caseFold(exp))) {
                        return true;
                    }
                }
                return false;
            }
            return expected.contains(actual);
        }

        // actual is an array — check if any actual element matches any expected element
        JsonArray actualArr = actual.getAsJsonArray();

        if (actualArr.isEmpty()) return false;

        for (JsonElement actualItem : actualArr) {
            for (JsonElement expectedItem : expected) {
                if (inSensitive) {
                    if (caseFold(actualItem).equals(caseFold(expectedItem))) {
                        return true;
                    }
                } else {
                    if (Objects.equals(actualItem, expectedItem)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Checks that for every element in expected, there is at least one matching element in actual.
     * Uses evalConditionValue for comparison, which supports operator objects.
     *
     * @param actual      the attribute value (must be an array)
     * @param expected    the condition array — every item must match at least one in actual
     * @param savedGroups saved groups for group-based conditions
     * @param inSensitive if true, string comparisons are case-insensitive
     * @return true if all expected items are matched
     */
    private Boolean isInAll(JsonArray actual, JsonArray expected, @Nullable JsonObject savedGroups, boolean inSensitive) {
        for (int i = 0; i < expected.size(); i++) {
            boolean passed = false;
            for (int j = 0; j < actual.size(); j++) {
                if (evalConditionValue(expected.get(i), actual.get(j), savedGroups, inSensitive)) {
                    passed = true;
                    break;
                }
            }
            if (!passed) return false;
        }
        return true;
    }


    private <T> boolean isMatchingPrimitive(
            JsonElement conditionValue,
            JsonElement attributeValue,
            Function<JsonPrimitive, T> extractor) {
        return attributeValue != null
                && attributeValue.isJsonPrimitive()
                && extractor.apply(
                        conditionValue.getAsJsonPrimitive())
                .equals(extractor.apply(
                        attributeValue.getAsJsonPrimitive()));
    }

    private static Boolean evalRegex(@Nullable JsonElement actual,
                                              JsonElement expected,
                                              DataType attributeDataType,
                                              boolean caseInsensitive,
                                              boolean negate) {
        if (actual == null || DataType.NULL.equals(attributeDataType)) return false;

        int flags = caseInsensitive ? Pattern.CASE_INSENSITIVE : 0;

        try {
            Pattern pattern = Pattern.compile(expected.getAsString(), flags);
            Matcher matcher = pattern.matcher(actual.getAsString());
            boolean matches = matcher.find();
            return negate != matches;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Folds a JsonElement to lowercase if it's a string and inSensitive is true.
     * Used where you need a folded value rather than a comparison.
     */
    private JsonElement caseFold(@Nullable JsonElement value) {
        if (value != null
                && value.isJsonPrimitive()
                && value.getAsJsonPrimitive().isString()) {
            return new JsonPrimitive(value.getAsString().toLowerCase());
        }
        return value;
    }
}
