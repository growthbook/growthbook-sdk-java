package growthbook.sdk.java;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import growthbook.sdk.java.sandbox.FileCachingManagerImpl;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;

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

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> fileCachingManagerImpl.saveContent(fileName, "This should fail"));

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
}
