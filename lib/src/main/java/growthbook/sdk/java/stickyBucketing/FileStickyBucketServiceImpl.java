package growthbook.sdk.java.stickyBucketing;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.gson.Gson;
import growthbook.sdk.java.model.StickyAssignmentsDocument;
import growthbook.sdk.java.sandbox.GbCacheManager;
import growthbook.sdk.java.util.GrowthBookJsonUtils;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * File-based implementation of {@link StickyBucketService}.
 *
 * <p>Each sticky assignment document is persisted as a separate JSON entry via a
 * {@link GbCacheManager}, keyed by {@code attributeName||attributeValue}. Storing one entry
 * per user avoids the read-modify-write of a single shared file, so concurrent users do not
 * contend and no full-store rewrite happens on each save.
 *
 * <p>Design notes addressing the known risks of file-backed persistence:
 * <ul>
 *   <li><b>Path traversal / illegal names:</b> the (user-controlled) attribute value is never
 *       used directly as a file name. The logical key is hashed with SHA-256, so the on-disk
 *       name is always a fixed-length hex string that cannot escape the cache directory and
 *       cannot collide due to separator characters.</li>
 *   <li><b>Corruption:</b> durability/atomicity of the write is delegated to the
 *       {@link GbCacheManager} (the file implementation writes atomically via a temp file +
 *       rename).</li>
 *   <li><b>Concurrency:</b> access is guarded by a fixed set of striped
 *       {@link ReadWriteLock}s. This bounds memory (unlike a per-key lock map that grows with
 *       the number of distinct users) while still allowing concurrent reads and per-key
 *       write isolation within the JVM.</li>
 *   <li><b>Read latency:</b> a bounded (LRU) in-memory cache fronts the store so repeated
 *       evaluations for the same user do not hit disk on every call. The bound keeps memory
 *       usage constant regardless of how many users are seen.</li>
 * </ul>
 */
@Slf4j
public class FileStickyBucketServiceImpl implements StickyBucketService {

    private static final int LOCK_STRIPES = 64;
    private static final String KEY_SEPARATOR = "||";
    private static final String KEY_PREFIX = "gbStickyBuckets__";
    private static final long DEFAULT_MAX_CACHED_DOCUMENTS = 10_000L;

    private final Gson gson;
    private final ReadWriteLock[] locks;
    private final GbCacheManager gbCacheManager;
    private final Cache<String, StickyAssignmentsDocument> memoryCache;

    /**
     * Creates a service with the default in-memory cache bound
     * ({@value #DEFAULT_MAX_CACHED_DOCUMENTS} documents).
     *
     * @param gbCacheManager the cache manager used to persist sticky assignments
     */
    public FileStickyBucketServiceImpl(GbCacheManager gbCacheManager) {
        this(gbCacheManager, DEFAULT_MAX_CACHED_DOCUMENTS);
    }

    /**
     * @param gbCacheManager      the cache manager used to persist sticky assignments
     * @param maxCachedDocuments  maximum number of documents kept in the in-memory LRU cache
     */
    public FileStickyBucketServiceImpl(GbCacheManager gbCacheManager, long maxCachedDocuments) {
        this.gbCacheManager = gbCacheManager;
        this.gson = GrowthBookJsonUtils.getInstance().gson;
        this.locks = new ReadWriteLock[LOCK_STRIPES];
        Arrays.setAll(this.locks, i -> new ReentrantReadWriteLock());
        this.memoryCache = CacheBuilder.newBuilder()
                .maximumSize(maxCachedDocuments)
                .build();
    }

    @Override
    public StickyAssignmentsDocument getAssignments(String attributeName, String attributeValue) {
        if (attributeName == null || attributeValue == null) {
            return null;
        }
        String logicalKey = logicalKey(attributeName, attributeValue);

        StickyAssignmentsDocument cached = memoryCache.getIfPresent(logicalKey);
        if (cached != null) {
            return cached;
        }

        ReadWriteLock lock = lockFor(logicalKey);
        lock.readLock().lock();
        try {
            String json = gbCacheManager.loadCache(fileName(logicalKey));

            if (json == null || json.isEmpty()) {
                return null;
            }
            StickyAssignmentsDocument doc = gson.fromJson(json, StickyAssignmentsDocument.class);

            if (doc != null) {
                memoryCache.put(logicalKey, doc);
            }
            return doc;
        } catch (RuntimeException e) {
            log.error("Failed to load sticky assignments for {}={}: {}",
                    attributeName, attributeValue, e.getMessage());
            return null;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void saveAssignments(StickyAssignmentsDocument doc) {
        if (doc == null || doc.getAttributeName() == null || doc.getAttributeValue() == null) {
            return;
        }
        String logicalKey = logicalKey(doc.getAttributeName(), doc.getAttributeValue());

        ReadWriteLock lock = lockFor(logicalKey);
        lock.writeLock().lock();
        try {
            String json = gson.toJson(doc);
            gbCacheManager.saveContent(fileName(logicalKey), json);
            memoryCache.put(logicalKey, doc);
        } catch (RuntimeException e) {
            log.error("Failed to save sticky assignments for {}={}: {}",
                    doc.getAttributeName(), doc.getAttributeValue(), e.getMessage());
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public Map<String, StickyAssignmentsDocument> getAllAssignments(Map<String, String> attributes) {
        Map<String, StickyAssignmentsDocument> docs = new HashMap<>();
        if (attributes == null) {
            return docs;
        }
        for (Map.Entry<String, String> entry : attributes.entrySet()) {
            StickyAssignmentsDocument doc = getAssignments(entry.getKey(), entry.getValue());
            if (doc != null) {
                docs.put(entry.getKey() + KEY_SEPARATOR + entry.getValue(), doc);
            }
        }
        return docs;
    }

    private String logicalKey(String attributeName, String attributeValue) {
        return KEY_PREFIX + attributeName + KEY_SEPARATOR + attributeValue;
    }

    /**
     * Maps a logical key to a safe, flat cache file name by hashing it with SHA-256.
     * This prevents path traversal (a user-controlled attribute value can never introduce
     * {@code "/"} or {@code ".."}) and avoids collisions from separator characters.
     */
    private String fileName(String logicalKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(logicalKey.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private ReadWriteLock lockFor(String logicalKey) {
        return locks[Math.floorMod(logicalKey.hashCode(), LOCK_STRIPES)];
    }
}
