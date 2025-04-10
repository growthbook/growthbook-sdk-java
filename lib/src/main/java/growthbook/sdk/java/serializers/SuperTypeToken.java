package growthbook.sdk.java.serializers;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;

public abstract class SuperTypeToken<T> {
    public Type getType() {
        Class<?> clazz = getClass();
        ParameterizedType t = (ParameterizedType) clazz.getGenericSuperclass();
        Type[] actualTypeArguments = t.getActualTypeArguments();
        return actualTypeArguments[0];
    }
}