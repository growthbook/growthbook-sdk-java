package growthbook.sdk.java.stickyBucketing;

import growthbook.sdk.java.model.StickyAssignmentsDocument;

/**
 * Seam through which evaluation hands newly assigned sticky bucket documents to
 * the owning client for persistence. Whether the write blocks, is offloaded, or
 * is fire-and-forget is the writer's choice — the evaluator stays synchronous
 * and ignorant of I/O (the same seam as the Python SDK's
 * {@code EvaluationContext.save_sticky_bucket_doc}).
 *
 * <p>When no writer is set on the evaluation context, the evaluator falls back
 * to calling the configured {@link StickyBucketService#saveAssignments} directly
 * — the legacy single-user path, unchanged.
 */
@FunctionalInterface
public interface StickyBucketDocWriter {
    /**
     * Persist a newly assigned document. Must not throw into evaluation.
     *
     * @param doc the document to persist
     */
    void write(StickyAssignmentsDocument doc);
}
