package growthbook.sdk.java;

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
}
