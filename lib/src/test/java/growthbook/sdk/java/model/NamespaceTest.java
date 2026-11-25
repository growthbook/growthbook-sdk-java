package growthbook.sdk.java.model;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SuppressWarnings("deprecation")
class NamespaceTest {

    @Test
    @DisplayName("Serializes to an id and range JSON triple")
    void serializesToIdAndRangeTriple() {
        // Given
        Namespace subject = Namespace.builder().id("pricing").rangeStart(0.0).rangeEnd(0.6).build();
        Gson gson = new GsonBuilder().registerTypeAdapter(Namespace.class, Namespace.getSerializer()).create();

        // When
        String json = gson.toJson(subject);

        // Then
        assertEquals("[\"pricing\",0.0,0.6]", json);
        assertEquals("[\"pricing\",0.0,0.6]", subject.toJson());
        assertEquals("[\"pricing\",0.0,0.6]", subject.toString());
    }

    @Test
    @DisplayName("Deserializes from an id and range JSON triple")
    void deserializesFromIdAndRangeTriple() {
        // Given
        Gson gson = new GsonBuilder().registerTypeAdapter(Namespace.class, Namespace.getDeserializer()).create();

        // When
        Namespace subject = gson.fromJson("[\"pricing\",0.0,0.6]", Namespace.class);

        // Then
        assertEquals("pricing", subject.getId());
        assertEquals(0.0, subject.getRangeStart());
        assertEquals(0.6, subject.getRangeEnd());
    }
}
