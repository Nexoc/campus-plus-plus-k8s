package at.campus.backend.modules.courses.service;

import at.campus.backend.modules.courses.model.Course;
import at.campus.backend.modules.courses.repository.CourseRepository;
import at.campus.backend.security.Roles;
import at.campus.backend.security.UserContext;
import at.campus.backend.common.exception.ForbiddenException;
import at.campus.backend.common.exception.NotFoundException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class CourseService {

    private static final Logger log =
            LoggerFactory.getLogger(CourseService.class);

    private final CourseRepository repository;
    private final UserContext userContext;

    public CourseService(
            CourseRepository repository,
            UserContext userContext
    ) {
        this.repository = repository;
        this.userContext = userContext;
    }

    // ==================================================
    // READ OPERATIONS
    // ==================================================

    public List<Course> getCourses(UUID studyProgramId, Integer ects) {

        log.debug(
                "Fetching courses (studyProgramId={}, ects={})",
                studyProgramId,
                ects
        );

        if (studyProgramId == null && ects == null) {
            return repository.findAll();
        }

        return repository.findFiltered(studyProgramId, ects);
    }

    public Course getCourseById(UUID courseId) {

        log.debug(
                "Fetching course by id={} (user={})",
                courseId,
                userContext.getUserId()
        );

        return repository.findById(courseId)
                .orElseThrow(() -> {
                    log.warn(
                            "Course {} not found (user={})",
                            courseId,
                            userContext.getUserId()
                    );
                    return new NotFoundException(
                            "Course not found: " + courseId
                    );
                });
    }

    // ==================================================
    // WRITE OPERATIONS (Moderator  ONLY)
    // ==================================================

    public void createCourse(Course course) {
        requireAdmin();

        log.info(
                "ADMIN {} creating course '{}'",
                userContext.getUserId(),
                course.getTitle()
        );

        repository.insert(course);
    }

    public void updateCourse(Course course) {
        requireAdmin();

        if (repository.findById(course.getCourseId()).isEmpty()) {

            log.warn(
                    "Course {} not found for update (user={})",
                    course.getCourseId(),
                    userContext.getUserId()
            );

            throw new NotFoundException(
                    "Course not found: " + course.getCourseId()
            );
        }

        log.info(
                "ADMIN {} updating course {}",
                userContext.getUserId(),
                course.getCourseId()
        );

        repository.update(course);
    }

    public void deleteCourse(UUID courseId) {
        requireAdmin();

        log.info(
                "ADMIN {} deleting course {}",
                userContext.getUserId(),
                courseId
        );

        boolean deleted = repository.deleteById(courseId);

        if (!deleted) {

            log.warn(
                    "Course {} not found for delete (user={})",
                    courseId,
                    userContext.getUserId()
            );

            throw new NotFoundException(
                    "Course not found: " + courseId
            );
        }
    }

    // ==================================================
    // ACCESS POLICY
    // ==================================================

    private void requireAdmin() {
        if (!userContext.hasRole(Roles.MODERATOR)) {

            log.warn(
                    "Forbidden access: user {} tried Moderator operation",
                    userContext.getUserId()
            );

            throw new ForbiddenException("Moderator role required");
        }
    }
}
