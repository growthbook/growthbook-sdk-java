package growthbook.sdk.java.multiusermode.configurations;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptionsGlobalAttributesTest {

    private static Options newOptions() {
        return Options.builder().build();
    }

    @Test
    @DisplayName("updateGlobalAttributes merges new keys into existing attributes")
    void updateMergesNewKeys() {
        Options options = newOptions();
        options.setGlobalAttributes("{\"id\":\"1\"}");

        options.updateGlobalAttributes("{\"plan\":\"pro\"}");

        JsonObject attributes = options.getGlobalAttributes();
        assertEquals("1", attributes.get("id").getAsString());
        assertEquals("pro", attributes.get("plan").getAsString());
    }

    @Test
    @DisplayName("updateGlobalAttributes overwrites existing keys and preserves the rest")
    void updateOverwritesAndPreserves() {
        Options options = newOptions();
        options.setGlobalAttributes("{\"id\":\"1\",\"plan\":\"free\"}");

        options.updateGlobalAttributes("{\"plan\":\"pro\"}");

        JsonObject attributes = options.getGlobalAttributes();
        assertEquals("1", attributes.get("id").getAsString());
        assertEquals("pro", attributes.get("plan").getAsString());
    }

    @Test
    @DisplayName("A JSON null value removes the key")
    void updateRemovesKeyOnJsonNull() {
        Options options = newOptions();
        options.setGlobalAttributes("{\"id\":\"1\",\"plan\":\"pro\"}");

        options.updateGlobalAttributes("{\"plan\":null}");

        JsonObject attributes = options.getGlobalAttributes();
        assertEquals("1", attributes.get("id").getAsString());
        assertFalse(attributes.has("plan"), "null value should remove the key");
    }

    @Test
    @DisplayName("The JsonObject overload merges with the same semantics")
    void updateJsonObjectOverload() {
        Options options = newOptions();
        options.setGlobalAttributes("{\"id\":\"1\"}");

        JsonObject incoming = new JsonObject();
        incoming.add("plan", new JsonPrimitive("pro"));
        incoming.add("id", JsonNull.INSTANCE);
        options.updateGlobalAttributes(incoming);

        JsonObject attributes = options.getGlobalAttributes();
        assertFalse(attributes.has("id"), "null value should remove the key");
        assertEquals("pro", attributes.get("plan").getAsString());
    }

    @Test
    @DisplayName("null and malformed JSON are a no-op and preserve existing attributes")
    void updateWithNullOrMalformedIsNoop() {
        Options options = newOptions();
        options.setGlobalAttributes("{\"id\":\"1\"}");

        options.updateGlobalAttributes((String) null);
        options.updateGlobalAttributes("not json");
        options.updateGlobalAttributes((JsonObject) null);

        JsonObject attributes = options.getGlobalAttributes();
        assertEquals("1", attributes.get("id").getAsString());
        assertEquals(1, attributes.size());
    }

    @Test
    @DisplayName("update onto empty attributes behaves like set")
    void updateOntoEmpty() {
        Options options = newOptions();

        options.updateGlobalAttributes("{\"id\":\"1\"}");

        assertEquals("1", options.getGlobalAttributes().get("id").getAsString());
    }

    @Test
    @DisplayName("setGlobalAttributes keeps its replace behavior")
    void setReplacesEverything() {
        Options options = newOptions();
        options.setGlobalAttributes("{\"id\":\"1\",\"plan\":\"pro\"}");

        options.setGlobalAttributes("{\"country\":\"UA\"}");

        JsonObject attributes = options.getGlobalAttributes();
        assertFalse(attributes.has("id"));
        assertFalse(attributes.has("plan"));
        assertEquals("UA", attributes.get("country").getAsString());
    }

    @Test
    @DisplayName("attributesJson string stays consistent with the parsed object")
    void attributesJsonStaysConsistent() {
        Options options = newOptions();
        options.setGlobalAttributes("{\"id\":\"1\"}");
        options.updateGlobalAttributes("{\"plan\":\"pro\"}");

        String json = options.getAttributesJson();
        assertNotNull(json);
        assertTrue(json.contains("\"id\""));
        assertTrue(json.contains("\"plan\""));
    }

    @Test
    @DisplayName("Mutating the getter's result does not change the stored attributes")
    void getterReturnsIndependentCopy() {
        Options options = newOptions();
        options.setGlobalAttributes("{\"id\":\"1\"}");

        options.getGlobalAttributes().addProperty("viaGetter", "x");

        assertEquals("{\"id\":\"1\"}", options.getAttributesJson());
        assertFalse(options.getGlobalAttributes().has("viaGetter"));
    }

    @Test
    @DisplayName("The JsonObject passed to the builder is copied on build")
    void builderCopiesAttributes() {
        JsonObject original = new JsonObject();
        original.addProperty("id", "1");
        Options options = Options.builder().globalAttributes(original).build();

        original.addProperty("viaOriginal", "y");

        assertEquals("{\"id\":\"1\"}", options.getAttributesJson());
        assertFalse(options.getGlobalAttributes().has("viaOriginal"));
    }

    @Test
    @DisplayName("Unset attributes read as an empty object, never null")
    void unsetAttributesAreEmpty() {
        Options options = newOptions();

        assertEquals("{}", options.getAttributesJson());
        assertEquals(0, options.getGlobalAttributes().size());
    }

    @Test
    @DisplayName("Concurrent merges never lose an update and never expose a partial snapshot")
    void concurrentMergesLoseNoUpdates() throws Exception {
        Options options = newOptions();
        options.setGlobalAttributes("{\"id\":\"1\"}");

        int threads = 8;
        int writesPerThread = 200;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CyclicBarrier start = new CyclicBarrier(threads);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        for (int t = 0; t < threads; t++) {
            final int thread = t;
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < writesPerThread; i++) {
                        // Every write uses a key that is never written again, so a lost update
                        // (a non-atomic read-modify-write) is permanent and caught below.
                        String key = thread + "-" + i;
                        options.updateGlobalAttributes("{\"" + key + "\":" + i + "}");

                        JsonObject snapshot = options.getGlobalAttributes();
                        assertEquals("1", snapshot.get("id").getAsString());
                        assertTrue(snapshot.has(key), () -> "own write not visible: " + key);
                    }
                } catch (Throwable e) {
                    failure.compareAndSet(null, e);
                }
            });
        }

        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS), "threads did not finish in time");
        assertNull(failure.get(), () -> "concurrent access failed: " + failure.get());

        JsonObject finalState = options.getGlobalAttributes();
        assertEquals(1 + threads * writesPerThread, finalState.size());
        assertEquals("1", finalState.get("id").getAsString());
        for (int t = 0; t < threads; t++) {
            for (int i = 0; i < writesPerThread; i++) {
                String key = t + "-" + i;
                assertTrue(finalState.has(key), () -> "lost update: " + key);
                assertEquals(i, finalState.get(key).getAsInt(), key);
            }
        }
        assertEquals(finalState.toString(), options.getAttributesJson());
    }
}
