package growthbook.sdk.java.multiusermode.configurations;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import growthbook.sdk.java.exception.InvalidOptionsException;
import growthbook.sdk.java.repository.FeatureRefreshStrategy;
import growthbook.sdk.java.sandbox.CacheMode;
import growthbook.sdk.java.testhelpers.InMemoryCacheManager;
import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class OptionsValidatorTest {

    private static final String CACHE_DISABLED_VIOLATION = "a cacheManager was supplied but caching is disabled "
            + "(isCacheDisabled=true or CacheMode.NONE); remove one of them";

    @ParameterizedTest(name = "apiHost \"{0}\"")
    @ValueSource(strings = {"https://cdn.growthbook.io", "cdn.growthbook.io", "http://localhost:8080"})
    @DisplayName("Finds no violations for valid options with an http(s), scheme-less or localhost apiHost")
    void findsNoViolationsForValidOptions(String apiHost) {
        // Given
        Options options = validOptions().apiHost(apiHost).build();

        // When
        List<String> violations = OptionsValidator.findViolations(options);

        // Then
        assertEquals(Collections.<String>emptyList(), violations);
    }

    @Test
    @DisplayName("Validating valid options does not throw")
    void validatingValidOptionsDoesNotThrow() {
        // Given
        Options options = validOptions().build();

        // When / Then
        assertDoesNotThrow(() -> OptionsValidator.validate(options));
    }

    @Test
    @DisplayName("Accepts a zero sticky bucket timeout, which disables the timeout")
    void acceptsZeroStickyBucketTimeout() {
        // Given
        Options options = validOptions().stickyBucketTimeout(Duration.ZERO).build();

        // When
        List<String> violations = OptionsValidator.findViolations(options);

        // Then
        assertEquals(Collections.<String>emptyList(), violations);
    }

    @Test
    @DisplayName("Validating null options does not throw")
    void validatingNullOptionsDoesNotThrow() {
        // When / Then
        assertDoesNotThrow(() -> OptionsValidator.validate(null));
    }

    @Test
    @DisplayName("Finds no violations for null options")
    void findsNoViolationsForNullOptions() {
        // When
        List<String> violations = OptionsValidator.findViolations(null);

        // Then
        assertEquals(Collections.<String>emptyList(), violations);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidOptions")
    @DisplayName("Rejects an invalid option with a violation naming it")
    void rejectsInvalidOptionWithViolationNamingIt(String description, Options options, String expectedViolation) {
        // When
        InvalidOptionsException exception = assertThrows(InvalidOptionsException.class,
                () -> OptionsValidator.validate(options));

        // Then
        assertEquals(Collections.singletonList(expectedViolation), exception.getViolations());
        assertEquals("Invalid GrowthBook options: " + expectedViolation, exception.getMessage());
    }

    static Stream<Arguments> invalidOptions() {
        return Stream.of(
                Arguments.of("missing apiHost",
                        Options.builder().clientKey("sdk-abc123").build(),
                        "apiHost is required"),
                Arguments.of("blank apiHost",
                        validOptions().apiHost("   ").build(),
                        "apiHost is required"),
                Arguments.of("non-http apiHost scheme",
                        validOptions().apiHost("ftp://cdn.growthbook.io").build(),
                        "apiHost must use http or https scheme: ftp://cdn.growthbook.io"),
                Arguments.of("apiHost without a host",
                        validOptions().apiHost("https://").build(),
                        "apiHost is not a valid URL: https://"),
                Arguments.of("missing clientKey",
                        Options.builder().apiHost("https://cdn.growthbook.io").build(),
                        "clientKey is required"),
                Arguments.of("zero refresh interval",
                        validOptions().swrTtlSeconds(0).build(),
                        "refresh interval (swrTtlSeconds) must be greater than 0, but was 0"),
                Arguments.of("negative background fetch interval",
                        validOptions().backgroundFetchInterval(Duration.ofSeconds(-1)).build(),
                        "backgroundFetchInterval must not be negative, but was PT-1S"),
                Arguments.of("negative sticky bucket timeout",
                        validOptions().stickyBucketTimeout(Duration.ofMillis(-1)).build(),
                        "stickyBucketTimeout must not be negative, was PT-0.001S"),
                Arguments.of("zero remote-eval cache TTL",
                        validOptions().remoteEvalCacheTtlSeconds(0).build(),
                        "remoteEvalCacheTtlSeconds must be greater than 0 when set, but was 0"),
                Arguments.of("cache manager with CacheMode.NONE",
                        validOptions().cacheManager(new InMemoryCacheManager()).cacheMode(CacheMode.NONE).build(),
                        CACHE_DISABLED_VIOLATION),
                Arguments.of("cache manager with isCacheDisabled",
                        validOptions().cacheManager(new InMemoryCacheManager()).isCacheDisabled(true).build(),
                        CACHE_DISABLED_VIOLATION),
                Arguments.of("CacheMode.CUSTOM without a cache manager",
                        validOptions().cacheMode(CacheMode.CUSTOM).build(),
                        "CacheMode.CUSTOM requires a cacheManager to be supplied")
        );
    }

    @Test
    @DisplayName("Finds no violations for CacheMode.CUSTOM with a cache manager")
    void findsNoViolationsForCustomCacheModeWithManager() {
        // Given
        Options options = validOptions().cacheMode(CacheMode.CUSTOM).cacheManager(new InMemoryCacheManager()).build();

        // When
        List<String> violations = OptionsValidator.findViolations(options);

        // Then
        assertEquals(Collections.<String>emptyList(), violations);
    }

    @Test
    @DisplayName("Reports every violation at once instead of failing on the first")
    void reportsEveryViolationAtOnce() {
        // Given
        Options options = Options.builder().swrTtlSeconds(0).build();

        // When
        InvalidOptionsException exception = assertThrows(InvalidOptionsException.class,
                () -> OptionsValidator.validate(options));

        // Then
        List<String> expected = Arrays.asList(
                "apiHost is required",
                "clientKey is required",
                "refresh interval (swrTtlSeconds) must be greater than 0, but was 0");
        assertEquals(expected, exception.getViolations());
        assertEquals("Invalid GrowthBook options: " + String.join("; ", expected), exception.getMessage());
    }

    @Test
    @DisplayName("Finding violations lists each problem without throwing")
    void findingViolationsListsEachProblem() {
        // Given
        Options options = Options.builder().swrTtlSeconds(0).build();

        // When
        List<String> violations = OptionsValidator.findViolations(options);

        // Then
        assertEquals(Arrays.asList(
                "apiHost is required",
                "clientKey is required",
                "refresh interval (swrTtlSeconds) must be greater than 0, but was 0"), violations);
    }

    @Test
    @DisplayName("Adds remote-eval incompatibilities to the general violations")
    void addsRemoteEvalIncompatibilities() {
        // Given
        Options options = validOptions()
                .remoteEval(true)
                .decryptionKey("secret")
                .refreshStrategy(FeatureRefreshStrategy.STALE_WHILE_REVALIDATE)
                .build();

        // When
        List<String> violations = OptionsValidator.findViolations(options);

        // Then
        assertEquals(Arrays.asList(
                "decryptionKey is not supported when remoteEval is enabled",
                "stale-while-revalidate is not supported when remoteEval is enabled",
                "remoteEval requires a self-hosted GrowthBook proxy or edge API host"), violations);
    }

    @Test
    @DisplayName("Does not add remote-eval violations when remote eval is disabled")
    void doesNotAddRemoteEvalViolationsWhenDisabled() {
        // Given
        Options options = validOptions().decryptionKey("secret").build();

        // When
        List<String> violations = OptionsValidator.findViolations(options);

        // Then
        assertEquals(Collections.<String>emptyList(), violations);
    }

    private static Options.OptionsBuilder validOptions() {
        return Options.builder()
                .apiHost("https://cdn.growthbook.io")
                .clientKey("sdk-abc123");
    }
}
