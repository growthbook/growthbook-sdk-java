package growthbook.sdk.java.evaluators.internal.condition;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import growthbook.sdk.java.model.DataType;
import growthbook.sdk.java.util.GrowthBookJsonUtils;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Parses a condition JSON object into a {@link Cond} tree: logical operators, attribute fields,
 * saved group references and literal values. Operators on a value are parsed by {@link Operators}.
 * Parsing never fails: a malformed part becomes a node that throws
 * {@link MalformedConditionException} when evaluation reaches it.
 */
public final class CondParser {

    /**
     * Keys a condition can have that aren't attributes, besides {@code $not}, which is also an
     * operator on a value. On a value they never match, and in {@code $elemMatch} they make the
     * operand a condition on each element's fields.
     */
    static final List<String> CONDITION_OPERATORS = Arrays.asList("$or", "$nor", "$and", "$savedGroup");

    private CondParser() {
    }

    /**
     * @param condition a condition object, e.g. {@code {"country": {"$in": ["US", "CA"]}}}
     * @return the parsed condition: every entry must match
     */
    public static Cond parse(JsonObject condition) {
        List<Cond> entries = new ArrayList<>(condition.size());
        for (Map.Entry<String, JsonElement> entry : condition.entrySet()) {
            entries.add(parseEntry(entry.getKey(), entry.getValue()));
        }
        return Described.cond(allOf(entries), condition);
    }

    /**
     * @param key   a logical operator ({@code $or}, {@code $nor}, {@code $and}, {@code $not}),
     *              {@code $savedGroup}, or an attribute path
     * @param value the entry's operand
     * @return the parsed entry
     */
    public static Cond parseEntry(String key, JsonElement value) {
        return Described.cond(parseUndescribedEntry(key, value), Described.entry(key, value));
    }

    private static Cond parseUndescribedEntry(String key, JsonElement value) {
        switch (key) {
            case "$or":
                return anyOf(parseList(key, value));
            case "$nor":
                return not(anyOf(parseList(key, value)));
            case "$and":
                return allOf(parseList(key, value));
            case "$not":
                return value.isJsonObject() ? not(parse(value.getAsJsonObject())) : invalid("$not needs a condition object");
            case "$savedGroup":
                return savedGroup(value);
            default:
                AttrPath path = AttrPath.parse(key);
                ValueTest test = parseValue(value);
                return (attributes, scope) -> test.test(path.attributeAt(attributes), scope);
        }
    }

    /**
     * @param conditionValue an object of operators, or a literal the attribute must equal
     * @return the parsed value test
     */
    static ValueTest parseValue(JsonElement conditionValue) {
        return parseValue(conditionValue, false);
    }

    /**
     * @param value a condition value
     * @return true if the value is an object whose keys are all operators (start with {@code $}),
     * including the empty object
     */
    public static boolean isOperatorObject(JsonElement value) {
        if (!value.isJsonObject()) {
            return false;
        }
        for (String key : value.getAsJsonObject().keySet()) {
            if (!key.startsWith("$")) {
                return false;
            }
        }
        return true;
    }

    /**
     * Negates a three-valued result: {@code null} (unknown) stays {@code null}.
     */
    @Nullable
    static Boolean negate(@Nullable Boolean result) {
        return result == null ? null : !result;
    }

    private static List<Cond> parseList(String key, JsonElement value) {
        List<Cond> conditions = new ArrayList<>();
        if (!value.isJsonArray()) {
            conditions.add(invalid(key + " needs an array of conditions"));
            return conditions;
        }
        for (JsonElement element : value.getAsJsonArray()) {
            conditions.add(element.isJsonObject() ? parse(element.getAsJsonObject()) : invalid(key + " needs condition objects"));
        }
        return conditions;
    }

    private static Cond allOf(List<Cond> conditions) {
        return (attributes, scope) -> {
            Boolean result = true;
            for (Cond condition : conditions) {
                Boolean matched = condition.eval(attributes, scope);
                if (Boolean.FALSE.equals(matched)) {
                    return false;
                }
                if (matched == null) {
                    result = null;
                }
            }
            return result;
        };
    }

    /**
     * An empty list matches, like {@code $or: []} in the JS SDK.
     */
    private static Cond anyOf(List<Cond> conditions) {
        return (attributes, scope) -> {
            if (conditions.isEmpty()) {
                return true;
            }
            Boolean result = false;
            for (Cond condition : conditions) {
                Boolean matched = condition.eval(attributes, scope);
                if (Boolean.TRUE.equals(matched)) {
                    return true;
                }
                if (matched == null) {
                    result = null;
                }
            }
            return result;
        };
    }

    private static Cond not(Cond condition) {
        return (attributes, scope) -> negate(condition.eval(attributes, scope));
    }

    private static Cond savedGroup(JsonElement reference) {
        return (attributes, scope) -> SavedGroupRef.resolve(reference, scope).eval(attributes);
    }

    static Cond invalid(String reason) {
        return (attributes, scope) -> {
            throw new MalformedConditionException(reason);
        };
    }

    static ValueTest parseValue(JsonElement conditionValue, boolean caseInsensitive) {
        if (!isOperatorObject(conditionValue)) {
            return Described.value(literal(conditionValue, caseInsensitive), conditionValue);
        }
        List<ValueTest> operators = new ArrayList<>();
        for (Map.Entry<String, JsonElement> entry : conditionValue.getAsJsonObject().entrySet()) {
            operators.add(Operators.parse(entry.getKey(), entry.getValue()));
        }
        return Described.value((actual, scope) -> {
            Boolean result = true;
            for (ValueTest operator : operators) {
                Boolean matched = operator.test(actual, scope);
                if (Boolean.FALSE.equals(matched)) {
                    return false;
                }
                if (matched == null) {
                    result = null;
                }
            }
            return result;
        }, conditionValue);
    }

    /**
     * Deep equality with a literal. With {@code caseInsensitive}, a string matches a string
     * ignoring case.
     */
    private static ValueTest literal(JsonElement expected, boolean caseInsensitive) {
        DataType type = GrowthBookJsonUtils.getElementType(expected);
        switch (type) {
            case STRING:
            case NUMBER:
            case BOOLEAN:
                JsonPrimitive primitive = expected.getAsJsonPrimitive();
                boolean foldCase = caseInsensitive && type == DataType.STRING;
                return (actual, scope) -> {
                    if (foldCase && JsonValues.isString(actual)) {
                        return primitive.getAsString().equalsIgnoreCase(actual.getAsString());
                    }
                    return actual != null && actual.isJsonPrimitive()
                            && JsonValues.primitivesEqual(primitive, actual.getAsJsonPrimitive());
                };
            case ARRAY:
                return (actual, scope) -> actual != null && actual.isJsonArray() && expected.equals(actual);
            case OBJECT:
                return (actual, scope) -> actual != null && actual.isJsonObject() && expected.equals(actual);
            case NULL:
                return (actual, scope) -> actual == null || actual.isJsonNull();
            default:
                return (actual, scope) -> expected.toString().equals(actual != null ? actual.toString() : null);
        }
    }
}
