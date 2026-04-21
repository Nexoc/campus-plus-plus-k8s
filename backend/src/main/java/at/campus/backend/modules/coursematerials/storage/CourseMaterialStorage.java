package at.campus.backend.modules.coursematerials.storage;

import java.io.IOException;
import java.io.InputStream;

/**
 * Storage abstraction for course material bytes.
 *
 * The current implementation is filesystem-backed, but the service depends on
 * this contract so the storage backend can later move to object storage
 * without rewriting business logic.
 */
public interface CourseMaterialStorage {

    void store(String storageKey, InputStream inputStream) throws IOException;

    boolean exists(String storageKey);

    InputStream open(String storageKey) throws IOException;

    void delete(String storageKey) throws IOException;
}
