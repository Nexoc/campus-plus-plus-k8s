package at.campus.backend.modules.comments.service;

import at.campus.backend.common.exception.ForbiddenException;
import at.campus.backend.modules.comments.model.Comment;
import at.campus.backend.modules.comments.model.CommentDto;
import at.campus.backend.modules.comments.model.UpdateCommentRequest;
import at.campus.backend.modules.comments.repository.CommentRepository;
import at.campus.backend.security.Roles;
import at.campus.backend.security.UserContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;

    private UserContext userContext;
    private CommentService commentService;

    @BeforeEach
    void setUp() {
        userContext = new UserContext();
        commentService = new CommentService(commentRepository, userContext);
    }

    @Test
    void moderatorCanUpdateCommentWhenRoleHeaderUsesLegacyCase() {
        UUID commentId = UUID.randomUUID();
        Comment comment = new Comment();
        comment.setId(commentId);
        comment.setPostId(UUID.randomUUID());
        comment.setUserId(UUID.randomUUID());
        comment.setContent("Old content");

        userContext.setUserId(UUID.randomUUID().toString());
        userContext.setRoles(Set.of("Moderator"));

        when(commentRepository.findById(commentId))
                .thenReturn(Optional.of(comment), Optional.of(comment));

        CommentDto updated = commentService.updateComment(
                commentId,
                new UpdateCommentRequest("New content")
        );

        assertThat(updated.getContent()).isEqualTo("New content");
        verify(commentRepository).update(comment);
    }

    @Test
    void nonModeratorCannotDeleteAnotherUsersComment() {
        UUID commentId = UUID.randomUUID();
        Comment comment = new Comment();
        comment.setId(commentId);
        comment.setUserId(UUID.randomUUID());

        userContext.setUserId(UUID.randomUUID().toString());
        userContext.setRoles(Set.of(Roles.STUDENT));

        when(commentRepository.findById(commentId)).thenReturn(Optional.of(comment));

        assertThatThrownBy(() -> commentService.deleteComment(commentId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Only the author or a moderator can delete this comment");
    }

    @Test
    void createCommentUsesTrustedDisplayNameFromUserContext() {
        UUID postId = UUID.randomUUID();

        userContext.setUserId(UUID.randomUUID().toString());
        userContext.setDisplayName("Trusted Nick");
        when(commentRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        CommentDto created = commentService.createComment(
                postId,
                new at.campus.backend.modules.comments.model.CreateCommentRequest("Comment content")
        );

        assertThat(created.getUserName()).isEqualTo("Trusted Nick");
        verify(commentRepository).save(argThat(comment ->
                "Trusted Nick".equals(comment.getUserName())));
    }
}
