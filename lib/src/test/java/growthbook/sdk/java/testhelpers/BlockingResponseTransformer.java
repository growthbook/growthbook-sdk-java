package growthbook.sdk.java.testhelpers;

import com.github.tomakehurst.wiremock.common.FileSource;
import com.github.tomakehurst.wiremock.extension.Parameters;
import com.github.tomakehurst.wiremock.extension.ResponseDefinitionTransformer;
import com.github.tomakehurst.wiremock.http.Request;
import com.github.tomakehurst.wiremock.http.ResponseDefinition;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * A WireMock extension that holds every response until {@link #release()} is called, so a test
 * can observe a request while it is in flight. Thread-safe.
 */
public final class BlockingResponseTransformer extends ResponseDefinitionTransformer {

    private static final long MAX_HOLD_SECONDS = 10;

    private final CountDownLatch started = new CountDownLatch(1);
    private final CountDownLatch released = new CountDownLatch(1);

    @Override
    public ResponseDefinition transform(Request request, ResponseDefinition responseDefinition,
                                        FileSource files, Parameters parameters) {
        started.countDown();
        try {
            released.await(MAX_HOLD_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return responseDefinition;
    }

    @Override
    public String getName() {
        return "blocking-response";
    }

    /**
     * @return whether a request reached the server within the timeout
     */
    public boolean awaitRequest(long timeout, TimeUnit unit) throws InterruptedException {
        return started.await(timeout, unit);
    }

    public void release() {
        released.countDown();
    }
}
