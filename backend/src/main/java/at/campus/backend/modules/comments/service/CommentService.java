package at.campus.backend.modules.comments.service;

import at.campus.backend.common.exception.ForbiddenException;
import at.campus.backend.common.exception.NotFoundException;
import at.campus.backend.modules.comments.model.Comment;
import at.campus.backend.modules.comments.model.CommentDto;
import at.campus.backend.modules.comments.model.CreateCommentRequest;
import at.campus.backend.modules.comments.model.UpdateCommentRequest;
import at.campus.backend.modules.comments.repository.CommentRepository;
import at.campus.backend.security.Roles;
import at.campus.backend.security.UserContext;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for managing comments.
 */
@Service
public class CommentService {

    private final CommentRepository commentRepository;
    private final UserContext userContext;

    public CommentService(CommentRepository commentRepository, UserContext userContext) {
        this.commentRepository = commentRepository;
        this.userContext = userContext;
    }

    /**
     * Get all comments for a post.
     */
    public List<CommentDto> getCommentsByPostId(UUID postId) {
        return commentRepository.findByPostId(postId)
            .stream()
            .map(CommentDto::fromDomain)
            .collect(Collectors.toList());
    }

    /**
     * Get a specific comment by ID.
     */
    public Optional<CommentDto> getCommentById(UUID commentId) {
        return commentRepository.findById(commentId)
            .map(CommentDto::fromDomain);
    }

    /**
     * Create a new comment (authenticated users only).
     */
    public CommentDto createComment(UUID postId, CreateCommentRequest request) {
        if (userContext.getUserId() == null) {
            throw new ForbiddenException("Only authenticated users can create comments");
        }

        Comment comment = new Comment();
        comment.setId(UUID.randomUUID());
        comment.setPostId(postId);
        comment.setUserId(UUID.fromString(userContext.getUserId()));
        comment.setUserName(userContext.getEffectiveDisplayName());
        comment.setContent(request.getContent());

        commentRepository.save(comment);

        // Fetch the saved comment
        Comment savedComment = commentRepository.findById(comment.getId()).orElse(comment);
        return CommentDto.fromDomain(savedComment);
    }

    /**
     * Update a comment (author or moderator only).
     */
    public CommentDto updateComment(UUID commentId, UpdateCommentRequest request) {
        if (userContext.getUserId() == null) {
            throw new ForbiddenException("Only authenticated users can update comments");
        }

        Comment comment = commentRepository.findById(commentId)
            .orElseThrow(() -> new NotFoundException("Comment not found: " + commentId));

        // Check authorization: author or moderator
        boolean isAuthor = comment.getUserId().equals(UUID.fromString(userContext.getUserId()));
        boolean isModerator = userContext.hasRole(Roles.MODERATOR);

        if (!isAuthor && !isModerator) {
            throw new ForbiddenException("Only the author or a moderator can update this comment");
        }

        comment.setContent(request.getContent());
        commentRepository.update(comment);

        // Fetch the updated comment
        Comment updatedComment = commentRepository.findById(commentId).orElse(comment);
        return CommentDto.fromDomain(updatedComment);
    }

    /**
     * Delete a comment (author or moderator only).
     */
    public void deleteComment(UUID commentId) {
        if (userContext.getUserId() == null) {
            throw new ForbiddenException("Only authenticated users can delete comments");
        }

        Comment comment = commentRepository.findById(commentId)
            .orElseThrow(() -> new NotFoundException("Comment not found: " + commentId));

        // Check authorization: author or moderator
        boolean isAuthor = comment.getUserId().equals(UUID.fromString(userContext.getUserId()));
        boolean isModerator = userContext.hasRole(Roles.MODERATOR);

        if (!isAuthor && !isModerator) {
            throw new ForbiddenException("Only the author or a moderator can delete this comment");
        }

        commentRepository.deleteById(commentId);
    }
}
