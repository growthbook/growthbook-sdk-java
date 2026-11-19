package growthbook.sdk.java.multiusermode;

import growthbook.sdk.java.model.StickyAssignmentsDocument;
import growthbook.sdk.java.stickyBucketing.AsyncStickyBucketService;
import growthbook.sdk.java.stickyBucketing.StickyBucketService;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

/**
 * Presents a blocking {@link StickyBucketService} as an {@link AsyncStickyBucketService} whose
 * calls run on the calling thread and return an already-completed stage. Used by the synchronous
 * evaluation API, where the caller is a request thread that is expected to block on store I/O;
 * the asynchronous entry points use {@code SyncOffloadStickyBucketAdapter} instead.
 *
 * <p>A delegate failure is reported through the returned stage, never thrown. Thread-safe when the
 * delegate is.
 */
final class CallerThreadStickyBucketAdapter implements AsyncStickyBucketService {

    private final StickyBucketService delegate;

    CallerThreadStickyBucketAdapter(StickyBucketService delegate) {
        this.delegate = delegate;
    }

    @Override
    public CompletionStage<StickyAssignmentsDocument> getAssignments(String attributeName, String attributeValue) {
        return completed(() -> delegate.getAssignments(attributeName, attributeValue));
    }

    @Override
    public CompletionStage<Void> saveAssignments(StickyAssignmentsDocument doc) {
        return completed(() -> {
            delegate.saveAssignments(doc);
            return null;
        });
    }

    @Override
    public CompletionStage<Map<String, StickyAssignmentsDocument>> getAllAssignments(Map<String, String> attributes) {
        return completed(() -> delegate.getAllAssignments(attributes));
    }

    private static <T> CompletionStage<T> completed(Supplier<T> call) {
        CompletableFuture<T> future = new CompletableFuture<>();
        try {
            future.complete(call.get());
        } catch (RuntimeException e) {
            future.completeExceptionally(e);
        }
        return future;
    }
}
