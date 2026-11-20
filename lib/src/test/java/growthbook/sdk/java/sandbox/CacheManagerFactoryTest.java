package growthbook.sdk.java.sandbox;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class CacheManagerFactoryTest {

    private static final String CACHE_DIR_PROPERTY = "growthbook.cache.dir";
    private static final String[] FALLBACK_PROPERTIES = {CACHE_DIR_PROPERTY, "user.home", "java.io.tmpdir"};

    @TempDir
    File tempDir;

    private final Map<String, String> savedProperties = new HashMap<>();

    @BeforeEach
    void saveSystemProperties() {
        for (String property : FALLBACK_PROPERTIES) {
            savedProperties.put(property, System.getProperty(property));
        }
        System.clearProperty(CACHE_DIR_PROPERTY);
    }

    @AfterEach
    void restoreSystemProperties() {
        for (String property : FALLBACK_PROPERTIES) {
            String value = savedProperties.get(property);
            if (value == null) {
                System.clearProperty(property);
            } else {
                System.setProperty(property, value);
            }
        }
    }

    @Test
    @DisplayName("Returns no cache manager in NONE mode")
    void returnsNoManagerInNoneMode() {
        // Given
        // When
        GbCacheManager result = CacheManagerFactory.create(CacheMode.NONE, tempDir.getAbsolutePath());

        // Then
        assertNull(result);
    }

    @Test
    @DisplayName("Returns a working in-memory manager in MEMORY mode")
    void returnsInMemoryManagerInMemoryMode() {
        // Given
        GbCacheManager manager = CacheManagerFactory.create(CacheMode.MEMORY, null);

        // When
        manager.saveContent("key-1", "value-1");

        // Then
        assertInstanceOf(InMemoryCachingManagerImpl.class, manager);
        assertEquals("value-1", manager.loadCache("key-1"));
    }

    @Test
    @DisplayName("Ignores an explicit directory in MEMORY mode")
    void ignoresExplicitDirectoryInMemoryMode() {
        // Given
        GbCacheManager manager = CacheManagerFactory.create(CacheMode.MEMORY, tempDir.getAbsolutePath());

        // When
        manager.saveContent("key-1", "value-1");

        // Then
        assertInstanceOf(InMemoryCachingManagerImpl.class, manager);
        assertEquals(0, tempDir.list().length);
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = CacheMode.class, names = {"FILE", "AUTO"})
    @DisplayName("Returns a file manager writing into a usable explicit directory")
    void returnsFileManagerForUsableExplicitDirectory(CacheMode mode) throws IOException {
        // Given
        GbCacheManager manager = CacheManagerFactory.create(mode, tempDir.getAbsolutePath());

        // When
        manager.saveContent("features.json", "{\"flag\":true}");

        // Then
        assertInstanceOf(FileCachingManagerImpl.class, manager);
        assertEquals("{\"flag\":true}", manager.loadCache("features.json"));
        assertEquals("{\"flag\":true}", readFile(new File(tempDir, "features.json")));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = CacheMode.class, names = {"FILE", "AUTO"})
    @DisplayName("Falls back to the growthbook.cache.dir directory when the explicit directory is unusable")
    void fallsBackToCacheDirPropertyWhenExplicitDirectoryUnusable(CacheMode mode) throws IOException {
        // Given
        File fallbackDir = new File(tempDir, "fallback");
        System.setProperty(CACHE_DIR_PROPERTY, fallbackDir.getAbsolutePath());
        GbCacheManager manager = CacheManagerFactory.create(mode, unusablePath("explicit"));

        // When
        manager.saveContent("features.json", "{\"flag\":true}");

        // Then
        assertInstanceOf(FileCachingManagerImpl.class, manager);
        assertEquals("{\"flag\":true}", readFile(new File(fallbackDir, "features.json")));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = CacheMode.class, names = {"FILE", "AUTO"})
    @DisplayName("Uses the growthbook.cache.dir directory when no explicit directory is given")
    void usesCacheDirPropertyWhenNoExplicitDirectory(CacheMode mode) throws IOException {
        // Given
        File fallbackDir = new File(tempDir, "fallback");
        System.setProperty(CACHE_DIR_PROPERTY, fallbackDir.getAbsolutePath());
        GbCacheManager manager = CacheManagerFactory.create(mode, null);

        // When
        manager.saveContent("features.json", "{\"flag\":true}");

        // Then
        assertInstanceOf(FileCachingManagerImpl.class, manager);
        assertEquals("{\"flag\":true}", readFile(new File(fallbackDir, "features.json")));
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = CacheMode.class, names = {"FILE", "AUTO"})
    @DisplayName("Falls back to an in-memory manager when no candidate directory is usable")
    void fallsBackToInMemoryWhenNoDirectoryUsable(CacheMode mode) throws IOException {
        // Given
        assumeTrue(System.getenv("XDG_CACHE_HOME") == null, "XDG_CACHE_HOME offers a directory the test cannot block");
        assumeTrue(System.getenv("LOCALAPPDATA") == null, "LOCALAPPDATA offers a directory the test cannot block");
        String explicitDir = unusablePath("explicit");
        System.setProperty("user.home", unusablePath("home"));
        System.setProperty("java.io.tmpdir", unusablePath("tmp"));

        // When
        GbCacheManager manager = CacheManagerFactory.create(mode, explicitDir);

        // Then
        assertInstanceOf(InMemoryCachingManagerImpl.class, manager);
    }

    private String unusablePath(String name) throws IOException {
        File plainFile = new File(tempDir, name + ".txt");
        assertTrue(plainFile.createNewFile());
        return plainFile.getAbsolutePath() + File.separator + "sub";
    }

    private static String readFile(File file) throws IOException {
        return new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
    }
}
