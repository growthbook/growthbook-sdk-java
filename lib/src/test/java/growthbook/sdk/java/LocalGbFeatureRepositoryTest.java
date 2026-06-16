package growthbook.sdk.java;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.listener.FeatureRefreshListener;
import growthbook.sdk.java.model.FeatureRefreshEvent;
import growthbook.sdk.java.model.FeatureRefreshSource;
import growthbook.sdk.java.repository.LocalGbFeatureRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

class LocalGbFeatureRepositoryTest {
    private LocalGbFeatureRepository repository;
    private static final String TEST_JSON_PATH = "/test_features.json";
    private static final String VALID_JSON_CONTENT = "{\"feature\":\"enabled\"}";

    @BeforeEach
    void setUp() {
        repository = spy(new LocalGbFeatureRepository(TEST_JSON_PATH));
    }

    @Test
    void testInitialize_Success() throws Exception {

        repository = mock(LocalGbFeatureRepository.class);

        repository.initialize();
        when(repository.getFeaturesJson()).thenReturn(VALID_JSON_CONTENT);

        assertEquals(VALID_JSON_CONTENT, repository.getFeaturesJson());
    }

    @Test
    void testInitialize_FileNotFound() {
        LocalGbFeatureRepository repo = new LocalGbFeatureRepository("non_existent_file.json");

        assertThrows(FeatureFetchException.class, repo::initialize);
    }

    @Test
    void testGetFeaturesJson_EmptyInitially() {
        String featuresJson = repository.getFeaturesJson();

        assertEquals("{}", featuresJson);
    }

    @Test
    void testFeatureRefreshListener_receivesInitializationMetadata() throws Exception {
        Path localFeaturesPath = writeLocalResource(
                "listener-test-features.json",
                "{\"local-feature\":{\"defaultValue\":true}}"
        );
        try {
            LocalGbFeatureRepository subject = new LocalGbFeatureRepository("listener-test-features.json");
            FeatureRefreshListener listener = mock(FeatureRefreshListener.class);
            subject.addFeatureRefreshListener(listener);

            subject.initialize();

            ArgumentCaptor<FeatureRefreshEvent> eventCaptor = ArgumentCaptor.forClass(FeatureRefreshEvent.class);
            verify(listener).onRefresh(eventCaptor.capture());
            FeatureRefreshEvent event = eventCaptor.getValue();
            assertTrue(event.isSuccessful());
            assertTrue(event.isFeaturesChanged());
            assertFalse(event.isLoadedFromCache());
            assertEquals(1, event.getActiveFeatureCount());
            assertEquals(FeatureRefreshSource.INITIALIZATION, event.getSource());
            assertTrue(event.getDurationMillis() >= 0);
        } finally {
            Files.deleteIfExists(localFeaturesPath);
        }
    }

    @Test
    void testFeatureRefreshListener_receivesInitializationFailureMetadata() {
        LocalGbFeatureRepository subject = new LocalGbFeatureRepository("missing-listener-test-features.json");
        FeatureRefreshListener listener = mock(FeatureRefreshListener.class);
        subject.addFeatureRefreshListener(listener);

        assertThrows(FeatureFetchException.class, subject::initialize);

        ArgumentCaptor<FeatureRefreshEvent> eventCaptor = ArgumentCaptor.forClass(FeatureRefreshEvent.class);
        verify(listener).onRefresh(eventCaptor.capture());
        FeatureRefreshEvent event = eventCaptor.getValue();
        assertFalse(event.isSuccessful());
        assertFalse(event.isFeaturesChanged());
        assertFalse(event.isLoadedFromCache());
        assertEquals(0, event.getActiveFeatureCount());
        assertEquals(FeatureRefreshSource.INITIALIZATION, event.getSource());
        assertNotNull(event.getError());
    }

    private Path writeLocalResource(String fileName, String content) throws Exception {
        Path resourcesDirectory = Paths.get("src", "main", "resources");
        Files.createDirectories(resourcesDirectory);
        Path filePath = resourcesDirectory.resolve(fileName);
        Files.write(filePath, content.getBytes(StandardCharsets.UTF_8));
        return filePath;
    }
}
