package growthbook.sdk.java;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonSerializer;
import com.google.gson.annotations.Expose;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * A tuple that specifies what part of a namespace an experiment includes. If two experiments are in the same namespace and their ranges don't overlap, they wil be mutually exclusive.
 * <p>
 * The tuple has 3 parts:
 * </p>
 * <ul>
 *     <li>The namespace id (string)</li>
 *     <li>The beginning of the range (float, between 0 and 1)</li>
 *     <li>The end of the range (float, between 0 and 1)</li>
 * </ul>
 */
@Deprecated
@Data
@Builder
@AllArgsConstructor
public class Namespace {
    @Expose(serialize = false)
    String id;

    @Expose(serialize = false)
    Float rangeStart;

    @Expose(serialize = false)
    Float rangeEnd;

    static JsonElement getJson(Namespace object) {
        JsonArray array = new JsonArray();

        array.add(object.id);
        array.add(object.rangeStart);
        array.add(object.rangeEnd);

        return array;
    }

    static Namespace fromJson(JsonElement jsonElement) {
        JsonArray namespaceArray = (JsonArray) jsonElement;
        String id = namespaceArray.get(0).getAsString();
        Float start = namespaceArray.get(1).getAsFloat();
        Float end = namespaceArray.get(2).getAsFloat();

        return Namespace
                .builder()
                .id(id)
                .rangeStart(start)
                .rangeEnd(end)
                .build();
    }

    /**
     * A JSON string for the namespace, resulting in a triple value [id, rangeStart, rangeEnd]
     *
     * @return JSON string
     */
    public String toJson() {
        return Namespace.getJson(this).toString();
    }

    @Override
    public String toString() {
        return toJson();
    }

    /**
     * a Gson deserializer for {@link Namespace}
     *
     * @return a deserializer for {@link Namespace}
     */
    public static JsonDeserializer<Namespace> getDeserializer() {
        return (json, typeOfT, context) -> Namespace.fromJson(json);
    }

    /**
     * a Gson serializer for {@link Namespace}
     *
     * @return a serializer for {@link Namespace}
     */
    public static JsonSerializer<Namespace> getSerializer() {
        return (src, typeOfSrc, context) -> Namespace.getJson(src);
    }
}
