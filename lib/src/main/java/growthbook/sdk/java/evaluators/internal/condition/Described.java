package growthbook.sdk.java.evaluators.internal.condition;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.annotation.Nullable;

/**
 * Gives parsed nodes a {@code toString} of the condition JSON they were parsed from, so a tree
 * reads like its source in a debugger or a log.
 */
final class Described {

    private Described() {
    }

    static Cond cond(Cond cond, JsonElement source) {
        return new Cond() {
            @Nullable
            @Override
            public Boolean eval(@Nullable JsonElement attributes, CondScope scope) {
                return cond.eval(attributes, scope);
            }

            @Override
            public String toString() {
                return source.toString();
            }
        };
    }

    static ValueTest value(ValueTest test, JsonElement source) {
        return new ValueTest() {
            @Nullable
            @Override
            public Boolean test(@Nullable JsonElement actual, CondScope scope) {
                return test.test(actual, scope);
            }

            @Override
            public String toString() {
                return source.toString();
            }
        };
    }

    static JsonObject entry(String key, JsonElement value) {
        JsonObject entry = new JsonObject();
        entry.add(key, value);
        return entry;
    }
}
