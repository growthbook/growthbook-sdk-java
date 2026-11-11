package growthbook.sdk.java.stickyBucketing;

import growthbook.sdk.java.model.StickyAssignmentsDocument;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

/**
 * Presents a blocking {@link StickyBucketService} as an
 * {@link AsyncStickyBucketService} by running each call on the supplied
 * executor. This is how the multi-user client keeps legacy synchronous
 * services working without blocking the evaluating thread; it is public so
 * applications composing their own pipelines can reuse it.
 *
 * <p>{@link #getAllAssignments} is offloaded as ONE task wrapping the
 * delegate's own {@code getAllAssignments}, preserving batched implementations
 * (e.g. a single Redis {@code MGET}) instead of exploding them into N calls.
 */
public final class SyncOffloadStickyBucketAdapter implements AsyncStickyBucketService {

    private final StickyBucketService delegate;
    private final Executor executor;

    /**
     * @param delegate the blocking service to adapt
     * @param executor where the delegate's blocking calls run; sized for
     *                 blocking I/O (never a CPU-sized pool)
     */
    public SyncOffloadStickyBucketAdapter(StickyBucketService delegate, Executor executor) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    @Override
    public CompletionStage<StickyAssignmentsDocument> getAssignments(String attributeName, String attributeValue) {
        return CompletableFuture.supplyAsync(() -> delegate.getAssignments(attributeName, attributeValue), executor);
    }

    @Override
    public CompletionStage<Void> saveAssignments(StickyAssignmentsDocument doc) {
        return CompletableFuture.runAsync(() -> delegate.saveAssignments(doc), executor);
    }

    @Override
    public CompletionStage<Map<String, StickyAssignmentsDocument>> getAllAssignments(Map<String, String> attributes) {
        return CompletableFuture.supplyAsync(() -> delegate.getAllAssignments(attributes), executor);
    }
}
