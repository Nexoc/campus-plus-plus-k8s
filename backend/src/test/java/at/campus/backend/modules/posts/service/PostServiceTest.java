package at.campus.backend.modules.posts.service;

import at.campus.backend.common.exception.ForbiddenException;
import at.campus.backend.modules.comments.repository.CommentRepository;
import at.campus.backend.modules.posts.model.Post;
import at.campus.backend.modules.posts.model.PostDto;
import at.campus.backend.modules.posts.model.UpdatePostRequest;
import at.campus.backend.modules.posts.repository.PostRepository;
import at.campus.backend.modules.watch.service.NotificationService;
import at.campus.backend.modules.watch.service.WatchService;
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
class PostServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private WatchService watchService;

    @Mock
    private NotificationService notificationService;

    private UserContext userContext;
    private PostService postService;

    @BeforeEach
    void setUp() {
        userContext = new UserContext();
        postService = new PostService(
                postRepository,
                commentRepository,
                userContext,
                watchService,
                notificationService
        );
    }

    @Test
    void moderatorCanUpdatePostWhenRoleHeaderUsesLegacyCase() {
        UUID postId = UUID.randomUUID();
        Post post = new Post();
        post.setId(postId);
        post.setThreadId(UUID.randomUUID());
        post.setUserId(UUID.randomUUID());
        post.setContent("Old content");

        userContext.setUserId(UUID.randomUUID().toString());
        userContext.setRoles(Set.of("Moderator"));

        when(postRepository.findById(postId))
                .thenReturn(Optional.of(post), Optional.of(post));

        PostDto updated = postService.updatePost(
                postId,
                new UpdatePostRequest("New content")
        );

        assertThat(updated.getContent()).isEqualTo("New content");
        verify(postRepository).update(post);
    }

    @Test
    void nonModeratorCannotDeleteAnotherUsersPost() {
        UUID postId = UUID.randomUUID();
        Post post = new Post();
        post.setId(postId);
        post.setUserId(UUID.randomUUID());

        userContext.setUserId(UUID.randomUUID().toString());
        userContext.setRoles(Set.of(Roles.STUDENT));

        when(postRepository.findById(postId)).thenReturn(Optional.of(post));

        assertThatThrownBy(() -> postService.deletePost(postId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Only the author or a moderator can delete this post");
    }

    @Test
    void createPostUsesTrustedDisplayNameFromUserContext() {
        UUID threadId = UUID.randomUUID();

        userContext.setUserId(UUID.randomUUID().toString());
        userContext.setDisplayName("Trusted Nick");
        when(postRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        PostDto created = postService.createPost(threadId, new at.campus.backend.modules.posts.model.CreatePostRequest("Post content"));

        assertThat(created.getUserName()).isEqualTo("Trusted Nick");
        verify(postRepository).save(argThat(post ->
                "Trusted Nick".equals(post.getUserName())));
    }
}
