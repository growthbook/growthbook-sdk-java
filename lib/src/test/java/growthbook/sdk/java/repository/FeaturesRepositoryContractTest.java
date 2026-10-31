package growthbook.sdk.java.repository;

import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.exception.FeatureFetchException.FeatureFetchErrorCode;
import growthbook.sdk.java.listener.FeatureRefreshListener;
import growthbook.sdk.java.model.FeatureRefreshEvent;
import growthbook.sdk.java.model.FeatureRefreshSource;
import growthbook.sdk.java.testhelpers.RecordingRefreshListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The {@link IGBFeaturesRepository} contract every implementation shares, whatever its feature
 * source: what it exposes before and after {@link IGBFeaturesRepository#initialize()}, how a
 * source that cannot be read fails it, and how refresh listeners are notified.
 *
 * <p>A subclass supplies the repository and controls its source. Behaviour that only applies to
 * repositories fetching over HTTP lives in {@link HttpFeaturesRepositoryContractTest}.
 *
 * @param <R> the repository under test
 */
abstract class FeaturesRepositoryContractTest<R extends IGBFeaturesRepository> {

    static final String FEATURES = "{\"dark_mode\":{\"defaultValue\":false},\"banner_text\":{\"defaultValue\":\"Welcome\"}}";

    private final List<R> repositories = new ArrayList<>();

    /**
     * @return a new repository reading the source this test controls
     */
    abstract R createRepository();

    /**
     * Makes the source hold the given features object.
     */
    abstract void givenSourceHolds(String featuresJson) throws Exception;

    /**
     * Makes the source impossible to read.
     */
    abstract void givenSourceIsUnavailable() throws Exception;

    /**
     * @return the error code initialize fails with when the source cannot be read
     */
    abstract FeatureFetchErrorCode unavailableSourceErrorCode();

    /**
     * Releases what the repository holds; called once per repository after each test.
     */
    void release(R repository) {
    }

    final R newRepository() {
        return track(createRepository());
    }

    final R track(R repository) {
        repositories.add(repository);
        return repository;
    }

    @AfterEach
    void releaseRepositories() {
        for (R repository : repositories) {
            release(repository);
        }
    }

    @Test
    @DisplayName("Exposes empty features and no refresh history before initialize")
    void exposesEmptyFeaturesBeforeInitialize() {
        // Given
        R subject = newRepository();

        // When
        String featuresJson = subject.getFeaturesJson();

        // Then
        assertEquals("{}", featuresJson);
        assertEquals(0, subject.getRefreshSuccessCount());
        assertEquals(0, subject.getRefreshFailureCount());
        assertNull(subject.getLastRefreshError());
        assertNull(subject.getLastRefreshLoadedFromCache());
    }

    @Test
    @DisplayName("Initialize loads the features from the source and exposes them")
    void initializeExposesFeatures() throws Exception {
        // Given
        givenSourceHolds(FEATURES);
        R subject = newRepository();

        // When
        subject.initialize();

        // Then
        assertEquals(FEATURES, subject.getFeaturesJson());
        assertEquals(1, subject.getRefreshSuccessCount());
        assertEquals(Boolean.FALSE, subject.getLastRefreshLoadedFromCache());
    }

    @Test
    @DisplayName("A listener receives a successful, changed initialization event")
    void listenerReceivesSuccessfulInitialization() throws Exception {
        // Given
        givenSourceHolds(FEATURES);
        R subject = newRepository();
        RecordingRefreshListener listener = new RecordingRefreshListener();
        subject.addFeatureRefreshListener(listener);

        // When
        subject.initialize();

        // Then
        assertEquals(1, listener.events.size());
        FeatureRefreshEvent event = listener.events.get(0);
        assertTrue(event.isSuccessful());
        assertTrue(event.isFeaturesChanged());
        assertFalse(event.isLoadedFromCache());
        assertEquals(2, event.getActiveFeatureCount());
        assertEquals(FeatureRefreshSource.INITIALIZATION, event.getSource());
        assertTrue(event.getDurationMillis() >= 0);
        assertNull(event.getError());
    }

    @Test
    @DisplayName("Initialize fails, records the failure and keeps empty features when the source cannot be read")
    void initializeFailsWhenSourceIsUnavailable() throws Exception {
        // Given
        givenSourceIsUnavailable();
        R subject = newRepository();

        // When
        FeatureFetchException exception = assertThrows(FeatureFetchException.class, subject::initialize);

        // Then
        assertEquals(unavailableSourceErrorCode(), exception.getErrorCode());
        assertEquals("{}", subject.getFeaturesJson());
        assertEquals(0, subject.getRefreshSuccessCount());
        assertEquals(1, subject.getRefreshFailureCount());
        assertEquals(1, subject.getRefreshConsecutiveFailureCount());
        assertSame(exception, subject.getLastRefreshError());
    }

    @Test
    @DisplayName("A listener receives a failed, unchanged initialization event when the source cannot be read")
    void listenerReceivesFailedInitialization() throws Exception {
        // Given
        givenSourceIsUnavailable();
        R subject = newRepository();
        RecordingRefreshListener listener = new RecordingRefreshListener();
        subject.addFeatureRefreshListener(listener);

        // When
        assertThrows(FeatureFetchException.class, subject::initialize);

        // Then
        assertEquals(1, listener.events.size());
        FeatureRefreshEvent event = listener.events.get(0);
        assertFalse(event.isSuccessful());
        assertFalse(event.isFeaturesChanged());
        assertFalse(event.isLoadedFromCache());
        assertEquals(0, event.getActiveFeatureCount());
        assertEquals(FeatureRefreshSource.INITIALIZATION, event.getSource());
        assertNotNull(event.getError());
    }

    @Test
    @DisplayName("A removed listener is no longer notified")
    void removedListenerIsNotNotified() throws Exception {
        // Given
        givenSourceHolds(FEATURES);
        R subject = newRepository();
        RecordingRefreshListener listener = new RecordingRefreshListener();
        subject.addFeatureRefreshListener(listener);
        subject.removeFeatureRefreshListener(listener);

        // When
        subject.initialize();

        // Then
        assertTrue(listener.events.isEmpty());
    }

    @Test
    @DisplayName("A throwing listener does not stop the next listener")
    void throwingListenerDoesNotStopNextListener() throws Exception {
        // Given
        givenSourceHolds(FEATURES);
        R subject = newRepository();
        RecordingRefreshListener listener = new RecordingRefreshListener();
        subject.addFeatureRefreshListener(new ThrowingRefreshListener());
        subject.addFeatureRefreshListener(listener);

        // When
        subject.initialize();

        // Then
        assertEquals(1, listener.events.size());
        assertTrue(listener.events.get(0).isSuccessful());
        assertEquals(FEATURES, subject.getFeaturesJson());
    }

    private static final class ThrowingRefreshListener implements FeatureRefreshListener {
        @Override
        public void onRefresh(FeatureRefreshEvent event) {
            throw new IllegalStateException("listener failed");
        }
    }
}
