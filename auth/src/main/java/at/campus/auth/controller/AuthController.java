package at.campus.auth.controller;

import java.util.Locale;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import at.campus.auth.dto.AccountResponse;
import at.campus.auth.dto.AuthResponse;
import at.campus.auth.dto.LoginRequest;
import at.campus.auth.dto.RegisterRequest;
import at.campus.auth.model.User;
import at.campus.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;


/**
 * AuthController
 *
 * REST controller responsible for authentication and identity-related endpoints.
 *
 * SECURITY MODEL OVERVIEW
 * --------------------------------------------------
 * Authentication:
 * - Stateless JWT-based authentication
 * - JWT is returned on successful login
 * - JWT is sent via Authorization header
 *
 * CSRF:
 * - CSRF is NOT required for login and registration
 * - CSRF IS required for authenticated browser actions
 *   (account, admin, sensitive state-changing endpoints)
 *
 * Endpoints:
 * --------------------------------------------------
 * POST /auth/register   - Create new user account (NO CSRF)
 * POST /auth/login      - Authenticate user and issue JWT (NO CSRF)
 * GET  /auth/validate   - Internal JWT validation (NGINX auth_request)
 * GET  /auth/csrf       - CSRF bootstrap for browser clients
 * GET  /auth/me         - Current authenticated user info
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Logger log =
            LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /* =====================================================
       REGISTER
       ===================================================== */

    /**
     * Registers a new user account.
     *
     * Security notes:
     * - This endpoint is PUBLIC (permitAll)
     * - CSRF protection is NOT required
     * - Protected by credentials only
     */
    @Operation(
            summary = "Register a new user",
            description = "Creates a new user account with email and password"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "User successfully registered"),
            @ApiResponse(responseCode = "409", description = "Email already exists"),
            @ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema()))
    })
    @PostMapping(
            value = "/register",
            consumes = "application/json"
    )
    @ResponseStatus(HttpStatus.CREATED)
    public void register(
            @Valid @RequestBody RegisterRequest request
    ) {
        log.info("Received registration request for email={}", request.getEmail());

        authService.register(
                request.getEmail(),
                request.getPassword(),
                request.getNickname()
        );

        log.info("Registration completed for email={}", request.getEmail());
    }

    /* =====================================================
       LOGIN
       ===================================================== */

    /**
     * Authenticates user credentials and returns a JWT token.
     *
     * Security notes:
     * - This endpoint is PUBLIC (permitAll)
     * - CSRF protection is NOT required
     * - Stateless authentication
     */
    @Operation(
            summary = "Authenticate user",
            description = "Authenticates user credentials and returns a JWT token"
    )
    @ApiResponses({
            @ApiResponse(
                    responseCode = "200",
                    description = "Authentication successful",
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = AuthResponse.class)
                    )
            ),
            @ApiResponse(responseCode = "401", description = "Invalid credentials"),
            @ApiResponse(responseCode = "400", description = "Validation error",
                    content = @Content(schema = @Schema()))
    })
    @PostMapping(
            value = "/login",
            consumes = "application/json",
            produces = "application/json"
    )
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request
    ) {
        log.info("Received login request for email={}", request.getEmail());

        AuthResponse response = authService.login(
                request.getEmail(),
                request.getPassword()
        );

        log.info("Login successful for email={}", request.getEmail());

        return ResponseEntity.ok(response);
    }

    /* =====================================================
       VALIDATE (INTERNAL)
       ===================================================== */

    /**
     * Internal JWT validation endpoint.
     *
     * Purpose:
     * - Used by NGINX auth_request module
     * - Validates JWT token
     * - Exposes user metadata via headers
     *
     * This endpoint MUST NOT be exposed to public clients.
     */
    @Operation(
            summary = "Validate JWT token (internal)",
            description = "Used by NGINX auth_request to validate JWT and extract user info"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Token is valid"),
            @ApiResponse(responseCode = "401", description = "Token is invalid or expired")
    })
    @GetMapping("/validate")
    public ResponseEntity<Void> validate(Authentication authentication) {

        User user = (User) authentication.getPrincipal();

        return ResponseEntity.ok()
                .header("X-User-Id", user.getId().toString())
                .header("X-User-Roles", toGatewayRoleHeader(user))
                .header("X-User-Name", resolveDisplayName(user))
                .build();
    }

    private String toGatewayRoleHeader(User user) {
        return user.getRole().name().toUpperCase(Locale.ROOT);
    }

    private String resolveDisplayName(User user) {
        String nickname = user.getNickname();
        if (nickname != null && !nickname.isBlank()) {
            return nickname;
        }

        return user.getEmail();
    }

    /* =====================================================
       CSRF BOOTSTRAP
       ===================================================== */

    /**
     * CSRF bootstrap endpoint.
     *
     * Purpose:
     * - Forces Spring Security to generate a CSRF token
     * - CSRF token is stored in a cookie (XSRF-TOKEN)
     *
     */
    @PostMapping("/csrf")
    public ResponseEntity<Void> csrf() {
        // CsrfFilter сработает ДО контроллера
        // Токен будет создан автоматически
        return ResponseEntity.noContent().build();
    }




    /* =====================================================
       CURRENT USER
       ===================================================== */
    /**
     * Returns profile information of the currently authenticated user.
     *
     * Security notes:
     * - Requires valid JWT
     * - CSRF required for browser clients (GET is safe)
     */
    @Operation(
            summary = "Get current user info",
            description = "Returns profile data of the currently authenticated user"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current user data returned"),
            @ApiResponse(responseCode = "401", description = "Unauthorized")
    })
    @GetMapping("/me")
    public ResponseEntity<AccountResponse> me() {

        User user = authService.getCurrentUser();

        AccountResponse response = new AccountResponse(
                user.getId(),
                user.getEmail(),
                user.getNickname(),
                user.getRole()
        );

        return ResponseEntity.ok(response);
    }
}
