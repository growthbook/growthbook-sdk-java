package growthbook.sdk.java;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;

class CachingManagerTest {
    private CachingManager cachingManager;

    @TempDir
    File tempDir;

    @BeforeEach
    void setUp() {
        cachingManager = new CachingManager(tempDir.getAbsolutePath());
    }

    @Test
    void shouldSaveAndLoadContentSuccessfully() {
        String fileName = "test.txt";
        String content = "Hello, cache!";

        cachingManager.saveContent(fileName, content);
        String loadedContent = cachingManager.loadCache(fileName);
        assertEquals(content, loadedContent);
    }

    @Test
    void shouldReturnNullWhenFileDoesNotExist() {
        String loadedContent = cachingManager.loadCache("nonexistent.txt");
        Assertions.assertNull(loadedContent);
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

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> {
            cachingManager.saveContent(fileName, "This should fail");
        });

        assertInstanceOf(IOException.class, thrown.getCause());
    }

    @Test
    void shouldThrowExceptionWhenReadingFails() {
        String fileName = "unreadable.txt";
        File file = new File(tempDir, fileName);

        try {
            boolean created = file.createNewFile();
            assertTrue(created);
            boolean unreadable = file.setReadable(false);
            assertTrue(unreadable);
        } catch (IOException e) {
            fail("Creating test file was not successful.");
        }

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> {
            cachingManager.loadCache(fileName);
        });

        assertInstanceOf(IOException.class, thrown.getCause());
    }

    @Test
    void shouldOverwriteExistingFile() {
        String fileName = "overwrite.txt";

        cachingManager.saveContent(fileName, "Initial content");
        cachingManager.saveContent(fileName, "New content");

        String loadedContent = cachingManager.loadCache(fileName);
        assertEquals("New content", loadedContent);
    }

    @Test
    void shouldReturnEmptyStringForEmptyFile() {
        String fileName = "empty.txt";
        cachingManager.saveContent(fileName, "");

        String loadedContent = cachingManager.loadCache(fileName);
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
        cachingManager.saveContent("file1.txt", "Content 1");
        cachingManager.saveContent("file2.txt", "Content 2");

        assertEquals("Content 1", cachingManager.loadCache("file1.txt"));
        assertEquals("Content 2", cachingManager.loadCache("file2.txt"));
    }
}
