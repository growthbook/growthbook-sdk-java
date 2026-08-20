package growthbook.sdk.java.multiusermode;

import growthbook.sdk.java.model.StickyAssignmentsDocument;
import growthbook.sdk.java.stickyBucketing.AsyncStickyBucketService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the sticky bucket manager's invariants: fetch coalescing,
 * authoritative overlay (a stale read can never roll back a local write),
 * per-key save serialization with trailing saves, rejection semantics, flush,
 * bounded eviction that never drops unsaved documents, and the opt-in cache.
 *
 * <p>All tests drive a hand-completed service double — no executors, no sleeps.
 */
class StickyBucketManagerTest {

    /** Service double whose futures are completed manually by the test. */
    private static final class ScriptedService implements AsyncStickyBucketService {
        final List<Map<String, String>> fetchCalls = new CopyOnWriteArrayList<>();
        final List<CompletableFuture<Map<String, StickyAssignmentsDocument>>> pendingFetches = new CopyOnWriteArrayList<>();
        final List<StickyAssignmentsDocument> savedDocs = new CopyOnWriteArrayList<>();
        final List<CompletableFuture<Void>> pendingSaves = new CopyOnWriteArrayList<>();
        final Map<String, StickyAssignmentsDocument> store = new ConcurrentHashMap<>();
        volatile boolean completeFetchesImmediately = false;
        volatile boolean completeSavesImmediately = false;
        volatile RuntimeException throwOnFetch = null;
        volatile RuntimeException throwOnSave = null;

        @Override
        public CompletionStage<StickyAssignmentsDocument> getAssignments(String attributeName, String attributeValue) {
            return CompletableFuture.completedFuture(store.get(getKey(attributeName, attributeValue)));
        }

        @Override
        public CompletionStage<Void> saveAssignments(StickyAssignmentsDocument doc) {
            RuntimeException toThrow = throwOnSave;
            if (toThrow != null) {
                throwOnSave = null; // throw once
                throw toThrow;
            }
            savedDocs.add(doc);
            store.put(getKey(doc.getAttributeName(), doc.getAttributeValue()), doc);
            if (completeSavesImmediately) {
                return CompletableFuture.completedFuture(null);
            }
            CompletableFuture<Void> pending = new CompletableFuture<>();
            pendingSaves.add(pending);
            return pending;
        }

        @Override
        public CompletionStage<Map<String, StickyAssignmentsDocument>> getAllAssignments(Map<String, String> attributes) {
            if (throwOnFetch != null) {
                throw throwOnFetch;
            }
            fetchCalls.add(attributes);
            if (completeFetchesImmediately) {
                Map<String, StickyAssignmentsDocument> result = new HashMap<>();
                for (Map.Entry<String, String> attribute : attributes.entrySet()) {
                    StickyAssignmentsDocument doc = store.get(getKey(attribute.getKey(), attribute.getValue()));
                    if (doc != null) {
                        result.put(getKey(doc.getAttributeName(), doc.getAttributeValue()), doc);
                    }
                }
                return CompletableFuture.completedFuture(result);
            }
            CompletableFuture<Map<String, StickyAssignmentsDocument>> pending = new CompletableFuture<>();
            pendingFetches.add(pending);
            return pending;
        }
    }

    private static StickyAssignmentsDocument doc(String value, String expKey, String variation) {
        Map<String, String> assignments = new HashMap<>();
        assignments.put(expKey, variation);
        return new StickyAssignmentsDocument("id", value, assignments);
    }

    private static Map<String, String> attrs(String idValue) {
        return Collections.singletonMap("id", idValue);
    }

    // ------------------------------------------------------------- coalescing

    @Test
    @Timeout(10)
    void concurrentFetchesForSameAttributesShareOneStoreCall() throws Exception {
        ScriptedService service = new ScriptedService();
        StickyBucketManager manager = new StickyBucketManager(service, null, null);

        CompletableFuture<Map<String, StickyAssignmentsDocument>> first = manager.fetchAssignments(attrs("u1"));
        CompletableFuture<Map<String, StickyAssignmentsDocument>> second = manager.fetchAssignments(attrs("u1"));

        assertEquals(1, service.fetchCalls.size(), "concurrent identical fetches must coalesce");

        Map<String, StickyAssignmentsDocument> fetched = new HashMap<>();
        fetched.put("id||u1", doc("u1", "exp__0", "1"));
        service.pendingFetches.get(0).complete(fetched);

        assertEquals("1", first.get(5, TimeUnit.SECONDS).get("id||u1").getAssignments().get("exp__0"));
        assertEquals("1", second.get(5, TimeUnit.SECONDS).get("id||u1").getAssignments().get("exp__0"));
    }

    @Test
    @Timeout(10)
    void distinctAttributesFetchIndependently() {
        ScriptedService service = new ScriptedService();
        StickyBucketManager manager = new StickyBucketManager(service, null, null);

        manager.fetchAssignments(attrs("u1"));
        manager.fetchAssignments(attrs("u2"));

        assertEquals(2, service.fetchCalls.size());
    }

    @Test
    @Timeout(10)
    void waiterCancellationDoesNotPoisonTheSharedFetch() throws Exception {
        ScriptedService service = new ScriptedService();
        StickyBucketManager manager = new StickyBucketManager(service, null, null);

        CompletableFuture<Map<String, StickyAssignmentsDocument>> owner = manager.fetchAssignments(attrs("u1"));
        CompletableFuture<Map<String, StickyAssignmentsDocument>> waiter = manager.fetchAssignments(attrs("u1"));

        waiter.cancel(true);

        service.pendingFetches.get(0).complete(Collections.emptyMap());
        assertNotNull(owner.get(5, TimeUnit.SECONDS), "cancelling one caller's future must not affect others");
    }

    // ---------------------------------------------------------------- overlay

    @Test
    @Timeout(10)
    void staleFetchCompletingAfterLocalWriteCannotRollItBack() throws Exception {
        ScriptedService service = new ScriptedService();
        service.completeSavesImmediately = true;
        StickyBucketManager manager = new StickyBucketManager(service, null, null);

        // A fetch is in flight against a store that does NOT yet contain the doc...
        CompletableFuture<Map<String, StickyAssignmentsDocument>> stale = manager.fetchAssignments(attrs("u1"));
        // ...while this process assigns and records a new document.
        manager.recordAndSave(doc("u1", "exp__0", "treatment"));
        // The stale store response arrives last.
        service.pendingFetches.get(0).complete(Collections.emptyMap());

        Map<String, StickyAssignmentsDocument> result = stale.get(5, TimeUnit.SECONDS);
        assertNotNull(result.get("id||u1"), "authoritative overlay must re-apply the local write");
        assertEquals("treatment", result.get("id||u1").getAssignments().get("exp__0"));
    }

    @Test
    @Timeout(10)
    void assignmentsFromDifferentSnapshotsForSameIdentifierAllSurvive() throws Exception {
        // Port of the Python 2.4.0 P1 regression: same identifier reached through
        // different attribute sets must never lose assignments to last-write-wins.
        ScriptedService service = new ScriptedService();
        service.completeFetchesImmediately = true;
        service.completeSavesImmediately = true;
        StickyBucketManager manager = new StickyBucketManager(service, null, null);

        manager.recordAndSave(doc("u1", "exp_a__0", "a1")).get(5, TimeUnit.SECONDS);
        manager.recordAndSave(doc("u1", "exp_b__0", "b1")).get(5, TimeUnit.SECONDS);

        StickyAssignmentsDocument persisted = service.store.get("id||u1");
        assertEquals("a1", persisted.getAssignments().get("exp_a__0"), "first assignment lost");
        assertEquals("b1", persisted.getAssignments().get("exp_b__0"), "second assignment lost");
    }

    // -------------------------------------------------------- save semantics

    @Test
    @Timeout(10)
    void savesAreSerializedPerKeyWithOneTrailingSave() throws Exception {
        ScriptedService service = new ScriptedService();
        StickyBucketManager manager = new StickyBucketManager(service, null, null);

        // First save goes in flight (gated open).
        manager.recordAndSave(doc("u1", "exp_a__0", "a"));
        assertEquals(1, service.savedDocs.size());

        // Two more writes while the save is in flight: dirty flag, no new store call.
        manager.recordAndSave(doc("u1", "exp_b__0", "b"));
        CompletableFuture<Void> lastWaiter = manager.recordAndSave(doc("u1", "exp_c__0", "c"));
        assertEquals(1, service.savedDocs.size(), "in-flight save must serialize concurrent writes");

        // Completing the first save triggers exactly ONE trailing save with the merged doc.
        service.pendingSaves.get(0).complete(null);
        assertEquals(2, service.savedDocs.size(), "dirty key gets one trailing save, not one per write");
        StickyAssignmentsDocument trailing = service.savedDocs.get(1);
        assertEquals("a", trailing.getAssignments().get("exp_a__0"));
        assertEquals("b", trailing.getAssignments().get("exp_b__0"));
        assertEquals("c", trailing.getAssignments().get("exp_c__0"));

        service.pendingSaves.get(1).complete(null);
        lastWaiter.get(5, TimeUnit.SECONDS);
    }

    @Test
    @Timeout(10)
    void rejectedSaveStaysDirtyAndFlushRetriesIt() throws Exception {
        ScriptedService service = new ScriptedService();
        service.completeSavesImmediately = true;
        service.throwOnSave = new RejectedExecutionException("pool saturated");
        StickyBucketManager manager = new StickyBucketManager(service, null, null);

        manager.recordAndSave(doc("u1", "exp__0", "v"));
        assertEquals(0, service.savedDocs.size(), "rejected save must not reach the store");

        // The document is still local truth and flush re-attempts it.
        manager.flush().get(5, TimeUnit.SECONDS);
        assertEquals(1, service.savedDocs.size(), "flush must re-kick a rejected save");
        assertEquals("v", service.store.get("id||u1").getAssignments().get("exp__0"));
    }

    @Test
    @Timeout(10)
    void failedSaveIsLoggedNotRetriedButDocRemainsAuthoritative() throws Exception {
        ScriptedService service = new ScriptedService();
        StickyBucketManager manager = new StickyBucketManager(service, null, null);

        CompletableFuture<Void> waiter = manager.recordAndSave(doc("u1", "exp__0", "v"));
        service.pendingSaves.get(0).completeExceptionally(new RuntimeException("store down"));
        waiter.get(5, TimeUnit.SECONDS); // quiesces without retry (JS/Python parity)
        assertEquals(1, service.savedDocs.size());

        // The overlay still serves the local write.
        service.completeFetchesImmediately = true;
        Map<String, StickyAssignmentsDocument> result =
                manager.fetchAssignments(attrs("u1")).get(5, TimeUnit.SECONDS);
        assertEquals("v", result.get("id||u1").getAssignments().get("exp__0"));
    }

    @Test
    @Timeout(10)
    void flushWaitsForPendingSaves() throws Exception {
        ScriptedService service = new ScriptedService();
        StickyBucketManager manager = new StickyBucketManager(service, null, null);

        manager.recordAndSave(doc("u1", "exp__0", "v"));
        CompletableFuture<Void> flush = manager.flush();
        assertFalse(flush.isDone(), "flush must wait for the in-flight save");

        service.pendingSaves.get(0).complete(null);
        flush.get(5, TimeUnit.SECONDS);
    }

    // -------------------------------------------------------------- failures

    @Test
    @Timeout(10)
    void fetchFailurePropagates() {
        ScriptedService service = new ScriptedService();
        service.throwOnFetch = new RejectedExecutionException("pool saturated");
        StickyBucketManager manager = new StickyBucketManager(service, null, null);

        CompletableFuture<Map<String, StickyAssignmentsDocument>> fetch = manager.fetchAssignments(attrs("u1"));
        ExecutionException error = assertThrows(ExecutionException.class, () -> fetch.get(5, TimeUnit.SECONDS));
        assertTrue(error.getCause() instanceof RejectedExecutionException);
    }

    @Test
    @Timeout(10)
    void fetchRejectionDoesNotWedgeSubsequentFetches() throws Exception {
        ScriptedService service = new ScriptedService();
        service.throwOnFetch = new RejectedExecutionException("pool saturated");
        StickyBucketManager manager = new StickyBucketManager(service, null, null);
        assertThrows(ExecutionException.class,
                () -> manager.fetchAssignments(attrs("u1")).get(5, TimeUnit.SECONDS));

        service.throwOnFetch = null;
        service.completeFetchesImmediately = true;
        assertNotNull(manager.fetchAssignments(attrs("u1")).get(5, TimeUnit.SECONDS),
                "a failed fetch must not leave a poisoned in-flight entry behind");
    }

    // -------------------------------------------------------------- eviction

    @Test
    @Timeout(30)
    void evictionBoundsTheDocMapButNeverDropsUnsavedDocs() throws Exception {
        ScriptedService service = new ScriptedService();
        service.completeSavesImmediately = true;
        StickyBucketManager manager = new StickyBucketManager(service, null, null);

        // One document whose save is held in flight — unsaved local truth.
        service.completeSavesImmediately = false;
        manager.recordAndSave(doc("dirty-user", "exp__0", "keep-me"));
        service.completeSavesImmediately = true;

        // Blow past the bound with clean saves.
        for (int i = 0; i < StickyBucketManager.STICKY_DOCS_MAX + 200; i++) {
            manager.recordAndSave(doc("user-" + i, "exp__0", "v")).get(5, TimeUnit.SECONDS);
        }

        assertTrue(manager.authoritativeDocCount() <= StickyBucketManager.STICKY_DOCS_MAX + 1,
                "authoritative doc map must be bounded, was " + manager.authoritativeDocCount());
        assertNotNull(manager.authoritativeDoc("id||dirty-user"),
                "a document with an in-flight (unsaved) save must never be evicted");

        service.pendingSaves.get(0).complete(null);
    }

    // ------------------------------------------------------------------ cache

    @Test
    @Timeout(10)
    void cacheDisabledByDefaultFetchesEveryTime() throws Exception {
        ScriptedService service = new ScriptedService();
        service.completeFetchesImmediately = true;
        StickyBucketManager manager = new StickyBucketManager(service, null, null);

        manager.fetchAssignments(attrs("u1")).get(5, TimeUnit.SECONDS);
        manager.fetchAssignments(attrs("u1")).get(5, TimeUnit.SECONDS);

        assertEquals(2, service.fetchCalls.size(), "per-eval fetch is the default; no implicit caching");
    }

    @Test
    @Timeout(10)
    void optInCacheServesRepeatFetchesAndStillOverlaysLocalWrites() throws Exception {
        ScriptedService service = new ScriptedService();
        service.completeFetchesImmediately = true;
        service.completeSavesImmediately = true;
        StickyBucketManager manager = new StickyBucketManager(service, 30L, 100);

        manager.fetchAssignments(attrs("u1")).get(5, TimeUnit.SECONDS);
        manager.fetchAssignments(attrs("u1")).get(5, TimeUnit.SECONDS);
        assertEquals(1, service.fetchCalls.size(), "TTL cache must absorb the repeat fetch");

        // A local write after caching must still be visible through the cache.
        manager.recordAndSave(doc("u1", "exp__0", "post-cache")).get(5, TimeUnit.SECONDS);
        Map<String, StickyAssignmentsDocument> cached = manager.fetchAssignments(attrs("u1")).get(5, TimeUnit.SECONDS);
        assertEquals("post-cache", cached.get("id||u1").getAssignments().get("exp__0"),
                "overlay must be applied per read, never baked into the cache");
    }

    @Test
    @Timeout(10)
    void nonPositiveCacheSizeDisablesCaching() throws Exception {
        ScriptedService service = new ScriptedService();
        service.completeFetchesImmediately = true;
        StickyBucketManager manager = new StickyBucketManager(service, 30L, -1);

        manager.fetchAssignments(attrs("u1")).get(5, TimeUnit.SECONDS);
        manager.fetchAssignments(attrs("u1")).get(5, TimeUnit.SECONDS);
        assertEquals(2, service.fetchCalls.size());
    }

    @Test
    @Timeout(10)
    void failedFetchesAreNeverCached() throws Exception {
        ScriptedService service = new ScriptedService();
        StickyBucketManager manager = new StickyBucketManager(service, 30L, 100);

        CompletableFuture<Map<String, StickyAssignmentsDocument>> failing = manager.fetchAssignments(attrs("u1"));
        service.pendingFetches.get(0).completeExceptionally(new RuntimeException("blip"));
        assertThrows(ExecutionException.class, () -> failing.get(5, TimeUnit.SECONDS));

        service.completeFetchesImmediately = true;
        assertNotNull(manager.fetchAssignments(attrs("u1")).get(5, TimeUnit.SECONDS));
        assertEquals(2, service.fetchCalls.size(), "the failure must not have been cached");
    }
}
