package growthbook.sdk.java;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import growthbook.sdk.java.callback.FeatureRefreshCallback;
import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.retry.FeatureFetchRetryPolicy;
import growthbook.sdk.java.repository.FeatureRefreshStrategy;
import growthbook.sdk.java.repository.NativeJavaGbFeatureRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.Duration;

class NativeJavaGbFeatureRepositoryTest {
    private static final String API_HOST = "https://cdn.growthbook.io";
    private static final String CLIENT_KEY = "java_NsrWldWd5bxQJZftGsWKl7R2yD2LtAK8C8EUYh9L8";
    private static final FeatureFetchRetryPolicy NO_DELAY_RETRY_POLICY =
            new FeatureFetchRetryPolicy(5, Duration.ZERO, Duration.ZERO);

    private NativeJavaGbFeatureRepository repository;


    @BeforeEach
    void setUp() {
        repository = NativeJavaGbFeatureRepository.builder()
                .apiHost(API_HOST)
                .clientKey(CLIENT_KEY)
                .encryptionKey(null)
                .refreshStrategy(FeatureRefreshStrategy.STALE_WHILE_REVALIDATE)
                .swrTtlSeconds(60)
                .retryPolicy(NO_DELAY_RETRY_POLICY)
                .build();
    }

    @Test
    void canBeConstructed_withNullEncryptionKey() {
        NativeJavaGbFeatureRepository subject = new NativeJavaGbFeatureRepository(
                "https://cdn.growthbook.io",
                "java_NsrWldWd5bxQJZftGsWKl7R2yD2LtAK8C8EUYh9L8",
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertNotNull(subject);
        assertEquals("https://cdn.growthbook.io/api/features/java_NsrWldWd5bxQJZftGsWKl7R2yD2LtAK8C8EUYh9L8", subject.getFeaturesEndpoint());
        assertEquals(FeatureRefreshStrategy.STALE_WHILE_REVALIDATE, subject.getRefreshStrategy());
    }

    @Test
    void canBeConstructed_withEncryptionKey() {
        NativeJavaGbFeatureRepository subject = new NativeJavaGbFeatureRepository(
                "https://cdn.growthbook.io",
                "sdk-862b5mHcP9XPugqD",
                "BhB1wORFmZLTDjbvstvS8w==",
                null,
                null,
                null,
                null,
                null
        );

        assertNotNull(subject);
        assertEquals("https://cdn.growthbook.io/api/features/sdk-862b5mHcP9XPugqD", subject.getFeaturesEndpoint());
        assertEquals("BhB1wORFmZLTDjbvstvS8w==", subject.getEncryptionKey());
    }

    @Test
    void canBeBuilt_withNullEncryptionKey() {
        NativeJavaGbFeatureRepository subject = NativeJavaGbFeatureRepository
                .builder()
                .apiHost("https://cdn.growthbook.io")
                .clientKey("java_NsrWldWd5bxQJZftGsWKl7R2yD2LtAK8C8EUYh9L8")
                .build();

        assertNotNull(subject);
        assertEquals("https://cdn.growthbook.io/api/features/java_NsrWldWd5bxQJZftGsWKl7R2yD2LtAK8C8EUYh9L8", subject.getFeaturesEndpoint());
    }

    @Test
    void canBeBuilt_withEncryptionKey() {
        NativeJavaGbFeatureRepository subject = NativeJavaGbFeatureRepository
                .builder()
                .apiHost("https://cdn.growthbook.io")
                .clientKey("sdk-862b5mHcP9XPugqD")
                .encryptionKey("BhB1wORFmZLTDjbvstvS8w==")
                .build();

        assertNotNull(subject);
        assertEquals("https://cdn.growthbook.io/api/features/sdk-862b5mHcP9XPugqD", subject.getFeaturesEndpoint());
        assertEquals("BhB1wORFmZLTDjbvstvS8w==", subject.getEncryptionKey());
    }

    @Test()
    void cannotBeBuild_withoutClientKey() {
        assertThrows(IllegalArgumentException.class, () -> new NativeJavaGbFeatureRepository(
                API_HOST,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        ));
    }

    @Test
    void getEmptyFeatureJson_withouInvokeInitialize() {
        assertEquals("{}", repository.getFeaturesJson());
    }

    @Test
    public void testOnFeaturesRefresh_AddsListener() {
        FeatureRefreshCallback listener = mock(FeatureRefreshCallback.class);
        repository.onFeaturesRefresh(listener);

        repository.onRefreshSuccess("{}");

        verify(listener, times(1)).onRefresh("{}");
    }

    @Test
    public void testOnFeatureError_AddsListener() {
        FeatureRefreshCallback listener = mock(FeatureRefreshCallback.class);
        repository.onFeaturesRefresh(listener);

        repository.onRefreshFailed(new Exception("Error"));

        verify(listener, times(1)).onError(any(Throwable.class));
    }

    @Test
    public void testClearCallbacks_ClearsAllCallbacks() {
        FeatureRefreshCallback refreshListener = mock(FeatureRefreshCallback.class);
        repository.onFeaturesRefresh(refreshListener);

        repository.clearCallbacks();


        verify(refreshListener, never()).onRefresh(anyString());
    }

    @Test
    void fetchFeaturesFails() throws Exception {
        repository = NativeJavaGbFeatureRepository.builder()
                .apiHost("http://127.0.0.1:1")
                .clientKey(CLIENT_KEY)
                .isCacheDisabled(true)
                .retryPolicy(NO_DELAY_RETRY_POLICY)
                .build();

        assertThrows(FeatureFetchException.class, () -> repository.fetchFeatures());

    }

    @Test
    void initializeFails() throws Exception {
        repository = NativeJavaGbFeatureRepository.builder()
                .apiHost("http://127.0.0.1:1")
                .clientKey(CLIENT_KEY)
                .isCacheDisabled(true)
                .retryPolicy(NO_DELAY_RETRY_POLICY)
                .build();

        assertThrows(FeatureFetchException.class, () -> repository.initialize());
    }
}
