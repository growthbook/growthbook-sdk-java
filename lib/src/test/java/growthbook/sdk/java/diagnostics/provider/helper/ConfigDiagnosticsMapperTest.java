package growthbook.sdk.java.diagnostics.provider.helper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import growthbook.sdk.java.diagnostics.model.ConfigDiagnostics;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.repository.GBFeaturesRepository;
import growthbook.sdk.java.retry.FeatureFetchRetryPolicy;
import growthbook.sdk.java.stickyBucketing.InMemoryStickyBucketServiceImpl;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class ConfigDiagnosticsMapperTest {

    private final ConfigDiagnosticsMapper mapper = new ConfigDiagnosticsMapper();

    @Test
    @DisplayName("Reports the SDK defaults when neither options nor a repository configure anything")
    void reportsDefaultsWithoutConfiguration() {
        // Given
        Options options = Options.builder().build();

        // When
        ConfigDiagnostics config = mapper.map(options, null);

        // Then
        ConfigDiagnostics expected = ConfigDiagnostics.builder()
                .apiHost("https://cdn.growthbook.io")
                .featuresEndpoint(null)
                .eventsEndpoint(null)
                .clientKeyMasked(null)
                .encryptionConfigured(false)
                .swrTtlSeconds(60)
                .backgroundFetchIntervalMillis(null)
                .retryMaxAttempts(5)
                .enabled(true)
                .qaMode(false)
                .stickyBucketingEnabled(false)
                .build();
        assertEquals(expected, config);
    }

    @Test
    @DisplayName("Reports the options configuration with the client key masked when there is no repository")
    void reportsOptionsConfigurationWithoutRepository() {
        // Given
        Options options = Options.builder()
                .apiHost("https://gb.example.test")
                .clientKey("sdk-abcdef123456")
                .decryptionKey("placeholder-decryption-key")
                .swrTtlSeconds(30)
                .backgroundFetchInterval(Duration.ofSeconds(2))
                .retryPolicy(new FeatureFetchRetryPolicy(3, Duration.ofSeconds(1), Duration.ofSeconds(2)))
                .enabled(false)
                .isQaMode(true)
                .stickyBucketService(new InMemoryStickyBucketServiceImpl())
                .build();

        // When
        ConfigDiagnostics config = mapper.map(options, null);

        // Then
        ConfigDiagnostics expected = ConfigDiagnostics.builder()
                .apiHost("https://gb.example.test")
                .featuresEndpoint("https://gb.example.test/api/features/sdk-...3456")
                .eventsEndpoint("https://gb.example.test/sub/sdk-...3456")
                .clientKeyMasked("sdk-...3456")
                .encryptionConfigured(true)
                .swrTtlSeconds(30)
                .backgroundFetchIntervalMillis(2000L)
                .retryMaxAttempts(3)
                .enabled(false)
                .qaMode(true)
                .stickyBucketingEnabled(true)
                .build();
        assertEquals(expected, config);
    }

    @Test
    @DisplayName("Prefers the repository's endpoints and settings over the options")
    void prefersRepositorySettingsOverOptions() {
        // Given
        Options options = Options.builder()
                .apiHost("https://options.example.test")
                .decryptionKey("placeholder-decryption-key")
                .swrTtlSeconds(10)
                .backgroundFetchInterval(Duration.ofSeconds(1))
                .retryPolicy(new FeatureFetchRetryPolicy(4, Duration.ZERO, Duration.ZERO))
                .build();
        GBFeaturesRepository repository = mock(GBFeaturesRepository.class);
        when(repository.getFeaturesEndpoint()).thenReturn("https://repo.example.test/api/features/sdk-repo987654");
        when(repository.getEventsEndpoint()).thenReturn("https://repo.example.test/sub/sdk-repo987654");
        when(repository.getSwrTtlSeconds()).thenReturn(45);
        when(repository.getBackgroundFetchInterval()).thenReturn(Duration.ofSeconds(5));
        when(repository.getRetryPolicy()).thenReturn(new FeatureFetchRetryPolicy(2, Duration.ZERO, Duration.ZERO));

        // When
        ConfigDiagnostics config = mapper.map(options, repository);

        // Then
        ConfigDiagnostics expected = ConfigDiagnostics.builder()
                .apiHost("https://repo.example.test")
                .featuresEndpoint("https://repo.example.test/api/features/sdk-...7654")
                .eventsEndpoint("https://repo.example.test/sub/sdk-...7654")
                .clientKeyMasked("sdk-...7654")
                .encryptionConfigured(false)
                .swrTtlSeconds(45)
                .backgroundFetchIntervalMillis(5000L)
                .retryMaxAttempts(2)
                .enabled(true)
                .qaMode(false)
                .stickyBucketingEnabled(false)
                .build();
        assertEquals(expected, config);
    }

    @Test
    @DisplayName("Falls back to the options for every setting the repository does not provide")
    void fallsBackToOptionsForSettingsRepositoryLacks() {
        // Given
        Options options = Options.builder()
                .apiHost("https://options.example.test")
                .clientKey("sdk-opts12345678")
                .swrTtlSeconds(10)
                .backgroundFetchInterval(Duration.ofSeconds(1))
                .retryPolicy(new FeatureFetchRetryPolicy(4, Duration.ZERO, Duration.ZERO))
                .build();
        GBFeaturesRepository repository = mock(GBFeaturesRepository.class);
        when(repository.getDecryptionKey()).thenReturn("placeholder-decryption-key");
        when(repository.getSwrTtlSeconds()).thenReturn(null);
        when(repository.getBackgroundFetchInterval()).thenReturn(null);

        // When
        ConfigDiagnostics config = mapper.map(options, repository);

        // Then
        ConfigDiagnostics expected = ConfigDiagnostics.builder()
                .apiHost("https://options.example.test")
                .featuresEndpoint("https://options.example.test/api/features/sdk-...5678")
                .eventsEndpoint("https://options.example.test/sub/sdk-...5678")
                .clientKeyMasked("sdk-...5678")
                .encryptionConfigured(true)
                .swrTtlSeconds(10)
                .backgroundFetchIntervalMillis(1000L)
                .retryMaxAttempts(4)
                .enabled(true)
                .qaMode(false)
                .stickyBucketingEnabled(false)
                .build();
        assertEquals(expected, config);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpointsWithoutUsableHost")
    @DisplayName("Takes the API host from the options when the repository endpoint has no host before the features path")
    void takesApiHostFromOptionsWhenRepositoryEndpointHasNoHost(String featuresEndpoint, String expectedClientKeyMasked) {
        // Given
        Options options = Options.builder().apiHost("https://options.example.test").build();
        GBFeaturesRepository repository = mock(GBFeaturesRepository.class);
        when(repository.getFeaturesEndpoint()).thenReturn(featuresEndpoint);

        // When
        ConfigDiagnostics config = mapper.map(options, repository);

        // Then
        assertEquals("https://options.example.test", config.getApiHost());
        assertEquals(expectedClientKeyMasked, config.getClientKeyMasked());
    }

    static Stream<Arguments> endpointsWithoutUsableHost() {
        return Stream.of(
                Arguments.of("/api/features/sdk-path12345678", "sdk-...5678"),
                Arguments.of("https://proxy.example.test/custom-features", null)
        );
    }
}
