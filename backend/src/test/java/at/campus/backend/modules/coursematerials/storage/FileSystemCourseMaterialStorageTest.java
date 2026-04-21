package at.campus.backend.modules.coursematerials.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FileSystemCourseMaterialStorageTest {

    @TempDir
    Path tempDir;

    @Test
    void storesAndReadsBinaryContent() throws Exception {
        FileSystemCourseMaterialStorage storage =
                new FileSystemCourseMaterialStorage(tempDir.toString());

        storage.store("material-1", new ByteArrayInputStream("payload".getBytes(StandardCharsets.UTF_8)));

        assertThat(storage.exists("material-1")).isTrue();
        try (InputStream in = storage.open("material-1")) {
            assertThat(in.readAllBytes()).isEqualTo("payload".getBytes(StandardCharsets.UTF_8));
        }
    }

    @Test
    void deleteRemovesStoredFile() throws Exception {
        FileSystemCourseMaterialStorage storage =
                new FileSystemCourseMaterialStorage(tempDir.toString());

        storage.store("material-2", new ByteArrayInputStream("payload".getBytes(StandardCharsets.UTF_8)));
        storage.delete("material-2");

        assertThat(storage.exists("material-2")).isFalse();
        assertThat(Files.exists(tempDir.resolve("material-2"))).isFalse();
    }

    @Test
    void rejectsStorageKeyTraversal() {
        FileSystemCourseMaterialStorage storage =
                new FileSystemCourseMaterialStorage(tempDir.toString());

        assertThatThrownBy(() -> storage.store("../escape", new ByteArrayInputStream(new byte[0])))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Invalid storage key");
    }
}
