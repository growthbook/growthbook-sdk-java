package growthbook.sdk.java.models;

import com.google.gson.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.apache.commons.math3.util.Precision;

import java.lang.reflect.Type;
import java.util.Objects;

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
        float start = array.get(0).getAsFloat();
        float end = array.get(1).getAsFloat();

        return BucketRange
                .builder()
                .rangeStart(Precision.round(start, 3))
                .rangeEnd(Precision.round(end, 3))
                .build();
    }

    public String toJson() {
        return BucketRange.getJson(this).toString();
    }

    @Override
    public String toString() {
        return this.toJson();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        BucketRange that = (BucketRange) o;
        return Objects.equals(
                Precision.round(this.rangeStart, 3),
                Precision.round(that.rangeStart, 3)
        ) && Objects.equals(
                Precision.round(this.rangeEnd, 3),
                Precision.round(that.rangeEnd, 3)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(rangeStart, rangeEnd);
    }

    static JsonElement getJson(BucketRange object) {
        JsonArray array = new JsonArray();

        array.add(Precision.round(object.rangeStart, 3));
        array.add(Precision.round(object.rangeEnd, 3));

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
