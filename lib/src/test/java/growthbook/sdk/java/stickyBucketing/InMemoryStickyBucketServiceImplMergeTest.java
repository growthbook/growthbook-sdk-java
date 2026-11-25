package growthbook.sdk.java.stickyBucketing;

import growthbook.sdk.java.model.StickyAssignmentsDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryStickyBucketServiceImplMergeTest {

    @Test
    @DisplayName("Adds the assignments of a saved document to those already stored for the user")
    void mergesSavedAssignments() {
        // Given
        InMemoryStickyBucketServiceImpl service = new InMemoryStickyBucketServiceImpl();
        service.saveAssignments(document(Collections.singletonMap("exp-1__0", "control")));

        // When
        service.saveAssignments(document(Collections.singletonMap("exp-2__0", "treatment")));

        // Then
        Map<String, String> expected = new HashMap<>();
        expected.put("exp-1__0", "control");
        expected.put("exp-2__0", "treatment");
        assertEquals(expected, service.getAssignments("id", "user-1").getAssignments());
    }

    @Test
    @DisplayName("Lets the saved document win when it reassigns an experiment")
    void savedDocumentWinsOnConflict() {
        // Given
        InMemoryStickyBucketServiceImpl service = new InMemoryStickyBucketServiceImpl();
        service.saveAssignments(document(Collections.singletonMap("exp-1__0", "control")));

        // When
        service.saveAssignments(document(Collections.singletonMap("exp-1__0", "treatment")));

        // Then
        assertEquals("treatment", service.getAssignments("id", "user-1").getAssignments().get("exp-1__0"));
    }

    @Test
    @Timeout(30)
    @DisplayName("Keeps every assignment when one user's documents are saved concurrently")
    void keepsAssignmentsSavedConcurrently() throws Exception {
        // Given
        int writers = 32;
        InMemoryStickyBucketServiceImpl service = new InMemoryStickyBucketServiceImpl();
        ExecutorService executor = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        Future<?>[] saves = new Future<?>[writers];
        for (int i = 0; i < writers; i++) {
            String experimentKey = "exp-" + i + "__0";
            saves[i] = executor.submit(() -> {
                start.await();
                service.saveAssignments(document(Collections.singletonMap(experimentKey, "variation")));
                return null;
            });
        }

        // When
        start.countDown();
        for (Future<?> save : saves) {
            save.get(10, TimeUnit.SECONDS);
        }
        executor.shutdown();

        // Then
        assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        assertEquals(writers, service.getAssignments("id", "user-1").getAssignments().size());
    }

    private static StickyAssignmentsDocument document(Map<String, String> assignments) {
        return new StickyAssignmentsDocument("id", "user-1", new HashMap<>(assignments));
    }
}
