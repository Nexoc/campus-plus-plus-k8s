package at.campus.backend.modules.reports.service;

import at.campus.backend.modules.reports.model.Report;
import at.campus.backend.modules.reports.model.ReportReason;
import at.campus.backend.modules.reports.model.ReportStatus;
import at.campus.backend.modules.reports.repository.ReportRepository;
import at.campus.backend.modules.reviews.repository.ReviewRepository;
import at.campus.backend.security.Roles;
import at.campus.backend.security.UserContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Service layer for Report business logic.
 *
 * Responsibilities:
 * - Moderation-only authorization
 * - Report management (create, update, list)
 * - Data validation
 *
 * Role mapping:
 * - Moderator role
 */
@Service
public class ReportService {

    private static final Logger log = LoggerFactory.getLogger(ReportService.class);

    private final ReportRepository repository;
    private final ReviewRepository reviewRepository;
    private final UserContext userContext;

    public ReportService(ReportRepository repository, ReviewRepository reviewRepository, UserContext userContext) {
        this.repository = repository;
        this.reviewRepository = reviewRepository;
        this.userContext = userContext;
    }

    /**
     * Get all reports (moderator only).
     *
     * Authorization:
     * - Only Moderator role
     */
    public List<Report> getAllReports() {
        requireModerator();
        return repository.findAll();
    }

    /**
     * Get reports by status (moderator only).
     */
    public List<Report> getReportsByStatus(ReportStatus status) {
        requireModerator();
        return repository.findByStatus(status);
    }

    /**
     * Count pending reports (moderator only).
     * FR-S-4: Used to display open reports badge in navigation.
     */
    public int countPendingReports() {
        requireModerator();
        return repository.countByStatus(ReportStatus.PENDING);
    }

    /**
     * Get a single report (moderator only).
     */
    public Report getReportById(UUID id) {
        requireModerator();
        return repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found"));
    }

    /**
     * Create a new report (any authenticated user).
     *
     * Authorization:
     * - Any authenticated user (STUDENT or Moderator)
     *
     * Validation:
     * - Target type is required (REVIEW or POST)
     * - Target ID is required
     * - Reason is required (from predefined enum)
     * - Target must exist (e.g., review must exist)
     * - User cannot report the same target twice
     * - User cannot report their own content (optional)
     */
    public Report createReport(Report report) {
        // 1. Check authentication
        String userId = userContext.getUserId();
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        // 2. Validate required fields
        if (report.getTargetType() == null || report.getTargetType().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target type is required");
        }

        if (report.getTargetId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Target ID is required");
        }

        if (report.getReason() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Reason is required");
        }

        // 3. Validate target type
        String targetType = report.getTargetType().toUpperCase();
        if (!targetType.equals("REVIEW") && !targetType.equals("POST")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                "Invalid target type. Must be REVIEW or POST");
        }
        report.setTargetType(targetType);

        // 4. Validate that target exists
        if (targetType.equals("REVIEW")) {
            validateReviewExists(report.getTargetId());
        }
        // TODO: Add validation for POST when post reporting is implemented

        // 5. Enforce user ownership (security)
        // User ID MUST come from UserContext, never from request
        UUID authenticatedUserId = UUID.fromString(userId);
        report.setUserId(authenticatedUserId);

        // 6. Check for duplicate report (optional: prevent same user from reporting same target multiple times)
        if (repository.existsByUserIdAndTargetTypeAndTargetId(authenticatedUserId, targetType, report.getTargetId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, 
                "You have already reported this " + targetType.toLowerCase());
        }

        // 7. Optional: Prevent users from reporting their own content
        if (targetType.equals("REVIEW")) {
            preventSelfReporting(authenticatedUserId, report.getTargetId());
        }

        // 8. Generate ID and save
        report.setId(UUID.randomUUID());
        report.setStatus(ReportStatus.PENDING);
        report.setCreatedAt(OffsetDateTime.now());
        repository.save(report);

        log.info("Report created: {} for {} with ID {} by user {}", 
            report.getTargetType(), report.getTargetId(), report.getId(), authenticatedUserId);

        return report;
    }

    /**
     * Validate that a review exists.
     */
    private void validateReviewExists(UUID reviewId) {
        reviewRepository.findById(reviewId)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, 
                "Review not found. Cannot report non-existent review."));
    }

    /**
     * Prevent users from reporting their own reviews (optional nice-to-have).
     */
    private void preventSelfReporting(UUID userId, UUID reviewId) {
        reviewRepository.findById(reviewId).ifPresent(review -> {
            if (review.getUserId().equals(userId)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    "You cannot report your own review");
            }
        });
    }

    /**
     * Update a report status (moderator only).
     *
     * Authorization:
     * - Only Moderator role
     *
     * Allowed transitions:
     * - PENDING -> RESOLVED or REJECTED
     * - RESOLVED -> REJECTED or PENDING (re-open)
     * - REJECTED -> PENDING (re-open)
     */
    public Report updateReportStatus(UUID id, ReportStatus newStatus, String moderatorNotes) {
        // 1. Check authorization
        requireModerator();

        // 2. Find existing report
        Report existing = repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found"));

        // 3. Validate status transition
        if (newStatus == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status is required");
        }

        // 4. Update status
        existing.setStatus(newStatus);
        if (newStatus != ReportStatus.PENDING) {
            existing.setResolvedAt(OffsetDateTime.now());
        }

        // 5. Add moderator notes
        if (moderatorNotes != null && !moderatorNotes.isBlank()) {
            existing.setModeratorNotes(moderatorNotes);
        }

        repository.save(existing);

        log.info("Report {} updated to status {} by moderator {}", 
            id, newStatus, userContext.getUserId());

        return existing;
    }

    /**
     * Delete a report (moderator only).
     *
     * Authorization:
     * - Only Moderator role
     */
    public void deleteReport(UUID id) {
        requireModerator();

        Report report = repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Report not found"));

        repository.deleteById(id);

        log.info("Report {} deleted by moderator {}", id, userContext.getUserId());
    }

    // ==================================================
    // AUTHORIZATION HELPERS
    // ==================================================

    /**
     * Require Moderator role.
     * Throws 403 Forbidden if user is not a moderator.
     */
    private void requireModerator() {
        if (!userContext.hasRole(Roles.MODERATOR)) {
            log.warn("Forbidden: user {} attempted moderation operation", userContext.getUserId());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, 
                "Only moderators can access moderation functionality");
        }
    }
}
