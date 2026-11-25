package growthbook.sdk.java.model;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import javax.annotation.Nullable;
import java.io.IOException;

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
     * Writes an {@code OptionalField} as the value it wraps — the format the features endpoint uses
     * (e.g. a rule's {@code force} is written as {@code "force": true}, not the internal
     * {@code {"isPresent":…,"value":…}} wrapper), keeping serialization symmetric with
     * {@link FeatureRule}'s deserializer, which reads the raw value.
     *
     * <p>An absent field writes JSON null, which the SDK's Gson ({@code serializeNulls} off) omits. A
     * present field holding {@code null} is written as an explicit {@code null} even with
     * {@code serializeNulls} off, because {@code "force": null} is a real override that differs from
     * no {@code force} at all. Reading is delegated to Gson's default adapter, unchanged.
     */
    public static TypeAdapterFactory getTypeAdapterFactory() {
        return new TypeAdapterFactory() {
            @Override
            public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
                if (!OptionalField.class.isAssignableFrom(type.getRawType())) {
                    return null;
                }
                TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);
                return new TypeAdapter<T>() {
                    @Override
                    public void write(JsonWriter out, T value) throws IOException {
                        OptionalField<?> field = (OptionalField<?>) value;
                        if (field == null || !field.isPresent()) {
                            out.nullValue();
                            return;
                        }
                        Object wrapped = field.getValue();
                        if (wrapped == null) {
                            boolean serializeNulls = out.getSerializeNulls();
                            out.setSerializeNulls(true);
                            try {
                                out.nullValue();
                            } finally {
                                out.setSerializeNulls(serializeNulls);
                            }
                            return;
                        }
                        gson.toJson(wrapped, wrapped.getClass(), out);
                    }

                    @Override
                    public T read(JsonReader in) throws IOException {
                        return delegate.read(in);
                    }
                };
            }
        };
    }
}
