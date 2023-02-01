package growthbook.sdk.java;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GBFeaturesRepositoryTest {

    @Test
    void canBeConstructed_withNullEncryptionKey() {
        GBFeaturesRepository subject = new GBFeaturesRepository(
            "https://cdn.growthbook.io/api/features/java_NsrWldWd5bxQJZftGsWKl7R2yD2LtAK8C8EUYh9L8",
            null
        );

        assertNotNull(subject);
        assertEquals("https://cdn.growthbook.io/api/features/java_NsrWldWd5bxQJZftGsWKl7R2yD2LtAK8C8EUYh9L8", subject.getEndpoint());
    }

    @Test
    void canBeConstructed_withEncryptionKey() {
        GBFeaturesRepository subject = new GBFeaturesRepository(
            "https://cdn.growthbook.io/api/features/sdk-862b5mHcP9XPugqD",
            "BhB1wORFmZLTDjbvstvS8w=="
        );

        assertNotNull(subject);
        assertEquals("https://cdn.growthbook.io/api/features/sdk-862b5mHcP9XPugqD", subject.getEndpoint());
        assertEquals("BhB1wORFmZLTDjbvstvS8w==", subject.getEncryptionKey());
    }

    @Test
    void canBeBuilt_withNullEncryptionKey() {
        GBFeaturesRepository subject = GBFeaturesRepository
            .builder()
            .endpoint("https://cdn.growthbook.io/api/features/java_NsrWldWd5bxQJZftGsWKl7R2yD2LtAK8C8EUYh9L8")
            .build();

        assertNotNull(subject);
        assertEquals("https://cdn.growthbook.io/api/features/java_NsrWldWd5bxQJZftGsWKl7R2yD2LtAK8C8EUYh9L8", subject.getEndpoint());
    }

    @Test
    void canBeBuilt_withEncryptionKey() {
        GBFeaturesRepository subject = GBFeaturesRepository
            .builder()
            .endpoint("https://cdn.growthbook.io/api/features/sdk-862b5mHcP9XPugqD")
            .encryptionKey("BhB1wORFmZLTDjbvstvS8w==")
            .build();

        assertNotNull(subject);
        assertEquals("https://cdn.growthbook.io/api/features/sdk-862b5mHcP9XPugqD", subject.getEndpoint());
        assertEquals("BhB1wORFmZLTDjbvstvS8w==", subject.getEncryptionKey());
    }
}
