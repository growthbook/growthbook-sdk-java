package growthbook.sdk.java;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
}
