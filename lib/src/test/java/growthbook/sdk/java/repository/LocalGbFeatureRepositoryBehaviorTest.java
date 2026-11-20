package growthbook.sdk.java.repository;

import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.exception.FeatureFetchException.FeatureFetchErrorCode;
import growthbook.sdk.java.model.FeatureRefreshEvent;
import growthbook.sdk.java.model.FeatureRefreshSource;
import growthbook.sdk.java.testhelpers.RecordingRefreshCallback;
import growthbook.sdk.java.testhelpers.RecordingRefreshListener;
import growthbook.sdk.java.testhelpers.TestClock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalGbFeatureRepositoryBehaviorTest {

    private static final String FILE_NAME = "local-repository-behavior-test-features";
    private static final String FEATURES = "{\"flag-a\":{\"defaultValue\":true},\"flag-b\":{\"defaultValue\":\"blue\"}}";

    private final List<Path> createdFiles = new ArrayList<>();

    @AfterEach
    void deleteCreatedFiles() throws IOException {
        for (Path file : createdFiles) {
            Files.deleteIfExists(file);
        }
    }

    @Test
    @DisplayName("Loads the features file and publishes it as one snapshot")
    void loadsFeaturesAndPublishesSnapshot() throws Exception {
        // Given
        writeResource(FEATURES);
        LocalGbFeatureRepository repository = new LocalGbFeatureRepository(FILE_NAME);

        // When
        repository.initialize();

        // Then
        FeatureSnapshot snapshot = repository.getFeatureSnapshot();
        assertEquals(FEATURES, snapshot.getFeaturesJson());
        assertEquals("", snapshot.getSavedGroupsJson());
        assertEquals(2, snapshot.getParsedFeatures().size());
        assertTrue(snapshot.getParsedFeatures().containsKey("flag-a"));
        assertTrue(snapshot.getParsedSavedGroups().entrySet().isEmpty());
        assertEquals(FEATURES, repository.getFeaturesJson());
        assertSame(snapshot.getParsedFeatures(), repository.getParsedFeatures());
    }

    @Test
    @DisplayName("Exposes empty features before the file is loaded")
    void exposesEmptyFeaturesBeforeLoad() {
        // Given
        LocalGbFeatureRepository repository = new LocalGbFeatureRepository(FILE_NAME);

        // When
        FeatureSnapshot snapshot = repository.getFeatureSnapshot();

        // Then
        assertSame(FeatureSnapshot.EMPTY, snapshot);
        assertEquals("{}", repository.getFeaturesJson());
        assertTrue(repository.getParsedFeatures().isEmpty());
    }

    @Test
    @DisplayName("Loads the features file through the retry overload too")
    void retryOverloadLoadsFile() throws Exception {
        // Given
        writeResource(FEATURES);
        LocalGbFeatureRepository repository = new LocalGbFeatureRepository(FILE_NAME);

        // When
        repository.initialize(true);

        // Then
        assertEquals(FEATURES, repository.getFeaturesJson());
        assertEquals(2, repository.getParsedFeatures().size());
        assertEquals(1, repository.getRefreshSuccessCount());
    }

    @ParameterizedTest(name = "path=[{0}]")
    @ValueSource(strings = {
            FILE_NAME,
            "/" + FILE_NAME,
            FILE_NAME + ".json",
            "/" + FILE_NAME + ".json"
    })
    @DisplayName("Resolves the path with or without a leading slash and json extension")
    void resolvesPathVariants(String jsonPath) throws Exception {
        // Given
        writeResource(FEATURES);
        LocalGbFeatureRepository repository = new LocalGbFeatureRepository(jsonPath);

        // When
        repository.initialize();

        // Then
        assertEquals(FEATURES, repository.getFeaturesJson());
    }

    @Test
    @DisplayName("Reads the features file as UTF-8")
    void readsNonAsciiValuesAsUtf8() throws Exception {
        // Given
        String features = "{\"greeting\":{\"defaultValue\":\"Привіт, café ✓\"}}";
        writeResource(features);
        LocalGbFeatureRepository repository = new LocalGbFeatureRepository(FILE_NAME);

        // When
        repository.initialize();

        // Then
        assertEquals(features, repository.getFeaturesJson());
        assertEquals("Привіт, café ✓",
                String.valueOf(repository.getParsedFeatures().get("greeting").getDefaultValue()));
    }

    @Test
    @DisplayName("Rejects a path the file system cannot represent as a configuration error and reports the failure")
    void rejectsInvalidPath() {
        // Given
        LocalGbFeatureRepository repository = new LocalGbFeatureRepository("invalid\u0000path");
        RecordingRefreshListener listener = new RecordingRefreshListener();
        repository.addFeatureRefreshListener(listener);

        // When
        FeatureFetchException exception = assertThrows(FeatureFetchException.class, () -> repository.initialize());

        // Then
        assertEquals(FeatureFetchErrorCode.CONFIGURATION_ERROR, exception.getErrorCode());
        assertEquals(1, listener.events.size());
        assertSame(exception, listener.events.get(0).getError());
        assertEquals("{}", repository.getFeaturesJson());
    }

    @ParameterizedTest(name = "content=[{0}]")
    @ValueSource(strings = {"", "   ", "null", "{\"flag-a\":", "[1,2]", "\"text\""})
    @DisplayName("Rejects an empty, null, malformed or non-object file as a configuration error")
    void rejectsInvalidFile(String content) throws Exception {
        // Given
        writeResource(content);
        LocalGbFeatureRepository repository = new LocalGbFeatureRepository(FILE_NAME);

        // When
        FeatureFetchException exception = assertThrows(FeatureFetchException.class, () -> repository.initialize());

        // Then
        assertEquals(FeatureFetchErrorCode.CONFIGURATION_ERROR, exception.getErrorCode());
        assertEquals("{}", repository.getFeaturesJson());
    }

    @ParameterizedTest(name = "content=[{0}]")
    @ValueSource(strings = {"", "null", "{\"flag-a\":"})
    @DisplayName("Does not throw from the retry overload when the file is invalid")
    void retryOverloadDoesNotThrowOnInvalidFile(String content) throws Exception {
        // Given
        writeResource(content);
        LocalGbFeatureRepository repository = new LocalGbFeatureRepository(FILE_NAME);

        // When
        assertDoesNotThrow(() -> repository.initialize(true));

        // Then
        assertEquals("{}", repository.getFeaturesJson());
        assertEquals(1, repository.getRefreshFailureCount());
    }

    @Test
    @DisplayName("Does not throw from the retry overload when the file is missing")
    void retryOverloadDoesNotThrowOnMissingFile() {
        // Given
        LocalGbFeatureRepository repository = new LocalGbFeatureRepository("missing-behavior-test-features");

        // When
        assertDoesNotThrow(() -> repository.initialize(false));

        // Then
        assertEquals(1, repository.getRefreshFailureCount());
    }

    @Test
    @DisplayName("Keeps the previously loaded features when a reload fails")
    void keepsPreviousFeaturesWhenReloadFails() throws Exception {
        // Given
        writeResource(FEATURES);
        LocalGbFeatureRepository repository = new LocalGbFeatureRepository(FILE_NAME);
        repository.initialize();
        writeResource("{\"flag-a\":");

        // When
        assertThrows(FeatureFetchException.class, () -> repository.initialize());

        // Then
        assertEquals(FEATURES, repository.getFeaturesJson());
        assertEquals(2, repository.getParsedFeatures().size());
    }

    @Test
    @DisplayName("Notifies legacy callbacks and listeners of a successful load")
    @SuppressWarnings("deprecation")
    void notifiesCallbacksAndListenersOnSuccess() throws Exception {
        // Given
        writeResource(FEATURES);
        LocalGbFeatureRepository repository = new LocalGbFeatureRepository(FILE_NAME);
        RecordingRefreshCallback callback = new RecordingRefreshCallback();
        RecordingRefreshListener listener = new RecordingRefreshListener();
        repository.onFeaturesRefresh(callback);
        repository.addFeatureRefreshListener(listener);

        // When
        repository.initialize();

        // Then
        assertEquals(1, callback.refreshes.size());
        assertEquals(FEATURES, callback.refreshes.get(0));
        assertTrue(callback.errors.isEmpty());
        assertEquals(1, listener.events.size());
        FeatureRefreshEvent event = listener.events.get(0);
        assertTrue(event.isSuccessful());
        assertTrue(event.isFeaturesChanged());
        assertFalse(event.isLoadedFromCache());
        assertEquals(2, event.getActiveFeatureCount());
        assertEquals(FeatureRefreshSource.INITIALIZATION, event.getSource());
        assertNull(event.getError());
    }

    @Test
    @DisplayName("Reports unchanged features when the same file is loaded again")
    void reportsUnchangedFeaturesOnSameReload() throws Exception {
        // Given
        writeResource(FEATURES);
        LocalGbFeatureRepository repository = new LocalGbFeatureRepository(FILE_NAME);
        repository.initialize();
        RecordingRefreshListener listener = new RecordingRefreshListener();
        repository.addFeatureRefreshListener(listener);

        // When
        repository.initialize();

        // Then
        assertEquals(1, listener.events.size());
        assertTrue(listener.events.get(0).isSuccessful());
        assertFalse(listener.events.get(0).isFeaturesChanged());
    }

    @Test
    @DisplayName("Notifies legacy callbacks and listeners of a failed load")
    @SuppressWarnings("deprecation")
    void notifiesCallbacksAndListenersOnFailure() {
        // Given
        LocalGbFeatureRepository repository = new LocalGbFeatureRepository("missing-behavior-test-features");
        RecordingRefreshCallback callback = new RecordingRefreshCallback();
        RecordingRefreshListener listener = new RecordingRefreshListener();
        repository.onFeaturesRefresh(callback);
        repository.addFeatureRefreshListener(listener);

        // When
        FeatureFetchException exception = assertThrows(FeatureFetchException.class, () -> repository.initialize());

        // Then
        assertTrue(callback.refreshes.isEmpty());
        assertEquals(1, callback.errors.size());
        assertSame(exception, callback.errors.get(0));
        assertEquals(1, listener.events.size());
        FeatureRefreshEvent event = listener.events.get(0);
        assertFalse(event.isSuccessful());
        assertFalse(event.isFeaturesChanged());
        assertSame(exception, event.getError());
        assertEquals(FeatureRefreshSource.INITIALIZATION, event.getSource());
    }

    @Test
    @DisplayName("Notifies legacy callbacks and listeners of a failure from the retry overload")
    @SuppressWarnings("deprecation")
    void retryOverloadNotifiesOnFailure() {
        // Given
        LocalGbFeatureRepository repository = new LocalGbFeatureRepository("missing-behavior-test-features");
        RecordingRefreshCallback callback = new RecordingRefreshCallback();
        RecordingRefreshListener listener = new RecordingRefreshListener();
        repository.onFeaturesRefresh(callback);
        repository.addFeatureRefreshListener(listener);

        // When
        repository.initialize(true);

        // Then
        assertEquals(1, callback.errors.size());
        assertEquals(1, listener.events.size());
        assertFalse(listener.events.get(0).isSuccessful());
    }

    @Test
    @DisplayName("Stops notifying cleared callbacks and removed listeners")
    @SuppressWarnings("deprecation")
    void stopsNotifyingClearedCallbacksAndRemovedListeners() throws Exception {
        // Given
        writeResource(FEATURES);
        LocalGbFeatureRepository repository = new LocalGbFeatureRepository(FILE_NAME);
        RecordingRefreshCallback callback = new RecordingRefreshCallback();
        RecordingRefreshListener listener = new RecordingRefreshListener();
        repository.onFeaturesRefresh(callback);
        repository.addFeatureRefreshListener(listener);
        repository.clearCallbacks();
        repository.removeFeatureRefreshListener(listener);

        // When
        repository.initialize();

        // Then
        assertTrue(callback.refreshes.isEmpty());
        assertTrue(listener.events.isEmpty());
    }

    @Test
    @DisplayName("Ignores null listeners")
    void ignoresNullListeners() throws Exception {
        // Given
        writeResource(FEATURES);
        LocalGbFeatureRepository repository = new LocalGbFeatureRepository(FILE_NAME);

        repository.addFeatureRefreshListener(null);
        repository.removeFeatureRefreshListener(null);

        // When
        repository.initialize();

        // Then
        assertEquals(FEATURES, repository.getFeaturesJson());
        assertEquals(1, repository.getRefreshSuccessCount());
    }

    @Test
    @DisplayName("Records a successful load in the metrics")
    void recordsSuccessInMetrics() throws Exception {
        // Given
        writeResource(FEATURES);
        LocalGbFeatureRepository repository = new LocalGbFeatureRepository(FILE_NAME);

        // When
        repository.initialize();

        // Then
        assertEquals(1, repository.getRefreshSuccessCount());
        assertEquals(0, repository.getRefreshFailureCount());
        assertEquals(0, repository.getRefreshConsecutiveFailureCount());
        assertEquals(Boolean.FALSE, repository.getLastRefreshLoadedFromCache());
        assertNull(repository.getLastRefreshError());
    }

    @Test
    @DisplayName("Records failed loads in the metrics and resets consecutive failures on success")
    void recordsFailuresInMetrics() throws Exception {
        // Given
        LocalGbFeatureRepository repository = new LocalGbFeatureRepository(FILE_NAME);
        assertThrows(FeatureFetchException.class, () -> repository.initialize());
        assertThrows(FeatureFetchException.class, () -> repository.initialize());

        // When
        long consecutiveBeforeSuccess = repository.getRefreshConsecutiveFailureCount();
        writeResource(FEATURES);
        repository.initialize();

        // Then
        assertEquals(2, consecutiveBeforeSuccess);
        assertEquals(2, repository.getRefreshFailureCount());
        assertEquals(1, repository.getRefreshSuccessCount());
        assertEquals(0, repository.getRefreshConsecutiveFailureCount());
        FeatureFetchException lastError = (FeatureFetchException) repository.getLastRefreshError();
        assertEquals(FeatureFetchErrorCode.CONFIGURATION_ERROR, lastError.getErrorCode());
    }

    @Test
    @DisplayName("Records refresh times from the injected clock")
    void recordsRefreshTimesFromInjectedClock() {
        // Given
        TestClock clock = TestClock.at("2026-01-01T00:00:00Z");
        LocalGbFeatureRepository repository = new LocalGbFeatureRepository("missing-features-for-clock-test", clock);

        // When
        assertThrows(FeatureFetchException.class, () -> repository.initialize());

        // Then
        assertEquals(clock.millis(), repository.getLastRefreshErrorAtMillis());
        assertEquals(clock.millis(), repository.getLastRefreshFailureAtMillis());
    }

    private void writeResource(String content) throws IOException {
        Path resourcesDirectory = Paths.get("src", "main", "resources");
        Files.createDirectories(resourcesDirectory);
        Path file = resourcesDirectory.resolve(FILE_NAME + ".json");
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        createdFiles.add(file);
    }
}
