package growthbook.sdk.java.multiusermode.configurations;

import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
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
    @DisplayName("Concurrent merges never expose a partial snapshot and never lose another thread's key")
    void concurrencySmokeTest() throws Exception {
        Options options = newOptions();
        options.setGlobalAttributes("{\"id\":\"1\"}");

        int threads = 8;
        int iterationsPerThread = 500;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        for (int t = 0; t < threads; t++) {
            final int id = t;
            final String ownKey = "plan-" + id;
            pool.submit(() -> {
                try {
                    start.await();
                    for (int i = 0; i < iterationsPerThread; i++) {
                        if (i % 2 == 0) {
                            options.updateGlobalAttributes("{\"" + ownKey + "\":\"v" + i + "\"}");
                        } else {
                            // Readers must always see a fully-built snapshot that still has "id"
                            // and this thread's own key once written.
                            JsonObject snapshot = options.getGlobalAttributes();
                            assertNotNull(snapshot);
                            assertEquals("1", snapshot.get("id").getAsString());
                            if (i > 0) {
                                assertTrue(snapshot.has(ownKey), ownKey);
                            }
                        }
                    }
                } catch (Throwable e) {
                    failure.compareAndSet(null, e);
                }
            });
        }

        start.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30, TimeUnit.SECONDS), "threads did not finish in time");
        assertNull(failure.get(), () -> "concurrent access failed: " + failure.get());

        // "id" must survive, and every thread's distinct key must be present: proof that concurrent
        // merges did not lose updates (which a non-atomic read-modify-write would).
        JsonObject finalState = options.getGlobalAttributes();
        assertEquals("1", finalState.get("id").getAsString());
        for (int t = 0; t < threads; t++) {
            assertTrue(finalState.has("plan-" + t), "plan-" + t);
        }
    }
}
