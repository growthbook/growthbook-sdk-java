package growthbook.sdk.java.models;

import com.google.gson.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.lang.reflect.Type;

/**
 * A tuple that describes a range of the number line between 0 and 1.
 * The tuple has 2 parts, both floats - the start of the range and the end.
 */
@Data
@Builder
@AllArgsConstructor
public class BucketRange {
    Float rangeStart;
    Float rangeEnd;

    static BucketRange fromJson(JsonElement jsonElement) {
        JsonArray array = (JsonArray) jsonElement;
        Float start = array.get(0).getAsFloat();
        Float end = array.get(1).getAsFloat();

        return BucketRange
                .builder()
                .rangeStart(start)
                .rangeEnd(end)
                .build();
    }

    public String toJson() {
        return BucketRange.getJson(this).toString();
    }

    @Override
    public String toString() {
        return this.toJson();
    }

    static JsonElement getJson(BucketRange object) {
        JsonArray array = new JsonArray();

        array.add(object.rangeStart);
        array.add(object.rangeEnd);

        return array;
    }

    public static JsonSerializer<BucketRange> getSerializer() {
        return new JsonSerializer<BucketRange>() {
            @Override
            public JsonElement serialize(BucketRange src, Type typeOfSrc, JsonSerializationContext context) {
                return BucketRange.getJson(src);
            }
        };
    }

    public static JsonDeserializer<BucketRange> getDeserializer() {
        return new JsonDeserializer<BucketRange>() {
            @Override
            public BucketRange deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
                return BucketRange.fromJson(json);
            }
        };
    }
}
