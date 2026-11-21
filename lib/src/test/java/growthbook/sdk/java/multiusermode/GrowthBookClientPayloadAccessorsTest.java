package growthbook.sdk.java.multiusermode;

import com.google.gson.JsonObject;
import growthbook.sdk.java.model.Experiment;
import growthbook.sdk.java.model.ExperimentResult;
import growthbook.sdk.java.model.Feature;
import growthbook.sdk.java.multiusermode.configurations.UserContext;
import growthbook.sdk.java.repository.GBFeaturesRepository;
import growthbook.sdk.java.util.PayloadUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Map;

import static growthbook.sdk.java.multiusermode.GrowthBookClientTestFixtures.createDefaultOptions;
import static growthbook.sdk.java.multiusermode.GrowthBookClientTestFixtures.createMockBuilder;
import static growthbook.sdk.java.multiusermode.GrowthBookClientTestFixtures.createMockRepository;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class GrowthBookClientPayloadAccessorsTest {

    private static final String ENCRYPTED_FEATURES =
            "7rvPA94JEsqRo9yPZsdsXg==.bJ8vtYvX+ur3cEUFVkYo1OyWb98oLnMlpeoO0Hs4YPc0EVb7oKX4KNz+Yt6GUMBsieXqtL7oaYzX+kMayZEtV+3bhyDYnS9QBrvalnfxbLExjtnsy8g0pPQHU/P/DPIzO0F+pphcahRfi+3AMTnIreqvkqrcX+MyOwHN56lqEs23Vp4Rsq2qDow/LZmn5kpwMNhMY0DBq7jC+lh2Oyly0g==";

    @Test
    @DisplayName("Verify: accessors return empty (not null) before the client is initialized")
    void accessorsReturnEmptyBeforeInitialize() {
        // Given
        GrowthBookClient client = new GrowthBookClient(createDefaultOptions(null));

        // Then
        assertTrue(client.getFeatures().isEmpty());
        assertTrue(client.getExperiments().isEmpty());
        assertTrue(client.getAllResults().isEmpty());
        assertEquals(0, client.getPayload().size());
        assertEquals(client.getPayload(), client.getDecryptedPayload());
    }

    @Test
    @DisplayName("Verify: getFeatures reflects the initialized repository's features")
    void getFeaturesReflectsRepository() {
        // Given
        GBFeaturesRepository repository = createMockRepository();
        GBFeaturesRepository.GBFeaturesRepositoryBuilder builder = createMockBuilder(repository);

        try (MockedStatic<GBFeaturesRepository> mocked = mockStatic(GBFeaturesRepository.class)) {
            mocked.when(GBFeaturesRepository::builder).thenReturn(builder);
            GrowthBookClient client = new GrowthBookClient(createDefaultOptions(null));
            client.initialize();

            // When
            Map<String, Feature<?>> features = client.getFeatures();

            // Then
            assertEquals(1, features.size());
            assertTrue(features.containsKey("test-feature"));
            assertThrows(UnsupportedOperationException.class, () -> features.put("x", new Feature<>()));
        }
    }

    @Test
    @DisplayName("Verify: getPayload and getDecryptedPayload delegate to the repository payload objects")
    void payloadAccessorsDelegateToRepository() {
        // Given
        JsonObject raw = PayloadUtils.parsePayload("{\"encryptedFeatures\":\"" + ENCRYPTED_FEATURES + "\"}");
        JsonObject decrypted = PayloadUtils.parsePayload("{\"features\":{\"greeting\":{\"defaultValue\":\"hi\"}}}");
        GBFeaturesRepository repository = createMockRepository();
        when(repository.getPayloadObject()).thenReturn(raw);
        when(repository.getDecryptedPayloadObject()).thenReturn(decrypted);
        GBFeaturesRepository.GBFeaturesRepositoryBuilder builder = createMockBuilder(repository);

        try (MockedStatic<GBFeaturesRepository> mocked = mockStatic(GBFeaturesRepository.class)) {
            mocked.when(GBFeaturesRepository::builder).thenReturn(builder);
            GrowthBookClient client = new GrowthBookClient(createDefaultOptions(null));
            client.initialize();

            // Then
            assertEquals(raw, client.getPayload());
            assertTrue(client.getPayload().has("encryptedFeatures"));
            assertEquals(decrypted, client.getDecryptedPayload());
            assertTrue(client.getDecryptedPayload().getAsJsonObject("features").has("greeting"));
            assertNotEquals(client.getPayload(), client.getDecryptedPayload());
        }
    }

    @Test
    @DisplayName("Verify: getExperiments parses the experiments array from the decrypted payload")
    void getExperimentsReadsPayloadExperiments() {
        // Given
        JsonObject decrypted = PayloadUtils.parsePayload(
                "{\"features\":{},\"experiments\":[{\"key\":\"auto-1\",\"variations\":[0,1]}]}");
        GBFeaturesRepository repository = createMockRepository();
        when(repository.getDecryptedPayloadObject()).thenReturn(decrypted);
        GBFeaturesRepository.GBFeaturesRepositoryBuilder builder = createMockBuilder(repository);

        try (MockedStatic<GBFeaturesRepository> mocked = mockStatic(GBFeaturesRepository.class)) {
            mocked.when(GBFeaturesRepository::builder).thenReturn(builder);
            GrowthBookClient client = new GrowthBookClient(createDefaultOptions(null));
            client.initialize();

            // When & Then
            assertEquals(1, client.getExperiments().size());
            assertEquals("auto-1", client.getExperiments().get(0).getKey());
        }
    }

    @Test
    @DisplayName("Verify: getAllResults records the last result of every experiment run")
    void getAllResultsReflectsRuns() {
        // Given
        GBFeaturesRepository repository = createMockRepository();
        GBFeaturesRepository.GBFeaturesRepositoryBuilder builder = createMockBuilder(repository);

        try (MockedStatic<GBFeaturesRepository> mocked = mockStatic(GBFeaturesRepository.class)) {
            mocked.when(GBFeaturesRepository::builder).thenReturn(builder);
            GrowthBookClient client = new GrowthBookClient(createDefaultOptions(null));
            client.initialize();
            Experiment<String> experiment = Experiment.<String>builder()
                    .key("client-exp")
                    .variations(new ArrayList<>(Arrays.asList("control", "variation")))
                    .build();

            // When
            ExperimentResult<String> result = client.run(experiment, UserContext.builder().build());

            // Then
            Map<String, ExperimentResult<?>> results = client.getAllResults();
            assertEquals(result, results.get("client-exp"));
            assertThrows(UnsupportedOperationException.class, () -> results.put("x", null));
        }
    }

    @Test
    @DisplayName("Verify: shutdown clears the results tracked by getAllResults")
    void shutdownClearsResults() {
        // Given
        GBFeaturesRepository repository = createMockRepository();
        GBFeaturesRepository.GBFeaturesRepositoryBuilder builder = createMockBuilder(repository);

        try (MockedStatic<GBFeaturesRepository> mocked = mockStatic(GBFeaturesRepository.class)) {
            mocked.when(GBFeaturesRepository::builder).thenReturn(builder);
            GrowthBookClient client = new GrowthBookClient(createDefaultOptions(null));
            client.initialize();
            client.run(Experiment.<String>builder()
                    .key("client-exp")
                    .variations(new ArrayList<>(Arrays.asList("control", "variation")))
                    .build(), UserContext.builder().build());
            assertFalse(client.getAllResults().isEmpty());

            // When
            client.shutdown();

            // Then
            assertTrue(client.getAllResults().isEmpty());
        }
    }
}
