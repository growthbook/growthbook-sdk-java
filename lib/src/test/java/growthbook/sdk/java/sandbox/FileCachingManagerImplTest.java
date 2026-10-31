package growthbook.sdk.java;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import growthbook.sdk.java.exception.FeatureCacheException;
import growthbook.sdk.java.sandbox.FileCachingManagerImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class FileCachingManagerImplTest {
    private FileCachingManagerImpl fileCachingManagerImpl;

    @TempDir
    File tempDir;

    @BeforeEach
    void setUp() {
        fileCachingManagerImpl = new FileCachingManagerImpl(tempDir.getAbsolutePath());
    }

    @Test
    void shouldSaveAndLoadContentSuccessfully() {
        String fileName = "test.txt";
        String content = "Hello, cache!";

        fileCachingManagerImpl.saveContent(fileName, content);
        String loadedContent = fileCachingManagerImpl.loadCache(fileName);
        assertEquals(content, loadedContent);
    }

    @Test
    void shouldReturnNullWhenFileDoesNotExist() {
        String loadedContent = fileCachingManagerImpl.loadCache("nonexistent.txt");
        assertNull(loadedContent);
        assertNull(fileCachingManagerImpl.getLastUpdatedMillis("nonexistent.txt"));
    }

    @Test
    void shouldExposeLastUpdatedTime() {
        String fileName = "timestamped.txt";

        fileCachingManagerImpl.saveContent(fileName, "cached");

        assertNotNull(fileCachingManagerImpl.getLastUpdatedMillis(fileName));
        assertTrue(fileCachingManagerImpl.getLastUpdatedMillis(fileName) > 0);
    }

    @Test
    void shouldThrowExceptionWhenWritingFails() {
        String fileName = "readonly.txt";
        File file = new File(tempDir, fileName);

        try {
            boolean created = file.createNewFile();
            assertTrue(created);
            boolean readOnly = file.setReadOnly();
            assertTrue(readOnly);
        } catch (IOException e) {
            fail("Creating test file was not successful.");
        }

        FeatureCacheException thrown = assertThrows(
                FeatureCacheException.class,
                () -> fileCachingManagerImpl.saveContent(fileName, "This should fail")
        );

        assertInstanceOf(IOException.class, thrown.getCause());
    }

    // can't change readable for ci/cd
//    @Test
//    void shouldThrowExceptionWhenReadingFails() {
//        String fileName = "unreadable.txt";
//        File file = new File(tempDir, fileName);
//
//        try {
//            boolean created = file.createNewFile();
//            assertTrue(created);
//            boolean unreadable = file.setReadable(false);
//            assertTrue(unreadable);
//        } catch (IOException e) {
//            fail("Creating test file was not successful.");
//        }
//
//        RuntimeException thrown = assertThrows(RuntimeException.class, () -> {
//            cachingManager.loadCache(fileName);
//        });
//
//        assertInstanceOf(IOException.class, thrown.getCause());
//    }

    @Test
    void shouldOverwriteExistingFile() {
        String fileName = "overwrite.txt";

        fileCachingManagerImpl.saveContent(fileName, "Initial content");
        fileCachingManagerImpl.saveContent(fileName, "New content");

        String loadedContent = fileCachingManagerImpl.loadCache(fileName);
        assertEquals("New content", loadedContent);
    }

    @Test
    void shouldRoundTripNonAsciiContentAsUtf8() {
        String fileName = "utf8.json";
        // Mix of Cyrillic, CJK and an emoji to ensure encoding is UTF-8 on both write and read.
        String content = "{\"msg\":\"Привіт, 世界 🚀\"}";

        fileCachingManagerImpl.saveContent(fileName, content);

        assertEquals(content, fileCachingManagerImpl.loadCache(fileName));
    }

    @Test
    void shouldNotLeaveTempFilesAfterSave() {
        fileCachingManagerImpl.saveContent("clean.txt", "payload");

        File[] files = tempDir.listFiles();
        assertNotNull(files);
        // Exactly the destination file, no leftover *.tmp from the write-to-temp + atomic move.
        assertEquals(1, files.length);
        assertFalse(files[0].getName().endsWith(".tmp"));
    }

    @Test
    void shouldReturnEmptyStringForEmptyFile() {
        String fileName = "empty.txt";
        fileCachingManagerImpl.saveContent(fileName, "");

        String loadedContent = fileCachingManagerImpl.loadCache(fileName);
        assertEquals("", loadedContent);
    }

    // fail ci/cd
//    @Test
//    void shouldHandleLargeContent() {
//        String fileName = "large.txt";
//        StringBuilder largeContent = new StringBuilder();
//        for (int i = 0; i < 10000; i++) {
//            largeContent.append("Line ").append(i).append("\n");
//        }
//
//        cachingManager.saveContent(fileName, largeContent.toString());
//        String loadedContent = cachingManager.loadCache(fileName);
//
//        assertEquals(largeContent.toString().trim(), loadedContent);
//    }

    @Test
    void shouldHandleMultipleFilesSeparately() {
        fileCachingManagerImpl.saveContent("file1.txt", "Content 1");
        fileCachingManagerImpl.saveContent("file2.txt", "Content 2");

        assertEquals("Content 1", fileCachingManagerImpl.loadCache("file1.txt"));
        assertEquals("Content 2", fileCachingManagerImpl.loadCache("file2.txt"));
    }

    @Test
    void shouldClearAllCachedFiles() {
        fileCachingManagerImpl.saveContent("a.txt", "aaa");
        fileCachingManagerImpl.saveContent("b.txt", "bbb");

        fileCachingManagerImpl.clearCache();

        assertNull(fileCachingManagerImpl.loadCache("a.txt"));
        assertNull(fileCachingManagerImpl.loadCache("b.txt"));
        assertEquals(0, tempDir.listFiles().length);
    }

    @Test
    void shouldCreateCacheDirIfNotExists() {
        File subDir = new File(tempDir, "nested/cache");
        assertFalse(subDir.exists());

        FileCachingManagerImpl manager = new FileCachingManagerImpl(subDir.getAbsolutePath());
        manager.saveContent("x.txt", "hello");

        assertEquals("hello", manager.loadCache("x.txt"));
    }

    /**
     * Non-ASCII sample text, written as escapes so the constant does not depend on the encoding
     * javac happens to read this file with. U+00E9 is "e" with an acute accent: the single-byte
     * charsets Windows and older Linux default to can all represent it, and each encodes it as
     * one byte that is not valid UTF-8 on its own.
     */
    private static final String NON_ASCII_SAMPLE = "{\"name\":\"caf\u00e9\"}";

    @Test
    void shouldReadLegacyCacheWrittenWithThePlatformDefaultCharset() throws IOException {
        // Caches written by earlier SDK versions used FileWriter, i.e. the platform default
        // charset. Such a file must stay readable instead of being discarded as a cache miss.
        Charset legacyCharset = Charset.defaultCharset();

        assumeTrue(legacyCharset.newEncoder().canEncode(NON_ASCII_SAMPLE),
                "platform default charset (" + legacyCharset + ") cannot represent the sample text, "
                        + "so it could not have produced this cache file either");
        byte[] legacyBytes = NON_ASCII_SAMPLE.getBytes(legacyCharset);
        assumeFalse(isValidUtf8(legacyBytes),
                "platform default charset (" + legacyCharset + ") already produces valid UTF-8, "
                        + "so there is no legacy encoding to exercise");

        String fileName = "legacy.txt";
        Files.write(new File(tempDir, fileName).toPath(), legacyBytes);

        assertEquals(NON_ASCII_SAMPLE, fileCachingManagerImpl.loadCache(fileName));
    }

    /** Mirrors how {@code Files.newBufferedReader} decodes: malformed input is reported, not replaced. */
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

    @Test
    void shouldTreatUndecodableCacheAsAMissRatherThanFailing() throws IOException {
        // Bytes that decode under neither UTF-8 nor the platform charset must not abort startup:
        // a cache miss lets the repository fetch instead.
        String fileName = "corrupt.txt";
        Files.write(new File(tempDir, fileName).toPath(), new byte[]{(byte) 0xC3, (byte) 0x28, (byte) 0xA9});

        String loaded = fileCachingManagerImpl.loadCache(fileName);

        if (StandardCharsets.UTF_8.equals(Charset.defaultCharset())) {
            assertNull(loaded);
        } else {
            // A single-byte legacy charset decodes anything, so the fallback read succeeds.
            assertNotNull(loaded);
        }
    }

    @Test
    void shouldRewriteALegacyCacheAsUtf8OnTheNextSave() throws IOException {
        String fileName = "rewrite.txt";
        String content = NON_ASCII_SAMPLE;

        fileCachingManagerImpl.saveContent(fileName, content);

        byte[] written = Files.readAllBytes(new File(tempDir, fileName).toPath());
        assertEquals(content, new String(written, StandardCharsets.UTF_8));
        assertEquals(content, fileCachingManagerImpl.loadCache(fileName));
    }

    @Test
    void shouldThrowWhenCacheDirectoryCannotBeCreated() {
        File file = new File(tempDir, "notadir.txt");
        try {
            file.createNewFile();
        } catch (IOException e) {
            fail("Precondition setup failed");
        }
        // A path *inside* a plain file cannot be a directory.
        String impossiblePath = file.getAbsolutePath() + File.separator + "subdir";

        assertThrows(RuntimeException.class, () -> new FileCachingManagerImpl(impossiblePath));
    }
}
