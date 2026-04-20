package at.campus.backend.security;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class UserContextTest {

    @Test
    void normalizesRolesOnWriteAndLookup() {
        UserContext userContext = new UserContext();

        userContext.setRoles(Set.of("Moderator", " student "));

        assertThat(userContext.getRoles())
                .containsExactlyInAnyOrder(Roles.MODERATOR, Roles.STUDENT);
        assertThat(userContext.hasRole("Moderator")).isTrue();
        assertThat(userContext.hasRole("MODERATOR")).isTrue();
        assertThat(userContext.hasRole("student")).isTrue();
    }

    @Test
    void trimsDisplayNameAndFallsBackToUserId() {
        UserContext userContext = new UserContext();

        userContext.setUserId("user-123");
        userContext.setDisplayName("  Trusted Nick  ");

        assertThat(userContext.getDisplayName()).isEqualTo("Trusted Nick");
        assertThat(userContext.getEffectiveDisplayName()).isEqualTo("Trusted Nick");

        userContext.setDisplayName("   ");

        assertThat(userContext.getDisplayName()).isNull();
        assertThat(userContext.getEffectiveDisplayName()).isEqualTo("user-123");
    }
}
