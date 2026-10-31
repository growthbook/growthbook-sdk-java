package growthbook.sdk.java.repository;

import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.exception.FeatureFetchException.FeatureFetchErrorCode;
import growthbook.sdk.java.model.FeatureRefreshEvent;
import growthbook.sdk.java.model.FeatureRefreshSource;
import growthbook.sdk.java.testhelpers.RecordingRefreshListener;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * The refresh events {@link LocalGbFeatureRepository} publishes to listeners.
 */
class LocalGbFeatureRepositoryTest {

    private static final String FILE_NAME = "listener-test-features";

    private final List<Path> createdFiles = new ArrayList<>();

    @AfterEach
    void deleteCreatedFiles() throws IOException {
        for (Path file : createdFiles) {
            Files.deleteIfExists(file);
        }
    }

    @Test
    @DisplayName("A listener receives the metadata of a load that failed because the file is missing")
    void listenerReceivesMissingFileFailureMetadata() {
        // Given
        LocalGbFeatureRepository subject = new LocalGbFeatureRepository("missing-listener-test-features.json");
        RecordingRefreshListener listener = new RecordingRefreshListener();
        subject.addFeatureRefreshListener(listener);

        // When
        FeatureFetchException exception = assertThrows(FeatureFetchException.class, subject::initialize);

        // Then
        assertEquals(FeatureFetchErrorCode.CONFIGURATION_ERROR, exception.getErrorCode());
        assertEquals(1, listener.events.size());
        FeatureRefreshEvent event = listener.events.get(0);
        assertFalse(event.isSuccessful());
        assertFalse(event.isFeaturesChanged());
        assertFalse(event.isLoadedFromCache());
        assertEquals(0, event.getActiveFeatureCount());
        assertEquals(FeatureRefreshSource.INITIALIZATION, event.getSource());
        assertSame(exception, event.getError());
    }

    @ParameterizedTest(name = "content=[{0}]")
    @ValueSource(strings = {"", "null", "{\"feature\":"})
    @DisplayName("A listener receives a failure, and the held features stay, when the file is empty, null or malformed")
    void listenerReceivesInvalidFileFailure(String content) throws Exception {
        // Given
        writeResource(content);
        LocalGbFeatureRepository subject = new LocalGbFeatureRepository(FILE_NAME + ".json");
        RecordingRefreshListener listener = new RecordingRefreshListener();
        subject.addFeatureRefreshListener(listener);

        // When
        FeatureFetchException exception = assertThrows(FeatureFetchException.class, subject::initialize);

        // Then
        assertEquals(FeatureFetchErrorCode.CONFIGURATION_ERROR, exception.getErrorCode());
        assertEquals(1, listener.events.size());
        FeatureRefreshEvent event = listener.events.get(0);
        assertFalse(event.isSuccessful());
        assertFalse(event.isFeaturesChanged());
        assertEquals(FeatureRefreshSource.INITIALIZATION, event.getSource());
        assertSame(exception, event.getError());
        assertEquals("{}", subject.getFeaturesJson());
    }

    private void writeResource(String content) throws IOException {
        Path resourcesDirectory = Paths.get("src", "main", "resources");
        Files.createDirectories(resourcesDirectory);
        Path file = resourcesDirectory.resolve(FILE_NAME + ".json");
        Files.write(file, content.getBytes(StandardCharsets.UTF_8));
        createdFiles.add(file);
    }
}
