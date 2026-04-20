package at.campus.backend.modules.threads.api;

import at.campus.backend.common.exception.ForbiddenException;
import at.campus.backend.common.exception.GlobalExceptionHandler;
import at.campus.backend.modules.threads.model.Thread;
import at.campus.backend.modules.threads.service.ThreadService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ThreadControllerTest {

    @Mock
    private ThreadService threadService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new ThreadController(threadService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void deleteThreadReturnsForbiddenInsteadOfInternalServerError() throws Exception {
        UUID threadId = UUID.randomUUID();

        doThrow(new ForbiddenException("Only the author or a moderator can delete this thread"))
                .when(threadService)
                .deleteThread(threadId);

        mockMvc.perform(delete("/api/threads/{threadId}", threadId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.message")
                        .value("Only the author or a moderator can delete this thread"));
    }

    @Test
    void createThreadIgnoresLegacyUserNameFieldFromClient() throws Exception {
        UUID courseId = UUID.randomUUID();
        UUID threadId = UUID.randomUUID();

        Thread thread = new Thread();
        thread.setId(threadId);
        thread.setCourseId(courseId);
        thread.setTitle("Trusted title");
        thread.setContent("Trusted content");
        thread.setCreatedByName("Trusted Nick");

        when(threadService.createThread(courseId, "Trusted title", "Trusted content"))
                .thenReturn(thread);

        mockMvc.perform(post("/api/courses/{courseId}/threads", courseId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Trusted title",
                                  "content": "Trusted content",
                                  "userName": "Spoofed Client Name"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Trusted title"))
                .andExpect(jsonPath("$.createdByName").value("Trusted Nick"));

        verify(threadService).createThread(courseId, "Trusted title", "Trusted content");
    }
}
