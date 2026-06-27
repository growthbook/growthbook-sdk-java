package growthbook.sdk.java.evaluators;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import growthbook.sdk.java.model.Operator;
import growthbook.sdk.java.util.StringUtils;
import growthbook.sdk.java.model.DataType;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
    public Boolean evaluateCondition(
        JsonObject attributes,
        JsonObject conditionJson,
        @Nullable JsonObject savedGroups) {
        try {
            return conditionJson.entrySet().stream()
                    .allMatch(entry ->
                        matchesConditionEntry(entry.getKey(), entry.getValue(), attributes, savedGroups));
        } catch (Exception exception) {
            log.error(exception.getMessage(), exception);
            return false;
        }
    }

    /**
     * Evaluates a single top-level condition entry: a logical operator ($or/$nor/$and/$not) is
     * handled by its strategy (recursing back through this evaluator); any other key is treated as
     * an attribute path and compared as a leaf value.
     */
    private boolean matchesConditionEntry(
        String key, JsonElement value,
        JsonObject attributes,
        @Nullable JsonObject savedGroups) {
        Condition operator = Condition.fromValue(key);
        return operator != null
                ? operator.apply(attributes, value, savedGroups, this)
                : evalConditionValue(value, (JsonElement) getPath(attributes, key), savedGroups);
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
        return object.getAsJsonObject().entrySet().stream()
                .allMatch(entry -> entry.getKey().startsWith("$"));
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
        if (Objects.equals(path, "")) {
            return null;
        }

        JsonElement element = attributes;
        for (String segment : path.split("\\.")) {
            if (!(element instanceof JsonObject)) {
                return null;
            }
            element = ((JsonObject) element).get(segment);
        }
        return element;
    }

    /**
     * Evaluates a single operator condition (the {@code attributeValue {op} conditionValue} form).
     *
     * <p>Operators fall into a few families:</p>
     * <ul>
     *   <li>comparison: <code>$eq, $ne, $lt, $lte, $gt, $gte, $regex</code></li>
     *   <li>array conditionValue: <code>$in, $nin</code></li>
     *   <li>array attributeValue: <code>$elemMatch, $size</code></li>
     *   <li>both arrays: <code>$all</code></li>
     *   <li>version: <code>$vgt, $vgte, $vlt, $vlte, $vne, $veq</code></li>
     *   <li>saved groups: <code>$inGroup, $notInGroup</code></li>
     *   <li>other: <code>$exists, $type, $not</code></li>
     * </ul>
     *
     * @param operatorString String value of the operator
     * @param actual         Nullable attribute value
     * @param expected       The condition value to compare against
     * @return if it's a match
     */
    Boolean evalOperatorCondition(String operatorString, @Nullable JsonElement actual, JsonElement expected, @Nullable JsonObject savedGroups) {
        Operator operator = Operator.fromString(operatorString);
        if (operator == null) return false;

        DataType attributeDataType = GrowthBookJsonUtils.getElementType(actual);

        switch (operator) {
            case IN:
            case INI:
            case NIN:
            case NINI:
                return evalMembership(operator, actual, expected);

            case GT:
            case GTE:
            case LT:
            case LTE:
                return evalComparison(operator, actual, expected, attributeDataType);

            case REGEX:
                return evalRegex(actual, expected, attributeDataType, false, false);
            case REGEX_I:
                return evalRegex(actual, expected, attributeDataType, true, false);
            case NOT_REGEX:
                return evalRegex(actual, expected, attributeDataType, false, true);
            case NOT_REGEX_I:
                return evalRegex(actual, expected, attributeDataType, true, true);

            case NE:
                if (DataType.NULL.equals(attributeDataType)) return !expected.isJsonNull();
                return !Objects.equals(actual, expected);

            case EQ:
                if (actual == null || DataType.NULL.equals(attributeDataType)) return false;
                return arePrimitivesEqual(actual.getAsJsonPrimitive(), expected.getAsJsonPrimitive(), attributeDataType);

            case SIZE:
                if (actual == null || !actual.isJsonArray()) return false;
                return evalConditionValue(expected, new JsonPrimitive(actual.getAsJsonArray().size()), savedGroups);

            case ELEMENT_MATCH:
                if (actual == null) return false;
                return elemMatch(actual, expected, savedGroups);

            case ALL:
                if (actual == null || !actual.isJsonArray() || !expected.isJsonArray()) return false;
                return isInAll(actual.getAsJsonArray(), expected.getAsJsonArray(), savedGroups, false);
            case ALLI:
                if (actual == null || !actual.isJsonArray() || !expected.isJsonArray()) return false;
                return isInAll(actual.getAsJsonArray(), expected.getAsJsonArray(), savedGroups, true);

            case NOT:
                return !evalConditionValue(expected, actual, savedGroups);

            case TYPE:
                return GrowthBookJsonUtils.getElementType(actual).toString().equals(expected.getAsString());

            case EXISTS:
                return expected.getAsBoolean() ? actual != null : (actual == null || actual.isJsonNull());

            case VERSION_GT:
            case VERSION_GTE:
            case VERSION_LT:
            case VERSION_LTE:
            case VERSION_NE:
            case VERSION_EQ:
                return evalVersion(operator, actual, expected, attributeDataType);

            case IN_GROUP:
            case NOT_IN_GROUP:
                return evalSavedGroup(operator, actual, expected, savedGroups);

            default:
                return false;
        }
    }

    /**
     * {@code $in}/{@code $nin} (and their case-insensitive variants): tests array membership.
     */
    private Boolean evalMembership(Operator operator, @Nullable JsonElement actual, JsonElement expected) {
        if (actual == null || !expected.isJsonArray()) {
            return false;
        }
        boolean caseInsensitive = operator == Operator.INI || operator == Operator.NINI;
        boolean negate = operator == Operator.NIN || operator == Operator.NINI;
        return negate != isIn(actual, expected.getAsJsonArray(), caseInsensitive);
    }

    /**
     * {@code $gt}/{@code $gte}/{@code $lt}/{@code $lte}: numeric or lexical comparison.
     */
    private Boolean evalComparison(Operator operator, @Nullable JsonElement actual, JsonElement expected, DataType attributeDataType) {
        if (actual == null || DataType.NULL.equals(attributeDataType)) {
            if (expected.isJsonPrimitive() && !expected.getAsJsonPrimitive().isNumber()) {
                return false;
            }
            return matchesSign(operator, Double.compare(0.0, expected.getAsDouble()));
        }
        if (operator == Operator.LT && actual.getAsString().toLowerCase().matches("\\d+")) {
            return Double.parseDouble(actual.getAsString()) < expected.getAsDouble();
        }
        if (actual.getAsJsonPrimitive().isNumber()) {
            return matchesSign(operator, Float.compare(actual.getAsNumber().floatValue(), expected.getAsNumber().floatValue()));
        }
        if (actual.getAsJsonPrimitive().isString()) {
            return matchesSign(operator, actual.getAsString().compareTo(expected.getAsString()));
        }
        return false;
    }

    /**
     * {@code $vgt}/{@code $vgte}/{@code $vlt}/{@code $vlte}/{@code $vne}/{@code $veq}:
     * compares padded semantic-version strings.
     */
    private Boolean evalVersion(Operator operator, @Nullable JsonElement actual, JsonElement expected, DataType attributeDataType) {
        if (actual == null || expected == null || DataType.NULL.equals(attributeDataType)) {
            return false;
        }
        int cmp = StringUtils.paddedVersionString(actual.getAsString())
                .compareTo(StringUtils.paddedVersionString(expected.getAsString()));
        switch (operator) {
            case VERSION_GT:  return cmp > 0;
            case VERSION_GTE: return cmp >= 0;
            case VERSION_LT:  return cmp < 0;
            case VERSION_LTE: return cmp <= 0;
            case VERSION_NE:  return cmp != 0;
            case VERSION_EQ:  return cmp == 0;
            default:          return false;
        }
    }

    /**
     * {@code $inGroup}/{@code $notInGroup}: membership in a named saved group (empty if unknown).
     */
    private Boolean evalSavedGroup(Operator operator, @Nullable JsonElement actual, @Nullable JsonElement expected, @Nullable JsonObject savedGroups) {
        if (actual == null || expected == null) {
            return false;
        }
        JsonElement group = savedGroups != null ? savedGroups.get(expected.getAsString()) : null;
        JsonArray groupValues = group != null ? group.getAsJsonArray() : new JsonArray();
        boolean inGroup = isIn(actual, groupValues, false);
        return operator == Operator.NOT_IN_GROUP ? !inGroup : inGroup;
    }

    /**
     * Maps a {@link Integer#compare}-style sign to the requested comparison operator.
     */
    private static boolean matchesSign(Operator operator, int comparison) {
        switch (operator) {
            case GT:  return comparison > 0;
            case GTE: return comparison >= 0;
            case LT:  return comparison < 0;
            case LTE: return comparison <= 0;
            default:  return false;
        }
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
        if (actual == null || DataType.NULL.equals(attributeDataType)) return negate;

        int flags = caseInsensitive ? Pattern.CASE_INSENSITIVE : 0;

        try {
            Pattern pattern = Pattern.compile(expected.getAsString(), flags);
            Matcher matcher = pattern.matcher(actual.getAsString());
            boolean matches = matcher.find();
            return negate != matches;
        } catch (Exception e) {
            return negate;
        }
    }

    /**
     * Folds a JsonElement to lowercase if it's a string. Used where you need a folded value
     * rather than a comparison.
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
