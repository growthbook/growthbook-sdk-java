package growthbook.sdk.java.multiusermode.internal;

import growthbook.sdk.java.exception.FeatureFetchException;
import growthbook.sdk.java.exception.GrowthBookInitializationException;
import growthbook.sdk.java.multiusermode.configurations.Options;
import growthbook.sdk.java.repository.GBFeaturesRepository;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

/**
 * Provides the feature repository owned by {@code GrowthBookClient}.
 *
 * <p>This class hides lazy repository creation, initialization reuse, failed-initialization retry,
 * and shutdown cleanup from the public client facade.
 */
@Slf4j
public final class FeatureRepositoryProvider {

    private final Options options;
    private final Consumer<GBFeaturesRepository> onRepositoryReady;
    private final Consumer<GBFeaturesRepository> configureRepository;
    private final GrowthBookClientRepositoryFactory repositoryFactory;
    private final AtomicReference<CompletableFuture<GBFeaturesRepository>> repositoryState = new AtomicReference<>();

    /**
     * Creates a provider using the default GrowthBook repository factory.
     *
     * @param options client options used to create the repository
     * @param configureRepository callback invoked before repository initialization
     * @param onRepositoryReady callback invoked after repository initialization succeeds
     */
    public FeatureRepositoryProvider(
            Options options,
            Consumer<GBFeaturesRepository> configureRepository,
            Consumer<GBFeaturesRepository> onRepositoryReady
    ) {
        this(options, new GrowthBookClientRepositoryFactory(), configureRepository, onRepositoryReady);
    }

    FeatureRepositoryProvider(
            Options options,
            GrowthBookClientRepositoryFactory repositoryFactory,
            Consumer<GBFeaturesRepository> configureRepository,
            Consumer<GBFeaturesRepository> onRepositoryReady
    ) {
        this.options = options;
        this.repositoryFactory = repositoryFactory;
        this.configureRepository = configureRepository;
        this.onRepositoryReady = onRepositoryReady;
    }

    /**
     * Initializes the repository if needed and returns the initialized repository.
     *
     * @return initialized repository, or null when initialization failed or was cancelled
     */
    public GBFeaturesRepository initialize() {
        try {
            return repositoryFuture().join();
        } catch (CancellationException e) {
            log.debug("GrowthBookClient repository initialization was cancelled.", e);
            return null;
        } catch (CompletionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            log.error("Failed to initialize GrowthBookClient repository", cause);
            return null;
        }
    }

    /**
     * Returns the initialized repository when available.
     *
     * @return initialized repository, or null when the client is not ready
     */
    public GBFeaturesRepository currentRepository() {
        CompletableFuture<GBFeaturesRepository> future = repositoryState.get();
        if (future == null || !future.isDone()) {
            return null;
        }

        try {
            return future.getNow(null);
        } catch (RuntimeException e) {
            log.warn("GrowthBookClient repository initialization did not complete successfully.", e);
            return null;
        }
    }

    /**
     * Detaches and shuts down the current repository.
     */
    public void shutdown() {
        CompletableFuture<GBFeaturesRepository> future = repositoryState.get();
        if (future == null || !repositoryState.compareAndSet(future, null)) {
            return;
        }

        if (!future.isDone()) {
            future.cancel(false);
            return;
        }

        try {
            GBFeaturesRepository repository = future.getNow(null);
            if (repository != null) {
                shutdownRepository(repository);
                log.info("Repository shut down");
            }
        } catch (CancellationException | CompletionException e) {
            log.debug("Skipping repository shutdown because initialization did not complete successfully.", e);
        }
    }

    private CompletableFuture<GBFeaturesRepository> repositoryFuture() {
        while (true) {
            CompletableFuture<GBFeaturesRepository> currentFuture = repositoryState.get();
            if (currentFuture != null) {
                return currentFuture;
            }

            CompletableFuture<GBFeaturesRepository> newFuture = new CompletableFuture<>();
            if (repositoryState.compareAndSet(null, newFuture)) {
                runInitialization(newFuture);
                return newFuture;
            }
        }
    }

    private void runInitialization(CompletableFuture<GBFeaturesRepository> future) {
        GBFeaturesRepository repository = null;
        try {
            repository = repositoryFactory.create(this.options);
            if (!activateRepository(future, repository)) {
                shutdownRepository(repository);
                return;
            }

            completeInitialization(future, repository);
        } catch (RuntimeException initializationException) {
            if (repository != null) {
                shutdownRepository(repository);
            }
            future.completeExceptionally(initializationException);
            repositoryState.compareAndSet(future, null);
        }
    }

    private boolean activateRepository(
            CompletableFuture<GBFeaturesRepository> future,
            GBFeaturesRepository repository
    ) {
        return runIfInitializationActive(future, () -> configure(repository))
                && runIfInitializationActive(future, () -> initializeRepository(repository))
                && runIfInitializationActive(future, () -> notifyReady(repository));
    }

    private boolean runIfInitializationActive(CompletableFuture<GBFeaturesRepository> future, Runnable step) {
        if (!isInitializationActive(future)) {
            return false;
        }

        step.run();
        return true;
    }

    private boolean isInitializationActive(CompletableFuture<GBFeaturesRepository> future) {
        return !future.isCancelled() && repositoryState.get() == future;
    }

    private void configure(GBFeaturesRepository repository) {
        if (configureRepository != null) {
            configureRepository.accept(repository);
        }
    }

    private void notifyReady(GBFeaturesRepository repository) {
        if (onRepositoryReady != null) {
            onRepositoryReady.accept(repository);
        }
    }

    private void completeInitialization(CompletableFuture<GBFeaturesRepository> future,
                                        GBFeaturesRepository repository) {
        if (future.complete(repository)) {
            log.info("GrowthBookClient initialized repository and registered feature refresh handlers.");
            return;
        }

        shutdownRepository(repository);
    }

    private void initializeRepository(GBFeaturesRepository repository) {
        try {
            repository.initialize();
        } catch (FeatureFetchException e) {
            throw new GrowthBookInitializationException(e);
        }
    }

    private void shutdownRepository(GBFeaturesRepository repository) {
        try {
            repository.shutdown();
        } catch (RuntimeException shutdownException) {
            log.warn("Failed to shut down repository", shutdownException);
        }
    }
}
