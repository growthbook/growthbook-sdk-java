package growthbook.sdk.java.evaluators.internal.condition;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import growthbook.sdk.java.model.DataType;
import growthbook.sdk.java.model.Operator;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import growthbook.sdk.java.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.regex.Pattern;

/**
 * The operators of an operator object ({@code {"$gt": 18, "$lt": 65}}), each parsed into a
 * {@link ValueTest}. Everything that depends only on the operand is done once here: regular
 * expressions are compiled, version operands padded and saved group ids extracted. An unknown
 * operator yields {@code null} (unknown); a malformed operand throws
 * {@link MalformedConditionException} when evaluation reaches it.
 */
@Slf4j
final class Operators {

    private static final Map<Operator, BiFunction<Operator, JsonElement, ValueTest>> FACTORIES = factories();

    private Operators() {
    }

    /**
     * One operator of an operator object. Operators fall into a few families:
     * <ul>
     *   <li>comparison: <code>$eq, $ne, $lt, $lte, $gt, $gte, $regex</code></li>
     *   <li>array conditionValue: <code>$in, $nin</code></li>
     *   <li>array attributeValue: <code>$elemMatch, $size</code></li>
     *   <li>both arrays: <code>$all</code></li>
     *   <li>version: <code>$vgt, $vgte, $vlt, $vlte, $vne, $veq</code></li>
     *   <li>saved groups: <code>$inGroup, $notInGroup</code></li>
     *   <li>other: <code>$exists, $type, $not</code></li>
     * </ul>
     * {@code $or}, {@code $nor}, {@code $and} and {@code $savedGroup} on a value never match; an
     * unknown operator is unknown. Each known operator has a factory in {@link #FACTORIES}.
     */
    static ValueTest parse(String operatorString, JsonElement expected) {
        return Described.value(parseUndescribed(operatorString, expected), Described.entry(operatorString, expected));
    }

    private static ValueTest parseUndescribed(String operatorString, JsonElement expected) {
        Operator operator = Operator.fromString(operatorString);
        if (operator != null) {
            return FACTORIES.getOrDefault(operator, (op, operand) -> constant(false)).apply(operator, expected);
        }
        if (CondParser.CONDITION_OPERATORS.contains(operatorString)) {
            return constant(false);
        }
        log.error("Unknown operator: {}", operatorString);
        return constant(null);
    }

    private static Map<Operator, BiFunction<Operator, JsonElement, ValueTest>> factories() {
        Map<Operator, BiFunction<Operator, JsonElement, ValueTest>> factories = new EnumMap<>(Operator.class);
        putAll(factories, Operators::membership, Operator.IN, Operator.INI, Operator.NIN, Operator.NINI);
        putAll(factories, Operators::comparison, Operator.GT, Operator.GTE, Operator.LT, Operator.LTE);
        putAll(factories, Operators::version, Operator.VERSION_GT, Operator.VERSION_GTE, Operator.VERSION_LT,
                Operator.VERSION_LTE, Operator.VERSION_NE, Operator.VERSION_EQ);
        putAll(factories, Operators::groupMembership, Operator.IN_GROUP, Operator.NOT_IN_GROUP);
        factories.put(Operator.REGEX, (op, operand) -> regex(operand, false, false));
        factories.put(Operator.REGEX_I, (op, operand) -> regex(operand, true, false));
        factories.put(Operator.NOT_REGEX, (op, operand) -> regex(operand, false, true));
        factories.put(Operator.NOT_REGEX_I, (op, operand) -> regex(operand, true, true));
        factories.put(Operator.EQ, (op, operand) -> (actual, scope) -> JsonValues.strictEquals(actual, operand));
        factories.put(Operator.NE, (op, operand) -> (actual, scope) -> !JsonValues.strictEquals(actual, operand));
        factories.put(Operator.SIZE, (op, operand) -> size(operand));
        factories.put(Operator.ELEMENT_MATCH, (op, operand) -> elemMatch(operand));
        factories.put(Operator.ALL, (op, operand) -> all(operand, false));
        factories.put(Operator.ALLI, (op, operand) -> all(operand, true));
        factories.put(Operator.NOT, (op, operand) -> not(operand));
        factories.put(Operator.TYPE, (op, operand) -> type(operand));
        factories.put(Operator.EXISTS, (op, operand) -> exists(operand));
        return Collections.unmodifiableMap(factories);
    }

    private static void putAll(Map<Operator, BiFunction<Operator, JsonElement, ValueTest>> factories,
                               BiFunction<Operator, JsonElement, ValueTest> factory,
                               Operator... operators) {
        for (Operator operator : operators) {
            factories.put(operator, factory);
        }
    }

    private static ValueTest not(JsonElement operand) {
        ValueTest inner = CondParser.parseValue(operand);
        return (actual, scope) -> CondParser.negate(inner.test(actual, scope));
    }

    /**
     * {@code $in}/{@code $nin} (and their case-insensitive variants): tests array membership.
     */
    private static ValueTest membership(Operator operator, JsonElement expected) {
        if (!expected.isJsonArray()) {
            return constant(false);
        }
        JsonArray values = expected.getAsJsonArray();
        boolean caseInsensitive = operator == Operator.INI || operator == Operator.NINI;
        boolean negate = operator == Operator.NIN || operator == Operator.NINI;
        return (actual, scope) -> actual != null && negate != JsonValues.isIn(actual, values, caseInsensitive);
    }

    /**
     * {@code $gt}/{@code $gte}/{@code $lt}/{@code $lte}: numeric or lexical comparison.
     *
     * <p>Both sides must be numbers, or both strings; anything else (a type mismatch, a boolean, a
     * missing or null attribute) never matches. Two strings compare as text, so {@code "10" < "9"}.
     *
     * <p>Numbers are compared as {@code double}, deliberately: the reference JavaScript SDK applies
     * native {@code <}/{@code >} to IEEE-754 doubles, so anything narrower diverges from the
     * cross-SDK contract — narrowing to {@code float} once made {@code 16777217 $gt 16777216} false.
     * A {@code NaN} operand makes every comparison false, as in JavaScript.
     */
    private static ValueTest comparison(Operator operator, JsonElement expected) {
        return (actual, scope) -> {
            DataType type = JsonValues.orderableType(actual, expected);
            if (actual == null || type == null) {
                return false;
            }
            if (type == DataType.STRING) {
                return matchesSign(operator, actual.getAsString().compareTo(expected.getAsString()));
            }
            double left = actual.getAsDouble();
            double right = expected.getAsDouble();
            return !Double.isNaN(left) && !Double.isNaN(right)
                    && matchesSign(operator, JsonValues.compareDoubles(left, right));
        };
    }

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
     * {@code $regex}/{@code $regexi} and their negations. Only a string attribute and a valid string
     * pattern can match; anything else fails the positive operators and matches the negated ones.
     */
    private static ValueTest regex(JsonElement expected, boolean caseInsensitive, boolean negate) {
        if (!JsonValues.isString(expected)) {
            return constant(negate);
        }
        // Like a JS /i regex, fold non-ASCII letters too ("É" matches "é"). Known gap: Java also
        // folds a few non-ASCII letters to ASCII ones ("ı" matches "i", "ſ" "s", Kelvin "K" "k"),
        // which JS /i without the u flag doesn't
        int flags = caseInsensitive ? Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE : 0;
        Pattern pattern;
        try {
            pattern = Pattern.compile(expected.getAsString(), flags);
        } catch (RuntimeException e) {
            return constant(negate);
        }
        return (actual, scope) -> {
            if (!JsonValues.isString(actual)) {
                return negate;
            }
            try {
                return negate != pattern.matcher(actual.getAsString()).find();
            } catch (RuntimeException e) {
                return negate;
            }
        };
    }

    private static ValueTest size(JsonElement expected) {
        ValueTest sizeTest = CondParser.parseValue(expected);
        return (actual, scope) -> {
            if (actual == null || !actual.isJsonArray()) {
                return false;
            }
            return sizeTest.test(new JsonPrimitive(actual.getAsJsonArray().size()), scope);
        };
    }

    /**
     * {@code $elemMatch}: true if any element of the attribute array matches.
     *
     * <p>Operators test each element itself. A condition, including one that only uses
     * {@code $or}, {@code $and}, {@code $nor} or {@code $savedGroup}, tests each element's fields.
     * Null elements are skipped; falsy values like {@code 0}, {@code ""} and {@code false} are tested.
     * The result is true if an element matches; otherwise null if any element is unknown, else false.
     */
    private static ValueTest elemMatch(JsonElement expected) {
        boolean testsElement = CondParser.isOperatorObject(expected)
                && expected.getAsJsonObject().keySet().stream().noneMatch(CondParser.CONDITION_OPERATORS::contains);
        ValueTest elementTest;
        if (testsElement) {
            elementTest = CondParser.parseValue(expected);
        } else {
            Cond condition = expected.isJsonObject() ? CondParser.parse(expected.getAsJsonObject()) : CondParser.invalid("$elemMatch needs a condition object");
            elementTest = condition::eval;
        }
        ValueTest nonNullElementTest = (element, scope) -> {
            if (element == null || element.isJsonNull()) {
                return false;
            }
            return elementTest.test(element, scope);
        };
        return (actual, scope) -> {
            if (actual == null || !actual.isJsonArray()) {
                return false;
            }
            return anyElementMatches(nonNullElementTest, actual.getAsJsonArray(), scope);
        };
    }

    /**
     * {@code $all}/{@code $alli}: every expected element must match at least one element of the
     * attribute array. The result is false if an expected element matches none; otherwise null if
     * any is unknown, else true.
     */
    private static ValueTest all(JsonElement expected, boolean caseInsensitive) {
        if (!expected.isJsonArray()) {
            return constant(false);
        }
        List<ValueTest> items = new ArrayList<>();
        for (JsonElement item : expected.getAsJsonArray()) {
            items.add(CondParser.parseValue(item, caseInsensitive));
        }
        return (actual, scope) -> {
            if (actual == null || !actual.isJsonArray()) {
                return false;
            }
            Boolean result = true;
            for (ValueTest item : items) {
                Boolean found = anyElementMatches(item, actual.getAsJsonArray(), scope);
                if (Boolean.FALSE.equals(found)) {
                    return false;
                }
                if (found == null) {
                    result = null;
                }
            }
            return result;
        };
    }

    /**
     * @return true if an element matches; otherwise null if any element is unknown, else false
     */
    @Nullable
    private static Boolean anyElementMatches(ValueTest test, JsonArray elements, CondScope scope) {
        Boolean result = false;
        for (JsonElement element : elements) {
            Boolean matched = test.test(element, scope);
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
     * {@code $type}: the attribute's type name equals the operand, compared strictly as in JS, so
     * only a string operand can match.
     */
    private static ValueTest type(JsonElement expected) {
        if (!JsonValues.isString(expected)) {
            return constant(false);
        }
        String typeName = expected.getAsString();
        return (actual, scope) -> GrowthBookJsonUtils.getElementType(actual).toString().equals(typeName);
    }

    /**
     * {@code $exists}: the operand is read with JS truthiness, so {@code 1} means "exists" and
     * {@code 0}, {@code ""} or {@code null} mean "is missing".
     */
    private static ValueTest exists(JsonElement expected) {
        boolean shouldExist = JsonValues.isTruthy(expected);
        return (actual, scope) -> shouldExist == (actual != null && !actual.isJsonNull());
    }

    /**
     * {@code $vgt}/{@code $vgte}/{@code $vlt}/{@code $vlte}/{@code $vne}/{@code $veq}: compares
     * padded semantic-version strings.
     *
     * <p>Both sides must be strings, or both numbers. On any other combination, including a missing
     * or null attribute, every operator fails except {@code $vne}, which matches.
     */
    private static ValueTest version(Operator operator, JsonElement expected) {
        boolean notEqual = operator == Operator.VERSION_NE;
        DataType expectedType = GrowthBookJsonUtils.getElementType(expected);
        if (expectedType != DataType.NUMBER && expectedType != DataType.STRING) {
            return constant(notEqual);
        }
        String expectedVersion = StringUtils.paddedVersionString(versionString(expected));
        return (actual, scope) -> {
            if (actual == null || JsonValues.orderableType(actual, expected) == null) {
                return notEqual;
            }
            int cmp = StringUtils.paddedVersionString(versionString(actual)).compareTo(expectedVersion);
            switch (operator) {
                case VERSION_GT:  return cmp > 0;
                case VERSION_GTE: return cmp >= 0;
                case VERSION_LT:  return cmp < 0;
                case VERSION_LTE: return cmp <= 0;
                case VERSION_NE:  return cmp != 0;
                case VERSION_EQ:  return cmp == 0;
                default:          return false;
            }
        };
    }

    /**
     * A version operand as text. A number is formatted like JS {@code String(number)}, so
     * {@code 1e3} and {@code 1000.0} are both {@code "1000"}, as they are in the JS SDK.
     */
    private static String versionString(JsonElement value) {
        JsonPrimitive primitive = value.getAsJsonPrimitive();
        return primitive.isNumber()
                ? StringUtils.jsNumberToString(primitive.getAsDouble())
                : primitive.getAsString();
    }

    /**
     * {@code $inGroup}/{@code $notInGroup}: membership in a named saved group.
     *
     * <p>An unknown id behaves like an empty list. The group can be a bare array or a typed
     * {@code list} entry. A condition group or a malformed entry matches nobody, under either
     * operator; a group type added after this SDK was built is unknown ({@code null}).
     */
    private static ValueTest groupMembership(Operator operator, JsonElement expected) {
        if (!JsonValues.isString(expected)) {
            return constant(false);
        }
        String groupId = expected.getAsString();
        boolean negate = operator == Operator.NOT_IN_GROUP;
        return (actual, scope) -> {
            if (actual == null) {
                return false;
            }
            JsonElement group = scope.savedGroups.get(groupId);
            if (SavedGroupRef.isNewGroupType(group)) {
                return null;
            }
            JsonArray values = SavedGroupRef.inGroupValues(group);
            if (values == null) {
                return false;
            }
            return negate != JsonValues.isIn(actual, values, false);
        };
    }

    private static ValueTest constant(@Nullable Boolean result) {
        return (actual, scope) -> result;
    }
}
