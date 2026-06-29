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
            // The condition matches only if every top-level entry is satisfied.
            return conditionJson.entrySet().stream()
                    .allMatch(entry -> matchesConditionEntry(entry.getKey(), entry.getValue(), attributes, savedGroups));
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
    private boolean matchesConditionEntry(String key, JsonElement value, JsonObject attributes, @Nullable JsonObject savedGroups) {
        Condition operator = Condition.fromValue(key);
        return operator != null
                ? operator.apply(attributes, value, savedGroups, this)
                : evalConditionValue(value, getPath(attributes, key), savedGroups);
    }

    /**
     * @param object The object to evaluate
     * @return true if the object is empty or every key is an operator (starts with {@code $})
     */
    public boolean isOperatorObject(JsonElement object) {
        if (!object.isJsonObject()) {
            return false;
        }
        return object.getAsJsonObject().entrySet().stream()
                .allMatch(entry -> entry.getKey().startsWith("$"));
    }

    /**
     * Resolves a dot-separated path against the attributes.
     *
     * @param attributes User attributes
     * @param path       String path, e.g. {@code path.to.something}
     * @return the value at that path, or {@code null} if the path doesn't exist
     */
    @Nullable
    public JsonElement getPath(JsonElement attributes, String path) {
        if (Objects.equals(path, "")) {
            return null;
        }

        JsonElement element = attributes;
        for (String segment : path.split("\\.")) {
            // Only objects can be descended into; null, arrays and primitives mean "no value here".
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
    boolean evalOperatorCondition(String operatorString, @Nullable JsonElement actual, JsonElement expected, @Nullable JsonObject savedGroups) {
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
    private boolean evalMembership(Operator operator, @Nullable JsonElement actual, JsonElement expected) {
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
    private boolean evalComparison(Operator operator, @Nullable JsonElement actual, JsonElement expected, DataType attributeDataType) {
        if (actual == null || DataType.NULL.equals(attributeDataType)) {
            if (expected.isJsonPrimitive() && !expected.getAsJsonPrimitive().isNumber()) {
                return false;
            }
            return matchesSign(operator, Double.compare(0.0, expected.getAsDouble()));
        }
        // Preserved quirk: $lt treats a digit-only attribute string as a number.
        if (operator == Operator.LT && actual.getAsString().toLowerCase().matches("\\d+")) {
            return Double.parseDouble(actual.getAsString()) < expected.getAsDouble();
        }
        if (actual.getAsJsonPrimitive().isNumber()) {
            return matchesSign(operator, Double.compare(actual.getAsNumber().doubleValue(), expected.getAsNumber().doubleValue()));
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
    private boolean evalVersion(Operator operator, @Nullable JsonElement actual, JsonElement expected, DataType attributeDataType) {
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
    private boolean evalSavedGroup(Operator operator, @Nullable JsonElement actual, @Nullable JsonElement expected, @Nullable JsonObject savedGroups) {
        if (actual == null || expected == null) {
            return false;
        }
        JsonElement group = savedGroups != null ? savedGroups.get(expected.getAsString()) : null;
        JsonArray groupValues = group != null ? group.getAsJsonArray() : new JsonArray();
        boolean negate = operator == Operator.NOT_IN_GROUP;
        return negate != isIn(actual, groupValues, false);
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
     * Compares two primitives for equality, based on their data type.
     */
    private boolean arePrimitivesEqual(JsonPrimitive a, JsonPrimitive b, DataType dataType) {
        switch (dataType) {
            case STRING:
                return a.getAsString().equals(b.getAsString());
            case NUMBER:
                return Double.compare(a.getAsDouble(), b.getAsDouble()) == 0;
            case BOOLEAN:
                return a.getAsBoolean() == b.getAsBoolean();
            default:
                log.info("Unsupported data type {}", dataType);
                return false;
        }
    }

    /**
     * If conditionValue is an operator object, every operator must match the attributeValue.
     * Otherwise this is a deep equality comparison between the two values.
     *
     * @param conditionValue Object or primitive
     * @param attributeValue Object or primitive
     * @param inSensitive    if true, top-level string comparisons are case-insensitive
     * @return true if equal / matched
     */
    boolean evalConditionValue(JsonElement conditionValue, @Nullable JsonElement attributeValue, @Nullable JsonObject savedGroups, boolean inSensitive) {
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
                return isMatchingPrimitive(conditionValue, attributeValue, JsonPrimitive::getAsString);

            case NUMBER:
                return isMatchingPrimitive(conditionValue, attributeValue, JsonPrimitive::getAsDouble);

            case BOOLEAN:
                return isMatchingPrimitive(conditionValue, attributeValue, JsonPrimitive::getAsBoolean);

            case ARRAY:
                return attributeValue != null && attributeValue.isJsonArray()
                        && conditionValue.equals(attributeValue);

            case OBJECT:
                JsonObject conditionValueObject = conditionValue.getAsJsonObject();
                if (isOperatorObject(conditionValueObject)) {
                    return conditionValueObject.entrySet().stream()
                            .allMatch(entry -> evalOperatorCondition(entry.getKey(), attributeValue, entry.getValue(), savedGroups));
                }
                return attributeValue != null && attributeValue.isJsonObject()
                        && conditionValue.equals(attributeValue);

            case NULL:
                return attributeValue == null || attributeValue.isJsonNull();

            case UNDEFINED:
            case UNKNOWN:
            default:
                return conditionValue.toString().equals(attributeValue != null ? attributeValue.toString() : null);
        }
    }

    boolean evalConditionValue(JsonElement conditionValue, @Nullable JsonElement attributeValue, @Nullable JsonObject savedGroups) {
        return evalConditionValue(conditionValue, attributeValue, savedGroups, false);
    }

    /**
     * {@code $elemMatch}: true if any element of the {@code actual} array matches the expected
     * operator object / nested condition.
     */
    private boolean elemMatch(JsonElement actual, JsonElement expected, @Nullable JsonObject savedGroups) {
        if (!actual.isJsonArray()) {
            return false;
        }
        boolean isOperator = isOperatorObject(expected);
        for (JsonElement element : actual.getAsJsonArray()) {
            boolean matched = isOperator
                    ? evalConditionValue(expected, element, savedGroups)
                    : evaluateCondition(element.getAsJsonObject(), expected.getAsJsonObject(), savedGroups);
            if (matched) {
                return true;
            }
        }
        return false;
    }

    /**
     * Tests whether {@code actual} (a primitive or array) is contained in the {@code expected} array.
     */
    private boolean isIn(JsonElement actual, JsonArray expected, boolean caseInsensitive) {
        if (actual == null) {
            return false;
        }
        if (!actual.isJsonArray()) {
            return containsElement(expected, actual, caseInsensitive);
        }
        for (JsonElement item : actual.getAsJsonArray()) {
            if (containsElement(expected, item, caseInsensitive)) {
                return true;
            }
        }
        return false;
    }

    private static boolean containsElement(JsonArray candidates, JsonElement value, boolean caseInsensitive) {
        for (JsonElement candidate : candidates) {
            if (elementsEqual(value, candidate, caseInsensitive)) {
                return true;
            }
        }
        return false;
    }

    private static boolean elementsEqual(JsonElement a, JsonElement b, boolean caseInsensitive) {
        return caseInsensitive
                ? Objects.equals(caseFold(a), caseFold(b))
                : Objects.equals(a, b);
    }

    /**
     * {@code $all}/{@code $allI}: every expected element must match at least one actual element.
     */
    private boolean isInAll(JsonArray actual, JsonArray expected, @Nullable JsonObject savedGroups, boolean inSensitive) {
        for (JsonElement expectedItem : expected) {
            boolean matched = false;
            for (JsonElement actualItem : actual) {
                if (evalConditionValue(expectedItem, actualItem, savedGroups, inSensitive)) {
                    matched = true;
                    break;
                }
            }
            if (!matched) {
                return false;
            }
        }
        return true;
    }

    private <T> boolean isMatchingPrimitive(JsonElement conditionValue, @Nullable JsonElement attributeValue, Function<JsonPrimitive, T> extractor) {
        return attributeValue != null
                && attributeValue.isJsonPrimitive()
                && extractor.apply(conditionValue.getAsJsonPrimitive())
                        .equals(extractor.apply(attributeValue.getAsJsonPrimitive()));
    }

    private static boolean evalRegex(@Nullable JsonElement actual, JsonElement expected, DataType attributeDataType, boolean caseInsensitive, boolean negate) {
        if (actual == null || DataType.NULL.equals(attributeDataType)) {
            return negate;
        }
        int flags = caseInsensitive ? Pattern.CASE_INSENSITIVE : 0;
        try {
            Matcher matcher = Pattern.compile(expected.getAsString(), flags).matcher(actual.getAsString());
            return negate != matcher.find();
        } catch (Exception e) {
            return negate;
        }
    }

    /**
     * Lowercases a string primitive; other elements (including {@code null}) are returned unchanged.
     */
    @Nullable
    private static JsonElement caseFold(@Nullable JsonElement value) {
        if (value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString()) {
            return new JsonPrimitive(value.getAsString().toLowerCase());
        }
        return value;
    }
}
