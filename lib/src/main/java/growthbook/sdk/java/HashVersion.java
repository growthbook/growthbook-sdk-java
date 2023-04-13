package growthbook.sdk.java;

import com.google.gson.*;

import javax.annotation.Nullable;
import java.lang.reflect.Type;

/**
 * The hashing algorithm version you'd like to use.
 *
 */
public enum HashVersion {
    /**
     * version 1 is the current and most widely-supported hash version.
     * @deprecated Use the latest hash version
     */
    V1(1),

    /**
     * version 2 is the latest and recommended hash version.
     */
    V2(2);

    private final Integer rawValue;

    HashVersion(Integer rawValue) {
        this.rawValue = rawValue;
    }

    @Override
    public String toString() {
        return this.rawValue.toString();
    }

    /**
     * @param object HashVersion
     * @return Gson JSON element
     */
    static JsonElement getJson(HashVersion object) {
        Integer hashVersionInt = object.intValue();
        return new JsonPrimitive(hashVersionInt);
    }

    static HashVersion fromJson(JsonElement jsonElement) {
        if (jsonElement == null) return null;
        if (!jsonElement.isJsonPrimitive()) return null;
        if (jsonElement.isJsonNull()) return null;

        Integer hashVersionInt = jsonElement.getAsInt();
        return HashVersion.fromInt(hashVersionInt);
    }

    /**
     * @return the integer value of the hash version
     */
    public Integer intValue() {
        return this.rawValue;
    }

    /**
     * Will attempt to convert the provided integer into a valid {@link HashVersion}.
     * If none exists, null will be returned.
     *
     * @param intValue The integer value that will be attempted to be turned into a valid {@link HashVersion}
     * @return a {@link HashVersion} or null
     */
    public static @Nullable HashVersion fromInt(Integer intValue) {
        for (HashVersion o : values()) {
            if (o.rawValue.equals(intValue)) {
                return o;
            }
        }

        return null;
    }

    public static JsonSerializer<HashVersion> getSerializer() {
        return new JsonSerializer<HashVersion>() {
            @Override
            public JsonElement serialize(HashVersion src, Type typeOfSrc, JsonSerializationContext context) {
                return HashVersion.getJson(src);
            }
        };
    }

    public static JsonDeserializer<HashVersion> getDeserializer() {
        return new JsonDeserializer<HashVersion>() {
            @Override
            public HashVersion deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
                return HashVersion.fromJson(json);
            }
        };
    }
}
