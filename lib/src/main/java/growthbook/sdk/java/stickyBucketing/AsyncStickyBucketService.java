package growthbook.sdk.java.stickyBucketing;

import growthbook.sdk.java.model.StickyAssignmentsDocument;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/**
 * Non-blocking sticky bucket persistence for the multi-user
 * {@link growthbook.sdk.java.multiusermode.GrowthBookClient}: reads and writes
 * never run on the evaluating thread's time. This is the async twin of
 * {@link StickyBucketService}, mirroring the JavaScript SDK's Promise-based
 * service interface and the Python SDK's {@code AbstractAsyncStickyBucketService}.
 *
 * <p>Implementations back this with a network store (Redis, DynamoDB, JDBC, ...)
 * using their client's native async API. Methods return {@link CompletionStage}
 * so natively-async clients (e.g. Lettuce's {@code RedisFuture}, Reactor's
 * {@code Mono#toFuture()}) plug in without adaptation.
 *
 * <p><b>Contract:</b>
 * <ul>
 *   <li>Store keys use {@code attributeName + "||" + attributeValue}
 *       ({@link #getKey}), byte-compatible with documents written by the other
 *       GrowthBook SDKs against the same store.</li>
 *   <li>{@link #getAssignments} completes with {@code null} when no document
 *       exists for the key.</li>
 *   <li>Completion may happen on the store client's own threads (e.g. a Netty
 *       event loop) — the SDK hops off that thread before evaluating, and
 *       implementations should not block inside completion callbacks.</li>
 *   <li>Implementations must be thread-safe; the SDK calls them concurrently.</li>
 * </ul>
 *
 * <p>Existing synchronous {@link StickyBucketService} implementations do not
 * need porting — pass them as {@code Options.stickyBucketService} and the
 * client offloads their blocking calls to its executor (see
 * {@link SyncOffloadStickyBucketAdapter}).
 */
public interface AsyncStickyBucketService {

    /**
     * Look up one sticky bucket document.
     *
     * @param attributeName  the identifier attribute name (e.g. {@code id})
     * @param attributeValue the identifier attribute value
     * @return a stage completing with the document, or {@code null} if absent
     */
    CompletionStage<StickyAssignmentsDocument> getAssignments(String attributeName, String attributeValue);

    /**
     * Persist one sticky bucket document (upsert by
     * {@code attributeName + "||" + attributeValue}).
     *
     * @param doc the document to persist
     * @return a stage completing when the write is durable
     */
    CompletionStage<Void> saveAssignments(StickyAssignmentsDocument doc);

    /**
     * The store key for a document. Shared convention across all GrowthBook SDKs.
     *
     * @param attributeName  the identifier attribute name
     * @param attributeValue the identifier attribute value
     * @return {@code attributeName + "||" + attributeValue}
     */
    default String getKey(String attributeName, String attributeValue) {
        return attributeName + "||" + attributeValue;
    }

    /**
     * Look up the documents for all of a user's identifier attributes.
     *
     * <p>The default implementation fans out one {@link #getAssignments} call per
     * attribute. Override it to batch all lookups into a single round trip
     * (e.g. one Redis {@code MGET}) — this method is called once per sticky
     * bucket fetch, so batching here is the highest-leverage optimization.
     *
     * @param attributes identifier attribute name → value
     * @return a stage completing with found documents keyed by
     *         {@link #getKey}; attributes with no document are omitted
     */
    default CompletionStage<Map<String, StickyAssignmentsDocument>> getAllAssignments(Map<String, String> attributes) {
        List<CompletableFuture<StickyAssignmentsDocument>> lookups = new ArrayList<>(attributes.size());
        for (Map.Entry<String, String> attribute : attributes.entrySet()) {
            lookups.add(getAssignments(attribute.getKey(), attribute.getValue()).toCompletableFuture());
        }
        return CompletableFuture.allOf(lookups.toArray(new CompletableFuture[0]))
                .thenApply(ignored -> {
                    Map<String, StickyAssignmentsDocument> docs = new HashMap<>();
                    for (CompletableFuture<StickyAssignmentsDocument> lookup : lookups) {
                        StickyAssignmentsDocument doc = lookup.join();
                        if (doc != null) {
                            docs.put(getKey(doc.getAttributeName(), doc.getAttributeValue()), doc);
                        }
                    }
                    return docs;
                });
    }
}
