package at.campus.backend.modules.reviews.service;

import at.campus.backend.modules.reviews.model.ModerationReviewDto;
import at.campus.backend.modules.reviews.model.Review;
import at.campus.backend.modules.reviews.model.ReviewDto;
import at.campus.backend.modules.reviews.model.ReviewSortOption;
import at.campus.backend.modules.reviews.model.ReviewSummary;
import at.campus.backend.modules.reviews.repository.ReviewRepository;
import at.campus.backend.modules.watch.model.WatchTargetType;
import at.campus.backend.modules.watch.service.NotificationService;
import at.campus.backend.modules.watch.service.WatchService;
import at.campus.backend.security.Roles;
import at.campus.backend.security.UserContext;
import org.springframework.beans.BeanUtils;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

/**
 * Service layer for Review business logic.
 *
 * Responsibilities:
 * - Role-based authorization
 * - Data validation
 * - User ownership enforcement
 */
@Service
public class ReviewService {

    private final ReviewRepository repository;
    private final UserContext userContext;
    private final UserLookupService userLookupService;
    private final CourseLookupService courseLookupService;
    private final WatchService watchService;
    private final NotificationService notificationService;

    public ReviewService(
            ReviewRepository repository,
            UserContext userContext,
            UserLookupService userLookupService,
            CourseLookupService courseLookupService,
            WatchService watchService,
            NotificationService notificationService
    ) {
        this.repository = repository;
        this.userContext = userContext;
        this.userLookupService = userLookupService;
        this.courseLookupService = courseLookupService;
        this.watchService = watchService;
        this.notificationService = notificationService;
    }

    /**
     * Get all reviews for a course.
     * Public endpoint - no authentication required.
     */
    public List<Review> getReviewsByCourse(UUID courseId) {
        return repository.findByCourseId(courseId);
    }

    /**
     * Get all reviews for a course with sorting.
     * Public endpoint - no authentication required.
     */
    public List<Review> getReviewsByCourse(UUID courseId, ReviewSortOption sortOption) {
        return repository.findByCourseId(courseId, sortOption);
    }

    /**
     * Get a single review by ID.
     * Public endpoint - no authentication required.
     */
    public Review getReviewById(UUID id) {
        return repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found"));
    }

    /**
     * Get all reviews.
     * Public endpoint - no authentication required.
     */
    public List<Review> getAllReviews() {
        return repository.findAll();
    }

    /**
     * Get review summary (average rating and count) for a course.
     * Public endpoint - no authentication required.
     */
    public ReviewSummary getReviewSummary(UUID courseId) {
        Double averageRating = repository.getAverageRatingByCourseId(courseId);
        Integer reviewCount = repository.getReviewCountByCourseId(courseId);
        
        // If no reviews exist, return null for average and 0 for count
        if (reviewCount == null || reviewCount == 0) {
            return new ReviewSummary(null, 0);
        }
        
        return new ReviewSummary(averageRating, reviewCount);
    }

    /**
     * Create a new review.
     *
     * Authorization rules:
     * - Only STUDENT and Moderator can create reviews
     * - Applicants are forbidden
     *
     * Validation:
     * - Rating is required and must be 1-5
     * - Course ID is required
     * - User ID is taken from UserContext (not from request)
     */
    public Review createReview(Review review) {
        // 1. Check authentication
        String userId = userContext.getUserId();
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        // 2. Check role authorization
        boolean hasStudentRole = userContext.hasRole(Roles.STUDENT);
        boolean hasModeratorRole = userContext.hasRole(Roles.MODERATOR);

        if (!hasStudentRole && !hasModeratorRole) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, 
                "Only students and moderators can create reviews");
        }

        // 3. Validate required fields
        if (review.getRating() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Rating is required");
        }

        if (review.getRating() < 1 || review.getRating() > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                "Rating must be between 1 and 5");
        }

        if (review.getCourseId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Course ID is required");
        }

        // 4. Enforce user ownership (security)
        // User ID MUST come from UserContext, never from request
        UUID authenticatedUserId = UUID.fromString(userId);
        review.setUserId(authenticatedUserId);

        // 5. Optional: Prevent duplicate reviews
        if (repository.existsByUserIdAndCourseId(authenticatedUserId, review.getCourseId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, 
                "You have already reviewed this course");
        }

        // 6. Generate ID and save
        review.setId(UUID.randomUUID());
        repository.save(review);

        // 7. Notify watchers (fire-and-forget, safe failure)
        try {
            List<UUID> watchers = watchService.getUsersWatchingTarget(
                WatchTargetType.COURSE, review.getCourseId());
            if (!watchers.isEmpty()) {
                notificationService.notifyNewReview(review.getCourseId(), review.getId(), watchers);
            }
        } catch (Exception e) {
            // Don't fail the review creation if notification fails
            // Just log the error
        }

        return review;
    }

    /**
     * Update an existing review.
     *
     * Authorization:
     * - Student: only own reviews
     * - Moderator: any review
     */
    public Review updateReview(UUID id, Review updatedReview) {
        // 1. Check authentication
        String userId = userContext.getUserId();
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        // 2. Find existing review
        Review existing = repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found"));

        // 3. Check authorization: ownership or moderator role
        boolean isModerator = userContext.hasRole(Roles.MODERATOR);
        boolean isOwner = existing.getUserId().toString().equals(userId);
        
        if (!isOwner && !isModerator) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, 
                "You can only edit your own reviews");
        }

        // 4. Validate rating
        if (updatedReview.getRating() != null) {
            if (updatedReview.getRating() < 1 || updatedReview.getRating() > 5) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, 
                    "Rating must be between 1 and 5");
            }
            existing.setRating(updatedReview.getRating());
        }

        // 5. Update fields
        if (updatedReview.getDifficulty() != null) {
            existing.setDifficulty(updatedReview.getDifficulty());
        }
        if (updatedReview.getWorkload() != null) {
            existing.setWorkload(updatedReview.getWorkload());
        }
        if (updatedReview.getSatisfaction() != null) {
            existing.setSatisfaction(updatedReview.getSatisfaction());
        }
        if (updatedReview.getPriorRequirements() != null) {
            existing.setPriorRequirements(updatedReview.getPriorRequirements());
        }
        if (updatedReview.getExamInfo() != null) {
            existing.setExamInfo(updatedReview.getExamInfo());
        }
        if (updatedReview.getText() != null) {
            existing.setText(updatedReview.getText());
        }

        repository.update(existing);
        return existing;
    }

    /**
     * Delete a review.
     *
     * Authorization:
     * - Student: only own reviews
     * - Moderator: any review
     */
    public void deleteReview(UUID id) {
        // 1. Check authentication
        String userId = userContext.getUserId();
        if (userId == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authentication required");
        }

        // 2. Find existing review
        Review existing = repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found"));

        // 3. Check authorization: ownership or moderator role
        boolean isModerator = userContext.hasRole(Roles.MODERATOR);
        boolean isOwner = existing.getUserId().toString().equals(userId);
        
        if (!isOwner && !isModerator) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, 
                "You can only delete your own reviews");
        }

        // 4. Delete
        repository.deleteById(id);
    }

    /**
     * Flag a review as inappropriate (moderator only).
     *
     * Authorization:
     * - Only Moderator role
     */
    public Review flagReview(UUID id, String reason) {
        // 1. Check moderator role
        if (!userContext.hasRole(Roles.MODERATOR)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, 
                "Only moderators can flag reviews");
        }

        // 2. Find existing review
        Review existing = repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found"));

        // 3. Flag the review
        existing.setModerationFlagged(true);
        existing.setModerationReason(reason);
        repository.update(existing);

        return existing;
    }

    /**
     * Unflag a review (moderator only).
     *
     * Authorization:
     * - Only Moderator role
     */
    public Review unflagReview(UUID id) {
        // 1. Check moderator role
        if (!userContext.hasRole(Roles.MODERATOR)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, 
                "Only moderators can unflag reviews");
        }

        // 2. Find existing review
        Review existing = repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found"));

        // 3. Unflag the review
        existing.setModerationFlagged(false);
        existing.setModerationReason(null);
        repository.update(existing);

        return existing;
    }

    /**
     * Delete a review by moderator (bypass ownership check).
     *
     * Authorization:
     * - Only Moderator role
     */
    public void deleteReviewByModerator(UUID id) {
        // 1. Check moderator role
        if (!userContext.hasRole(Roles.MODERATOR)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, 
                "Only moderators can delete reviews");
        }

        // 2. Find and delete
        Review existing = repository.findById(id)
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Review not found"));
        
        repository.deleteById(id);
    }

    public List<ModerationReviewDto> getAllModerationReviews() {
        List<Review> reviews = repository.findAll();

        return reviews.stream().map(review -> {
            ModerationReviewDto dto = new ModerationReviewDto();

            // base fields
            ReviewDto base = ReviewDto.fromDomain(review);
            BeanUtils.copyProperties(base, dto);

            // enrichment
            dto.setUserName(userLookupService.getUserName(review.getUserId()));
            dto.setCourseTitle(courseLookupService.getCourseTitle(review.getCourseId()));

            return dto;
        }).toList();
    }


    public List<ModerationReviewDto> toModerationDtos(List<Review> reviews) {
        return reviews.stream().map(review -> {
            ModerationReviewDto dto = new ModerationReviewDto();

            // base fields (как ReviewDto.fromDomain, но вручную)
            dto.setId(review.getId());
            dto.setUserId(review.getUserId());
            dto.setCourseId(review.getCourseId());
            dto.setRating(review.getRating());
            dto.setDifficulty(review.getDifficulty());
            dto.setWorkload(review.getWorkload());
            dto.setSatisfaction(review.getSatisfaction());
            dto.setPriorRequirements(review.getPriorRequirements());
            dto.setExamInfo(review.getExamInfo());
            dto.setText(review.getText());
            dto.setCreatedAt(review.getCreatedAt());
            dto.setUpdatedAt(review.getUpdatedAt());
            dto.setModerationFlagged(review.isModerationFlagged());
            dto.setModerationReason(review.getModerationReason());

            // 🔥 enrichment
            dto.setUserName(userLookupService.getUserName(review.getUserId()));
            dto.setCourseTitle(courseLookupService.getCourseTitle(review.getCourseId()));

            return dto;
        }).toList();
    }


}
