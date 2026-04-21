package at.campus.backend.modules.comments.api;

import at.campus.backend.common.exception.ForbiddenException;
import at.campus.backend.common.exception.GlobalExceptionHandler;
import at.campus.backend.modules.comments.model.CommentDto;
import at.campus.backend.modules.comments.service.CommentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CommentControllerTest {

    @Mock
    private CommentService commentService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new CommentController(commentService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void deleteCommentReturnsForbiddenInsteadOfInternalServerError() throws Exception {
        UUID commentId = UUID.randomUUID();

        doThrow(new ForbiddenException("Only the author or a moderator can delete this comment"))
                .when(commentService)
                .deleteComment(commentId);

        mockMvc.perform(delete("/api/comments/{commentId}", commentId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message")
                        .value("Only the author or a moderator can delete this comment"));
    }

    @Test
    void createCommentIgnoresLegacyUserNameFieldFromClient() throws Exception {
        UUID postId = UUID.randomUUID();
        UUID commentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        CommentDto comment = new CommentDto(
                commentId,
                postId,
                userId,
                "Trusted Nick",
                "Trusted content",
                LocalDateTime.now(),
                LocalDateTime.now()
        );

        when(commentService.createComment(argThat(id -> postId.equals(id)), argThat(request ->
                request != null && "Trusted content".equals(request.getContent()))))
                .thenReturn(comment);

        mockMvc.perform(post("/api/posts/{postId}/comments", postId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "content": "Trusted content",
                                  "userName": "Spoofed Client Name"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.content").value("Trusted content"))
                .andExpect(jsonPath("$.userName").value("Trusted Nick"));

        verify(commentService).createComment(argThat(id -> postId.equals(id)), argThat(request ->
                request != null && "Trusted content".equals(request.getContent())));
    }
}
