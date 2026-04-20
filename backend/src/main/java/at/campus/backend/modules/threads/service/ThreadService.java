package at.campus.backend.modules.threads.service;

import at.campus.backend.common.exception.ForbiddenException;
import at.campus.backend.common.exception.NotFoundException;
import at.campus.backend.modules.threads.model.Thread;
import at.campus.backend.modules.threads.model.UpdateThreadRequest;
import at.campus.backend.modules.threads.repository.ThreadRepository;
import at.campus.backend.modules.watch.model.WatchTargetType;
import at.campus.backend.modules.watch.service.NotificationService;
import at.campus.backend.modules.watch.service.WatchService;
import at.campus.backend.security.Roles;
import at.campus.backend.security.UserContext;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

/**
 * ThreadService handles business logic for threads.
 */
@Service
public class ThreadService {

    private final ThreadRepository threadRepository;
    private final UserContext userContext;
    private final WatchService watchService;
    private final NotificationService notificationService;

    public ThreadService(
            ThreadRepository threadRepository, 
            UserContext userContext,
            WatchService watchService,
            NotificationService notificationService
    ) {
        this.threadRepository = threadRepository;
        this.userContext = userContext;
        this.watchService = watchService;
        this.notificationService = notificationService;
    }

    /**
     * Get all threads for a course (public operation).
     */
    public List<Thread> getThreadsByCourseId(UUID courseId) {
        return threadRepository.findByCourseId(courseId);
    }

    /**
     * Get a specific thread by ID (public operation).
     */
    public Thread getThreadById(UUID threadId) {
        return threadRepository.findById(threadId)
            .orElseThrow(() -> new NotFoundException("Thread not found: " + threadId));
    }

    /**
     * Create a new thread (requires authentication).
     * Authorization: Any authenticated user can create a thread.
     */
    public Thread createThread(UUID courseId, String title, String content) {
        if (userContext.getUserId() == null) {
            throw new ForbiddenException("Authentication required to create a thread");
        }
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Thread title is required");
        }

        Thread thread = new Thread();
        thread.setId(UUID.randomUUID());
        thread.setCourseId(courseId);
        thread.setTitle(title);
        thread.setContent(content);
        thread.setCreatedBy(UUID.fromString(userContext.getUserId()));
        thread.setCreatedByName(userContext.getEffectiveDisplayName());

        threadRepository.save(thread);
        
        // Fetch the saved thread to get the timestamp
        Thread savedThread = threadRepository.findById(thread.getId()).orElse(thread);
        
        // Notify watchers of the course (fire-and-forget, safe failure)
        try {
            List<UUID> watchers = watchService.getUsersWatchingTarget(
                WatchTargetType.COURSE, courseId);
            if (!watchers.isEmpty()) {
                notificationService.notifyNewThread(courseId, savedThread.getId(), savedThread.getTitle(), watchers);
            }
        } catch (Exception e) {
            // Don't fail thread creation if notification fails
        }
        
        return savedThread;
    }

    /**
     * Update a thread (author or moderator only).
     */
    public Thread updateThread(UUID threadId, UpdateThreadRequest request) {
        if (userContext.getUserId() == null) {
            throw new ForbiddenException("Only authenticated users can update threads");
        }

        Thread thread = threadRepository.findById(threadId)
            .orElseThrow(() -> new NotFoundException("Thread not found: " + threadId));

        // Check if user is the author or a moderator
        boolean isAuthor = thread.getCreatedBy().toString().equals(userContext.getUserId());
        boolean isModerator = userContext.hasRole(Roles.MODERATOR);

        if (!isAuthor && !isModerator) {
            throw new ForbiddenException("Only the author or a moderator can update this thread");
        }

        if (request.getTitle() != null && !request.getTitle().isBlank()) {
            thread.setTitle(request.getTitle());
        }
        thread.setContent(request.getContent());

        threadRepository.update(thread);
        
        // Fetch the updated thread
        return threadRepository.findById(threadId).orElse(thread);
    }

    /**
     * Delete a thread (author or moderator only).
     */
    public void deleteThread(UUID threadId) {
        if (userContext.getUserId() == null) {
            throw new ForbiddenException("Only authenticated users can delete threads");
        }

        Thread thread = threadRepository.findById(threadId)
            .orElseThrow(() -> new NotFoundException("Thread not found: " + threadId));

        // Check if user is the author or a moderator
        boolean isAuthor = thread.getCreatedBy().toString().equals(userContext.getUserId());
        boolean isModerator = userContext.hasRole(Roles.MODERATOR);

        if (!isAuthor && !isModerator) {
            throw new ForbiddenException("Only the author or a moderator can delete this thread");
        }

        threadRepository.deleteById(threadId);
    }
}
