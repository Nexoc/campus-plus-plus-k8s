package at.campus.backend.modules.posts.api;

import at.campus.backend.common.exception.ForbiddenException;
import at.campus.backend.common.exception.GlobalExceptionHandler;
import at.campus.backend.modules.posts.model.PostDto;
import at.campus.backend.modules.posts.service.PostService;
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
class PostControllerTest {

    @Mock
    private PostService postService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new PostController(postService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void deletePostReturnsForbiddenInsteadOfInternalServerError() throws Exception {
        UUID postId = UUID.randomUUID();

        doThrow(new ForbiddenException("Only the author or a moderator can delete this post"))
                .when(postService)
                .deletePost(postId);

        mockMvc.perform(delete("/api/posts/{postId}", postId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message")
                        .value("Only the author or a moderator can delete this post"));
    }

    @Test
    void createPostIgnoresLegacyUserNameFieldFromClient() throws Exception {
        UUID threadId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        PostDto post = new PostDto(
                postId,
                threadId,
                userId,
                "Trusted Nick",
                "Trusted content",
                LocalDateTime.now(),
                LocalDateTime.now(),
                0
        );

        when(postService.createPost(argThat(id -> threadId.equals(id)), argThat(request ->
                request != null && "Trusted content".equals(request.getContent()))))
                .thenReturn(post);

        mockMvc.perform(post("/api/threads/{threadId}/posts", threadId)
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

        verify(postService).createPost(argThat(id -> threadId.equals(id)), argThat(request ->
                request != null && "Trusted content".equals(request.getContent())));
    }
}
