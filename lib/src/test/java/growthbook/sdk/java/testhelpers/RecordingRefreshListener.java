package growthbook.sdk.java.testhelpers;

import growthbook.sdk.java.listener.FeatureRefreshListener;
import growthbook.sdk.java.model.FeatureRefreshEvent;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Records every feature refresh event. Thread-safe.
 */
public final class RecordingRefreshListener implements FeatureRefreshListener {

    public final List<FeatureRefreshEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void onRefresh(FeatureRefreshEvent event) {
        events.add(event);
    }
}
