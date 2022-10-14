package growthbook.sdk.java.models;

import com.google.gson.*;
import com.google.gson.annotations.Expose;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.lang.reflect.Type;

/**
 * A tuple that specifies what part of a namespace an experiment includes. If two experiments are in the same namespace and their ranges don't overlap, they wil be mutually exclusive.
 *
 * The tuple has 3 parts:
 *
 *      - The namespace id (string)
 *      - The beginning of the range (float, between 0 and 1)
 *      - The end of the range (float, between 0 and 1)
 */
@Data @Builder @AllArgsConstructor
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

    public String toJson() {
        return Namespace.getJson(this).toString();
    }

    static JsonSerializer<Namespace> getSerializer() {
        return new JsonSerializer<Namespace>() {
            @Override
            public JsonElement serialize(Namespace src, Type typeOfSrc, JsonSerializationContext context) {
                return Namespace.getJson(src);
            }
        };
    }
}
