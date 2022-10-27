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

    private static final int BUCKET_RANGE_FLOAT_PRECISION = 3;

    Float rangeStart;
    Float rangeEnd;

    static BucketRange fromJson(JsonElement jsonElement) {
        JsonArray array = (JsonArray) jsonElement;
        float start = array.get(0).getAsFloat();
        float end = array.get(1).getAsFloat();

        return BucketRange
                .builder()
                .rangeStart(Precision.round(start, BUCKET_RANGE_FLOAT_PRECISION))
                .rangeEnd(Precision.round(end, BUCKET_RANGE_FLOAT_PRECISION))
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
                Precision.round(this.rangeStart, BUCKET_RANGE_FLOAT_PRECISION),
                Precision.round(that.rangeStart, BUCKET_RANGE_FLOAT_PRECISION)
        ) && Objects.equals(
                Precision.round(this.rangeEnd, BUCKET_RANGE_FLOAT_PRECISION),
                Precision.round(that.rangeEnd, BUCKET_RANGE_FLOAT_PRECISION)
        );
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                Precision.round(this.rangeStart, BUCKET_RANGE_FLOAT_PRECISION),
                Precision.round(this.rangeEnd, BUCKET_RANGE_FLOAT_PRECISION)
        );
    }

    static JsonElement getJson(BucketRange object) {
        JsonArray array = new JsonArray();

        array.add(Precision.round(object.rangeStart, BUCKET_RANGE_FLOAT_PRECISION));
        array.add(Precision.round(object.rangeEnd, BUCKET_RANGE_FLOAT_PRECISION));

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
