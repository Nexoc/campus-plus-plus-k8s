package at.campus.backend.modules.threads.service;

import at.campus.backend.common.exception.ForbiddenException;
import at.campus.backend.modules.threads.model.Thread;
import at.campus.backend.modules.threads.model.UpdateThreadRequest;
import at.campus.backend.modules.threads.repository.ThreadRepository;
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
class ThreadServiceTest {

    @Mock
    private ThreadRepository threadRepository;

    @Mock
    private WatchService watchService;

    @Mock
    private NotificationService notificationService;

    private UserContext userContext;
    private ThreadService threadService;

    @BeforeEach
    void setUp() {
        userContext = new UserContext();
        threadService = new ThreadService(
                threadRepository,
                userContext,
                watchService,
                notificationService
        );
    }

    @Test
    void moderatorCanUpdateThreadWhenRoleHeaderUsesLegacyCase() {
        UUID threadId = UUID.randomUUID();
        Thread thread = new Thread();
        thread.setId(threadId);
        thread.setCreatedBy(UUID.randomUUID());
        thread.setTitle("Old title");
        thread.setContent("Old content");

        userContext.setUserId(UUID.randomUUID().toString());
        userContext.setRoles(Set.of("Moderator"));

        when(threadRepository.findById(threadId)).thenReturn(Optional.of(thread));

        Thread updated = threadService.updateThread(
                threadId,
                new UpdateThreadRequest("New title", "New content")
        );

        assertThat(updated.getTitle()).isEqualTo("New title");
        assertThat(updated.getContent()).isEqualTo("New content");
        verify(threadRepository).update(thread);
    }

    @Test
    void nonModeratorCannotDeleteAnotherUsersThread() {
        UUID threadId = UUID.randomUUID();
        Thread thread = new Thread();
        thread.setId(threadId);
        thread.setCreatedBy(UUID.randomUUID());

        userContext.setUserId(UUID.randomUUID().toString());
        userContext.setRoles(Set.of(Roles.STUDENT));

        when(threadRepository.findById(threadId)).thenReturn(Optional.of(thread));

        assertThatThrownBy(() -> threadService.deleteThread(threadId))
                .isInstanceOf(ForbiddenException.class)
                .hasMessage("Only the author or a moderator can delete this thread");
    }

    @Test
    void createThreadUsesTrustedDisplayNameFromUserContext() {
        UUID courseId = UUID.randomUUID();

        userContext.setUserId(UUID.randomUUID().toString());
        userContext.setDisplayName("Trusted Nick");
        when(threadRepository.findById(any(UUID.class))).thenReturn(Optional.empty());

        Thread created = threadService.createThread(courseId, "Thread title", "Thread content");

        assertThat(created.getCreatedByName()).isEqualTo("Trusted Nick");
        verify(threadRepository).save(argThat(thread ->
                "Trusted Nick".equals(thread.getCreatedByName())));
    }
}
