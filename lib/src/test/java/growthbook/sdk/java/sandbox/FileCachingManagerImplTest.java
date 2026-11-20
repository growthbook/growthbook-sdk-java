package growthbook.sdk.java.sandbox;

import growthbook.sdk.java.exception.FeatureCacheException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class FileCachingManagerImplTest {

    private static final String NON_ASCII_SAMPLE = "{\"name\":\"caf\u00e9\"}";
    private static final byte[] UNDECODABLE_BYTES = {(byte) 0xC3, (byte) 0x28, (byte) 0xA9};

    @TempDir
    File tempDir;

    private FileCachingManagerImpl manager;

    @BeforeEach
    void setUp() {
        manager = new FileCachingManagerImpl(tempDir.getAbsolutePath());
    }

    @ParameterizedTest(name = "content \"{0}\"")
    @ValueSource(strings = {
            "Hello, cache!",
            "",
            "{\"msg\":\"Привіт, 世界 🚀\"}"
    })
    @DisplayName("Loads exactly the content that was saved")
    void loadsSavedContent(String content) {
        // Given
        manager.saveContent("content.json", content);

        // When
        String loaded = manager.loadCache("content.json");

        // Then
        assertEquals(content, loaded);
    }

    @Test
    @DisplayName("Returns null content and no timestamp for a file that does not exist")
    void returnsNullForMissingFile() {
        // Given
        // When
        String loaded = manager.loadCache("missing.json");
        Long updatedAt = manager.getLastUpdatedMillis("missing.json");

        // Then
        assertNull(loaded);
        assertNull(updatedAt);
    }

    @Test
    @DisplayName("Reports the cache file's last modified time as the last updated timestamp")
    void reportsLastModifiedTimeAsLastUpdated() throws IOException {
        // Given
        manager.saveContent("timestamped.json", "cached");
        Files.setLastModifiedTime(new File(tempDir, "timestamped.json").toPath(), FileTime.fromMillis(1_700_000_000_000L));

        // When
        Long updatedAt = manager.getLastUpdatedMillis("timestamped.json");

        // Then
        assertEquals(1_700_000_000_000L, updatedAt);
    }

    @Test
    @DisplayName("Fails with a cache exception when the existing cache file is read-only")
    void failsWhenCacheFileIsReadOnly() throws IOException {
        // Given
        File file = new File(tempDir, "readonly.json");
        assertTrue(file.createNewFile());
        assertTrue(file.setReadOnly());

        // When
        FeatureCacheException thrown = assertThrows(FeatureCacheException.class,
                () -> manager.saveContent("readonly.json", "content"));

        // Then
        assertEquals("Failed to write feature cache file: readonly.json", thrown.getMessage());
        assertInstanceOf(IOException.class, thrown.getCause());
    }

    @Test
    @DisplayName("Replaces the content of a file that is saved again")
    void replacesContentOfSavedFile() {
        // Given
        manager.saveContent("overwrite.json", "Initial content");

        // When
        manager.saveContent("overwrite.json", "New content");

        // Then
        assertEquals("New content", manager.loadCache("overwrite.json"));
    }

    @Test
    @DisplayName("Leaves only the destination file behind after a save")
    void leavesNoTemporaryFilesAfterSave() {
        // Given
        // When
        manager.saveContent("clean.json", "payload");

        // Then
        assertArrayEquals(new String[]{"clean.json"}, tempDir.list());
    }

    @Test
    @DisplayName("Keeps the content of different files independent")
    void keepsFilesIndependent() {
        // Given
        // When
        manager.saveContent("file-1.json", "Content 1");
        manager.saveContent("file-2.json", "Content 2");

        // Then
        assertEquals("Content 1", manager.loadCache("file-1.json"));
        assertEquals("Content 2", manager.loadCache("file-2.json"));
    }

    @Test
    @DisplayName("Deletes every cached file when cleared")
    void deletesAllFilesWhenCleared() {
        // Given
        manager.saveContent("a.json", "aaa");
        manager.saveContent("b.json", "bbb");

        // When
        manager.clearCache();

        // Then
        assertNull(manager.loadCache("a.json"));
        assertNull(manager.loadCache("b.json"));
        assertEquals(0, tempDir.list().length);
    }

    @Test
    @DisplayName("Creates a missing nested cache directory")
    void createsMissingNestedCacheDirectory() {
        // Given
        File nestedDir = new File(tempDir, "nested/cache");
        assertFalse(nestedDir.exists());

        // When
        FileCachingManagerImpl nested = new FileCachingManagerImpl(nestedDir.getAbsolutePath());
        nested.saveContent("x.json", "hello");

        // Then
        assertTrue(nestedDir.isDirectory());
        assertEquals("hello", nested.loadCache("x.json"));
    }

    @Test
    @DisplayName("Fails with a cache exception when the cache directory cannot be created")
    void failsWhenCacheDirectoryCannotBeCreated() throws IOException {
        // Given
        File plainFile = new File(tempDir, "notadir.txt");
        assertTrue(plainFile.createNewFile());
        String impossiblePath = plainFile.getAbsolutePath() + File.separator + "subdir";

        // When
        FeatureCacheException thrown = assertThrows(FeatureCacheException.class,
                () -> new FileCachingManagerImpl(impossiblePath));

        // Then
        assertEquals("Failed to create cache directory at " + impossiblePath, thrown.getMessage());
    }

    @Test
    @DisplayName("Reads a legacy cache file written in the platform default charset")
    void readsLegacyCacheInPlatformDefaultCharset() throws IOException {
        // Given
        Charset legacyCharset = Charset.defaultCharset();
        assumeTrue(legacyCharset.newEncoder().canEncode(NON_ASCII_SAMPLE),
                "platform default charset (" + legacyCharset + ") cannot represent the sample text");
        byte[] legacyBytes = NON_ASCII_SAMPLE.getBytes(legacyCharset);
        assumeFalse(isValidUtf8(legacyBytes),
                "platform default charset (" + legacyCharset + ") already produces valid UTF-8");
        Files.write(new File(tempDir, "legacy.json").toPath(), legacyBytes);

        // When
        String loaded = manager.loadCache("legacy.json");

        // Then
        assertEquals(NON_ASCII_SAMPLE, loaded);
    }

    @Test
    @DisplayName("Treats a file that is not valid UTF-8 as a cache miss when the platform charset is UTF-8")
    void treatsUndecodableFileAsMissOnUtf8Platform() throws IOException {
        // Given
        assumeTrue(StandardCharsets.UTF_8.equals(Charset.defaultCharset()), "platform default charset is not UTF-8");
        Files.write(new File(tempDir, "corrupt.json").toPath(), UNDECODABLE_BYTES);

        // When
        String loaded = manager.loadCache("corrupt.json");

        // Then
        assertNull(loaded);
    }

    @Test
    @DisplayName("Decodes a file that is not valid UTF-8 with a non-UTF-8 platform charset")
    void decodesNonUtf8FileWithNonUtf8PlatformCharset() throws IOException {
        // Given
        Charset platformCharset = Charset.defaultCharset();
        assumeFalse(StandardCharsets.UTF_8.equals(platformCharset), "platform default charset is UTF-8");
        Files.write(new File(tempDir, "corrupt.json").toPath(), UNDECODABLE_BYTES);

        // When
        String loaded = manager.loadCache("corrupt.json");

        // Then
        assertEquals(new String(UNDECODABLE_BYTES, platformCharset).trim(), loaded);
    }

    @Test
    @DisplayName("Rewrites a legacy-encoded cache file as UTF-8 on the next save")
    void rewritesLegacyCacheAsUtf8OnNextSave() throws IOException {
        // Given
        Path cacheFile = new File(tempDir, "rewrite.json").toPath();
        Files.write(cacheFile, NON_ASCII_SAMPLE.getBytes(StandardCharsets.ISO_8859_1));

        // When
        manager.saveContent("rewrite.json", NON_ASCII_SAMPLE);

        // Then
        assertArrayEquals(NON_ASCII_SAMPLE.getBytes(StandardCharsets.UTF_8), Files.readAllBytes(cacheFile));
        assertEquals(NON_ASCII_SAMPLE, manager.loadCache("rewrite.json"));
    }

    private static boolean isValidUtf8(byte[] bytes) {
        CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT);
        try {
            decoder.decode(ByteBuffer.wrap(bytes));
            return true;
        } catch (CharacterCodingException e) {
            return false;
        }
    }
}
