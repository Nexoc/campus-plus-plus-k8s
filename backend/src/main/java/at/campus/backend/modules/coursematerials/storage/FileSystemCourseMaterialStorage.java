package at.campus.backend.modules.coursematerials.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Component
public class FileSystemCourseMaterialStorage implements CourseMaterialStorage {

    private final Path storageDir;

    public FileSystemCourseMaterialStorage(
            @Value("${app.course-materials.storage-dir:/data/course-materials}") String storageDir
    ) {
        this.storageDir = Path.of(storageDir).toAbsolutePath().normalize();
    }

    @Override
    public void store(String storageKey, InputStream inputStream) throws IOException {
        Files.createDirectories(storageDir);
        Files.copy(inputStream, resolvePath(storageKey), StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public boolean exists(String storageKey) {
        return Files.exists(resolvePath(storageKey));
    }

    @Override
    public InputStream open(String storageKey) throws IOException {
        return Files.newInputStream(resolvePath(storageKey));
    }

    @Override
    public void delete(String storageKey) throws IOException {
        Files.deleteIfExists(resolvePath(storageKey));
    }

    Path getStorageDir() {
        return storageDir;
    }

    private Path resolvePath(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            throw new IllegalArgumentException("Storage key is required");
        }

        Path resolved = storageDir.resolve(storageKey).normalize();
        if (!resolved.startsWith(storageDir)) {
            throw new IllegalArgumentException("Invalid storage key");
        }

        return resolved;
    }
}
