package at.campus.backend.modules.threads.api;

import at.campus.backend.modules.threads.model.CreateThreadRequest;
import at.campus.backend.modules.threads.model.ThreadDto;
import at.campus.backend.modules.threads.model.UpdateThreadRequest;
import at.campus.backend.modules.threads.service.ThreadService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Protected thread endpoints (requires authentication via NGINX gateway).
 *
 * Endpoints:
 * - POST /api/courses/{courseId}/threads — Create thread
 * - DELETE /api/threads/{threadId} — Delete thread (moderator only)
 */
@RestController
@RequestMapping("/api")
public class ThreadController {

    private final ThreadService service;

    public ThreadController(ThreadService service) {
        this.service = service;
    }

    /**
     * Create a new thread.
     *
     * Authorization:
     * - Only authenticated users
     */
    @PostMapping("/courses/{courseId}/threads")
    public ThreadDto createThread(
        @PathVariable UUID courseId,
        @RequestBody CreateThreadRequest request
    ) {
        var thread = service.createThread(courseId, request.getTitle(), request.getContent());
        return ThreadDto.fromDomain(thread, 0);
    }

    /**
     * Update a thread.
     *
     * Authorization:
     * - Thread author or moderator
     */
    @PutMapping("/threads/{threadId}")
    public ThreadDto updateThread(
        @PathVariable UUID threadId,
        @RequestBody UpdateThreadRequest request
    ) {
        var thread = service.updateThread(threadId, request);
        return ThreadDto.fromDomain(thread, 0);
    }

    /**
     * Delete a thread.
     *
     * Authorization:
     * - Thread author or moderator
     */
    @DeleteMapping("/threads/{threadId}")
    public void deleteThread(@PathVariable UUID threadId) {
        service.deleteThread(threadId);
    }
}
