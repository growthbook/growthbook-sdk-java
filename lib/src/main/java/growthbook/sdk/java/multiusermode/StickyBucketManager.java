package growthbook.sdk.java.multiusermode;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import growthbook.sdk.java.model.StickyAssignmentsDocument;
import growthbook.sdk.java.stickyBucketing.AsyncStickyBucketService;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Owns all sticky bucket I/O for one {@link GrowthBookClient}: coalesced
 * non-blocking reads, fire-and-forget writes serialized per document key, and
 * the authoritative in-process record of every document this process has
 * written. Package-private on purpose — its invariants (merge direction,
 * dirty/in-flight save states, overlay rules) are implementation detail, not
 * public API; consumers reach it through the client's methods only.
 *
 * <p>Invariants (ported from the Python SDK 2.4.0 design, growthbook-python
 * #128/#129):
 * <ul>
 *   <li><b>Authoritative overlay:</b> every fetched or cached snapshot has this
 *       process's own documents merged over it (local wins per experiment key),
 *       so a slow or stale store read can never roll back an assignment.</li>
 *   <li><b>Per-key save serialization:</b> at most one save per document key is
 *       in flight; a write during flight marks the key dirty and the completion
 *       re-saves the latest merged document, so completion order can never
 *       regress the stored document.</li>
 *   <li><b>Coalesced reads:</b> concurrent fetches for the same attribute map
 *       share one store call; a waiter cancelling its own future cannot affect
 *       the shared fetch (reserve-then-start: waiters only ever see dependent
 *       stages of the reserved future).</li>
 *   <li><b>Bounded memory:</b> the authoritative map is pruned to
 *       {@value #STICKY_DOCS_MAX} entries (least-recently-saved first); dirty or
 *       in-flight documents are never evicted, nor is any document whose save
 *       completed after the oldest currently in-flight fetch started (an
 *       overlay against that fetch's result still needs it).</li>
 * </ul>
 *
 * <p>Threading: no {@code synchronized} (virtual-thread friendly) and no side
 * effects inside {@code ConcurrentHashMap} compute lambdas (bin locks are
 * hidden monitors; a synchronously-completing service would otherwise re-enter
 * them). Store calls are launched outside all locks.
 */
@Slf4j
final class StickyBucketManager {

    static final int STICKY_DOCS_MAX = 1000;

    private final AsyncStickyBucketService service;

    /** Authoritative record of every document this process has written, keyed attributeName||attributeValue. */
    private final ConcurrentHashMap<String, StickyAssignmentsDocument> authoritativeDocs = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, SaveState> saveStates = new ConcurrentHashMap<>();

    /** In-flight coalesced fetches. Keys are immutable attribute-map copies —
     * content-based equality, collision-proof (a joined-string fingerprint is
     * ambiguous for attribute values containing the separator). */
    private final ConcurrentHashMap<Map<String, String>, FetchEntry> inflightFetches = new ConcurrentHashMap<>();

    /** Monotonic sequence ordering fetch starts and save completions, for the eviction guard. */
    private final AtomicLong sequence = new AtomicLong();

    /** Opt-in bounded read cache of successful raw fetches (overlay is applied per read, never cached). */
    @Nullable
    private final Cache<Map<String, String>, Map<String, StickyAssignmentsDocument>> readCache;

    private volatile boolean draining = false;

    StickyBucketManager(AsyncStickyBucketService service,
                        @Nullable Long cacheTtlSeconds,
                        @Nullable Integer cacheSize) {
        this.service = service;
        boolean cacheEnabled = cacheTtlSeconds != null && cacheTtlSeconds > 0
                && (cacheSize == null || cacheSize > 0);
        this.readCache = cacheEnabled
                ? CacheBuilder.newBuilder()
                .maximumSize(cacheSize == null ? 1000 : cacheSize)
                .expireAfterWrite(cacheTtlSeconds, TimeUnit.SECONDS)
                .build()
                : null;
    }

    private static final class FetchEntry {
        final CompletableFuture<Map<String, StickyAssignmentsDocument>> future = new CompletableFuture<>();
        final long startSequence;

        FetchEntry(long startSequence) {
            this.startSequence = startSequence;
        }
    }

    private static final class SaveState {
        final ReentrantLock lock = new ReentrantLock();
        boolean inFlight;
        boolean dirty;
        CompletableFuture<Void> quiescent = CompletableFuture.completedFuture(null);
        long lastSaveCompletedSequence;
    }

    // ------------------------------------------------------------------ reads

    /**
     * Fetch the sticky bucket documents for these identifier attributes, with
     * this process's own writes overlaid. Failures propagate (a rejecting
     * store completes the future exceptionally — matching the JS and Python
     * SDKs, which never silently evaluate without sticky data).
     */
    CompletableFuture<Map<String, StickyAssignmentsDocument>> fetchAssignments(Map<String, String> attributes) {
        if (attributes.isEmpty()) {
            return CompletableFuture.completedFuture(overlay(attributes, Collections.emptyMap()));
        }
        Map<String, String> key = Collections.unmodifiableMap(new HashMap<>(attributes));

        if (readCache != null) {
            Map<String, StickyAssignmentsDocument> cached = readCache.getIfPresent(key);
            if (cached != null) {
                // Re-apply local writes: another snapshot for the same identifier
                // may have assigned since this entry was cached.
                return CompletableFuture.completedFuture(overlay(key, cached));
            }
        }

        while (true) {
            FetchEntry existing = inflightFetches.get(key);
            if (existing != null) {
                // Dependent stage: a waiter's cancel() cannot reach the shared future.
                return existing.future.thenApply(fetched -> overlay(key, fetched));
            }
            FetchEntry created = new FetchEntry(sequence.incrementAndGet());
            if (inflightFetches.putIfAbsent(key, created) == null) {
                startFetch(key, created);
                return created.future.thenApply(fetched -> overlay(key, fetched));
            }
        }
    }

    private void startFetch(Map<String, String> key, FetchEntry entry) {
        if (draining) {
            inflightFetches.remove(key, entry);
            entry.future.completeExceptionally(new IllegalStateException("client is shutting down"));
            return;
        }
        CompletionStage<Map<String, StickyAssignmentsDocument>> stage;
        try {
            stage = service.getAllAssignments(key);
        } catch (RuntimeException e) {
            // Includes RejectedExecutionException from an offload executor at
            // saturation: surface it, don't silently evaluate without sticky data.
            inflightFetches.remove(key, entry);
            entry.future.completeExceptionally(e);
            return;
        }
        if (stage == null) {
            inflightFetches.remove(key, entry);
            entry.future.complete(Collections.emptyMap());
            return;
        }
        stage.whenComplete((fetched, error) -> {
            // Remove BEFORE completing so a dependent stage that immediately
            // re-fetches starts a fresh store call instead of re-joining this one.
            inflightFetches.remove(key, entry);
            if (error != null) {
                entry.future.completeExceptionally(error);
                return;
            }
            Map<String, StickyAssignmentsDocument> result =
                    fetched == null ? Collections.emptyMap() : fetched;
            if (readCache != null) {
                readCache.put(key, result); // successful raw fetches only
            }
            entry.future.complete(result);
        });
    }

    /**
     * Merge this process's authoritative documents (local wins per experiment
     * key) over a fetched/cached snapshot, for the document keys this attribute
     * map can address. Returns a fresh mutable map — the evaluator mutates the
     * per-eval docs map in place for in-eval read-your-writes, and that map must
     * never be shared between evaluations.
     */
    private Map<String, StickyAssignmentsDocument> overlay(Map<String, String> attributes,
                                                           Map<String, StickyAssignmentsDocument> fetched) {
        Map<String, StickyAssignmentsDocument> merged = new HashMap<>(Math.max(8, fetched.size() * 2));
        merged.putAll(fetched);
        for (Map.Entry<String, String> attribute : attributes.entrySet()) {
            String docKey = attribute.getKey() + "||" + attribute.getValue();
            StickyAssignmentsDocument local = authoritativeDocs.get(docKey);
            if (local != null) {
                merged.put(docKey, mergeDocs(merged.get(docKey), local));
            }
        }
        return merged;
    }

    /** Per-experiment-key merge; {@code wins}'s entries take precedence. Always returns a fresh document. */
    private static StickyAssignmentsDocument mergeDocs(@Nullable StickyAssignmentsDocument base,
                                                       StickyAssignmentsDocument wins) {
        Map<String, String> assignments = new HashMap<>();
        if (base != null && base.getAssignments() != null) {
            assignments.putAll(base.getAssignments());
        }
        if (wins.getAssignments() != null) {
            assignments.putAll(wins.getAssignments());
        }
        return new StickyAssignmentsDocument(wins.getAttributeName(), wins.getAttributeValue(), assignments);
    }

    // ----------------------------------------------------------------- writes

    /**
     * Record a newly assigned document in the authoritative map and persist it,
     * fire-and-forget. The document actually written to the store is re-read
     * from the authoritative map at save time, so a trailing save always writes
     * the latest merged state. Store failures are logged, not retried (JS/Python
     * parity); an executor rejection leaves the key dirty so the next write or
     * {@link #flush()} re-attempts it — nothing is lost either way, the
     * authoritative map still holds the document.
     */
    CompletableFuture<Void> recordAndSave(StickyAssignmentsDocument doc) {
        String key = doc.getAttributeName() + "||" + doc.getAttributeValue();
        authoritativeDocs.merge(key, doc, (existing, incoming) -> mergeDocs(existing, incoming));

        SaveState state = saveStates.computeIfAbsent(key, k -> new SaveState()); // pure creation only
        boolean start = false;
        CompletableFuture<Void> waiter;
        state.lock.lock();
        try {
            if (state.inFlight) {
                state.dirty = true;
            } else {
                state.inFlight = true;
                state.dirty = false; // the save snapshots the latest merged doc anyway
                state.quiescent = new CompletableFuture<>();
                start = true;
            }
            waiter = state.quiescent;
        } finally {
            state.lock.unlock();
        }
        if (start) {
            driveSave(key, state); // outside the map AND the state lock
        }
        return waiter.thenApply(v -> v); // dependent copy: caller cancel() can't detach other writers
    }

    private void driveSave(String key, SaveState state) {
        StickyAssignmentsDocument snapshot = authoritativeDocs.get(key);
        CompletableFuture<Void> saveFuture;
        boolean rejected = false;
        try {
            CompletionStage<Void> stage = snapshot == null ? null : service.saveAssignments(snapshot);
            saveFuture = stage == null
                    ? CompletableFuture.completedFuture(null)
                    : stage.toCompletableFuture();
        } catch (RuntimeException e) {
            // Typically RejectedExecutionException from an offload executor at
            // saturation or during drain: keep the key dirty for a later re-kick.
            log.warn("Sticky bucket save could not be scheduled for {}; will retry on next write or flush", key, e);
            rejected = true;
            saveFuture = CompletableFuture.completedFuture(null);
        }

        final boolean keepDirty = rejected;
        saveFuture.whenComplete((ignored, error) -> {
            if (error != null) {
                log.warn("Sticky bucket save failed for {}", key, error);
            }
            boolean again;
            CompletableFuture<Void> quiescent = null;
            state.lock.lock();
            try {
                if (keepDirty) {
                    state.dirty = true;
                }
                again = state.dirty && !keepDirty;
                if (again) {
                    state.dirty = false;
                } else {
                    state.inFlight = false;
                    state.lastSaveCompletedSequence = sequence.incrementAndGet();
                    quiescent = state.quiescent;
                }
            } finally {
                state.lock.unlock();
            }
            if (again) {
                driveSave(key, state);
            } else {
                quiescent.complete(null);
                pruneAuthoritativeDocs();
            }
        });
    }

    // ------------------------------------------------------------- lifecycle

    /**
     * Completes when every pending or dirty save has been attempted. Loops
     * because a trailing save can be scheduled while a round is awaited;
     * bounded so a persistently rejecting executor cannot spin it forever.
     */
    CompletableFuture<Void> flush() {
        return flush(20);
    }

    private CompletableFuture<Void> flush(int roundsLeft) {
        if (roundsLeft <= 0) {
            log.warn("Sticky bucket flush gave up after repeated rounds; some saves may remain unflushed");
            return CompletableFuture.completedFuture(null);
        }
        List<CompletableFuture<Void>> pending = new ArrayList<>();
        for (Map.Entry<String, SaveState> entry : saveStates.entrySet()) {
            SaveState state = entry.getValue();
            boolean kick = false;
            CompletableFuture<Void> waiter = null;
            state.lock.lock();
            try {
                if (state.inFlight) {
                    waiter = state.quiescent;
                } else if (state.dirty) {
                    state.dirty = false;
                    state.inFlight = true;
                    state.quiescent = new CompletableFuture<>();
                    waiter = state.quiescent;
                    kick = true;
                }
            } finally {
                state.lock.unlock();
            }
            if (kick) {
                driveSave(entry.getKey(), state);
            }
            if (waiter != null) {
                pending.add(waiter);
            }
        }
        if (pending.isEmpty()) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.allOf(pending.toArray(new CompletableFuture[0]))
                .thenCompose(ignored -> flush(roundsLeft - 1));
    }

    /** Stop accepting new fetches; writes still merge into the authoritative map. */
    void startDraining() {
        this.draining = true;
    }

    /** Blocking, bounded flush for {@code shutdown()}. Never throws. */
    void close(long timeoutMillis) {
        try {
            flush().get(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while flushing sticky bucket saves during shutdown");
        } catch (TimeoutException e) {
            log.warn("Timed out after {}ms flushing sticky bucket saves during shutdown", timeoutMillis);
        } catch (ExecutionException e) {
            log.warn("Sticky bucket save flush failed during shutdown", e.getCause());
        }
    }

    // -------------------------------------------------------------- eviction

    private void pruneAuthoritativeDocs() {
        if (authoritativeDocs.size() <= STICKY_DOCS_MAX) {
            return;
        }
        long oldestInflightFetchStart = Long.MAX_VALUE;
        for (FetchEntry entry : inflightFetches.values()) {
            oldestInflightFetchStart = Math.min(oldestInflightFetchStart, entry.startSequence);
        }

        List<Map.Entry<String, SaveState>> candidates = new ArrayList<>();
        for (Map.Entry<String, SaveState> entry : saveStates.entrySet()) {
            SaveState state = entry.getValue();
            state.lock.lock();
            try {
                if (!state.inFlight && !state.dirty
                        && state.lastSaveCompletedSequence < oldestInflightFetchStart) {
                    candidates.add(entry);
                }
            } finally {
                state.lock.unlock();
            }
        }
        candidates.sort(Comparator.comparingLong(e -> e.getValue().lastSaveCompletedSequence));

        int excess = authoritativeDocs.size() - STICKY_DOCS_MAX;
        for (Map.Entry<String, SaveState> entry : candidates) {
            if (excess <= 0) {
                break;
            }
            SaveState state = entry.getValue();
            state.lock.lock();
            try {
                if (state.inFlight || state.dirty) {
                    continue; // became active since the scan — never evict unsaved local truth
                }
                if (authoritativeDocs.remove(entry.getKey()) != null) {
                    excess--;
                }
                saveStates.remove(entry.getKey(), state);
            } finally {
                state.lock.unlock();
            }
        }
    }

    // ------------------------------------------------------------------ test seams

    int authoritativeDocCount() {
        return authoritativeDocs.size();
    }

    @Nullable
    StickyAssignmentsDocument authoritativeDoc(String key) {
        return authoritativeDocs.get(key);
    }

    Set<Map<String, String>> inflightFetchKeys() {
        return inflightFetches.keySet();
    }
}
