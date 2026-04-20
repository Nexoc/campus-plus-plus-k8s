package at.campus.backend.modules.posts.service;

import at.campus.backend.common.exception.ForbiddenException;
import at.campus.backend.common.exception.NotFoundException;
import at.campus.backend.modules.posts.model.Post;
import at.campus.backend.modules.posts.model.PostDto;
import at.campus.backend.modules.posts.model.CreatePostRequest;
import at.campus.backend.modules.posts.model.UpdatePostRequest;
import at.campus.backend.modules.posts.repository.PostRepository;
import at.campus.backend.modules.comments.repository.CommentRepository;
import at.campus.backend.modules.watch.model.WatchTargetType;
import at.campus.backend.modules.watch.service.NotificationService;
import at.campus.backend.modules.watch.service.WatchService;
import at.campus.backend.security.Roles;
import at.campus.backend.security.UserContext;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing posts.
 */
@Service
public class PostService {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final UserContext userContext;
    private final WatchService watchService;
    private final NotificationService notificationService;

    public PostService(
            PostRepository postRepository, 
            CommentRepository commentRepository, 
            UserContext userContext,
            WatchService watchService,
            NotificationService notificationService
    ) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.userContext = userContext;
        this.watchService = watchService;
        this.notificationService = notificationService;
    }

    /**
     * Get all posts for a thread.
     */
    public List<PostDto> getPostsByThreadId(UUID threadId) {
        return postRepository.findByThreadId(threadId)
            .stream()
            .map(post -> {
                Integer commentCount = commentRepository.getCommentCountByPostId(post.getId());
                return PostDto.fromDomain(post, commentCount);
            })
            .collect(Collectors.toList());
    }

    /**
     * Get a specific post by ID.
     */
    public Optional<PostDto> getPostById(UUID postId) {
        return postRepository.findById(postId)
            .map(post -> {
                Integer commentCount = commentRepository.getCommentCountByPostId(post.getId());
                return PostDto.fromDomain(post, commentCount);
            });
    }

    /**
     * Create a new post (authenticated users only).
     */
    public PostDto createPost(UUID threadId, CreatePostRequest request) {
        if (userContext.getUserId() == null) {
            throw new ForbiddenException("Only authenticated users can create posts");
        }

        Post post = new Post();
        post.setId(UUID.randomUUID());
        post.setThreadId(threadId);
        post.setUserId(UUID.fromString(userContext.getUserId()));
        post.setUserName(userContext.getEffectiveDisplayName());
        post.setContent(request.getContent());

        postRepository.save(post);

        // Fetch the saved post
        Post savedPost = postRepository.findById(post.getId()).orElse(post);
        
        // Notify watchers of the thread (fire-and-forget, safe failure)
        try {
            List<UUID> watchers = watchService.getUsersWatchingTarget(
                WatchTargetType.THREAD, threadId);
            if (!watchers.isEmpty()) {
                notificationService.notifyNewPost(threadId, savedPost.getId(), watchers);
            }
        } catch (Exception e) {
            // Don't fail post creation if notification fails
        }
        
        return PostDto.fromDomain(savedPost, 0);
    }

    /**
     * Update a post (author or moderator only).
     */
    public PostDto updatePost(UUID postId, UpdatePostRequest request) {
        if (userContext.getUserId() == null) {
            throw new ForbiddenException("Only authenticated users can update posts");
        }

        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new NotFoundException("Post not found: " + postId));

        // Check authorization: author or moderator
        boolean isAuthor = post.getUserId().equals(UUID.fromString(userContext.getUserId()));
        boolean isModerator = userContext.hasRole(Roles.MODERATOR);

        if (!isAuthor && !isModerator) {
            throw new ForbiddenException("Only the author or a moderator can update this post");
        }

        post.setContent(request.getContent());
        postRepository.update(post);

        // Fetch the updated post
        Post updatedPost = postRepository.findById(postId).orElse(post);
        return PostDto.fromDomain(updatedPost, 0);
    }

    /**
     * Delete a post (author or moderator only).
     */
    public void deletePost(UUID postId) {
        if (userContext.getUserId() == null) {
            throw new ForbiddenException("Only authenticated users can delete posts");
        }

        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new NotFoundException("Post not found: " + postId));

        // Check authorization: author or moderator
        boolean isAuthor = post.getUserId().equals(UUID.fromString(userContext.getUserId()));
        boolean isModerator = userContext.hasRole(Roles.MODERATOR);

        if (!isAuthor && !isModerator) {
            throw new ForbiddenException("Only the author or a moderator can delete this post");
        }

        postRepository.deleteById(postId);
    }
}
