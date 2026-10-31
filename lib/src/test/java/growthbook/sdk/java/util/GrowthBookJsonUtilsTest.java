package growthbook.sdk.java;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import growthbook.sdk.java.model.BucketRange;
import growthbook.sdk.java.model.DataType;
import growthbook.sdk.java.model.Namespace;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import org.junit.jupiter.api.Test;

class GrowthBookJsonUtilsTest {
    final GrowthBookJsonUtils subject = GrowthBookJsonUtils.getInstance();

    @Test
    void canSerializeNamespaces() {
        Namespace namespace = Namespace
                .builder()
                .id("pricing")
                .rangeStart(0.0)
                .rangeEnd(0.6)
                .build();

        assertEquals("[\"pricing\",0.0,0.6]", subject.gson.toJson(namespace));
    }

    @Test
    void canDeSerializeNamespaces() {
        Namespace namespace = subject.gson.fromJson("[\"pricing\",0.0,0.6]", Namespace.class);

        assertEquals(namespace.getId(), "pricing");
        assertEquals(namespace.getRangeStart(), 0.0);
        assertEquals(namespace.getRangeEnd(), 0.6);
    }

    @Test
    void canSerializeBucketRanges() {
        BucketRange subject = new BucketRange(0.3, 0.7);

        assertEquals("[0.3,0.7]", GrowthBookJsonUtils.getInstance().gson.toJson(subject));
    }

    @Test
    void canDeserializeBucketRanges() {
        BucketRange subject = GrowthBookJsonUtils.getInstance().gson.fromJson("[0.3,0.7]", BucketRange.class);

        assertEquals(subject.getRangeStart(), 0.3);
        assertEquals(subject.getRangeEnd(), 0.7);
    }


    @Test
    void test_getElementType() {
        Gson gson = GrowthBookJsonUtils.getInstance().gson;

        assertEquals(DataType.NULL, GrowthBookJsonUtils.getElementType(gson.fromJson("null", JsonElement.class)));
        assertEquals(DataType.NULL, GrowthBookJsonUtils.getElementType(JsonNull.INSTANCE));
        assertEquals(DataType.ARRAY, GrowthBookJsonUtils.getElementType(gson.fromJson("[1]", JsonElement.class)));
        assertEquals(DataType.OBJECT, GrowthBookJsonUtils.getElementType(gson.fromJson("{ \"foo\": 2}", JsonElement.class)));
        assertEquals(DataType.BOOLEAN, GrowthBookJsonUtils.getElementType(gson.fromJson("true", JsonElement.class)));
        assertEquals(DataType.NUMBER, GrowthBookJsonUtils.getElementType(gson.fromJson("1337", JsonElement.class)));
        assertEquals(DataType.STRING, GrowthBookJsonUtils.getElementType(gson.fromJson("\"hello\"", JsonElement.class)));
    }
}
