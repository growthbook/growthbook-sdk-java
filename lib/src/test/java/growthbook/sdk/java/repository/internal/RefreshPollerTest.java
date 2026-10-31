package growthbook.sdk.java.repository.internal;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Clock;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefreshPollerTest {

    @Test
    @DisplayName("Keeps polling when a poll throws")
    void keepsPollingWhenPollThrows() {
        // Given
        AtomicInteger polls = new AtomicInteger();
        RefreshPoller poller = new RefreshPoller(() -> {
            polls.incrementAndGet();
            throw new IllegalStateException("network down");
        }, Clock.systemUTC());

        // When
        poller.pollOnce();
        poller.pollOnce();

        // Then
        assertEquals(2, polls.get());
    }

    @Test
    @Timeout(10)
    @DisplayName("Skips a poll that would overlap one still running")
    void skipsOverlappingPoll() throws InterruptedException {
        // Given
        CountDownLatch insidePoll = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger polls = new AtomicInteger();
        RefreshPoller poller = new RefreshPoller(() -> {
            polls.incrementAndGet();
            insidePoll.countDown();
            release.await();
        }, Clock.systemUTC());
        Thread firstPoll = new Thread(poller::pollOnce);
        firstPoll.start();
        assertTrue(insidePoll.await(5, TimeUnit.SECONDS));

        // When
        poller.pollOnce();
        release.countDown();
        firstPoll.join();

        // Then
        assertEquals(1, polls.get());
    }

    @Test
    @DisplayName("Polls on a named daemon thread so it never keeps the JVM alive")
    void pollsOnNamedDaemonThread() {
        // Given
        RefreshPoller poller = new RefreshPoller(() -> {
        }, Clock.systemUTC());

        // When
        poller.start(3600);
        Thread pollThread = null;
        for (int i = 0; i < 1_000 && pollThread == null; i++) {
            for (Thread thread : Thread.getAllStackTraces().keySet()) {
                if ("growthbook-feature-poll".equals(thread.getName())) {
                    pollThread = thread;
                }
            }
        }
        poller.stop();

        // Then
        assertNotNull(pollThread);
        assertTrue(pollThread.isDaemon());
    }

    @Test
    @DisplayName("Stops without error when it never started")
    void stopsWithoutStarting() {
        // Given
        RefreshPoller poller = new RefreshPoller(() -> {
        }, Clock.systemUTC());

        // When / Then
        assertDoesNotThrow(poller::stop);
    }
}
