package growthbook.sdk.java.model;

import com.google.gson.JsonNull;
import com.google.gson.JsonSerializer;

import javax.annotation.Nullable;

public class OptionalField<ValueType> {
    private final boolean isPresent;
    @Nullable
    private final ValueType value;

    public OptionalField(boolean isPresent, @Nullable ValueType value) {
        this.isPresent = isPresent;
        this.value = value;
    }

    public boolean isPresent() {
        return isPresent;
    }

    @Nullable
    public ValueType getValue() {
        return value;
    }

    /**
     * Serializes an {@code OptionalField} to the value it wraps — the format the features endpoint
     * uses (e.g. a rule's {@code force} is written as {@code "force": true}, not the internal
     * {@code {"isPresent":…,"value":…}} wrapper). An absent field serializes to JSON null, which the
     * SDK's Gson (with {@code serializeNulls} off) omits, matching the endpoint. This keeps
     * serialization symmetric with {@link FeatureRule}'s deserializer, which reads the raw value.
     */
    @SuppressWarnings("rawtypes")
    public static JsonSerializer<OptionalField> getSerializer() {
        return (src, typeOfSrc, context) ->
                src != null && src.isPresent() ? context.serialize(src.getValue()) : JsonNull.INSTANCE;
    }
}
