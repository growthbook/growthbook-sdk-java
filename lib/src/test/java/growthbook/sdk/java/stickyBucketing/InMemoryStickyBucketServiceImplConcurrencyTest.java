package growthbook.sdk.java.stickyBucketing;

import growthbook.sdk.java.model.StickyAssignmentsDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The no-arg constructor must be safe under the multi-user client's concurrent
 * evaluations: parallel saves and reads against one instance. (A plain HashMap
 * here is a structural data race, not just a lost update.)
 */
class InMemoryStickyBucketServiceImplConcurrencyTest {

    private static final int THREADS = 8;
    private static final int DOCS_PER_THREAD = 500;

    @Test
    @Timeout(30)
    void concurrentSavesAndReadsAreSafeWithDefaultConstructor() throws Exception {
        InMemoryStickyBucketServiceImpl service = new InMemoryStickyBucketServiceImpl();

        List<Throwable> failures = new CopyOnWriteArrayList<>();
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(THREADS);
        AtomicBoolean stop = new AtomicBoolean(false);

        for (int t = 0; t < THREADS; t++) {
            final int threadId = t;
            Thread writer = new Thread(() -> {
                try {
                    start.await();
                    for (int i = 0; i < DOCS_PER_THREAD; i++) {
                        Map<String, String> assignments = new HashMap<>();
                        assignments.put("exp__0", "v" + i);
                        service.saveAssignments(new StickyAssignmentsDocument(
                                "id", threadId + "-" + i, assignments));
                    }
                } catch (Throwable e) {
                    failures.add(e);
                } finally {
                    done.countDown();
                }
            });
            writer.setDaemon(true);
            writer.start();
        }

        Thread reader = new Thread(() -> {
            try {
                start.await();
                while (!stop.get()) {
                    service.getAllAssignments(Collections.singletonMap("id", "0-0"));
                }
            } catch (Throwable e) {
                failures.add(e);
            }
        });
        reader.setDaemon(true);
        reader.start();

        start.countDown();
        assertTrue(done.await(20, TimeUnit.SECONDS), "writers did not finish");
        stop.set(true);
        reader.join(TimeUnit.SECONDS.toMillis(5));

        assertEquals(0, failures.size(), "concurrent save/read threw: " + failures);
        for (int t = 0; t < THREADS; t++) {
            for (int i = 0; i < DOCS_PER_THREAD; i++) {
                assertNotNull(service.getAssignments("id", t + "-" + i),
                        "lost document id||" + t + "-" + i);
            }
        }
    }
}
