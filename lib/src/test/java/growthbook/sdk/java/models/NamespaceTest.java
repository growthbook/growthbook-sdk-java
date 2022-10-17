package growthbook.sdk.java.models;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NamespaceTest {
    @Test
    void implementsToJson() {
        Namespace subject = Namespace
                .builder()
                .id("pricing")
                .rangeStart(0.0f)
                .rangeEnd(0.6f)
                .build();

        assertEquals("[\"pricing\",0.0,0.6]", subject.toJson());
    }

    @Test
    void isGsonSerializable() {
        Namespace subject = Namespace
                .builder()
                .id("pricing")
                .rangeStart(0.0f)
                .rangeEnd(0.6f)
                .build();

        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(Namespace.class, Namespace.getSerializer());
        Gson customGson = gsonBuilder.create();

        assertEquals("[\"pricing\",0.0,0.6]", customGson.toJson(subject));
    }

    @Test
    void isGsonDeSerializable() {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(Namespace.class, Namespace.getDeserializer());
        Gson customGson = gsonBuilder.create();

        Namespace subject = customGson.fromJson("[\"pricing\",0.0,0.6]", Namespace.class);

        assertEquals(subject.getId(), "pricing");
        assertEquals(subject.getRangeStart(), 0.0f);
        assertEquals(subject.getRangeEnd(), 0.6f);
    }
}
