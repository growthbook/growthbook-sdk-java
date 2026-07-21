package growthbook.sdk.java.multiusermode.configurations;

import growthbook.sdk.java.exception.InvalidOptionsException;
import growthbook.sdk.java.repository.FeatureRefreshStrategy;
import growthbook.sdk.java.sandbox.CacheMode;
import growthbook.sdk.java.sandbox.GbCacheManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OptionsValidatorTest {

    private static Options.OptionsBuilder validOptions() {
        return Options.builder()
                .apiHost("https://cdn.growthbook.io")
                .clientKey("sdk-abc123");
    }

    @Test
    @DisplayName("Verify: accepts a fully valid set of options")
    void acceptsValidOptions() {
        assertDoesNotThrow(() -> OptionsValidator.validate(validOptions().build()));
    }

    @Test
    @DisplayName("Verify: accepts an apiHost without an explicit scheme")
    void acceptsApiHostWithoutScheme() {
        assertDoesNotThrow(() -> OptionsValidator.validate(
                validOptions().apiHost("cdn.growthbook.io").build()));
    }

    @Test
    @DisplayName("Verify: accepts a localhost apiHost with port")
    void acceptsLocalhostApiHost() {
        assertDoesNotThrow(() -> OptionsValidator.validate(
                validOptions().apiHost("http://localhost:8080").build()));
    }

    @Test
    @DisplayName("Verify: treats null options as a no-op")
    void nullOptionsIsNoOp() {
        assertDoesNotThrow(() -> OptionsValidator.validate(null));
    }

    @Test
    @DisplayName("Verify: rejects a missing apiHost")
    void rejectsMissingApiHost() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> OptionsValidator.validate(Options.builder().clientKey("sdk-abc123").build()));
        assertTrue(ex.getMessage().contains("apiHost"));
    }

    @Test
    @DisplayName("Verify: rejects a blank apiHost")
    void rejectsBlankApiHost() {
        assertThrows(IllegalArgumentException.class,
                () -> OptionsValidator.validate(validOptions().apiHost("   ").build()));
    }

    @Test
    @DisplayName("Verify: rejects an apiHost with a non-http(s) scheme")
    void rejectsNonHttpApiHostScheme() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> OptionsValidator.validate(validOptions().apiHost("ftp://cdn.growthbook.io").build()));
        assertTrue(ex.getMessage().contains("http or https"));
    }

    @Test
    @DisplayName("Verify: rejects a malformed apiHost with no host")
    void rejectsMalformedApiHost() {
        assertThrows(IllegalArgumentException.class,
                () -> OptionsValidator.validate(validOptions().apiHost("https://").build()));
    }

    @Test
    @DisplayName("Verify: rejects a missing clientKey")
    void rejectsMissingClientKey() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> OptionsValidator.validate(Options.builder().apiHost("https://cdn.growthbook.io").build()));
        assertTrue(ex.getMessage().contains("clientKey"));
    }

    @Test
    @DisplayName("Verify: rejects a non-positive refresh interval")
    void rejectsNonPositiveRefreshInterval() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> OptionsValidator.validate(validOptions().swrTtlSeconds(0).build()));
        assertTrue(ex.getMessage().contains("refresh interval"));
    }

    @Test
    @DisplayName("Verify: rejects a negative backgroundFetchInterval")
    void rejectsNegativeBackgroundFetchInterval() {
        assertThrows(IllegalArgumentException.class,
                () -> OptionsValidator.validate(
                        validOptions().backgroundFetchInterval(Duration.ofSeconds(-1)).build()));
    }

    @Test
    @DisplayName("Verify: rejects a non-positive remoteEvalCacheTtlSeconds")
    void rejectsNonPositiveRemoteEvalCacheTtl() {
        assertThrows(IllegalArgumentException.class,
                () -> OptionsValidator.validate(validOptions().remoteEvalCacheTtlSeconds(0).build()));
    }

    @Test
    @DisplayName("Verify: rejects a cacheManager when CacheMode.NONE disables caching")
    void rejectsCacheManagerWhenCacheDisabled() {
        GbCacheManager cacheManager = Mockito.mock(GbCacheManager.class);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> OptionsValidator.validate(
                        validOptions().cacheManager(cacheManager).cacheMode(CacheMode.NONE).build()));
        assertTrue(ex.getMessage().contains("caching is disabled"));
    }

    @Test
    @DisplayName("Verify: rejects a cacheManager when the isCacheDisabled flag is set")
    void rejectsCacheManagerWhenIsCacheDisabledFlagSet() {
        GbCacheManager cacheManager = Mockito.mock(GbCacheManager.class);
        assertThrows(IllegalArgumentException.class,
                () -> OptionsValidator.validate(
                        validOptions().cacheManager(cacheManager).isCacheDisabled(true).build()));
    }

    @Test
    @DisplayName("Verify: rejects CacheMode.CUSTOM without a cacheManager")
    void rejectsCustomCacheModeWithoutManager() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> OptionsValidator.validate(validOptions().cacheMode(CacheMode.CUSTOM).build()));
        assertTrue(ex.getMessage().contains("CacheMode.CUSTOM"));
    }

    @Test
    @DisplayName("Verify: accepts CacheMode.CUSTOM when a cacheManager is supplied")
    void acceptsCustomCacheModeWithManager() {
        GbCacheManager cacheManager = Mockito.mock(GbCacheManager.class);
        assertDoesNotThrow(() -> OptionsValidator.validate(
                validOptions().cacheMode(CacheMode.CUSTOM).cacheManager(cacheManager).build()));
    }

    @Test
    @DisplayName("Verify: reports every violation at once instead of failing on the first")
    void reportsAllViolationsTogether() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> OptionsValidator.validate(Options.builder().swrTtlSeconds(0).build()));
        assertTrue(ex.getMessage().contains("apiHost"), ex.getMessage());
        assertTrue(ex.getMessage().contains("clientKey"), ex.getMessage());
        assertTrue(ex.getMessage().contains("refresh interval"), ex.getMessage());
    }

    @Test
    @DisplayName("Verify: findViolations returns an empty list for valid options")
    void findViolationsReturnsEmptyForValidOptions() {
        assertTrue(OptionsValidator.findViolations(validOptions().build()).isEmpty());
    }

    @Test
    @DisplayName("Verify: findViolations lists each problem without throwing")
    void findViolationsListsEachProblem() {
        List<String> violations = OptionsValidator.findViolations(
                Options.builder().swrTtlSeconds(0).build());
        assertEquals(3, violations.size(), violations.toString());
    }

    @Test
    @DisplayName("Verify: findViolations returns an empty list for null options")
    void findViolationsHandlesNull() {
        assertTrue(OptionsValidator.findViolations(null).isEmpty());
    }

    @Test
    @DisplayName("Verify: throws InvalidOptionsException carrying the structured violations")
    void throwsInvalidOptionsExceptionWithViolations() {
        InvalidOptionsException ex = assertThrows(InvalidOptionsException.class,
                () -> OptionsValidator.validate(Options.builder().swrTtlSeconds(0).build()));
        assertEquals(3, ex.getViolations().size(), ex.getViolations().toString());
    }

    @Test
    @DisplayName("Verify: aggregates remote-eval incompatibilities with general violations")
    void aggregatesRemoteEvalViolations() {
        GbCacheManager cacheManager = Mockito.mock(GbCacheManager.class);
        List<String> violations = OptionsValidator.findViolations(validOptions()
                .remoteEval(true)
                .decryptionKey("secret")
                .refreshStrategy(FeatureRefreshStrategy.STALE_WHILE_REVALIDATE)
                .build());

        assertTrue(violations.stream().anyMatch(v -> v.contains("decryptionKey")), violations.toString());
        assertTrue(violations.stream().anyMatch(v -> v.contains("stale-while-revalidate")), violations.toString());
        assertTrue(violations.stream().noneMatch(v -> v.equals("apiHost is required")), violations.toString());
    }

    @Test
    @DisplayName("Verify: does not add remote-eval violations when remote eval is disabled")
    void skipsRemoteEvalViolationsWhenDisabled() {
        assertDoesNotThrow(() -> OptionsValidator.validate(
                validOptions().decryptionKey("secret").build()));
    }
}
