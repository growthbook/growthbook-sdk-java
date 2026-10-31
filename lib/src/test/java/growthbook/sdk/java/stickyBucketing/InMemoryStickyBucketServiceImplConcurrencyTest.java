package growthbook.sdk.java.stickyBucketing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import growthbook.sdk.java.model.StickyAssignmentsDocument;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

class InMemoryStickyBucketServiceImplConcurrencyTest {

    private static final int THREADS = 8;
    private static final int DOCS_PER_THREAD = 500;

    @Test
    @Timeout(30)
    @DisplayName("The default service keeps every document saved by parallel writers while a reader queries it")
    void defaultServiceKeepsDocumentsSavedByParallelWriters() throws Exception {
        // Given
        InMemoryStickyBucketServiceImpl service = new InMemoryStickyBucketServiceImpl();
        List<Throwable> failures = new CopyOnWriteArrayList<>();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch writersDone = new CountDownLatch(THREADS);
        AtomicBoolean stopReading = new AtomicBoolean(false);
        for (int t = 0; t < THREADS; t++) {
            startDaemon(writer(service, t, start, writersDone, failures));
        }
        Thread reader = startDaemon(reader(service, start, stopReading, failures));

        // When
        start.countDown();
        boolean writersFinished = writersDone.await(20, TimeUnit.SECONDS);
        stopReading.set(true);
        reader.join(TimeUnit.SECONDS.toMillis(5));

        // Then
        assertTrue(writersFinished);
        assertEquals(Collections.<Throwable>emptyList(), failures);
        List<String> lost = new ArrayList<>();
        for (int t = 0; t < THREADS; t++) {
            for (int i = 0; i < DOCS_PER_THREAD; i++) {
                StickyAssignmentsDocument doc = service.getAssignments("id", t + "-" + i);
                if (doc == null || !Collections.singletonMap("exp__0", "v" + i).equals(doc.getAssignments())) {
                    lost.add("id||" + t + "-" + i);
                }
            }
        }
        assertEquals(Collections.<String>emptyList(), lost);
    }

    private static Runnable writer(InMemoryStickyBucketServiceImpl service, int threadId, CountDownLatch start,
                                   CountDownLatch done, List<Throwable> failures) {
        return () -> {
            try {
                start.await();
                for (int i = 0; i < DOCS_PER_THREAD; i++) {
                    Map<String, String> assignments = new HashMap<>();
                    assignments.put("exp__0", "v" + i);
                    service.saveAssignments(new StickyAssignmentsDocument("id", threadId + "-" + i, assignments));
                }
            } catch (Throwable e) {
                failures.add(e);
            } finally {
                done.countDown();
            }
        };
    }

    private static Runnable reader(InMemoryStickyBucketServiceImpl service, CountDownLatch start,
                                   AtomicBoolean stop, List<Throwable> failures) {
        return () -> {
            try {
                start.await();
                while (!stop.get()) {
                    service.getAllAssignments(Collections.singletonMap("id", "0-0"));
                }
            } catch (Throwable e) {
                failures.add(e);
            }
        };
    }

    private static Thread startDaemon(Runnable task) {
        Thread thread = new Thread(task);
        thread.setDaemon(true);
        thread.start();
        return thread;
    }
}
