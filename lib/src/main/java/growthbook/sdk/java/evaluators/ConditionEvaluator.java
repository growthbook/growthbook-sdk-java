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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * <b>INTERNAL</b>: Implementation of condition evaluation.
 *
 * <p>Public only so the evaluators package can share it; it is not part of the supported API.
 * Even so, {@link #getPath} and {@link #isOperatorObject} keep the exact return types published
 * in 0.11.0: a return type is part of the JVM method descriptor, so narrowing one is a
 * {@link NoSuchMethodError} for anything linked against that release, which no compiler warns
 * about. Internally use {@link #resolvePath} and {@link #isOperatorObjectInternal}, which carry
 * the precise types.
 */
@Slf4j
public class ConditionEvaluator implements IConditionEvaluator {

    /**
     * Keys a condition can have that aren't attributes, besides {@code $not}, which is also an
     * operator on a value. On a value they never match, and in {@code $elemMatch} they make the
     * operand a condition on each element's fields.
     */
    private static final List<String> CONDITION_OPERATORS = Arrays.asList("$or", "$nor", "$and", "$savedGroup");

    private static final String SAVED_GROUP = "$savedGroup";

    /**
     * <b>INTERNAL</b>: The saved groups a condition is evaluated against, and the group ids already
     * being resolved, to stop reference cycles. Immutable; a nested group gets a copy.
     */
    static final class Scope {
        final JsonObject savedGroups;
        final Set<String> visited;

        Scope(@Nullable JsonObject savedGroups) {
            this(savedGroups != null ? savedGroups : new JsonObject(), Collections.<String>emptySet());
        }

        private Scope(JsonObject savedGroups, Set<String> visited) {
            this.savedGroups = savedGroups;
            this.visited = visited;
        }

        Scope visiting(String groupId) {
            Set<String> next = new HashSet<>(visited);
            next.add(groupId);
            return new Scope(savedGroups, Collections.unmodifiableSet(next));
        }
    }

    /**
     * Negates a three-valued result: {@code null} (unknown) stays {@code null}.
     */
    @Nullable
    static Boolean not(@Nullable Boolean result) {
        return result == null ? null : !result;
    }

    /**
     * Evaluate a condition for a set of user attributes based on the provided condition.
     * The condition syntax closely resembles MongoDB's syntax.
     * This is defined in the Feature's targeting conditions' Advanced settings.
     *
     * <p>A condition that depends on an operator or saved group type this SDK doesn't support
     * fails, including under {@code $not} and {@code $nor}. A malformed condition fails as a whole.
     *
     * @param attributes    A JsonObject of the user attributes to evaluate
     * @param conditionJson A JsonObject of the condition
     * @return Whether the condition should be true for the user
     */
    @Override
    public Boolean evaluateCondition(JsonObject attributes, JsonObject conditionJson, @Nullable JsonObject savedGroups) {
        try {
            return Boolean.TRUE.equals(evalCondition(attributes, conditionJson, new Scope(savedGroups)));
        } catch (Exception exception) {
            log.error(exception.getMessage(), exception);
            return false;
        }
    }

    /**
     * Evaluates every entry of a condition: a logical operator ($or/$nor/$and/$not) is handled by
     * its strategy (recursing back through this evaluator); any other key is treated as an
     * attribute path and compared as a leaf value. Throws on a malformed condition.
     *
     * @return false if any entry fails; otherwise null if any entry is unknown, else true
     */
    @Nullable
    Boolean evalCondition(@Nullable JsonElement attributes, JsonObject condition, Scope scope) {
        Boolean result = true;
        for (Map.Entry<String, JsonElement> entry : condition.entrySet()) {
            Boolean matched = evalConditionEntry(entry.getKey(), entry.getValue(), attributes, scope);
            if (Boolean.FALSE.equals(matched)) {
                return false;
            }
            if (matched == null) {
                result = null;
            }
        }
        return result;
    }

    @Nullable
    private Boolean evalConditionEntry(String key, JsonElement value, @Nullable JsonElement attributes, Scope scope) {
        if (SAVED_GROUP.equals(key)) {
            return evalSavedGroupReference(attributes, value, scope);
        }
        Condition operator = Condition.fromValue(key);
        return operator != null
                ? operator.apply(attributes, value, scope, this)
                : evalConditionValue(value, resolvePath(attributes, key), scope, false);
    }

    /**
     * Returns a boxed {@code Boolean} to keep the signature published in 0.11.0: a return type is
     * part of the JVM method descriptor, so narrowing it to {@code boolean} is a
     * {@link NoSuchMethodError} for callers compiled against that release. The value is never
     * null; prefer {@link #isOperatorObjectInternal} inside this class.
     *
     * @param object The object to evaluate
     * @return true if the object is empty or every key is an operator (starts with {@code $})
     */
    public Boolean isOperatorObject(JsonElement object) {
        return isOperatorObjectInternal(object);
    }

    private boolean isOperatorObjectInternal(JsonElement object) {
        if (!object.isJsonObject()) {
            return false;
        }
        return object.getAsJsonObject().entrySet().stream()
                .allMatch(entry -> entry.getKey().startsWith("$"));
    }

    /**
     * Resolves a dot-separated path against the attributes.
     *
     * <p>Declared as {@code Object} to keep the signature published in 0.11.0, for the same
     * binary-compatibility reason as {@link #isOperatorObject}. Every value it can return is a
     * {@link JsonElement} or {@code null}; use {@link #resolvePath} inside this class to get that
     * type without a cast.
     *
     * @param attributes User attributes
     * @param path       String path, e.g. {@code path.to.something}
     * @return the value at that path, or {@code null} if the path doesn't exist
     */
    @Nullable
    public Object getPath(JsonElement attributes, String path) {
        return resolvePath(attributes, path);
    }

    @Nullable
    private JsonElement resolvePath(@Nullable JsonElement attributes, String path) {
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
     * @return if it's a match, or null for an operator or saved group type this SDK doesn't
     * support. {@code $or}, {@code $nor}, {@code $and} and {@code $savedGroup} on a value never match.
     */
    @Nullable
    Boolean evalOperatorCondition(String operatorString, @Nullable JsonElement actual, JsonElement expected, Scope scope) {
        Operator operator = Operator.fromString(operatorString);
        if (operator == null) {
            if (CONDITION_OPERATORS.contains(operatorString)) {
                return false;
            }
            log.error("Unknown operator: {}", operatorString);
            return null;
        }

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
                return !strictEquals(actual, expected);

            case EQ:
                return strictEquals(actual, expected);

            case SIZE:
                if (actual == null || !actual.isJsonArray()) return false;
                return evalConditionValue(expected, new JsonPrimitive(actual.getAsJsonArray().size()), scope, false);

            case ELEMENT_MATCH:
                if (actual == null) return false;
                return elemMatch(actual, expected, scope);

            case ALL:
                if (actual == null || !actual.isJsonArray() || !expected.isJsonArray()) return false;
                return isInAll(actual.getAsJsonArray(), expected.getAsJsonArray(), scope, false);
            case ALLI:
                if (actual == null || !actual.isJsonArray() || !expected.isJsonArray()) return false;
                return isInAll(actual.getAsJsonArray(), expected.getAsJsonArray(), scope, true);

            case NOT:
                return not(evalConditionValue(expected, actual, scope, false));

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
                return evalVersion(operator, actual, expected);

            case IN_GROUP:
            case NOT_IN_GROUP:
                return evalGroupMembership(operator, actual, expected, scope);

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
     *
     * <p>Both sides must be numbers, or both strings; anything else (a type mismatch, a boolean, a
     * missing or null attribute) never matches. Two strings compare as text, so {@code "10" < "9"}.
     *
     * <p>Numbers are compared as {@code double}, deliberately: the reference JavaScript SDK applies
     * native {@code <}/{@code >} to IEEE-754 doubles, so anything narrower diverges from the
     * cross-SDK contract. An earlier version narrowed both operands to {@code float} first, which
     * made values differing only beyond 2^24 compare equal — for example {@code 16777217 $gt
     * 16777216} returned {@code false}. Do not reintroduce narrowing here; {@code primitivesEqual}
     * compares doubles on the {@code $eq}/{@code $ne} path for the same reason.
     */
    private boolean evalComparison(Operator operator, @Nullable JsonElement actual, JsonElement expected, DataType attributeDataType) {
        if (!isOrderable(actual, expected)) {
            return false;
        }
        if (attributeDataType == DataType.NUMBER) {
            return compareNumbers(operator, actual.getAsDouble(), expected.getAsDouble());
        }
        return matchesSign(operator, actual.getAsString().compareTo(expected.getAsString()));
    }

    /**
     * Whether two values can be ordered against each other: both numbers, or both strings.
     */
    private static boolean isOrderable(@Nullable JsonElement actual, @Nullable JsonElement expected) {
        DataType type = GrowthBookJsonUtils.getElementType(actual);
        return (type == DataType.NUMBER || type == DataType.STRING)
                && type == GrowthBookJsonUtils.getElementType(expected);
    }

    /**
     * Orders two doubles the way the reference JavaScript SDK's {@code <}/{@code >} do.
     *
     * <p>{@link Double#compare} imposes a total order in which {@code -0.0} sorts below {@code 0.0},
     * so {@code -0.0 $gte 0} would be false. Native comparison treats them as equal, which is what
     * the other SDKs — and this one before the switch to doubles — report.
     */
    private static int compareDoubles(double left, double right) {
        if (left < right) {
            return -1;
        }
        if (left > right) {
            return 1;
        }
        return 0;
    }

    /**
     * Applies a {@code $gt}/{@code $gte}/{@code $lt}/{@code $lte} operator to two doubles.
     *
     * <p>A {@code NaN} operand makes every comparison false, matching the reference JavaScript SDK
     * where {@code <}/{@code >}/{@code <=}/{@code >=} against {@code NaN} are all false. Without this
     * guard {@link #compareDoubles} reports {@code NaN} as equal to any finite number — neither
     * {@code <} nor {@code >} holds — so {@code $gte}/{@code $lte} would wrongly match.
     */
    private static boolean compareNumbers(Operator operator, double left, double right) {
        if (Double.isNaN(left) || Double.isNaN(right)) {
            return false;
        }
        return matchesSign(operator, compareDoubles(left, right));
    }

    /**
     * {@code $vgt}/{@code $vgte}/{@code $vlt}/{@code $vlte}/{@code $vne}/{@code $veq}:
     * compares padded semantic-version strings.
     *
     * <p>Both sides must be strings, or both numbers. On any other combination, including a missing
     * or null attribute, every operator fails except {@code $vne}, which matches.
     */
    private boolean evalVersion(Operator operator, @Nullable JsonElement actual, JsonElement expected) {
        if (!isOrderable(actual, expected)) {
            return operator == Operator.VERSION_NE;
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
     * {@code $inGroup}/{@code $notInGroup}: membership in a named saved group.
     *
     * <p>An unknown id behaves like an empty list. The group can be a bare array or a typed
     * {@code list} entry. A condition group or a malformed entry matches nobody, under either
     * operator; a group type added after this SDK was built is unknown ({@code null}).
     */
    @Nullable
    private Boolean evalGroupMembership(Operator operator, @Nullable JsonElement actual, @Nullable JsonElement expected, Scope scope) {
        if (actual == null || expected == null || !isString(expected)) {
            return false;
        }
        JsonElement group = scope.savedGroups.get(expected.getAsString());
        if (isNewGroupType(group)) {
            return null;
        }
        JsonArray groupValues = savedGroupArrayValues(group);
        if (groupValues == null) {
            return false;
        }
        boolean negate = operator == Operator.NOT_IN_GROUP;
        return negate != isIn(actual, groupValues, false);
    }

    /**
     * The values of a saved group usable by {@code $inGroup}: an empty list for an unknown id, a
     * bare array, or a typed {@code list} entry's {@code values}; {@code null} for anything else.
     */
    @Nullable
    private static JsonArray savedGroupArrayValues(@Nullable JsonElement group) {
        if (group == null) {
            return new JsonArray();
        }
        if (group.isJsonArray()) {
            return group.getAsJsonArray();
        }
        if (group.isJsonObject() && "list".equals(stringField(group.getAsJsonObject(), "type"))) {
            JsonElement values = group.getAsJsonObject().get("values");
            return values != null && values.isJsonArray() ? values.getAsJsonArray() : null;
        }
        return null;
    }

    /**
     * Whether a saved group entry has a type added after this SDK was built.
     */
    static boolean isNewGroupType(@Nullable JsonElement group) {
        if (group == null || !group.isJsonObject()) {
            return false;
        }
        String type = stringField(group.getAsJsonObject(), "type");
        return !"list".equals(type) && !"condition".equals(type);
    }

    @Nullable
    static String stringField(JsonObject object, String key) {
        JsonElement value = object.get(key);
        return isString(value) ? value.getAsString() : null;
    }

    static boolean isString(@Nullable JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isString();
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
     * {@code $eq}/{@code $ne} equality: the same type and the same value, with no coercion, like
     * JavaScript's {@code ===}. A missing attribute equals nothing, and arrays and objects are never
     * equal here.
     */
    private static boolean strictEquals(@Nullable JsonElement actual, JsonElement expected) {
        if (actual == null) {
            return false;
        }
        if (actual.isJsonNull()) {
            return expected.isJsonNull();
        }
        return actual.isJsonPrimitive() && expected.isJsonPrimitive()
                && primitivesEqual(actual.getAsJsonPrimitive(), expected.getAsJsonPrimitive());
    }

    /**
     * Whether two primitives have the same type and value. Numbers compare as {@code double}, so
     * {@code 1} equals {@code 1.0} and {@code -0.0} equals {@code 0}; a number never equals a string
     * or boolean.
     */
    private static boolean primitivesEqual(JsonPrimitive a, JsonPrimitive b) {
        if (a.isNumber() && b.isNumber()) {
            return a.getAsDouble() == b.getAsDouble();
        }
        if (a.isString() && b.isString()) {
            return a.getAsString().equals(b.getAsString());
        }
        if (a.isBoolean() && b.isBoolean()) {
            return a.getAsBoolean() == b.getAsBoolean();
        }
        return false;
    }

    /**
     * If conditionValue is an operator object, every operator must match the attributeValue.
     * Otherwise this is a deep equality comparison between the two values.
     *
     * @param conditionValue Object or primitive
     * @param attributeValue Object or primitive
     * @param inSensitive    if true, top-level string comparisons are case-insensitive
     * @return true if equal / matched, null if an operator can't be evaluated by this SDK
     */
    @Nullable
    Boolean evalConditionValue(JsonElement conditionValue, @Nullable JsonElement attributeValue, Scope scope, boolean inSensitive) {
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
            case NUMBER:
            case BOOLEAN:
                return attributeValue != null && attributeValue.isJsonPrimitive()
                        && primitivesEqual(conditionValue.getAsJsonPrimitive(), attributeValue.getAsJsonPrimitive());

            case ARRAY:
                return attributeValue != null && attributeValue.isJsonArray()
                        && conditionValue.equals(attributeValue);

            case OBJECT:
                JsonObject conditionValueObject = conditionValue.getAsJsonObject();
                if (isOperatorObjectInternal(conditionValueObject)) {
                    Boolean result = true;
                    for (Map.Entry<String, JsonElement> entry : conditionValueObject.entrySet()) {
                        Boolean matched = evalOperatorCondition(entry.getKey(), attributeValue, entry.getValue(), scope);
                        if (Boolean.FALSE.equals(matched)) {
                            return false;
                        }
                        if (matched == null) {
                            result = null;
                        }
                    }
                    return result;
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

    /**
     * {@code $elemMatch}: true if any element of the {@code actual} array matches.
     *
     * <p>Operators test each element itself. A condition, including one that only uses
     * {@code $or}, {@code $and}, {@code $nor} or {@code $savedGroup}, tests each element's fields.
     * Null elements are skipped; falsy values like {@code 0}, {@code ""} and {@code false} are tested.
     *
     * @return true if an element matches; otherwise null if any element is unknown, else false
     */
    @Nullable
    private Boolean elemMatch(JsonElement actual, JsonElement expected, Scope scope) {
        if (!actual.isJsonArray()) {
            return false;
        }
        boolean testsElement = isOperatorObjectInternal(expected)
                && expected.getAsJsonObject().keySet().stream().noneMatch(CONDITION_OPERATORS::contains);
        Boolean result = false;
        for (JsonElement element : actual.getAsJsonArray()) {
            if (element == null || element.isJsonNull()) {
                continue;
            }
            Boolean matched = testsElement
                    ? evalConditionValue(expected, element, scope, false)
                    : evalCondition(element, expected.getAsJsonObject(), scope);
            if (Boolean.TRUE.equals(matched)) {
                return true;
            }
            if (matched == null) {
                result = null;
            }
        }
        return result;
    }

    /**
     * Tests whether {@code actual} (a primitive or array) is contained in the {@code expected} array.
     */
    static boolean isIn(@Nullable JsonElement actual, JsonArray expected, boolean caseInsensitive) {
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
     *
     * @return false if an expected element matches none; otherwise null if any is unknown, else true
     */
    @Nullable
    private Boolean isInAll(JsonArray actual, JsonArray expected, Scope scope, boolean inSensitive) {
        Boolean result = true;
        for (JsonElement expectedItem : expected) {
            Boolean passed = false;
            for (JsonElement actualItem : actual) {
                Boolean matched = evalConditionValue(expectedItem, actualItem, scope, inSensitive);
                if (Boolean.TRUE.equals(matched)) {
                    passed = true;
                    break;
                }
                if (matched == null) {
                    passed = null;
                }
            }
            if (Boolean.FALSE.equals(passed)) {
                return false;
            }
            if (passed == null) {
                result = null;
            }
        }
        return result;
    }

    /**
     * {@code $regex}/{@code $regexi} and their negations. Only a string attribute can match; any
     * other attribute fails the positive operators and matches the negated ones.
     */
    private static boolean evalRegex(@Nullable JsonElement actual, JsonElement expected, DataType attributeDataType, boolean caseInsensitive, boolean negate) {
        if (attributeDataType != DataType.STRING) {
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
     * What a {@code $savedGroup} reference resolves to before it is evaluated: a decided result, an
     * unknown group type, a list group (attribute key and values), or a condition group.
     */
    private static final class SavedGroupTarget {
        @Nullable
        final Boolean decided;
        @Nullable
        final String listKey;
        @Nullable
        final JsonArray listValues;
        @Nullable
        final JsonObject condition;
        @Nullable
        final Scope nested;

        private SavedGroupTarget(@Nullable Boolean decided, @Nullable String listKey, @Nullable JsonArray listValues,
                                 @Nullable JsonObject condition, @Nullable Scope nested) {
            this.decided = decided;
            this.listKey = listKey;
            this.listValues = listValues;
            this.condition = condition;
            this.nested = nested;
        }

        static SavedGroupTarget decided(@Nullable Boolean result) {
            return new SavedGroupTarget(result, null, null, null, null);
        }

        boolean isDecided() {
            return listKey == null && condition == null;
        }
    }

    /**
     * Resolves a {@code $savedGroup} reference ({@code {"id": ..., "attributeKey": ...}}).
     * Anything unrecognized matches nobody; a group type added after this SDK was built is unknown.
     */
    private static SavedGroupTarget resolveSavedGroup(@Nullable JsonElement reference, Scope scope) {
        if (reference == null || !reference.isJsonObject()) {
            return SavedGroupTarget.decided(false);
        }
        JsonObject ref = reference.getAsJsonObject();
        String id = stringField(ref, "id");
        if (id == null || scope.visited.contains(id)) {
            return SavedGroupTarget.decided(false);
        }
        // Falling back to the entry's attribute would check a different population
        JsonElement attributeKey = ref.get("attributeKey");
        if (attributeKey != null && !isString(attributeKey)) {
            return SavedGroupTarget.decided(false);
        }

        JsonElement entry = scope.savedGroups.get(id);
        if (entry == null || !entry.isJsonObject()) {
            return SavedGroupTarget.decided(false);
        }
        JsonObject group = entry.getAsJsonObject();
        String type = stringField(group, "type");

        if ("list".equals(type)) {
            String key = attributeKey != null ? attributeKey.getAsString() : stringField(group, "attributeKey");
            JsonElement values = group.get("values");
            if (key == null || values == null || !values.isJsonArray()) {
                return SavedGroupTarget.decided(false);
            }
            return new SavedGroupTarget(null, key, values.getAsJsonArray(), null, null);
        }

        if ("condition".equals(type)) {
            // A condition group has no single attribute, so any override is ignored
            JsonElement condition = group.get("condition");
            if (condition == null || !condition.isJsonObject()) {
                return SavedGroupTarget.decided(false);
            }
            return new SavedGroupTarget(null, null, null, condition.getAsJsonObject(), scope.visiting(id));
        }

        return SavedGroupTarget.decided(null);
    }

    /**
     * {@code $savedGroup}: whether the attributes are in the referenced saved group.
     *
     * @return the result, or null for a saved group type this SDK doesn't know
     */
    @Nullable
    private Boolean evalSavedGroupReference(@Nullable JsonElement attributes, @Nullable JsonElement reference, Scope scope) {
        SavedGroupTarget target = resolveSavedGroup(reference, scope);
        if (target.isDecided()) {
            return target.decided;
        }
        if (target.listKey != null) {
            return isIn(resolvePath(attributes, target.listKey), target.listValues, false);
        }
        return evalCondition(attributes, target.condition, target.nested);
    }

    /**
     * Partially evaluates a condition when only some attributes are known. Attributes missing
     * from {@code attributes} are unknown, and so is any nested path.
     *
     * @return {@code true} or {@code false} once the result can't depend on unknown attributes,
     * otherwise the condition minus every branch that is already decided. A condition that
     * throws, such as a malformed one, is returned as written.
     */
    JsonElement pruneCondition(JsonObject attributes, JsonObject condition, @Nullable JsonObject savedGroups) {
        try {
            return pruneEntries(attributes, condition, new Scope(savedGroups));
        } catch (RuntimeException | StackOverflowError e) {
            // A very long chain of group references can exhaust the stack; keep it as written
            return condition;
        }
    }

    /**
     * Prunes the saved group with this id.
     *
     * @return the group's result once decided; its condition minus every decided branch for a
     * condition group that isn't; or {@code null} while a list group's attribute is unknown, the
     * group's type is unknown, or its condition throws (it is then kept as written)
     */
    @Nullable
    JsonElement pruneSavedGroup(JsonObject attributes, String groupId, @Nullable JsonObject savedGroups) {
        JsonObject reference = new JsonObject();
        reference.addProperty("id", groupId);
        try {
            return pruneSavedGroupReference(attributes, reference, new Scope(savedGroups));
        } catch (RuntimeException | StackOverflowError e) {
            return null;
        }
    }

    @Nullable
    private JsonElement pruneSavedGroupReference(JsonObject attributes, @Nullable JsonElement reference, Scope scope) {
        SavedGroupTarget target = resolveSavedGroup(reference, scope);
        if (target.isDecided()) {
            return target.decided == null ? null : new JsonPrimitive(target.decided);
        }
        if (target.listKey != null) {
            if (!isKnown(attributes, target.listKey)) {
                return null;
            }
            return new JsonPrimitive(isIn(resolvePath(attributes, target.listKey), target.listValues, false));
        }
        return pruneEntries(attributes, target.condition, target.nested);
    }

    /**
     * @return a boolean primitive once decided, otherwise the undecided entries
     */
    private JsonElement pruneEntries(JsonObject attributes, JsonObject condition, Scope scope) {
        JsonObject out = new JsonObject();
        for (Map.Entry<String, JsonElement> entry : condition.entrySet()) {
            String key = entry.getKey();
            JsonElement value = entry.getValue();
            JsonElement pruned;
            switch (key) {
                case "$or":
                case "$nor":
                case "$and":
                    pruned = pruneList(attributes, value.getAsJsonArray(), scope, !"$and".equals(key));
                    break;
                case "$not":
                    pruned = pruneEntries(attributes, value.getAsJsonObject(), scope);
                    break;
                case SAVED_GROUP:
                    pruned = pruneSavedGroupReference(attributes, value, scope);
                    // Keep an undecided reference as is rather than inlining the group
                    if (!isBoolean(pruned)) {
                        pruned = null;
                    }
                    break;
                default:
                    // null when the attribute is unknown, or the value uses an operator this SDK
                    // doesn't support, which the client's SDK may
                    Boolean matched = isKnown(attributes, key)
                            ? evalConditionValue(value, resolvePath(attributes, key), scope, false)
                            : null;
                    pruned = matched == null ? null : new JsonPrimitive(matched);
            }
            // $not and $nor pass when what they hold fails
            if (("$not".equals(key) || "$nor".equals(key)) && isBoolean(pruned)) {
                pruned = new JsonPrimitive(!pruned.getAsBoolean());
            }

            if (isBoolean(pruned) && !pruned.getAsBoolean()) {
                return new JsonPrimitive(false);
            }
            if (pruned == null) {
                out.add(key, value);
            } else if (!isBoolean(pruned)) {
                out.add(key, pruned);
            }
        }
        return out.size() > 0 ? out : new JsonPrimitive(true);
    }

    /**
     * Prunes an {@code $or} (or an {@code $and}) list.
     *
     * @return the conditions still undecided, or the list's result once there are none
     */
    private JsonElement pruneList(JsonObject attributes, JsonArray conditions, Scope scope, boolean or) {
        JsonArray out = new JsonArray();
        for (JsonElement condition : conditions) {
            JsonElement pruned = pruneEntries(attributes, condition.getAsJsonObject(), scope);
            if (isBoolean(pruned)) {
                // One passing condition decides an $or, and one failing condition an $and
                if (or == pruned.getAsBoolean()) {
                    return pruned;
                }
            } else {
                out.add(pruned);
            }
        }
        if (out.size() > 0) {
            return out;
        }
        // Every condition failed an $or, or passed an $and. Like evaluation, an empty $or passes.
        return new JsonPrimitive(!or || conditions.isEmpty());
    }

    /**
     * Whether a top-level attribute is known. A nested path is unknown, since a server may only
     * have part of the object.
     */
    private static boolean isKnown(JsonObject attributes, String path) {
        return !path.contains(".") && attributes.has(path);
    }

    static boolean isBoolean(@Nullable JsonElement value) {
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isBoolean();
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
