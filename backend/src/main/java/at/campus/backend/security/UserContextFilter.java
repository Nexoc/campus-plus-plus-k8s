package at.campus.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * UserContextFilter
 * ==================================================
 *
 * Servlet filter responsible for populating UserContext
 * from HTTP headers injected by the gateway (Nginx).
 *
 * SECURITY MODEL
 * --------------------------------------------------
 * - Authentication is performed by Nginx via auth_request.
 * - Backend TRUSTS the gateway completely.
 * - Backend NEVER parses or validates JWT tokens.
 *
 * EXPECTED HEADERS
 * --------------------------------------------------
 *   X-User-Id     -> unique user identifier
 *   X-User-Roles  -> role or comma-separated roles
 *   X-User-Name   -> trusted display name resolved by the gateway
 *
 * LIFECYCLE
 * --------------------------------------------------
 * - Runs once per HTTP request
 * - Executed BEFORE controllers and services
 * - Populates request-scoped UserContext
 * - Does NOT perform authorization
 */
@Component
public class UserContextFilter extends OncePerRequestFilter {

    private static final String USER_ID_HEADER = "X-User-Id";
    private static final String USER_ROLES_HEADER = "X-User-Roles";
    private static final String USER_NAME_HEADER = "X-User-Name";

    private final UserContext userContext;

    /**
     * UserContext is injected by Spring.
     *
     * Because it is @RequestScope, this instance
     * is unique per HTTP request.
     */
    public UserContextFilter(UserContext userContext) {
        this.userContext = userContext;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // --------------------------------------------------
        // Extract identity headers injected by the gateway
        // --------------------------------------------------
        String userId = request.getHeader(USER_ID_HEADER);
        String roleHeader = request.getHeader(USER_ROLES_HEADER);
        String userName = request.getHeader(USER_NAME_HEADER);

        // --------------------------------------------------
        // Populate UserContext ONLY if userId is present
        // --------------------------------------------------
        // If X-User-Id is missing, the request is considered
        // unauthenticated and UserContext remains empty.
        if (userId != null) {

            userContext.setUserId(userId);
            userContext.setDisplayName(userName);

            // --------------------------------------------------
            // Populate roles ONLY if header is present and valid
            // --------------------------------------------------
            // The current auth service emits a single role, but the backend
            // accepts comma-separated values to stay robust if that changes.
            if (roleHeader != null) {
                Set<String> roles = Arrays.stream(roleHeader.split(","))
                        .map(String::trim)
                        .filter(role -> !role.isBlank())
                        .collect(Collectors.toSet());

                if (!roles.isEmpty()) {
                    userContext.setRoles(roles);
                }
            }
        }

        // --------------------------------------------------
        // Continue filter chain
        // --------------------------------------------------
        // No cleanup required:
        // UserContext is request-scoped and will be
        // destroyed automatically after request completion.
        filterChain.doFilter(request, response);
    }
}
