package at.campus.auth.controller;

import at.campus.auth.dto.AuthResponse;
import at.campus.auth.model.User;
import at.campus.auth.model.UserRole;
import at.campus.auth.security.JwtAuthenticationFilter;
import at.campus.auth.service.AuthService;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;

import org.springframework.http.MediaType;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller slice test for AuthController.
 *
 * IMPORTANT:
 * - Uses @WebMvcTest -> loads only MVC layer (controller + MVC infrastructure)
 * - Security filters are NOT executed (addFilters = false)
 * - Custom security beans MUST be mocked explicitly
 */
@WebMvcTest(AuthController.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * Mocked service layer.
     * Controller delegates all business logic to this service.
     */
    @MockitoBean
    private AuthService authService;

    /**
     * CRITICAL:
     * JwtAuthenticationFilter is a @Component and would otherwise be created
     * by Spring during context initialization.
     *
     * Mocking it prevents Spring from trying to inject JwtService into it,
     * which is NOT part of a @WebMvcTest slice.
     */
    @MockitoBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    /**
     * These are usually wired in SecurityConfig.
     * Mocking them avoids "missing bean" errors in controller slice tests.
     */
    @MockitoBean
    private AuthenticationEntryPoint authenticationEntryPoint;

    @MockitoBean
    private AccessDeniedHandler accessDeniedHandler;

    /**
     * Always clean up SecurityContext between tests
     * to avoid test interference.
     */
    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void register_shouldReturn201() throws Exception {
        // GIVEN
        String json = """
            {
              "email": "newuser@test.com",
              "password": "StrongPass123",
              "nickname": "tester"
            }
            """;

        // WHEN / THEN
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated());

        verify(authService)
                .register("newuser@test.com", "StrongPass123", "tester");

        verifyNoMoreInteractions(authService);
    }

    @Test
    void login_shouldReturnJwtToken() throws Exception {
        // GIVEN
        String json = """
            {
              "email": "user@test.com",
              "password": "StrongPass123"
            }
            """;

        when(authService.login("user@test.com", "StrongPass123"))
                .thenReturn(new AuthResponse("jwt-token"));

        // WHEN / THEN
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-token"));

        verify(authService).login("user@test.com", "StrongPass123");
        verifyNoMoreInteractions(authService);
    }

    @Test
    void validate_shouldReturnHeaders() throws Exception {
        UUID fixedId = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");

        User user = new User(
                "admin@test.com",
                "admin",
                "hash",
                UserRole.Moderator
        );
        setUserIdViaReflection(user, fixedId);

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        user,
                        null,
                        user.getAuthorities()
                );

        mockMvc.perform(get("/auth/validate")
                        // IMPORTANT:
                        // Manually inject Authentication into request
                        .principal(authentication))
                .andExpect(status().isOk())
                .andExpect(header().string("X-User-Id", fixedId.toString()))
                .andExpect(header().string("X-User-Roles", "Moderator"))
                .andExpect(header().string("X-User-Name", "admin"));

        verifyNoInteractions(authService);
    }


    @Test
    void csrf_shouldReturn204() throws Exception {
        mockMvc.perform(post("/auth/csrf"))
                .andExpect(status().isNoContent());

        verifyNoInteractions(authService);
    }

    @Test
    void me_shouldReturnCurrentUser() throws Exception {
        // GIVEN
        User user = new User(
                "user@test.com",
                "tester",
                "hash",
                UserRole.STUDENT
        );

        when(authService.getCurrentUser()).thenReturn(user);

        // WHEN / THEN
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@test.com"))
                .andExpect(jsonPath("$.nickname").value("tester"))
                .andExpect(jsonPath("$.role").value("STUDENT"));

        verify(authService).getCurrentUser();
        verifyNoMoreInteractions(authService);
    }

    // --------------------------------------------------
    // Test helper methods
    // --------------------------------------------------

    /**
     * Sets private JPA-generated UUID field for stable and deterministic tests.
     *
     * This avoids persisting entities just to obtain an ID.
     */
    private static void setUserIdViaReflection(User user, UUID id) {
        try {
            Field field = User.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(user, id);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to set User.id via reflection", ex);
        }
    }
}
