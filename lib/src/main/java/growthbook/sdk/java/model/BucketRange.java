package growthbook.sdk.java.model;

import com.google.gson.JsonArray;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonSerializer;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * A tuple that describes a range of the number line between 0 and 1.
 * The tuple has 2 parts, both doubles - the start of the range and the end.
 */
@Data
@Builder
@AllArgsConstructor
public class BucketRange {

    Double rangeStart;
    Double rangeEnd;

    /**
     * This method help to convert BucketRange object from JsonElement
     *
     * @param jsonElement json element
     * @return BucketRange object
     */
    static BucketRange fromJson(JsonElement jsonElement) {
        JsonArray array = (JsonArray) jsonElement;
        return BucketRange
                .builder()
                .rangeStart(array.get(0).getAsDouble())
                .rangeEnd(array.get(1).getAsDouble())
                .build();
    }

    /**
     * Converts the bucket range to the serialized tuple
     *
     * @return JSON string of the bucket range
     */
    public String toJson() {
        return BucketRange.getJson(this).toString();
    }

    @Override
    public String toString() {
        return this.toJson();
    }

    /**
     * @param object bucket range
     * @return Gson JSON element
     */
    static JsonElement getJson(BucketRange object) {
        JsonArray array = new JsonArray();

        array.add(object.rangeStart);
        array.add(object.rangeEnd);

        return array;
    }

    /**
     * @return serializer for {@link BucketRange}
     */
    public static JsonSerializer<BucketRange> getSerializer() {
        return (src, typeOfSrc, context) -> BucketRange.getJson(src);
    }

    /**
     * @return deserializer for {@link BucketRange}
     */
    public static JsonDeserializer<BucketRange> getDeserializer() {
        return (json, typeOfT, context) -> BucketRange.fromJson(json);
    }
}
