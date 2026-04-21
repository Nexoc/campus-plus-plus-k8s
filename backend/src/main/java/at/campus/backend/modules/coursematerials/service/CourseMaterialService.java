package at.campus.backend.modules.coursematerials.service;

import at.campus.backend.common.exception.NotFoundException;
import at.campus.backend.modules.coursematerials.model.CourseMaterial;
import at.campus.backend.modules.coursematerials.model.CourseMaterialDto;
import at.campus.backend.modules.coursematerials.model.CourseMaterialUpdateRequest;
import at.campus.backend.modules.coursematerials.repository.CourseMaterialRepository;
import at.campus.backend.modules.coursematerials.storage.CourseMaterialStorage;
import at.campus.backend.modules.courses.repository.CourseRepository;
import at.campus.backend.security.Roles;
import at.campus.backend.security.UserContext;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.InputStream;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Service layer for course materials.
 *
 * Security rule:
 * - Any authenticated user (Applicant, Student, Moderator)
 */
@Service
public class CourseMaterialService {

    private static final Set<String> ALLOWED_TYPES = Set.of(
            "application/pdf",
            "image/png",
            "image/jpeg"
    );

    private final CourseMaterialRepository materialRepo;
    private final CourseRepository courseRepo;
    private final CourseMaterialStorage storage;
    private final UserContext userContext;

    public CourseMaterialService(
            CourseMaterialRepository materialRepo,
            CourseRepository courseRepo,
            CourseMaterialStorage storage,
            UserContext userContext
    ) {
        this.materialRepo = materialRepo;
        this.courseRepo = courseRepo;
        this.storage = storage;
        this.userContext = userContext;
    }

    public CourseMaterialDto upload(UUID courseId, MultipartFile file, String title, String description) {

        String userId = userContext.getUserId();
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        courseRepo.findById(courseId).orElseThrow(() ->
                new NotFoundException("Course not found: " + courseId)
        );

        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "File is required"
            );
        }

        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_TYPES.contains(contentType)) {
            throw new ResponseStatusException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Unsupported file type: " + contentType
            );
        }

        UUID uploaderUuid = UUID.fromString(userId);
        String storageKey = UUID.randomUUID().toString();

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || originalFilename.isBlank()) {
            originalFilename = "upload";
        } else {
            originalFilename = Paths.get(originalFilename).getFileName().toString();
        }

        CourseMaterial material = new CourseMaterial();
        material.setId(UUID.randomUUID());
        material.setCourseId(courseId);
        material.setUploaderId(uploaderUuid);
        material.setTitle(title);
        material.setDescription(description);
        material.setOriginalFilename(originalFilename);
        material.setContentType(contentType);
        material.setSizeBytes(file.getSize());
        material.setStorageKey(storageKey);
        material.setCreatedAt(LocalDateTime.now());

        try {
            try (InputStream in = file.getInputStream()) {
                storage.store(storageKey, in);
            }

            materialRepo.insert(material);
            return CourseMaterialDto.fromDomain(material);

        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to store course material",
                    e
            );
        }
    }

    /**
     * Lists all materials for a course.
     *
     * Security:
     * - Authentication required
     */
    public List<CourseMaterialDto> listByCourseId(UUID courseId) {

        if (userContext.getUserId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        courseRepo.findById(courseId).orElseThrow(() ->
                new NotFoundException("Course not found: " + courseId)
        );

        return materialRepo.findByCourseId(courseId)
                .stream()
                .map(CourseMaterialDto::fromDomain)
                .toList();
    }

    /**
     * Download material file.
     *
     * Security:
     * - Authentication required
     */
    public CourseMaterialDownload getDownload(UUID materialId) {

        if (userContext.getUserId() == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        CourseMaterial material = materialRepo.findById(materialId)
                .orElseThrow(() ->
                        new NotFoundException("Course material not found: " + materialId)
                );

        if (!storage.exists(material.getStorageKey())) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Material file missing on disk"
            );
        }

        try {
            InputStream in = storage.open(material.getStorageKey());
            return new CourseMaterialDownload(
                    in,
                    material.getOriginalFilename(),
                    material.getContentType()
            );
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to read material file",
                    e
            );
        }
    }

    public record CourseMaterialDownload(
            InputStream inputStream,
            String originalFilename,
            String contentType
    ) {}

    public CourseMaterialDto update(UUID materialId, CourseMaterialUpdateRequest req) {

        String userId = userContext.getUserId();
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        CourseMaterial material = materialRepo.findById(materialId)
                .orElseThrow(() ->
                        new NotFoundException("Course material not found: " + materialId)
                );

        boolean isModerator =
                userContext.hasRole(Roles.MODERATOR);


        boolean isUploader = material.getUploaderId().toString().equals(userId);

        if (!isModerator && !isUploader) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        materialRepo.updateMetadata(
                materialId,
                req.getTitle(),
                req.getDescription()
        );

        material.setTitle(req.getTitle());
        material.setDescription(req.getDescription());

        return CourseMaterialDto.fromDomain(material);
    }

    public void delete(UUID materialId) {

        String userId = userContext.getUserId();
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        CourseMaterial material = materialRepo.findById(materialId)
                .orElseThrow(() ->
                        new NotFoundException("Course material not found: " + materialId)
                );

        boolean isModerator =
                userContext.hasRole(Roles.MODERATOR);

        boolean isUploader = material.getUploaderId().toString().equals(userId);

        if (!isModerator && !isUploader) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        try {
            storage.delete(material.getStorageKey());
            materialRepo.deleteById(materialId);
        } catch (Exception e) {
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to delete material",
                    e
            );
        }
    }


}
