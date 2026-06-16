package growthbook.sdk.java.remoteeval;

import com.google.common.base.Ticker;
import com.google.gson.JsonObject;
import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.model.RequestBodyForRemoteEval;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.fail;

class RemoteEvalCacheTest {

    private static final String KEY = "host::key||{}";

    @Test
    @DisplayName("Serves the cached response while still within the stale window")
    void servesCachedResponseWithinStaleWindow() throws Exception {
        CountingService service = new CountingService();
        FakeTicker ticker = new FakeTicker();
        RemoteEvalCache cache = new RemoteEvalCache(service, 10, Duration.ofSeconds(30), Duration.ofSeconds(300), ticker);

        cache.get(KEY, payload());
        ticker.advance(Duration.ofSeconds(5));
        cache.get(KEY, payload());

        assertEquals(1, service.callCount());
    }

    @Test
    @DisplayName("Refetches once the hard cache TTL has passed")
    void hardExpiryRefetchesPastCacheTtl() throws Exception {
        CountingService service = new CountingService();
        FakeTicker ticker = new FakeTicker();
        RemoteEvalCache cache = new RemoteEvalCache(service, 10, null, Duration.ofSeconds(30), ticker);

        cache.get(KEY, payload());
        ticker.advance(Duration.ofSeconds(31));
        cache.get(KEY, payload());

        assertEquals(2, service.callCount());
    }

    @Test
    @DisplayName("Past the stale TTL, serves the stale value while refreshing in the background")
    void staleWhileRevalidateServesStaleThenRefreshesInBackground() throws Exception {
        CountingService service = new CountingService();
        FakeTicker ticker = new FakeTicker();
        RemoteEvalCache cache = new RemoteEvalCache(service, 10, Duration.ofSeconds(30), Duration.ofSeconds(300), ticker);

        RemoteEvalResponse first = cache.get(KEY, payload());
        ticker.advance(Duration.ofSeconds(31));

        CountDownLatch gate = new CountDownLatch(1);
        service.blockFetchesUntil(gate);

        RemoteEvalResponse stale = cache.get(KEY, payload());
        assertSame(first, stale);
        assertEquals(1, service.callCount());

        gate.countDown();
        awaitCallCount(service, 2);

        RemoteEvalResponse refreshed = awaitRefreshedValue(cache, first);
        assertSame(service.lastResponse(), refreshed);
        assertEquals(2, service.callCount());

        cache.shutdown();
    }

    @Test
    @DisplayName("A zero maximum size disables retention and refetches on every read")
    void zeroSizeDisablesRetentionAndRefetchesEveryRead() throws Exception {
        CountingService service = new CountingService();
        RemoteEvalCache cache = new RemoteEvalCache(service, 0, null, null, new FakeTicker());

        cache.get(KEY, payload());
        cache.get(KEY, payload());

        assertEquals(2, service.callCount());
    }

    @Test
    @DisplayName("A failed load is not cached, so the next read retries and then caches the success")
    void failedLoadIsNotCached() throws Exception {
        CountingService service = new CountingService();
        service.failNextFetches(1);
        RemoteEvalCache cache = new RemoteEvalCache(service, 10, null, Duration.ofSeconds(300), new FakeTicker());

        assertThrows(FeatureFetchException.class, () -> cache.get(KEY, payload()));
        RemoteEvalResponse ok = cache.get(KEY, payload());
        assertSame(service.lastResponse(), ok);
        cache.get(KEY, payload());
        assertEquals(2, service.callCount());
    }

    @Test
    @DisplayName("A failed background refresh keeps serving the stale value")
    void failedBackgroundRefreshKeepsStaleValue() throws Exception {
        CountingService service = new CountingService();
        FakeTicker ticker = new FakeTicker();
        RemoteEvalCache cache = new RemoteEvalCache(service, 10, Duration.ofSeconds(30), Duration.ofSeconds(300), ticker);

        RemoteEvalResponse first = cache.get(KEY, payload());
        service.failNextFetches(1);
        ticker.advance(Duration.ofSeconds(31));

        assertSame(first, cache.get(KEY, payload()));
        awaitCallCount(service, 2);

        CountDownLatch retryGate = new CountDownLatch(1);
        service.blockFetchesUntil(retryGate);
        assertSame(first, cache.get(KEY, payload()));
        retryGate.countDown();

        cache.shutdown();
    }

    @Test
    @DisplayName("A closed cache rejects reads")
    void closedCacheRejectsReads() throws Exception {
        CountingService service = new CountingService();
        RemoteEvalCache cache = new RemoteEvalCache(service, 10, Duration.ofSeconds(30), Duration.ofSeconds(300), new FakeTicker());

        cache.get(KEY, payload());
        cache.shutdown();

        assertThrows(FeatureFetchException.class, () -> cache.get(KEY, payload()));
    }

    @Test
    @DisplayName("An in-flight load that completes after shutdown is never served from the cache")
    void inFlightLoadAfterShutdownIsNotServedFromCache() throws Exception {
        CountingService service = new CountingService();
        RemoteEvalCache cache = new RemoteEvalCache(service, 10, Duration.ofSeconds(30), Duration.ofSeconds(300), new FakeTicker());

        CountDownLatch gate = new CountDownLatch(1);
        service.blockFetchesUntil(gate);

        Thread loader = new Thread(() -> {
            try {
                cache.get(KEY, payload());
            } catch (FeatureFetchException ignored) {
            }
        });
        loader.start();
        Thread.sleep(50);

        cache.shutdown();
        gate.countDown();
        loader.join(2000);

        assertThrows(FeatureFetchException.class, () -> cache.get(KEY, payload()));
    }

    private static RequestBodyForRemoteEval payload() {
        return new RequestBodyForRemoteEval(new JsonObject(), Collections.emptyList(), Collections.emptyMap(), "");
    }

    private static void awaitCallCount(CountingService service, int expected) throws InterruptedException {
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline) {
            if (service.callCount() >= expected) {
                return;
            }
            Thread.sleep(10);
        }
        fail("Expected background refresh to reach " + expected + " calls, but saw " + service.callCount());
    }

    private static RemoteEvalResponse awaitRefreshedValue(RemoteEvalCache cache, RemoteEvalResponse stale) throws Exception {
        long deadline = System.currentTimeMillis() + 2000;
        while (System.currentTimeMillis() < deadline) {
            RemoteEvalResponse current = cache.get(KEY, payload());
            if (current != stale) {
                return current;
            }
            Thread.sleep(10);
        }
        return fail("Expected the cache entry to be replaced by the background refresh");
    }

    /** Counts fetch attempts and returns a distinct response each time; an optional gate can hold a fetch open. */
    private static final class CountingService extends RemoteEvalService {
        private final AtomicInteger calls = new AtomicInteger();
        private final AtomicInteger failuresRemaining = new AtomicInteger();
        private volatile RemoteEvalResponse lastResponse;
        private volatile CountDownLatch gate;

        CountingService() {
            super("http://localhost", "key");
        }

        @Override
        public RemoteEvalResponse fetch(RequestBodyForRemoteEval requestBodyForRemoteEval) throws FeatureFetchException {
            CountDownLatch currentGate = this.gate;
            if (currentGate != null) {
                try {
                    currentGate.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
            calls.incrementAndGet();
            if (failuresRemaining.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                throw new FeatureFetchException(
                        FeatureFetchException.FeatureFetchErrorCode.NO_RESPONSE_ERROR, "remote eval failed");
            }
            this.lastResponse = new RemoteEvalResponse(Collections.emptyMap(), new JsonObject());
            return this.lastResponse;
        }

        void blockFetchesUntil(CountDownLatch gate) {
            this.gate = gate;
        }

        void failNextFetches(int count) {
            this.failuresRemaining.set(count);
        }

        int callCount() {
            return calls.get();
        }

        RemoteEvalResponse lastResponse() {
            return lastResponse;
        }
    }

    private static final class FakeTicker extends Ticker {
        private final AtomicLong nanos = new AtomicLong();

        @Override
        public long read() {
            return nanos.get();
        }

        void advance(Duration duration) {
            nanos.addAndGet(duration.toNanos());
        }
    }
}
