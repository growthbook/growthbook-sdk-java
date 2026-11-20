package growthbook.sdk.java.testhelpers;


import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Records every call to the legacy refresh callback. Thread-safe.
 */
@SuppressWarnings("deprecation")
public final class RecordingRefreshCallback implements growthbook.sdk.java.callback.FeatureRefreshCallback {

    public final List<String> refreshes = new CopyOnWriteArrayList<>();
    public final List<Throwable> errors = new CopyOnWriteArrayList<>();

    @Override
    public void onRefresh(String featuresJson) {
        refreshes.add(featuresJson);
    }

    @Override
    public void onError(Throwable throwable) {
        errors.add(throwable);
    }
}
