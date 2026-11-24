package growthbook.sdk.java.multiusermode.internal;

import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.remoteeval.RemoteEvalService;
import growthbook.sdk.java.repository.GBFeaturesRepository;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Duration;
import java.util.concurrent.Executor;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Verifies that {@code Options.requestTimeout} reaches the HTTP clients the multi-user client builds. */
class RequestTimeoutPropagationTest {

    private static final Duration TIMEOUT = Duration.ofMillis(1_234);

    @Test
    @DisplayName("The repository factory passes Options.requestTimeout to the features client")
    void repositoryFactoryAppliesRequestTimeout() throws Exception {
        GBFeaturesRepository repository = new GrowthBookClientRepositoryFactory().create(options());
        try {
            OkHttpClient client = field(GBFeaturesRepository.class, repository, "okHttpClient");
            assertEquals(1_234, client.callTimeoutMillis());
            assertEquals(1_234, client.readTimeoutMillis());
        } finally {
            repository.shutdown();
        }
    }

    @Test
    @DisplayName("The remote-eval coordinator passes Options.requestTimeout to the remote-eval client")
    void remoteEvalCoordinatorAppliesRequestTimeout() throws Exception {
        RemoteEvalCoordinator coordinator =
                new RemoteEvalCoordinator(options(), new FeatureRefreshListenerRegistry((Executor) null));
        try {
            OkHttpClient client = field(RemoteEvalService.class, coordinator.getService(), "okHttpClient");
            assertEquals(1_234, client.callTimeoutMillis());
            assertEquals(1_234, client.readTimeoutMillis());
        } finally {
            coordinator.shutdown();
        }
    }

    private static Options options() {
        return Options.builder()
                .apiHost("https://cdn.growthbook.io")
                .clientKey("sdk-propagation")
                .isCacheDisabled(true)
                .requestTimeout(TIMEOUT)
                .build();
    }

    @SuppressWarnings("unchecked")
    private static <T> T field(Class<?> type, Object target, String name) throws Exception {
        Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(target);
    }
}
