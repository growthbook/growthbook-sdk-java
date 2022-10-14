package growthbook.sdk.java.models;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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
}