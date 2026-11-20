package growthbook.sdk.java.repository.internal;

import growthbook.sdk.java.callback.FeatureRefreshCallback;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The legacy {@link FeatureRefreshCallback}s. Registration happens on caller threads while
 * refresh threads dispatch, hence the copy-on-write list. A callback that throws is logged and
 * the others still run.
 *
 * <p>The callback type is deprecated but still public API, so this class, which exists only to
 * serve it, uses it knowingly; it goes when the callback does.
 */
@Slf4j
@SuppressWarnings("deprecation")
public final class RefreshCallbacks {

    private final CopyOnWriteArrayList<FeatureRefreshCallback> callbacks = new CopyOnWriteArrayList<>();

    public void add(FeatureRefreshCallback callback) {
        if (callback != null) {
            callbacks.addIfAbsent(callback);
        }
    }

    public void clear() {
        callbacks.clear();
    }

    public void notifyRefresh(String featuresJson) {
        for (FeatureRefreshCallback callback : callbacks) {
            try {
                callback.onRefresh(featuresJson);
            } catch (RuntimeException e) {
                log.warn("Feature refresh callback failed", e);
            }
        }
    }

    public void notifyError(Throwable error) {
        for (FeatureRefreshCallback callback : callbacks) {
            try {
                callback.onError(error);
            } catch (RuntimeException e) {
                log.warn("Feature refresh error callback failed", e);
            }
        }
    }
}
