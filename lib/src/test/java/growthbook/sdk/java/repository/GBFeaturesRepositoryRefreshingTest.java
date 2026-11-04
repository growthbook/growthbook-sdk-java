package growthbook.sdk.java.repository;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import growthbook.sdk.java.exception.FeatureFetchException;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import okhttp3.Call;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Verifies {@link FeatureRefreshStrategy#STALE_WHILE_REVALIDATE} background polling by driving an
 * injected {@link ManualScheduledExecutorService} instead of the real timer, so the number of
 * network fetches is a deterministic function of how many polling intervals are advanced — no
 * {@code Thread.sleep} and no wall-clock dependence.
 */
class GBFeaturesRepositoryRefreshingTest {

    private static final int TTL_SECONDS = 5;
    private static final String ENCRYPTION_KEY = "o0maZL/O7AphxcbRvaJIzw==";
    private static final String FAKE_RESPONSE_JSON = "{\n" +
            "  \"status\": 200,\n" +
            "  \"features\": {},\n" +
            "  \"dateUpdated\": \"2023-01-25T00:51:26.772Z\",\n" +
            "  \"encryptedFeatures\": \"jfLnSxjChWcbyHaIF30RNw==.iz8DywkSk4+WhNqnIwvr/PdvAwaRNjN3RE30JeOezGAQ/zZ2yoVyVo4w0nLHYqOje5MbhmL0ssvlH0ojk/BxqdSzXD4Wzo3DXfKV81Nzi1aSdiCMnVAIYEzjPl1IKZC3fl88YDBNV3F6YnR9Lemy9yzT03cvMZ0NZ9t5LZO2xS2MhpPYNcAfAlfxXhBGXj6UFDoNKGAtGKdc/zmJsUVQGLtHmqLspVynnJlPPo9nXG+87bt6SjSfQfySUgHm28hb4VmDhVmCx0N37buolVr3pzjZ1QK+tyMKIV7x4/Gu06k8sm0eU4HjG5DFsPgTR7qDu/N5Nk5UTRpG7aSXTUErxhHSJ7MQaxH/Dp/71zVEicaJ0qZE3oPRnU187QVBfdVLLRbqq2QU7Yu0GyJ1jjuf6TA+759OgifHdm17SX43L94Qe62CMU7JQyAqt7h7XmTTQBG664HYwgHJ0ju/9jySC4KUlRxNsixH1tJfznnEXqxgSozn4J61UprTqcmlxLZ1hZPCcRew3mm9DMAG9+YEiL8MhaIwsw8oVq9GirN1S8G3m/6UxQHxZVraPvMRXpGt5VpzEDJ0Po+phrIAhPuIbNpgb08b6Ej4Xh9XXeOLtIcpuj+gNpc4pR4tqF2IOwET\"\n" +
            "}";

    @Test
    @DisplayName("Verify: a polling tick after the TTL elapses triggers exactly one background refresh")
    void pollingTickAfterTtlTriggersOneRefresh() throws IOException, FeatureFetchException {
        // Given an initialized SWR repository whose background polling is driven manually
        OkHttpClient client = mockHttpClient(FAKE_RESPONSE_JSON);
        ManualScheduledExecutorService scheduler = new ManualScheduledExecutorService();
        GBFeaturesRepository subject = newRepository(client, scheduler);
        subject.initialize();

        // When one polling interval elapses
        scheduler.tick();

        // Then the initial fetch and exactly one background refresh have been issued
        verify(client, times(2)).newCall(any());
    }

    @Test
    @DisplayName("Verify: no polling tick within the TTL performs no background refresh")
    void noPollingTickWithinTtlPerformsNoRefresh() throws IOException, FeatureFetchException {
        // Given an initialized SWR repository whose background polling is driven manually
        OkHttpClient client = mockHttpClient(FAKE_RESPONSE_JSON);
        ManualScheduledExecutorService scheduler = new ManualScheduledExecutorService();
        GBFeaturesRepository subject = newRepository(client, scheduler);
        subject.initialize();

        // When no polling interval elapses

        // Then only the initial fetch has been issued
        verify(client, times(1)).newCall(any());
    }

    @Test
    @DisplayName("Verify: each elapsed TTL interval triggers its own background refresh")
    void eachElapsedTtlIntervalTriggersItsOwnRefresh() throws IOException, FeatureFetchException {
        // Given an initialized SWR repository whose background polling is driven manually
        OkHttpClient client = mockHttpClient(FAKE_RESPONSE_JSON);
        ManualScheduledExecutorService scheduler = new ManualScheduledExecutorService();
        GBFeaturesRepository subject = newRepository(client, scheduler);
        subject.initialize();

        // When three polling intervals elapse
        scheduler.tick();
        scheduler.tick();
        scheduler.tick();

        // Then the initial fetch and one background refresh per interval have been issued
        verify(client, times(4)).newCall(any());
    }

    private static GBFeaturesRepository newRepository(OkHttpClient client, ScheduledExecutorService scheduler) {
        GBFeaturesRepository subject = new GBFeaturesRepository(
                "http://localhost:80",
                "sdk-abc123",
                ENCRYPTION_KEY,
                FeatureRefreshStrategy.STALE_WHILE_REVALIDATE,
                TTL_SECONDS,
                client,
                null,
                null,
                null,
                null
        );
        subject.setPollSchedulerFactory(() -> scheduler);
        return subject;
    }

    private static OkHttpClient mockHttpClient(final String serializedBody) throws IOException {
        OkHttpClient okHttpClient = mock(OkHttpClient.class);

        Call remoteCall = mock(Call.class);

        Response response = new Response.Builder()
                .request(new Request.Builder().url("http://url.com").build())
                .protocol(Protocol.HTTP_1_1)
                .code(200).message("").body(
                        ResponseBody.create(
                                serializedBody,
                                MediaType.parse("application/json")
                        ))
                .build();

        when(remoteCall.execute()).thenReturn(response);
        when(okHttpClient.newCall(any())).thenReturn(remoteCall);

        return okHttpClient;
    }

    /**
     * A {@link ScheduledExecutorService} that never runs anything on its own: it captures the task
     * submitted via {@link #scheduleWithFixedDelay} and runs it once per explicit {@link #tick()},
     * turning "one polling interval elapsed" into a deterministic call. Every other operation is
     * unsupported because the repository only ever uses fixed-delay scheduling.
     */
    private static final class ManualScheduledExecutorService implements ScheduledExecutorService {
        private Runnable command;
        private volatile boolean shutdown;

        void tick() {
            if (this.command != null) {
                this.command.run();
            }
        }

        @Override
        public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
            this.command = command;
            return null;
        }

        @Override
        public void shutdown() {
            this.shutdown = true;
        }

        @Override
        public List<Runnable> shutdownNow() {
            this.shutdown = true;
            return Collections.emptyList();
        }

        @Override
        public boolean isShutdown() {
            return this.shutdown;
        }

        @Override
        public boolean isTerminated() {
            return this.shutdown;
        }

        @Override
        public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean awaitTermination(long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> Future<T> submit(Callable<T> task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> Future<T> submit(Runnable task, T result) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Future<?> submit(Runnable task) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> List<Future<T>> invokeAll(java.util.Collection<? extends Callable<T>> tasks) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> List<Future<T>> invokeAll(java.util.Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T invokeAny(java.util.Collection<? extends Callable<T>> tasks) {
            throw new UnsupportedOperationException();
        }

        @Override
        public <T> T invokeAny(java.util.Collection<? extends Callable<T>> tasks, long timeout, TimeUnit unit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void execute(Runnable command) {
            throw new UnsupportedOperationException();
        }
    }
}
