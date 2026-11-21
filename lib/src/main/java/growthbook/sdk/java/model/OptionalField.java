package growthbook.sdk.java.model;

import javax.annotation.Nullable;

public class OptionalField<T> {
    private final boolean isPresent;
    @Nullable
    private final T value;

    public OptionalField(boolean isPresent, @Nullable T value) {
        this.isPresent = isPresent;
        this.value = value;
    }

    public boolean isPresent() {
        return isPresent;
    }

    @Nullable
    public T getValue() {
        return value;
    }
}
