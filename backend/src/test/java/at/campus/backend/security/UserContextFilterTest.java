package at.campus.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class UserContextFilterTest {

    @Test
    void populatesNormalizedRolesFromHeader() throws Exception {
        UserContext userContext = new UserContext();
        UserContextFilter filter = new UserContextFilter(userContext);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-User-Id", "user-123");
        request.addHeader("X-User-Roles", "Moderator, STUDENT");
        request.addHeader("X-User-Name", "Trusted Nick");

        filter.doFilter(request, new MockHttpServletResponse(), new MockFilterChain());

        assertThat(userContext.getUserId()).isEqualTo("user-123");
        assertThat(userContext.getRoles())
                .containsExactlyInAnyOrder(Roles.MODERATOR, Roles.STUDENT);
        assertThat(userContext.getDisplayName()).isEqualTo("Trusted Nick");
    }
}
