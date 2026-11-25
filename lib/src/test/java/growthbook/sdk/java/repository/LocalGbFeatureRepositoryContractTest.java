package growthbook.sdk.java.repository;

import growthbook.sdk.java.exception.FeatureFetchException.FeatureFetchErrorCode;
import org.junit.jupiter.api.AfterEach;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

class LocalGbFeatureRepositoryContractTest extends FeaturesRepositoryContractTest<LocalGbFeatureRepository> {

    private static final String FILE_NAME = "contract-test-features";
    private static final Path FILE = Paths.get("src", "main", "resources", FILE_NAME + ".json");

    @AfterEach
    void deleteFeaturesFile() throws IOException {
        Files.deleteIfExists(FILE);
    }

    @Override
    LocalGbFeatureRepository createRepository() {
        return new LocalGbFeatureRepository(FILE_NAME);
    }

    @Override
    void givenSourceHolds(String featuresJson) throws IOException {
        Files.createDirectories(FILE.getParent());
        Files.write(FILE, featuresJson.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    void givenSourceIsUnavailable() throws IOException {
        Files.deleteIfExists(FILE);
    }

    @Override
    FeatureFetchErrorCode unavailableSourceErrorCode() {
        return FeatureFetchErrorCode.CONFIGURATION_ERROR;
    }
}
